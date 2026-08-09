package dev.molang.iamzombieq.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

final class MigrationIdentityPolicy {
    static final String WINDOWS_BASIC_FINGERPRINT_V1 =
            "WINDOWS_BASIC_FINGERPRINT_V1";

    private static final String WINDOWS_PROVIDER =
            "sun.nio.fs.WindowsFileSystemProvider";
    private final Probe probe;

    MigrationIdentityPolicy() {
        this(new JdkProbe());
    }

    MigrationIdentityPolicy(int observedJavaFeature) {
        this(new JdkProbe(observedJavaFeature));
    }

    MigrationIdentityPolicy(Probe probe) {
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    String directoryIdentity(Path path) throws IOException {
        Path checked = Objects.requireNonNull(path, "path");
        BasicFileAttributes attributes = Objects.requireNonNull(
                probe.readNofollowAttributes(checked), "directory attributes");
        if (attributes.isSymbolicLink()) {
            throw new UnsafePathException(
                    "Migration directory is a symbolic link: " + checked);
        }
        if (attributes.isOther()) {
            throw new UnsafePathException(
                    "Migration directory is a junction or other reparse point: "
                            + checked);
        }
        if (!attributes.isDirectory()) {
            throw new UnsafePathException(
                    "Migration directory is not a directory: " + checked);
        }
        String identity = identity(Subject.DIRECTORY, checked, attributes);
        if (identity.isBlank()) {
            throw new IOException(
                    "Migration directory identity is unavailable: " + checked);
        }
        return identity;
    }

    MigrationPathState.Metadata regularFileMetadata(Path path)
            throws IOException {
        Path checked = Objects.requireNonNull(path, "path");
        BasicFileAttributes attributes = Objects.requireNonNull(
                probe.readNofollowAttributes(checked), "regular-file attributes");
        if (attributes.isSymbolicLink()) {
            return new MigrationPathState.Metadata(
                    attributes.isRegularFile(), true, "", attributes.size());
        }
        if (attributes.isOther()) {
            return new MigrationPathState.Metadata(
                    false, false, "", attributes.size());
        }
        if (!attributes.isRegularFile()) {
            return new MigrationPathState.Metadata(
                    false, false, "", attributes.size());
        }
        return new MigrationPathState.Metadata(
                true,
                false,
                identity(Subject.REGULAR_FILE, checked, attributes),
                attributes.size());
    }

    EmptyLockRecoveryPolicy emptyLockRecoveryPolicy(
            MigrationAccessProfile profile, MigrationBinding binding) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(binding, "binding");
        if (profile == MigrationAccessProfile.BASIC) {
            return EmptyLockRecoveryPolicy.MANUAL_ONLY;
        }
        if (isWindowsFingerprint(binding.directoryIdentity())) {
            return EmptyLockRecoveryPolicy.MANUAL_ONLY;
        }
        for (MigrationBinding.Ancestor ancestor : binding.ancestors()) {
            if (isWindowsFingerprint(ancestor.identity())) {
                return EmptyLockRecoveryPolicy.MANUAL_ONLY;
            }
        }
        return EmptyLockRecoveryPolicy.EXACT_FILE_KEY;
    }

    String bindingFileStoreIdentity(
            Path logicalParent, Path physicalParent, String parentIdentity)
            throws IOException {
        Path logical = Objects.requireNonNull(logicalParent, "logicalParent");
        Path physical = Objects.requireNonNull(physicalParent, "physicalParent");
        String identity = Objects.requireNonNull(parentIdentity, "parentIdentity");
        Platform platform = Objects.requireNonNull(
                probe.platform(logical), "binding platform");
        if (!platform.isWindowsBasicCandidate()) {
            return probe.fileStoreIdentity(physical);
        }
        if (!isWindowsFingerprint(identity)) {
            throw new IOException(
                    "Windows BASIC binding lacks its tagged parent identity");
        }
        WindowsContext logicalContext = readWindowsContext(logical);
        WindowsContext physicalContext = readWindowsContext(physical);
        requireWindowsContext(logical, logicalContext);
        requireWindowsContext(physical, physicalContext);
        Object logicalVsn = readVolumeSerial(logicalContext.driveRoot());
        Object physicalVsn = readVolumeSerial(physicalContext.driveRoot());
        if (!logicalContext.realPath().equals(physicalContext.realPath())
                || !sameStore(logicalContext, physicalContext)
                || !sameVolumeSerial(logicalVsn, physicalVsn)) {
            throw new IOException(
                    "Windows BASIC parent FileStore binding changed");
        }
        return fileStoreIdentity(logicalContext);
    }

    private String identity(
            Subject subject, Path path, BasicFileAttributes attributes)
            throws IOException {
        Platform platform = Objects.requireNonNull(
                probe.platform(path), "identity platform");
        if (platform.isWindowsBasicCandidate()) {
            return windowsFingerprint(subject, path, attributes, platform);
        }
        Object fileKey = attributes.fileKey();
        return fileKey == null ? "" : fileKey.toString();
    }

    private String windowsFingerprint(
            Subject subject,
            Path path,
            BasicFileAttributes attributes,
            Platform platform)
            throws IOException {
        WindowsContext firstContext = readWindowsContext(path);
        requireWindowsContext(path, firstContext);
        Object firstVsn = readVolumeSerial(firstContext.driveRoot());

        BasicFileAttributes secondAttributes = Objects.requireNonNull(
                probe.readNofollowAttributes(path),
                "rechecked Windows identity attributes");
        requireSubject(subject, path, secondAttributes);
        Platform secondPlatform = Objects.requireNonNull(
                probe.platform(path), "rechecked identity platform");
        WindowsContext secondContext = readWindowsContext(path);
        requireWindowsContext(path, secondContext);
        Object secondVsn = readVolumeSerial(secondContext.driveRoot());
        if (!platform.equals(secondPlatform)
                || !firstContext.equals(secondContext)
                || !sameAttributes(attributes, secondAttributes)) {
            throw new IOException(
                    "Windows BASIC identity tuple changed while observing "
                            + path);
        }
        if (!(firstVsn instanceof Number)
                || !firstVsn.getClass().equals(secondVsn == null
                        ? null
                        : secondVsn.getClass())
                || !firstVsn.equals(secondVsn)) {
            throw new IOException(
                    "Windows NTFS volume:vsn is unavailable or unstable for "
                            + path);
        }
        if (attributes.creationTime() == null) {
            throw new IOException(
                    "Windows BASIC creationTime is unavailable for " + path);
        }
        // The raw access spelling is validated in WindowsContext but cannot
        // be an identity field: bootstrap may use a case/8.3 alias while the
        // bound BASIC session uses the canonical parent. Bind both the
        // normalized absolute identity path and its NOFOLLOW real path to the
        // canonical representation so those two views agree on one object.
        Path canonicalAbsolute =
                firstContext.realPath().toAbsolutePath().normalize();

        return tagged(List.of(
                subject.name(),
                platform.operatingSystem(),
                Integer.toString(platform.javaFeature()),
                Boolean.toString(platform.defaultProvider()),
                platform.providerScheme(),
                platform.providerClass(),
                canonicalAbsolute.toString(),
                firstContext.realPath().toString(),
                firstContext.driveRoot().toString(),
                firstContext.fileStoreName(),
                firstContext.fileStoreType(),
                firstContext.fileStoreClass(),
                firstVsn.getClass().getName(),
                firstVsn.toString(),
                attributes.creationTime().toString()));
    }

    private WindowsContext readWindowsContext(Path path) throws IOException {
        try {
            return Objects.requireNonNull(
                    probe.windowsContext(path), "Windows identity context");
        } catch (RuntimeException failure) {
            throw new IOException(
                    "Windows BASIC identity context is unavailable for " + path,
                    failure);
        }
    }

    private Object readVolumeSerial(Path realPath) throws IOException {
        try {
            return probe.volumeSerialNumber(realPath);
        } catch (IOException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IOException(
                    "Windows NTFS volume:vsn query failed for " + realPath,
                    failure);
        }
    }

    private static void requireSubject(
            Subject subject, Path path, BasicFileAttributes attributes)
            throws IOException {
        if (attributes.isSymbolicLink()) {
            throw new UnsafePathException(
                    "Migration path became a symbolic link: " + path);
        }
        if (attributes.isOther()) {
            throw new UnsafePathException(
                    "Migration path became a junction or other reparse point: "
                            + path);
        }
        boolean expectedType = switch (subject) {
            case DIRECTORY -> attributes.isDirectory();
            case REGULAR_FILE -> attributes.isRegularFile();
        };
        if (!expectedType) {
            throw new UnsafePathException(
                    "Migration path type changed while observing " + path);
        }
    }

    private static boolean sameAttributes(
            BasicFileAttributes first, BasicFileAttributes second) {
        return first.isRegularFile() == second.isRegularFile()
                && first.isDirectory() == second.isDirectory()
                && first.isSymbolicLink() == second.isSymbolicLink()
                && first.isOther() == second.isOther()
                && first.size() == second.size()
                && Objects.equals(first.creationTime(), second.creationTime());
    }

    private static boolean sameStore(
            WindowsContext first, WindowsContext second) {
        return Objects.equals(first.driveRoot(), second.driveRoot())
                && Objects.equals(
                        first.fileStoreName(), second.fileStoreName())
                && Objects.equals(
                        first.fileStoreType(), second.fileStoreType())
                && Objects.equals(
                        first.fileStoreClass(), second.fileStoreClass());
    }

    private static boolean sameVolumeSerial(Object first, Object second) {
        return first instanceof Number
                && second instanceof Number
                && first.getClass().equals(second.getClass())
                && first.equals(second);
    }

    private static String fileStoreIdentity(WindowsContext context) {
        return context.fileStoreName()
                + "|"
                + context.fileStoreType()
                + "|"
                + context.fileStoreClass();
    }

    private static void requireWindowsContext(
            Path requested, WindowsContext context) throws IOException {
        if (context.unc()) {
            throw new IOException(
                    "Windows BASIC identity does not permit UNC paths: "
                            + requested);
        }
        requireNormalizedAbsolute(
                context.absolutePath(), "Windows absolute path");
        requireNormalizedAbsolute(context.realPath(), "Windows real path");
        requireNormalizedAbsolute(context.driveRoot(), "Windows drive root");
        if (!requested.equals(context.absolutePath())) {
            throw new IOException(
                    "Windows BASIC absolute path changed while observing "
                            + requested);
        }
        if (!"NTFS".equals(context.fileStoreType())) {
            throw new IOException(
                    "Windows BASIC identity requires exact NTFS FileStore type");
        }
        if (context.fileStoreName() == null) {
            throw new IOException("FileStore name is unavailable");
        }
        requireNonBlank(context.fileStoreClass(), "FileStore class");
    }

    private static void requireNormalizedAbsolute(Path path, String field)
            throws IOException {
        if (path == null
                || !path.isAbsolute()
                || !path.equals(path.toAbsolutePath().normalize())) {
            throw new IOException(
                    field + " is not normalized and absolute: " + path);
        }
    }

    private static void requireNonBlank(String value, String field)
            throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException(field + " is unavailable");
        }
    }

    private static String tagged(List<String> fields) throws IOException {
        StringBuilder result = new StringBuilder(
                WINDOWS_BASIC_FINGERPRINT_V1);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        for (String field : fields) {
            if (field == null) {
                throw new IOException(
                        "Windows BASIC fingerprint field is unavailable");
            }
            result.append(':').append(encoder.encodeToString(
                    field.getBytes(StandardCharsets.UTF_8)));
        }
        return result.toString();
    }

    private static boolean isWindowsFingerprint(String identity) {
        return identity.startsWith(WINDOWS_BASIC_FINGERPRINT_V1 + ":");
    }

    enum EmptyLockRecoveryPolicy {
        EXACT_FILE_KEY,
        MANUAL_ONLY
    }

    static final class UnsafePathException extends IOException {
        private UnsafePathException(String message) {
            super(message);
        }
    }

    record Platform(
            String operatingSystem,
            int javaFeature,
            boolean defaultProvider,
            String providerScheme,
            String providerClass) {
        Platform {
            operatingSystem = Objects.requireNonNull(
                    operatingSystem, "operatingSystem");
            providerScheme = Objects.requireNonNull(
                    providerScheme, "providerScheme");
            providerClass = Objects.requireNonNull(
                    providerClass, "providerClass");
        }

        private boolean isWindowsBasicCandidate() {
            return operatingSystem.startsWith("Windows")
                    && MigrationJavaRuntimeMatrix
                            .supportsBasicProfile(javaFeature)
                    && defaultProvider
                    && providerScheme.equals("file")
                    && providerClass.equals(WINDOWS_PROVIDER);
        }
    }

    record WindowsContext(
            boolean unc,
            Path absolutePath,
            Path realPath,
            Path driveRoot,
            String fileStoreName,
            String fileStoreType,
            String fileStoreClass) {}

    interface Probe {
        BasicFileAttributes readNofollowAttributes(Path path) throws IOException;

        Platform platform(Path path) throws IOException;

        WindowsContext windowsContext(Path path) throws IOException;

        Object volumeSerialNumber(Path path) throws IOException;

        String fileStoreIdentity(Path path) throws IOException;
    }

    private enum Subject {
        DIRECTORY,
        REGULAR_FILE
    }

    private static final class JdkProbe implements Probe {
        private final int observedJavaFeature;

        private JdkProbe() {
            this(Runtime.version().feature());
        }

        private JdkProbe(int observedJavaFeature) {
            if (observedJavaFeature <= 0) {
                throw new IllegalArgumentException(
                        "Invalid observed Java feature: "
                                + observedJavaFeature);
            }
            this.observedJavaFeature = observedJavaFeature;
        }

        @Override
        public BasicFileAttributes readNofollowAttributes(Path path)
                throws IOException {
            return Files.readAttributes(
                    path,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
        }

        @Override
        public Platform platform(Path path) {
            var provider = path.getFileSystem().provider();
            return new Platform(
                    System.getProperty("os.name", "unknown"),
                    observedJavaFeature,
                    provider == FileSystems.getDefault().provider(),
                    provider.getScheme(),
                    provider.getClass().getName());
        }

        @Override
        public WindowsContext windowsContext(Path path) throws IOException {
            Path absolute = path.toAbsolutePath().normalize();
            if (!path.isAbsolute() || !path.equals(absolute)) {
                throw new IOException(
                        "Windows identity path is not normalized and absolute: "
                                + path);
            }
            Path root = absolute.getRoot();
            if (root == null || root.toString().startsWith("\\\\")) {
                throw new IOException(
                        "Windows BASIC identity does not permit UNC paths: "
                                + path);
            }
            Path real = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!real.isAbsolute()
                    || !real.equals(real.toAbsolutePath().normalize())) {
                throw new IOException(
                        "Windows identity real path is not normalized and absolute: "
                                + real);
            }
            Path realRoot = real.getRoot();
            if (root == null || realRoot == null || !root.equals(realRoot)) {
                throw new IOException(
                        "Windows identity path changed drive root: " + path);
            }
            StoreSnapshot firstStore = storeSnapshot(realRoot);
            StoreSnapshot secondStore = storeSnapshot(realRoot);
            if (!firstStore.equals(secondStore)) {
                throw new IOException(
                        "Windows drive-root FileStore changed while observing "
                                + path);
            }
            return new WindowsContext(
                    false,
                    absolute,
                    real,
                    realRoot,
                    firstStore.name(),
                    firstStore.type(),
                    firstStore.storeClass());
        }

        @Override
        public Object volumeSerialNumber(Path path) throws IOException {
            return Files.getFileStore(path).getAttribute("volume:vsn");
        }

        @Override
        public String fileStoreIdentity(Path path) throws IOException {
            FileStore store = Files.getFileStore(path);
            return store.name()
                    + "|"
                    + store.type()
                    + "|"
                    + store.getClass().getName();
        }

        private static StoreSnapshot storeSnapshot(Path root)
                throws IOException {
            FileStore store = Files.getFileStore(root);
            Object volumeSerial = store.getAttribute("volume:vsn");
            if (!(volumeSerial instanceof Number)) {
                throw new IOException(
                        "Windows NTFS volume:vsn is unavailable for " + root);
            }
            return new StoreSnapshot(
                    store.name(),
                    store.type(),
                    store.getClass().getName(),
                    volumeSerial.getClass().getName(),
                    volumeSerial.toString());
        }

        private record StoreSnapshot(
                String name,
                String type,
                String storeClass,
                String volumeSerialClass,
                String volumeSerialValue) {}
    }
}
