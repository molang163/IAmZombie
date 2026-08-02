package dev.molang.iamzombieq.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class JdkMigrationFileSystem
        implements MigrationFileSystem, MigrationDirectorySession.Factory {
    private final ContentOpenHook contentOpenHook;

    JdkMigrationFileSystem() {
        this(ContentOpenHook.none());
    }

    JdkMigrationFileSystem(ContentOpenHook contentOpenHook) {
        this.contentOpenHook =
                Objects.requireNonNull(contentOpenHook, "contentOpenHook");
    }

    MigrationPathState classify(Path path) {
        return observe(path).state();
    }

    MigrationPathState.Observation observe(Path path) {
        Objects.requireNonNull(path, "path");
        return MigrationPathState.observe(() -> readNofollowMetadata(path));
    }

    DirectoryObservation observeDirectory(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    path,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || !attributes.isDirectory()) {
                return new DirectoryObservation(
                        MigrationPathState.UNSAFE,
                        "path is a symbolic link or not a directory",
                        null);
            }
            if (attributes.fileKey() == null) {
                return new DirectoryObservation(
                        MigrationPathState.UNKNOWN,
                        "directory identity is unavailable",
                        null);
            }
            return new DirectoryObservation(
                    MigrationPathState.PRESENT,
                    "safe directory",
                    null);
        } catch (java.nio.file.NoSuchFileException absent) {
            return new DirectoryObservation(
                    MigrationPathState.ABSENT,
                    "directory is absent",
                    absent);
        } catch (IOException failure) {
            return new DirectoryObservation(
                    MigrationPathState.UNKNOWN,
                    "directory metadata failed: "
                            + failure.getClass().getSimpleName()
                            + ": "
                            + failure.getMessage(),
                    failure);
        } catch (RuntimeException failure) {
            return new DirectoryObservation(
                    MigrationPathState.UNKNOWN,
                    "directory metadata failed: "
                            + failure.getClass().getSimpleName()
                            + ": "
                            + failure.getMessage(),
                    failure);
        }
    }

    String validateRelativeBasename(String operand) {
        return MigrationDirectorySession.requireBasename(operand);
    }

    StoreSession openStore(
            MigrationAccessProfile profile,
            MigrationBinding binding,
            Path legacy) {
        return openStore(profile, binding, legacy, true);
    }

    StoreSession openStore(
            MigrationAccessProfile profile,
            MigrationBinding binding,
            Path legacy,
            boolean bindExternalLegacy) {
        try {
            return new StoreSession(
                    this,
                    profile,
                    binding,
                    legacy,
                    bindExternalLegacy);
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof MigrationFailure migrationFailure) {
                throw migrationFailure;
            }
            throw MigrationFailure.operational(
                    legacy,
                    binding.target(),
                    MigrationTargetState.Phase.NO_EVIDENCE,
                    "migration-store",
                    "bound-store-open",
                    "Could not open bound JDK migration store",
                    failure);
        }
    }

    @Override
    public MigrationPathState.Metadata readNofollowMetadata(Path path)
            throws IOException {
        Objects.requireNonNull(path, "path");
        return metadata(Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS));
    }

    @Override
    public MigrationBinding.Observation observeBinding(Path target)
            throws IOException {
        Path checkedTarget = normalizedTarget(target);
        Path logicalParent = checkedTarget.getParent();
        List<Path> ancestorPaths = ancestorPaths(logicalParent);
        ArrayList<MigrationBinding.Ancestor> ancestors =
                new ArrayList<>(ancestorPaths.size());

        BasicFileAttributes parentAttributes = null;
        for (Path ancestor : ancestorPaths) {
            BasicFileAttributes attributes = Files.readAttributes(
                    ancestor,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory()
                    || attributes.isSymbolicLink()
                    || attributes.fileKey() == null) {
                throw new IOException(
                        "Unsafe or untrusted migration ancestor: " + ancestor);
            }
            String identity = attributes.fileKey().toString();
            ancestors.add(new MigrationBinding.Ancestor(ancestor, identity));
            if (ancestor.equals(logicalParent)) {
                parentAttributes = attributes;
            }
        }
        if (parentAttributes == null) {
            throw new IOException(
                    "Could not observe migration target parent: " + logicalParent);
        }

        Path physicalParent = logicalParent.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!physicalParent.isAbsolute()
                || !physicalParent.equals(physicalParent.normalize())) {
            throw new IOException(
                    "Untrusted physical migration parent: " + physicalParent);
        }
        FileStore store = Files.getFileStore(physicalParent);
        String providerIdentity = logicalParent.getFileSystem()
                        .provider()
                        .getScheme()
                + ":"
                + logicalParent.getFileSystem()
                        .provider()
                        .getClass()
                        .getName();
        String fileStoreIdentity =
                store.name() + "|" + store.type() + "|" + store.getClass().getName();

        return new MigrationBinding.Observation(
                checkedTarget,
                logicalParent,
                physicalParent,
                ancestors,
                parentAttributes.fileKey().toString(),
                providerIdentity,
                fileStoreIdentity,
                Runtime.version().feature(),
                System.getProperty("os.name", "unknown"));
    }

    @Override
    public MigrationAccessProfile.Capabilities capabilities(
            MigrationBinding binding) throws IOException {
        Objects.requireNonNull(binding, "binding");
        binding.verifyUnchanged(observeBinding(binding.target()));

        var provider = binding.logicalParent().getFileSystem().provider();
        boolean defaultProvider =
                provider.equals(FileSystems.getDefault().provider());
        boolean secure = supportsSecureDirectoryStream(binding.physicalParent());
        boolean certifiedDefault = defaultProvider
                && provider.getScheme().equals("file")
                && (provider.getClass()
                                .getName()
                                .equals("sun.nio.fs.LinuxFileSystemProvider")
                        || provider.getClass()
                                .getName()
                                .equals("sun.nio.fs.WindowsFileSystemProvider"));

        MigrationAccessProfile.Capabilities result =
                new MigrationAccessProfile.Capabilities(
                        binding.operatingSystem(),
                        binding.javaFeature(),
                        provider.getScheme(),
                        provider.getClass().getName(),
                        defaultProvider,
                        secure,
                        certifiedDefault,
                        certifiedDefault,
                        certifiedDefault);
        binding.verifyUnchanged(observeBinding(binding.target()));
        return result;
    }

    @Override
    public MigrationDirectorySession openDirectorySession(
            MigrationAccessProfile profile, MigrationBinding binding) {
        return MigrationDirectorySession.open(profile, binding, this);
    }

    @Override
    public MigrationDirectorySession.Backend openSecure(MigrationBinding binding)
            throws IOException {
        Objects.requireNonNull(binding, "binding");
        binding.verifyUnchanged(observeBinding(binding.target()));
        DirectoryStream<Path> opened =
                Files.newDirectoryStream(binding.physicalParent());
        if (!(opened instanceof SecureDirectoryStream<?> rawSecure)) {
            opened.close();
            throw new IOException(
                    "Provider did not return SecureDirectoryStream");
        }
        @SuppressWarnings("unchecked")
        SecureDirectoryStream<Path> secure =
                (SecureDirectoryStream<Path>) rawSecure;
        try {
            binding.verifyUnchanged(observeBinding(binding.target()));
            return new SecureBackend(this, binding, secure);
        } catch (RuntimeException | IOException failure) {
            secure.close();
            throw failure;
        }
    }

    @Override
    public MigrationDirectorySession.Backend openBasic(MigrationBinding binding)
            throws IOException {
        Objects.requireNonNull(binding, "binding");
        binding.verifyUnchanged(observeBinding(binding.target()));
        return new BasicBackend(this, binding);
    }

    private boolean supportsSecureDirectoryStream(Path parent) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
            return stream instanceof SecureDirectoryStream<?>;
        } catch (IOException failure) {
            return false;
        }
    }

    private static MigrationPathState.Metadata metadata(
            BasicFileAttributes attributes) {
        String identity =
                attributes.fileKey() == null ? "" : attributes.fileKey().toString();
        return new MigrationPathState.Metadata(
                attributes.isRegularFile(),
                attributes.isSymbolicLink(),
                identity,
                attributes.size());
    }

    record DirectoryObservation(
            MigrationPathState state, String detail, Throwable cause) {
        DirectoryObservation {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(detail, "detail");
            if (detail.isBlank()) {
                throw new IllegalArgumentException(
                        "Directory observation detail must not be blank");
            }
        }
    }

    private static Path normalizedTarget(Path target) {
        Objects.requireNonNull(target, "target");
        if (!target.isAbsolute()
                || !target.equals(target.toAbsolutePath().normalize())
                || target.getParent() == null
                || target.getFileName() == null) {
            throw new IllegalArgumentException(
                    "Target must be a normalized absolute file path: " + target);
        }
        return target;
    }

    private static List<Path> ancestorPaths(Path parent) {
        ArrayList<Path> paths = new ArrayList<>();
        for (Path current = parent; current != null; current = current.getParent()) {
            paths.add(current);
        }
        Collections.reverse(paths);
        return List.copyOf(paths);
    }

    private static byte[] readAll(SeekableByteChannel channel) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        while (true) {
            int count = channel.read(buffer);
            if (count < 0) {
                break;
            }
            if (count == 0) {
                continue;
            }
            buffer.flip();
            output.write(buffer.array(), 0, buffer.remaining());
            buffer.clear();
        }
        return output.toByteArray();
    }

    private OperationalBackend openOperational(
            MigrationAccessProfile profile, MigrationBinding binding)
            throws IOException {
        return switch (Objects.requireNonNull(profile, "profile")) {
            case SECURE -> (OperationalBackend) openSecure(binding);
            case BASIC -> (OperationalBackend) openBasic(binding);
        };
    }

    private interface OperationalBackend
            extends MigrationDirectorySession.Backend {
        FileChannel openFile(String basename, Set<OpenOption> options)
                throws IOException;

        void atomicMove(String source, String destination) throws IOException;

        void forceDirectory() throws IOException;
    }

    static final class StoreSession
            implements ConfigMigrationEngine.Store, AutoCloseable {
        private final JdkMigrationFileSystem fileSystem;
        private final MigrationAccessProfile profile;
        private final MigrationBinding binding;
        private final Path legacy;
        private final OperationalBackend targetBackend;
        private final MigrationDirectorySession targetSession;
        private final MigrationBinding legacyBinding;
        private final OperationalBackend legacyBackend;
        private final MigrationDirectorySession legacySession;
        private final boolean externalLegacyBound;
        private LockPort lockPort;
        private boolean closed;

        private StoreSession(
                JdkMigrationFileSystem fileSystem,
                MigrationAccessProfile profile,
                MigrationBinding binding,
                Path legacy,
                boolean bindExternalLegacy)
                throws IOException {
            this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
            this.profile = Objects.requireNonNull(profile, "profile");
            this.binding = Objects.requireNonNull(binding, "binding");
            this.legacy = normalizedTarget(legacy);
            MigrationAccessProfile certifiedProfile =
                    MigrationAccessProfile.select(
                            fileSystem.capabilities(binding), false);
            if (certifiedProfile != profile) {
                throw new IllegalStateException(
                        "Requested migration profile "
                                + profile
                                + " differs from certified profile "
                                + certifiedProfile);
            }

            OperationalBackend openedTarget =
                    fileSystem.openOperational(profile, binding);
            MigrationDirectorySession openedTargetSession =
                    MigrationDirectorySession.adopt(
                            profile, binding, openedTarget);
            MigrationBinding openedLegacyBinding = binding;
            OperationalBackend openedLegacyBackend = openedTarget;
            MigrationDirectorySession openedLegacySession =
                    openedTargetSession;
            boolean openedExternalLegacy = false;
            try {
                if (bindExternalLegacy
                        && !binding.logicalParent().equals(
                                this.legacy.getParent())) {
                    openedLegacyBinding = MigrationBinding.capture(
                            fileSystem.observeBinding(this.legacy));
                    MigrationAccessProfile legacyProfile =
                            MigrationAccessProfile.select(
                                    fileSystem.capabilities(
                                            openedLegacyBinding),
                                    false);
                    if (legacyProfile != profile) {
                        throw new IOException(
                                "Legacy source and actual target require "
                                        + "different access profiles");
                    }
                    openedLegacyBackend = fileSystem.openOperational(
                            profile, openedLegacyBinding);
                    openedLegacySession = MigrationDirectorySession.adopt(
                            profile,
                            openedLegacyBinding,
                            openedLegacyBackend);
                    openedExternalLegacy = true;
                }
            } catch (RuntimeException | IOException failure) {
                openedTargetSession.close();
                throw failure;
            }
            this.targetBackend = openedTarget;
            this.targetSession = openedTargetSession;
            this.legacyBinding = openedLegacyBinding;
            this.legacyBackend = openedLegacyBackend;
            this.legacySession = openedLegacySession;
            this.externalLegacyBound = openedExternalLegacy;
        }

        @Override
        public MigrationPathState state(Path path) {
            return observe(path).state();
        }

        @Override
        public MigrationPathState.Observation observe(Path path) {
            ensureOpen();
            Path checked = normalizedTarget(path);
            OperationalBackend backend = backendFor(checked);
            return MigrationPathState.observe(() ->
                    backend.readNofollowMetadata(
                            checked.getFileName().toString()));
        }

        MigrationPathState.Metadata readNofollowMetadata(Path path)
                throws IOException {
            ensureOpen();
            Path checked = normalizedTarget(path);
            return backendFor(checked).readNofollowMetadata(
                    checked.getFileName().toString());
        }

        @Override
        public byte[] read(
                Path path, MigrationDirectorySession.ContentKind kind) {
            ensureOpen();
            Path checked = normalizedTarget(path);
            MigrationDirectorySession session = sessionFor(checked);
            try {
                return session.readBoundContent(
                        kind, checked.getFileName().toString());
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Could not read bound migration content " + checked,
                        failure);
            }
        }

        @Override
        public ConfigMigrationEngine.LockLease acquirePermanentLock(
                ConfigMigrationEngine.LockRequest request) {
            ensureOpen();
            requireTargetSibling(request.lock());
            if (!request.target().equals(binding.target())) {
                throw new IllegalArgumentException(
                        "Lock request target differs from bound target");
            }
            MigrationPathState.Observation lockObservation =
                    observe(request.lock());
            MigrationPathState lockState = lockObservation.state();
            String expectedIdentity = switch (lockState) {
                case ABSENT -> "";
                case PRESENT -> identity(request.lock());
                case UNKNOWN, UNSAFE -> throw new IllegalStateException(
                        "Permanent lock metadata is "
                                + lockState
                                + ": "
                                + lockObservation.detail(),
                        lockObservation.cause());
            };
            if (lockPort != null) {
                throw new IllegalStateException(
                        "Permanent lock acquisition was already attempted");
            }
            lockPort = new LockPort(targetBackend);
            PermanentMigrationLock.Acquisition acquired =
                    new PermanentMigrationLock(
                                    lockPort,
                                    request.faults(),
                                    request.phase())
                            .acquire(
                            new PermanentMigrationLock.Request(
                                    request.lock()
                                            .getFileName()
                                            .toString(),
                                    request.payload(),
                                    expectedIdentity,
                                    request.profile(),
                                    request.strongRequired(),
                                    request.allowEmptyFirstCreationRecovery()),
                            () -> request.allowEmptyFirstCreationRecovery()
                                    ? hasEmptyFirstCreationPortrait(request)
                                    : !request.requireTargetAbsent()
                                            || isSafelyAbsent(
                                                    request.target(),
                                                    "under-lock target"));
            boolean targetAbsent =
                    (!request.requireTargetAbsent()
                                    && !request.allowEmptyFirstCreationRecovery())
                            || isSafelyAbsent(
                                    request.target(),
                                    "post-initialization target");
            return new ConfigMigrationEngine.LockLease(
                    acquired.identity(),
                    acquired.payloadSha256(),
                    acquired.durability(),
                    acquired.createdNow(),
                    targetAbsent,
                    acquired.profile(),
                    acquired.recoveredEmptyFirstCreation());
        }

        private boolean hasEmptyFirstCreationPortrait(
                ConfigMigrationEngine.LockRequest request) {
            MigrationFileSystem.ArtifactPaths artifacts =
                    MigrationFileSystem.ArtifactPaths.forTarget(
                            request.target());
            if (!isSafelyPresent(
                    request.legacy(),
                    "empty-lock recovery legacy")) {
                throw new IllegalStateException(
                        "Empty-lock recovery legacy is absent: "
                                + request.legacy());
            }
            for (Path requiredAbsent : java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(
                                    artifacts.target(),
                                    artifacts.journal(),
                                    artifacts.backup(),
                                    artifacts.initial(),
                                    artifacts.marker()),
                            artifacts.fixedStages().stream())
                    .toList()) {
                if (!isSafelyAbsent(
                        requiredAbsent,
                        "empty-lock recovery artifact")) {
                    throw new IllegalStateException(
                            "Empty-lock recovery artifact is present: "
                                    + requiredAbsent);
                }
            }
            return true;
        }

        private boolean isSafelyAbsent(Path path, String description) {
            MigrationPathState.Observation observation = observe(path);
            return switch (observation.state()) {
                case ABSENT -> true;
                case PRESENT -> false;
                case UNKNOWN, UNSAFE -> throw new IllegalStateException(
                        description
                                + " metadata is "
                                + observation.state()
                                + ": "
                                + observation.detail(),
                        observation.cause());
            };
        }

        private boolean isSafelyPresent(Path path, String description) {
            MigrationPathState.Observation observation = observe(path);
            return switch (observation.state()) {
                case PRESENT -> true;
                case ABSENT -> false;
                case UNKNOWN, UNSAFE -> throw new IllegalStateException(
                        description
                                + " metadata is "
                                + observation.state()
                                + ": "
                                + observation.detail(),
                        observation.cause());
            };
        }

        @Override
        public AtomicConfigPublisher.Port publicationPort(
                ConfigMigrationEngine.PublishRequest request) {
            ensureOpen();
            requireTargetSibling(request.stage());
            requireTargetSibling(request.destination());
            return new PublisherPort(targetBackend, request);
        }

        @Override
        public String identity(Path path) {
            ensureOpen();
            Path checked = normalizedTarget(path);
            try {
                return safeRegular(
                                backendFor(checked)
                                        .readNofollowMetadata(
                                                checked.getFileName()
                                                        .toString()),
                                checked.getFileName().toString())
                        .identity();
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Could not read bound migration identity " + checked,
                        failure);
            }
        }

        @Override
        public void verifyPermanentLock(
                Path path, ConfigMigrationEngine.LockLease lease) {
            ensureOpen();
            requireTargetSibling(path);
            Objects.requireNonNull(lease, "lease");
            if (lockPort == null) {
                throw new IllegalStateException(
                        "Permanent lock channel is not open");
            }
            if (lease.profile() != profile) {
                throw new IllegalStateException(
                        "Permanent lock lease profile differs from store");
            }
            try {
                lockPort.verifyBound(
                        path.getFileName().toString(),
                        lease.identity(),
                        lease.payloadSha256());
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Permanent lock identity or payload revalidation "
                                + "failed for "
                                + path,
                        failure);
            }
        }

        @Override
        public void verifyBinding(MigrationBinding expected) {
            ensureOpen();
            if (!binding.equals(expected)) {
                throw new IllegalStateException(
                        "Store was asked to verify a different binding");
            }
            try {
                binding.verifyUnchanged(
                        fileSystem.observeBinding(binding.target()));
                if (externalLegacyBound) {
                    legacyBinding.verifyUnchanged(
                            fileSystem.observeBinding(legacy));
                }
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Could not revalidate migration binding", failure);
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            IOException failure = null;
            if (lockPort != null) {
                try {
                    lockPort.close();
                } catch (IOException closeFailure) {
                    failure = closeFailure;
                }
            }
            if (externalLegacyBound) {
                try {
                    legacySession.close();
                } catch (IOException closeFailure) {
                    if (failure == null) {
                        failure = closeFailure;
                    } else {
                        failure.addSuppressed(closeFailure);
                    }
                }
            }
            try {
                targetSession.close();
            } catch (IOException closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private OperationalBackend backendFor(Path path) {
            if (path.getParent().equals(binding.logicalParent())) {
                return targetBackend;
            }
            if (path.equals(legacy) && externalLegacyBound) {
                return legacyBackend;
            }
            throw new IllegalArgumentException(
                    "Path is outside the bound migration store: " + path);
        }

        private MigrationDirectorySession sessionFor(Path path) {
            return backendFor(path) == targetBackend
                    ? targetSession
                    : legacySession;
        }

        private void requireTargetSibling(Path path) {
            Path checked = normalizedTarget(path);
            if (!checked.getParent().equals(binding.logicalParent())) {
                throw new IllegalArgumentException(
                        "Migration artifact is outside the bound target parent: "
                                + path);
            }
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException(
                        "Bound JDK migration store is closed");
            }
        }
    }

    private abstract static class BoundBackend
            implements OperationalBackend {
        final JdkMigrationFileSystem fileSystem;
        final MigrationBinding binding;

        BoundBackend(
                JdkMigrationFileSystem fileSystem, MigrationBinding binding) {
            this.fileSystem = fileSystem;
            this.binding = binding;
        }

        final void verifyBinding() throws IOException {
            binding.verifyUnchanged(
                    fileSystem.observeBinding(binding.target()));
        }

        final void contentOpenCheckpoint(
                ContentOpenPoint point, String basename) throws IOException {
            fileSystem.contentOpenHook.at(
                    point, binding.physicalParent().resolve(basename));
        }

        @Override
        public void forceDirectory() throws IOException {
            verifyBinding();
            try (FileChannel directory = FileChannel.open(
                    binding.physicalParent(), StandardOpenOption.READ)) {
                directory.force(true);
            }
            verifyBinding();
        }
    }

    private static final class SecureBackend extends BoundBackend {
        private final SecureDirectoryStream<Path> directory;

        private SecureBackend(
                JdkMigrationFileSystem fileSystem,
                MigrationBinding binding,
                SecureDirectoryStream<Path> directory) {
            super(fileSystem, binding);
            this.directory = directory;
        }

        @Override
        public MigrationPathState.Metadata readNofollowMetadata(String basename)
                throws IOException {
            String operand = MigrationDirectorySession.requireBasename(basename);
            verifyBinding();
            MigrationPathState.Metadata result = readMetadata(operand);
            verifyBinding();
            return result;
        }

        @Override
        public MigrationDirectorySession.OpenedContent openNofollow(String basename)
                throws IOException {
            String operand = MigrationDirectorySession.requireBasename(basename);
            verifyBinding();
            MigrationPathState.Metadata before = readMetadata(operand);
            contentOpenCheckpoint(
                    ContentOpenPoint.BEFORE_PRIMARY_OPEN, operand);
            Set<OpenOption> options =
                    Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            byte[] bytes;
            try (SeekableByteChannel channel =
                    directory.newByteChannel(Path.of(operand), options)) {
                bytes = readAll(channel);
            }
            contentOpenCheckpoint(
                    ContentOpenPoint.AFTER_PRIMARY_READ, operand);
            MigrationPathState.Metadata after = readMetadata(operand);
            verifySameRegular(before, after, bytes.length, operand);
            byte[] verifiedBytes;
            try (SeekableByteChannel channel =
                    directory.newByteChannel(Path.of(operand), options)) {
                verifiedBytes = readAll(channel);
            }
            MigrationPathState.Metadata verified = readMetadata(operand);
            verifySameRegular(
                    after, verified, verifiedBytes.length, operand);
            if (!Arrays.equals(bytes, verifiedBytes)) {
                throw new IOException(
                        "Opened leaf bytes differ from the rebound "
                                + "NOFOLLOW identity: "
                                + operand);
            }
            verifyBinding();
            return new MigrationDirectorySession.OpenedContent(
                    verified.identity(), true, verifiedBytes);
        }

        @Override
        public void close() throws IOException {
            directory.close();
        }

        @Override
        public FileChannel openFile(
                String basename, Set<OpenOption> options)
                throws IOException {
            String operand = MigrationDirectorySession.requireBasename(basename);
            verifyBinding();
            SeekableByteChannel opened =
                    directory.newByteChannel(Path.of(operand), options);
            if (!(opened instanceof FileChannel channel)) {
                opened.close();
                throw new IOException(
                        "SECURE relative channel is not forceable");
            }
            verifyBinding();
            return channel;
        }

        @Override
        public void atomicMove(String source, String destination)
                throws IOException {
            String checkedSource =
                    MigrationDirectorySession.requireBasename(source);
            String checkedDestination =
                    MigrationDirectorySession.requireBasename(destination);
            verifyBinding();
            directory.move(
                    Path.of(checkedSource),
                    directory,
                    Path.of(checkedDestination));
            verifyBinding();
        }

        private MigrationPathState.Metadata readMetadata(String basename)
                throws IOException {
            BasicFileAttributeView view = directory.getFileAttributeView(
                    Path.of(basename),
                    BasicFileAttributeView.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (view == null) {
                throw new IOException(
                        "Provider has no basic NOFOLLOW attribute view");
            }
            return metadata(view.readAttributes());
        }
    }

    private static final class BasicBackend extends BoundBackend {
        private BasicBackend(
                JdkMigrationFileSystem fileSystem, MigrationBinding binding) {
            super(fileSystem, binding);
        }

        @Override
        public MigrationPathState.Metadata readNofollowMetadata(String basename)
                throws IOException {
            String operand = MigrationDirectorySession.requireBasename(basename);
            verifyBinding();
            MigrationPathState.Metadata result = fileSystem.readNofollowMetadata(
                    binding.physicalParent().resolve(operand));
            verifyBinding();
            return result;
        }

        @Override
        public MigrationDirectorySession.OpenedContent openNofollow(String basename)
                throws IOException {
            String operand = MigrationDirectorySession.requireBasename(basename);
            Path path = binding.physicalParent().resolve(operand);
            verifyBinding();
            MigrationPathState.Metadata before =
                    fileSystem.readNofollowMetadata(path);
            contentOpenCheckpoint(
                    ContentOpenPoint.BEFORE_PRIMARY_OPEN, operand);
            byte[] bytes;
            try (FileChannel channel = FileChannel.open(
                    path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                bytes = readAll(channel);
            }
            contentOpenCheckpoint(
                    ContentOpenPoint.AFTER_PRIMARY_READ, operand);
            MigrationPathState.Metadata after =
                    fileSystem.readNofollowMetadata(path);
            verifySameRegular(before, after, bytes.length, operand);
            byte[] verifiedBytes;
            try (FileChannel channel = FileChannel.open(
                    path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                verifiedBytes = readAll(channel);
            }
            MigrationPathState.Metadata verified =
                    fileSystem.readNofollowMetadata(path);
            verifySameRegular(
                    after, verified, verifiedBytes.length, operand);
            if (!Arrays.equals(bytes, verifiedBytes)) {
                throw new IOException(
                        "Opened leaf bytes differ from the rebound "
                                + "NOFOLLOW identity: "
                                + operand);
            }
            verifyBinding();
            return new MigrationDirectorySession.OpenedContent(
                    verified.identity(), true, verifiedBytes);
        }

        @Override
        public void close() {}

        @Override
        public FileChannel openFile(
                String basename, Set<OpenOption> options)
                throws IOException {
            String operand = MigrationDirectorySession.requireBasename(basename);
            verifyBinding();
            FileChannel channel = FileChannel.open(
                    binding.physicalParent().resolve(operand), options);
            verifyBinding();
            return channel;
        }

        @Override
        public void atomicMove(String source, String destination)
                throws IOException {
            String checkedSource =
                    MigrationDirectorySession.requireBasename(source);
            String checkedDestination =
                    MigrationDirectorySession.requireBasename(destination);
            verifyBinding();
            Files.move(
                    binding.physicalParent().resolve(checkedSource),
                    binding.physicalParent().resolve(checkedDestination),
                    StandardCopyOption.ATOMIC_MOVE);
            verifyBinding();
        }
    }

    private static final class PublisherPort
            implements AtomicConfigPublisher.Port {
        private final OperationalBackend backend;
        private final ConfigMigrationEngine.PublishRequest request;
        private FileChannel stage;
        private boolean moved;

        private PublisherPort(
                OperationalBackend backend,
                ConfigMigrationEngine.PublishRequest request) {
            this.backend = Objects.requireNonNull(backend, "backend");
            this.request = Objects.requireNonNull(request, "request");
        }

        @Override
        public void createNew(String stageName) throws IOException {
            requireStage(stageName);
            if (stage != null) {
                throw new IOException("Fixed stage is already open");
            }
            stage = backend.openFile(
                    stageName,
                    Set.of(
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS));
        }

        @Override
        public void write(String stageName, byte[] bytes)
                throws IOException {
            requireStage(stageName);
            FileChannel channel = requireStageChannel();
            if (channel.size() != 0) {
                throw new IOException("New fixed stage is not empty");
            }
            writeAll(channel, bytes);
            if (channel.size() != bytes.length) {
                throw new IOException("Fixed stage length differs after write");
            }
        }

        @Override
        public void forceFile(String stageName) throws IOException {
            requireStage(stageName);
            requireStageChannel().force(true);
        }

        @Override
        public void closeStage(String stageName) throws IOException {
            requireStage(stageName);
            if (stage != null) {
                stage.close();
                stage = null;
            }
        }

        @Override
        public void verifyDestination(
                String destination,
                AtomicConfigPublisher.DestinationExpectation expectation)
                throws IOException {
            requireDestination(destination);
            Objects.requireNonNull(expectation, "expectation");
            MigrationPathState observed =
                    observeSafeMetadata(destination, "destination").state();
            if (expectation.state()
                    == AtomicConfigPublisher.ExpectedState.ABSENT) {
                if (observed != MigrationPathState.ABSENT) {
                    throw new IOException(
                            "Create-once destination is not safely absent");
                }
                return;
            }
            if (observed != MigrationPathState.PRESENT) {
                throw new IOException(
                        "Expected prior destination is not a safe regular file");
            }
            MigrationPathState.Metadata metadata = safeRegular(
                    backend.readNofollowMetadata(destination), destination);
            if (!metadata.identity().equals(expectation.priorIdentity())) {
                throw new IOException(
                        "Expected prior destination identity changed");
            }
            byte[] current = backend.openNofollow(destination).bytes();
            if (!Arrays.equals(current, expectation.priorBytes())) {
                throw new IOException(
                        "Expected prior destination bytes changed");
            }
        }

        @Override
        public void atomicMove(String stageName, String destination)
                throws IOException {
            requireStage(stageName);
            requireDestination(destination);
            if (stage != null) {
                throw new IOException(
                        "Fixed stage channel must close before atomic move");
            }
            if (moved) {
                throw new IOException(
                        "Atomic publication attempted a second move");
            }
            moved = true;
            backend.atomicMove(stageName, destination);
        }

        @Override
        public byte[] reopenNofollow(String destination)
                throws IOException {
            requireDestination(destination);
            return backend.openNofollow(destination).bytes();
        }

        @Override
        public void validate(String destination, byte[] expected)
                throws IOException {
            requireDestination(destination);
            if (!Arrays.equals(
                    backend.openNofollow(destination).bytes(), expected)) {
                throw new IOException(
                        "Canonical destination differs during validation");
            }
        }

        @Override
        public void reparse(String destination, byte[] expected)
                throws IOException {
            requireDestination(destination);
            try {
                switch (request.artifact()) {
                    case JOURNAL -> MigrationJournal.decode(expected);
                    case BACKUP, INITIAL, TARGET ->
                            LegacyConfigParser.parse(expected);
                    case MARKER -> MigrationMarker.decode(expected);
                }
            } catch (IllegalArgumentException failure) {
                throw new IOException(
                        "Canonical artifact failed strict reparse", failure);
            }
        }

        @Override
        public void forceDirectory() throws IOException {
            backend.forceDirectory();
        }

        @Override
        public boolean stageExists(String stageName) throws IOException {
            requireStage(stageName);
            MigrationPathState observed =
                    observeSafeMetadata(stageName, "fixed stage").state();
            return observed == MigrationPathState.PRESENT;
        }

        @Override
        public boolean canonicalMatches(
                String destination, byte[] expected) throws IOException {
            requireDestination(destination);
            MigrationPathState observed =
                    observeSafeMetadata(destination, "canonical destination")
                            .state();
            if (observed == MigrationPathState.ABSENT) {
                return false;
            }
            if (observed != MigrationPathState.PRESENT) {
                throw new IOException(
                        "Canonical destination is unknown or unsafe");
            }
            return Arrays.equals(
                    backend.openNofollow(destination).bytes(), expected);
        }

        private MigrationPathState.Observation observeSafeMetadata(
                String basename, String description) throws IOException {
            MigrationPathState.Observation observation =
                    MigrationPathState.observe(
                            () -> backend.readNofollowMetadata(basename));
            if (observation.state() == MigrationPathState.UNKNOWN
                    || observation.state() == MigrationPathState.UNSAFE) {
                throw new IOException(
                        description
                                + " metadata is "
                                + observation.state()
                                + ": "
                                + observation.detail(),
                        observation.cause());
            }
            return observation;
        }

        private FileChannel requireStageChannel() throws IOException {
            if (stage == null || !stage.isOpen()) {
                throw new IOException("Fixed stage channel is not open");
            }
            return stage;
        }

        private void requireStage(String value) {
            if (!request.stage().getFileName().toString().equals(value)) {
                throw new IllegalArgumentException(
                        "Unexpected fixed stage basename: " + value);
            }
        }

        private void requireDestination(String value) {
            if (!request.destination()
                    .getFileName()
                    .toString()
                    .equals(value)) {
                throw new IllegalArgumentException(
                        "Unexpected canonical destination basename: " + value);
            }
        }
    }

    private static final class LockPort
            implements PermanentMigrationLock.Port, AutoCloseable {
        private final OperationalBackend backend;
        private FileChannel channel;
        private FileLock lock;

        private LockPort(OperationalBackend backend) {
            this.backend = Objects.requireNonNull(backend, "backend");
        }

        @Override
        public boolean createNew(String basename) throws IOException {
            ensureNoChannel();
            try {
                channel = backend.openFile(
                        basename,
                        Set.of(
                                StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.READ,
                                StandardOpenOption.WRITE,
                                LinkOption.NOFOLLOW_LINKS));
                return true;
            } catch (FileAlreadyExistsException present) {
                return false;
            }
        }

        @Override
        public void openExisting(String basename) throws IOException {
            ensureNoChannel();
            channel = backend.openFile(
                    basename,
                    Set.of(
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS));
        }

        @Override
        public PermanentMigrationLock.TryLockResult tryLock(
                String basename, boolean currentCall) throws IOException {
            if (lock != null) {
                throw new IOException(
                        "Permanent lock was acquired more than once");
            }
            try {
                lock = requireChannel().tryLock();
            } catch (OverlappingFileLockException contention) {
                return PermanentMigrationLock.TryLockResult.CONTENDED;
            }
            return lock == null
                    ? PermanentMigrationLock.TryLockResult.CONTENDED
                    : PermanentMigrationLock.TryLockResult.ACQUIRED;
        }

        @Override
        public byte[] readPayload(String basename) throws IOException {
            MigrationPathState.Metadata before =
                    safeRegular(
                            backend.readNofollowMetadata(basename), basename);
            FileChannel opened = requireChannel();
            opened.position(0);
            byte[] bytes = readAll(opened);
            MigrationPathState.Metadata after =
                    safeRegular(
                            backend.readNofollowMetadata(basename), basename);
            verifySameRegular(before, after, bytes.length, basename);
            return bytes;
        }

        @Override
        public String identity(String basename) throws IOException {
            return safeRegular(
                            backend.readNofollowMetadata(basename), basename)
                    .identity();
        }

        @Override
        public void validatePayload(byte[] actual, byte[] expected)
                throws IOException {
            if (!Arrays.equals(actual, expected)) {
                throw new IOException(
                        "Permanent lock payload changed during validation");
            }
        }

        @Override
        public void verifyBound(
                String basename,
                String expectedIdentity,
                String expectedPayloadSha256)
                throws IOException {
            MigrationPathState.Metadata before =
                    safeRegular(
                            backend.readNofollowMetadata(basename), basename);
            FileChannel opened = requireChannel();
            opened.position(0);
            byte[] bytes = readAll(opened);
            MigrationPathState.Metadata after =
                    safeRegular(
                            backend.readNofollowMetadata(basename), basename);
            verifySameRegular(before, after, bytes.length, basename);
            if (!expectedIdentity.equals(before.identity())
                    || !expectedIdentity.equals(after.identity())) {
                throw new IOException(
                        "Permanent lock pathname identity changed: "
                                + basename);
            }
            if (!expectedPayloadSha256.equals(
                    PermanentMigrationLock.payloadSha256(bytes))) {
                throw new IOException(
                        "Permanent lock payload changed: " + basename);
            }
        }

        @Override
        public void writePayload(String basename, byte[] bytes)
                throws IOException {
            FileChannel opened = requireChannel();
            if (opened.size() != 0) {
                throw new IOException(
                        "Permanent lock initialization cannot overwrite bytes");
            }
            writeAll(opened, bytes);
            if (opened.size() != bytes.length) {
                throw new IOException(
                        "Permanent lock payload length differs after write");
            }
        }

        @Override
        public void forceFile(String basename) throws IOException {
            requireChannel().force(true);
        }

        @Override
        public void forceDirectory() throws IOException {
            backend.forceDirectory();
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            if (lock != null) {
                try {
                    lock.close();
                } catch (IOException closeFailure) {
                    failure = closeFailure;
                }
                lock = null;
            }
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException closeFailure) {
                    if (failure == null) {
                        failure = closeFailure;
                    } else {
                        failure.addSuppressed(closeFailure);
                    }
                }
                channel = null;
            }
            if (failure != null) {
                throw failure;
            }
        }

        private FileChannel requireChannel() throws IOException {
            if (channel == null || !channel.isOpen()) {
                throw new IOException(
                        "Permanent lock channel is not open");
            }
            return channel;
        }

        private void ensureNoChannel() throws IOException {
            if (channel != null) {
                throw new IOException(
                        "Permanent lock channel was already opened");
            }
        }
    }

    private static void verifySameRegular(
            MigrationPathState.Metadata before,
            MigrationPathState.Metadata after,
            int openedLength,
            String basename)
            throws IOException {
        if (before == null
                || after == null
                || !before.regularFile()
                || !after.regularFile()
                || before.symbolicLink()
                || after.symbolicLink()
                || before.identity() == null
                || !before.identity().equals(after.identity())
                || before.size() != after.size()
                || after.size() != openedLength) {
            throw new IOException(
                    "NOFOLLOW leaf changed or was unsafe while opening "
                            + basename);
        }
    }

    private static MigrationPathState.Metadata safeRegular(
            MigrationPathState.Metadata metadata, String basename)
            throws IOException {
        if (metadata == null
                || !metadata.regularFile()
                || metadata.symbolicLink()
                || metadata.identity() == null
                || metadata.identity().isBlank()
                || metadata.size() < 0) {
            throw new IOException(
                    "NOFOLLOW leaf is absent, unsafe, or untrusted: "
                            + basename);
        }
        return metadata;
    }

    private static void writeAll(FileChannel channel, byte[] bytes)
            throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            int count = channel.write(buffer);
            if (count <= 0) {
                throw new IOException(
                        "Writable FileChannel made no forward progress");
            }
        }
    }

    enum ContentOpenPoint {
        BEFORE_PRIMARY_OPEN,
        AFTER_PRIMARY_READ
    }

    @FunctionalInterface
    interface ContentOpenHook {
        void at(ContentOpenPoint point, Path path) throws IOException;

        static ContentOpenHook none() {
            return (point, path) -> {};
        }
    }
}
