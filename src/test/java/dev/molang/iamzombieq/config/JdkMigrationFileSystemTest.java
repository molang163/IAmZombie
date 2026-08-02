package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdkMigrationFileSystemTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storeSessionExposesRawMetadataThroughItsBoundRelativeBackend()
            throws IOException {
        Path target = Files.writeString(
                temporaryDirectory.resolve("iamzombieq-server.toml"),
                "payload");
        JdkMigrationFileSystem fileSystem =
                new JdkMigrationFileSystem();
        MigrationBinding binding = MigrationBinding.capture(
                fileSystem.observeBinding(target));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);

        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, target)) {
            MigrationPathState.Metadata metadata =
                    store.readNofollowMetadata(target);
            assertTrue(metadata.regularFile());
            assertFalse(metadata.symbolicLink());
            assertFalse(metadata.identity().isBlank());
            assertEquals(Files.size(target), metadata.size());
        }
    }

    @Test
    void realNofollowMetadataDistinguishesRegularSymlinkAndNonregular()
            throws IOException {
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        Path regular = Files.writeString(temporaryDirectory.resolve("regular"), "data");
        Path directory = Files.createDirectory(temporaryDirectory.resolve("directory"));
        Path link = Files.createSymbolicLink(
                temporaryDirectory.resolve("link"), regular.getFileName());

        assertEquals(MigrationPathState.PRESENT, fileSystem.classify(regular));
        assertEquals(MigrationPathState.ABSENT, fileSystem.classify(
                temporaryDirectory.resolve("absent")));
        assertEquals(MigrationPathState.UNSAFE, fileSystem.classify(directory));
        assertEquals(MigrationPathState.UNSAFE, fileSystem.classify(link));
    }

    @Test
    void zeroLockRecoveryRejectsSymlinkAndNonregularLockWithoutArtifacts()
            throws IOException {
        byte[] legacyBytes;
        try (InputStream input = getClass().getResourceAsStream(
                "/dev/molang/iamzombieq/config/migration/parser/"
                        + "legacy-complete.toml")) {
            assertTrue(input != null, "legacy fixture must be available");
            legacyBytes = input.readAllBytes();
        }

        for (boolean symlink : new boolean[] {false, true}) {
            Path caseDirectory = Files.createDirectory(
                    temporaryDirectory.resolve(
                            symlink ? "symlink-lock" : "directory-lock"));
            Path legacy = Files.write(
                    caseDirectory.resolve("iamzombieq-common.toml"),
                    legacyBytes);
            Path target =
                    caseDirectory.resolve("iamzombieq-server.toml");
            MigrationFileSystem.ArtifactPaths artifacts =
                    MigrationFileSystem.ArtifactPaths.forTarget(target);
            Path linkTarget = null;
            if (symlink) {
                linkTarget = Files.createFile(
                        caseDirectory.resolve("external-zero-lock"));
                Files.createSymbolicLink(
                        artifacts.lock(), linkTarget.getFileName());
            } else {
                Files.createDirectory(artifacts.lock());
            }
            JdkMigrationFileSystem fileSystem =
                    new JdkMigrationFileSystem();
            MigrationBinding binding = MigrationBinding.capture(
                    fileSystem.observeBinding(target));
            MigrationAccessProfile profile =
                    MigrationAccessProfile.select(
                            fileSystem.capabilities(binding), false);
            ConfigMigrationEngine.Request request =
                    new ConfigMigrationEngine.Request(
                            MigrationTarget.SERVER,
                            legacy,
                            target,
                            binding,
                            profile,
                            Optional.empty(),
                            false);

            try (JdkMigrationFileSystem.StoreSession store =
                    fileSystem.openStore(profile, binding, legacy)) {
                MigrationFailure failure = assertThrows(
                        MigrationFailure.class,
                        () -> new ConfigMigrationEngine(
                                        ConfigSchemaCatalog.load(),
                                        MigrationFaultInjector.none())
                                .migrate(request, store),
                        Boolean.toString(symlink));
                assertEquals(
                        "nofollow-metadata",
                        failure.operation(),
                        Boolean.toString(symlink));
            }

            assertFalse(Files.exists(target));
            assertFalse(Files.exists(artifacts.journal()));
            assertFalse(Files.exists(artifacts.marker()));
            if (symlink) {
                assertTrue(Files.isSymbolicLink(artifacts.lock()));
                assertEquals(0, Files.size(linkTarget));
            } else {
                assertTrue(Files.isDirectory(artifacts.lock()));
            }
        }
    }

    @Test
    void relativeOperandsCannotEscapeTheBoundParent() {
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        for (String operand : new String[] {"", ".", "..", "../escape", "nested/file"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> fileSystem.validateRelativeBasename(operand));
        }
        assertEquals(
                "iamzombieq-server.toml",
                fileSystem.validateRelativeBasename("iamzombieq-server.toml"));
    }

    @Test
    void publisherMetadataFailureRetainsTheConcreteBackendCause()
            throws IOException {
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        Path legacy = temporaryDirectory.resolve("iamzombieq-common.toml");
        Path target = temporaryDirectory.resolve("iamzombieq-server.toml");
        MigrationBinding binding =
                MigrationBinding.capture(fileSystem.observeBinding(target));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        ConfigMigrationEngine.PublishRequest request =
                new ConfigMigrationEngine.PublishRequest(
                        AtomicConfigPublisher.Artifact.JOURNAL,
                        artifacts.fixedStages().getFirst(),
                        artifacts.journal(),
                        new byte[] {1},
                        AtomicConfigPublisher.DestinationExpectation.absent(),
                        false);
        AtomicConfigPublisher.Port port;
        JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy);
        try {
            port = store.publicationPort(request);
        } finally {
            store.close();
        }

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new AtomicConfigPublisher(port)
                        .resume(request.atomicRequest()));

        assertTrue(failure.getCause() instanceof IOException);
        Throwable backendCause = failure.getCause().getCause();
        assertTrue(
                backendCause != null,
                "publisher metadata failure must retain the backend cause");
        assertTrue(failure.getCause()
                .getMessage()
                .contains(backendCause.getClass().getSimpleName()));
    }

    @Test
    void realBackendRejectsBytesFromAReplacedOpenedLeaf() throws IOException {
        Path target = Files.writeString(
                temporaryDirectory.resolve("iamzombieq-server.toml"), "AAAA");
        Path alternate = Files.writeString(
                temporaryDirectory.resolve("alternate.toml"), "BBBB");
        Path held = temporaryDirectory.resolve("held.toml");
        JdkMigrationFileSystem.ContentOpenHook hook = (point, path) -> {
            if (!path.equals(target)) {
                return;
            }
            if (point
                    == JdkMigrationFileSystem.ContentOpenPoint
                            .BEFORE_PRIMARY_OPEN) {
                Files.move(target, held, StandardCopyOption.ATOMIC_MOVE);
                Files.move(alternate, target, StandardCopyOption.ATOMIC_MOVE);
            } else if (point
                    == JdkMigrationFileSystem.ContentOpenPoint
                            .AFTER_PRIMARY_READ) {
                Files.move(target, alternate, StandardCopyOption.ATOMIC_MOVE);
                Files.move(held, target, StandardCopyOption.ATOMIC_MOVE);
            }
        };
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem(hook);
        MigrationBinding binding =
                MigrationBinding.capture(fileSystem.observeBinding(target));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);

        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, target)) {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> store.read(
                            target,
                            MigrationDirectorySession.ContentKind
                                    .EXISTING_TARGET));
            assertTrue(failure.getMessage().contains("bound migration content"));
        }

        assertEquals("AAAA", Files.readString(target));
        assertEquals("BBBB", Files.readString(alternate));
    }

    @Test
    void realBoundStoreMigratesAndRecoversWithoutLegacyReseed()
            throws IOException {
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        Path legacy = temporaryDirectory.resolve("iamzombieq-common.toml");
        Path target = temporaryDirectory.resolve("iamzombieq-server.toml");
        try (InputStream input = getClass().getResourceAsStream(
                "/dev/molang/iamzombieq/config/migration/parser/"
                        + "legacy-complete.toml")) {
            assertTrue(input != null, "legacy fixture must be available");
            Files.write(legacy, input.readAllBytes());
        }

        MigrationBinding binding =
                MigrationBinding.capture(fileSystem.observeBinding(target));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);
        ConfigMigrationEngine engine = new ConfigMigrationEngine(
                ConfigSchemaCatalog.load(), MigrationFaultInjector.none());
        ConfigMigrationEngine.Request request = new ConfigMigrationEngine.Request(
                MigrationTarget.SERVER,
                legacy,
                target,
                binding,
                profile,
                Optional.empty(),
                false);

        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy)) {
            MigrationTargetState migrated = engine.migrate(request, store);
            assertEquals(
                    MigrationTargetState.Outcome.MIGRATED,
                    migrated.outcome());
        }

        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        assertTrue(Files.isRegularFile(target));
        assertTrue(Files.isRegularFile(artifacts.lock()));
        assertTrue(Files.isRegularFile(artifacts.journal()));
        assertTrue(Files.isRegularFile(artifacts.backup()));
        assertTrue(Files.isRegularFile(artifacts.initial()));
        assertTrue(Files.isRegularFile(artifacts.marker()));
        for (Path stage : artifacts.fixedStages()) {
            assertFalse(Files.exists(stage));
        }

        Files.writeString(legacy, "not valid legacy TOML");
        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy)) {
            MigrationTargetState recovered = engine.migrate(request, store);
            assertEquals(
                    MigrationTargetState.Outcome.COMPLETE,
                    recovered.outcome());
        }
    }

    @Test
    void realRestartRecoversZeroLengthFirstCreationLockOnSameInode()
            throws IOException {
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        Path legacy = temporaryDirectory.resolve("iamzombieq-common.toml");
        try (InputStream input = getClass().getResourceAsStream(
                "/dev/molang/iamzombieq/config/migration/parser/"
                        + "legacy-complete.toml")) {
            assertTrue(input != null, "legacy fixture must be available");
            Files.write(legacy, input.readAllBytes());
        }
        Path target = temporaryDirectory.resolve("iamzombieq-server.toml");
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        MigrationBinding binding =
                MigrationBinding.capture(fileSystem.observeBinding(target));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);
        ConfigMigrationEngine.Request request =
                new ConfigMigrationEngine.Request(
                        MigrationTarget.SERVER,
                        legacy,
                        target,
                        binding,
                        profile,
                        Optional.empty(),
                        false);
        MigrationFaultInjector stopAfterCreate = point -> {
            if (point.operation()
                            == MigrationFaultInjector.Operation.LOCK_TRY_LOCK
                    && point.timing()
                            == MigrationFaultInjector.Timing.BEFORE) {
                throw new IllegalStateException(
                        "synthetic first-creation interruption");
            }
        };

        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy)) {
            MigrationFailure first = assertThrows(
                    MigrationFailure.class,
                    () -> new ConfigMigrationEngine(
                                    ConfigSchemaCatalog.load(),
                                    stopAfterCreate)
                            .migrate(request, store));
            assertTrue(first.synthetic());
        }

        assertTrue(Files.isRegularFile(artifacts.lock()));
        assertEquals(0, Files.size(artifacts.lock()));
        BasicFileAttributes before = Files.readAttributes(
                artifacts.lock(), BasicFileAttributes.class);
        java.util.EnumSet<MigrationFaultInjector.Operation> operations =
                java.util.EnumSet.noneOf(
                        MigrationFaultInjector.Operation.class);
        MigrationFaultInjector recorder =
                point -> operations.add(point.operation());

        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy)) {
            MigrationTargetState recovered =
                    new ConfigMigrationEngine(
                                    ConfigSchemaCatalog.load(), recorder)
                            .migrate(request, store);
            assertEquals(
                    MigrationTargetState.Outcome.MIGRATED,
                    recovered.outcome());
        }

        BasicFileAttributes after = Files.readAttributes(
                artifacts.lock(), BasicFileAttributes.class);
        assertEquals(before.fileKey(), after.fileKey());
        assertTrue(Files.size(artifacts.lock()) > 0);
        assertTrue(operations.contains(
                MigrationFaultInjector.Operation.LOCK_OPEN));
        assertTrue(operations.contains(
                MigrationFaultInjector.Operation.LOCK_TRY_LOCK));
        assertTrue(operations.contains(
                MigrationFaultInjector.Operation.LOCK_READ));
        assertTrue(operations.contains(
                MigrationFaultInjector.Operation.WRITE));
        assertTrue(operations.contains(
                MigrationFaultInjector.Operation.FILE_FORCE));
        assertFalse(operations.contains(
                MigrationFaultInjector.Operation.LOCK_CREATE));
        assertTrue(Files.isRegularFile(target));
        assertTrue(Files.isRegularFile(artifacts.journal()));
        assertTrue(Files.isRegularFile(artifacts.backup()));
        assertTrue(Files.isRegularFile(artifacts.initial()));
        assertTrue(Files.isRegularFile(artifacts.marker()));
        for (Path stage : artifacts.fixedStages()) {
            assertFalse(Files.exists(stage));
        }
    }

    @Test
    void emptyLockFaultRestartsRecoverOnlyPrewriteZeroLengthStates()
            throws IOException {
        java.util.List<EmptyLockFaultCase> cases =
                new java.util.ArrayList<>();
        for (MigrationFaultInjector.Operation operation : java.util.List.of(
                MigrationFaultInjector.Operation.LOCK_OPEN,
                MigrationFaultInjector.Operation.LOCK_TRY_LOCK,
                MigrationFaultInjector.Operation.LOCK_IDENTITY,
                MigrationFaultInjector.Operation.LOCK_READ,
                MigrationFaultInjector.Operation.LOCK_PAYLOAD_VALIDATION)) {
            for (MigrationFaultInjector.Timing timing
                    : MigrationFaultInjector.Timing.values()) {
                cases.add(new EmptyLockFaultCase(
                        operation + "-" + timing,
                        operation,
                        timing,
                        1,
                        false,
                        true));
            }
        }
        for (int occurrence : java.util.List.of(1, 2)) {
            for (MigrationFaultInjector.Timing timing
                    : MigrationFaultInjector.Timing.values()) {
                cases.add(new EmptyLockFaultCase(
                        "LOCK_VALIDATE_"
                                + occurrence
                                + "-"
                                + timing,
                        MigrationFaultInjector.Operation.LOCK_VALIDATE,
                        timing,
                        occurrence,
                        false,
                        true));
            }
        }
        cases.add(new EmptyLockFaultCase(
                "WRITE-BEFORE",
                MigrationFaultInjector.Operation.WRITE,
                MigrationFaultInjector.Timing.BEFORE,
                1,
                false,
                true));
        cases.add(new EmptyLockFaultCase(
                "WRITE-AFTER",
                MigrationFaultInjector.Operation.WRITE,
                MigrationFaultInjector.Timing.AFTER,
                1,
                false,
                false));
        for (MigrationFaultInjector.Operation operation : java.util.List.of(
                MigrationFaultInjector.Operation.FILE_FORCE,
                MigrationFaultInjector.Operation.DIRECTORY_DURABILITY)) {
            for (MigrationFaultInjector.Timing timing
                    : MigrationFaultInjector.Timing.values()) {
                cases.add(new EmptyLockFaultCase(
                        operation + "-" + timing,
                        operation,
                        timing,
                        1,
                        operation
                                == MigrationFaultInjector.Operation
                                        .DIRECTORY_DURABILITY,
                        false));
            }
        }
        for (MigrationFaultInjector.Timing timing
                : MigrationFaultInjector.Timing.values()) {
            cases.add(new EmptyLockFaultCase(
                    "LOCK_VALIDATE_3-" + timing,
                    MigrationFaultInjector.Operation.LOCK_VALIDATE,
                    timing,
                    3,
                    false,
                    false));
        }

        byte[] legacyBytes;
        try (InputStream input = getClass().getResourceAsStream(
                "/dev/molang/iamzombieq/config/migration/parser/"
                        + "legacy-complete.toml")) {
            assertTrue(input != null, "legacy fixture must be available");
            legacyBytes = input.readAllBytes();
        }

        for (int index = 0; index < cases.size(); index++) {
            EmptyLockFaultCase faultCase = cases.get(index);
            Path caseDirectory = Files.createDirectory(
                    temporaryDirectory.resolve("fault-restart-" + index));
            Path legacy = Files.write(
                    caseDirectory.resolve("iamzombieq-common.toml"),
                    legacyBytes);
            Path target =
                    caseDirectory.resolve("iamzombieq-server.toml");
            MigrationFileSystem.ArtifactPaths artifacts =
                    MigrationFileSystem.ArtifactPaths.forTarget(target);
            Files.createFile(artifacts.lock());
            BasicFileAttributes before = Files.readAttributes(
                    artifacts.lock(), BasicFileAttributes.class);

            JdkMigrationFileSystem fileSystem =
                    new JdkMigrationFileSystem();
            MigrationBinding binding = MigrationBinding.capture(
                    fileSystem.observeBinding(target));
            MigrationAccessProfile profile =
                    MigrationAccessProfile.select(
                            fileSystem.capabilities(binding), false);
            ConfigMigrationEngine.Request request =
                    new ConfigMigrationEngine.Request(
                            MigrationTarget.SERVER,
                            legacy,
                            target,
                            binding,
                            profile,
                            Optional.empty(),
                            faultCase.strong());
            java.util.concurrent.atomic.AtomicInteger occurrences =
                    new java.util.concurrent.atomic.AtomicInteger();
            MigrationFaultInjector injector = point -> {
                if (point.operation() == faultCase.operation()
                        && point.timing() == faultCase.timing()
                        && occurrences.incrementAndGet()
                                == faultCase.occurrence()) {
                    throw new IllegalStateException(
                            "synthetic empty-lock restart "
                                    + faultCase.id());
                }
            };

            try (JdkMigrationFileSystem.StoreSession store =
                    fileSystem.openStore(profile, binding, legacy)) {
                MigrationFailure first = assertThrows(
                        MigrationFailure.class,
                        () -> new ConfigMigrationEngine(
                                        ConfigSchemaCatalog.load(), injector)
                                .migrate(request, store),
                        faultCase.id());
                assertTrue(first.synthetic(), faultCase.id());
            }

            assertTrue(
                    occurrences.get() >= faultCase.occurrence(),
                    faultCase.id());
            if (faultCase.recoverable()) {
                assertEquals(
                        0,
                        Files.size(artifacts.lock()),
                        faultCase.id());
            } else {
                assertTrue(
                        Files.size(artifacts.lock()) > 0,
                        faultCase.id());
            }
            assertFalse(
                    Files.exists(artifacts.journal()),
                    faultCase.id());
            BasicFileAttributes afterFault = Files.readAttributes(
                    artifacts.lock(), BasicFileAttributes.class);
            assertEquals(
                    before.fileKey(),
                    afterFault.fileKey(),
                    faultCase.id());

            if (!faultCase.recoverable()) {
                Files.writeString(
                        legacy,
                        "invalid legacy must not be parsed on restart");
            }
            try (JdkMigrationFileSystem.StoreSession store =
                    fileSystem.openStore(profile, binding, legacy)) {
                if (faultCase.recoverable()) {
                    assertEquals(
                            MigrationTargetState.Outcome.MIGRATED,
                            new ConfigMigrationEngine(
                                            ConfigSchemaCatalog.load(),
                                            MigrationFaultInjector.none())
                                    .migrate(request, store)
                                    .outcome(),
                            faultCase.id());
                } else {
                    MigrationFailure restart = assertThrows(
                            MigrationFailure.class,
                            () -> new ConfigMigrationEngine(
                                            ConfigSchemaCatalog.load(),
                                            MigrationFaultInjector.none())
                                    .migrate(request, store),
                            faultCase.id());
                    assertTrue(
                            restart.operation().equals("locked-recovery")
                                    || restart.operation().equals(
                                            "lock-durability-recovery"),
                            faultCase.id() + ": " + restart.operation());
                }
            }

            BasicFileAttributes afterRestart = Files.readAttributes(
                    artifacts.lock(), BasicFileAttributes.class);
            assertEquals(
                    before.fileKey(),
                    afterRestart.fileKey(),
                    faultCase.id());
            if (!faultCase.recoverable()) {
                assertFalse(Files.exists(target), faultCase.id());
                assertFalse(
                        Files.exists(artifacts.journal()),
                        faultCase.id());
                assertFalse(
                        Files.exists(artifacts.marker()),
                        faultCase.id());
            }
        }
    }

    @Test
    void realEmptyLockGateRejectsEveryArtifactAppearingWhileOsLockIsHeld()
            throws IOException {
        byte[] legacyBytes;
        try (InputStream input = getClass().getResourceAsStream(
                "/dev/molang/iamzombieq/config/migration/parser/"
                        + "legacy-complete.toml")) {
            assertTrue(input != null, "legacy fixture must be available");
            legacyBytes = input.readAllBytes();
        }

        for (int index = 0; index < 10; index++) {
            Path caseDirectory = Files.createDirectory(
                    temporaryDirectory.resolve("under-lock-" + index));
            Path legacy = Files.write(
                    caseDirectory.resolve("iamzombieq-common.toml"),
                    legacyBytes);
            Path target =
                    caseDirectory.resolve("iamzombieq-server.toml");
            MigrationFileSystem.ArtifactPaths artifacts =
                    MigrationFileSystem.ArtifactPaths.forTarget(target);
            java.util.List<Path> conflicts =
                    new java.util.ArrayList<>(java.util.List.of(
                            target,
                            artifacts.journal(),
                            artifacts.backup(),
                            artifacts.initial(),
                            artifacts.marker()));
            conflicts.addAll(artifacts.fixedStages());
            Path conflict = conflicts.get(index);
            Files.createFile(artifacts.lock());
            BasicFileAttributes before = Files.readAttributes(
                    artifacts.lock(), BasicFileAttributes.class);

            JdkMigrationFileSystem fileSystem =
                    new JdkMigrationFileSystem();
            MigrationBinding binding = MigrationBinding.capture(
                    fileSystem.observeBinding(target));
            MigrationAccessProfile profile =
                    MigrationAccessProfile.select(
                            fileSystem.capabilities(binding), false);
            ConfigMigrationEngine.Request request =
                    new ConfigMigrationEngine.Request(
                            MigrationTarget.SERVER,
                            legacy,
                            target,
                            binding,
                            profile,
                            Optional.empty(),
                            false);
            AtomicBoolean injected = new AtomicBoolean();
            MigrationFaultInjector appearUnderLock = point -> {
                if (point.operation()
                                == MigrationFaultInjector.Operation.LOCK_READ
                        && point.timing()
                                == MigrationFaultInjector.Timing.AFTER
                        && injected.compareAndSet(false, true)) {
                    try {
                        Files.write(
                                conflict,
                                "non-cooperative conflict"
                                        .getBytes(
                                                java.nio.charset.StandardCharsets
                                                        .UTF_8),
                                StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE);
                    } catch (IOException failure) {
                        throw new IllegalStateException(
                                "could not create under-lock conflict",
                                failure);
                    }
                }
            };

            MigrationFailure failure;
            try (JdkMigrationFileSystem.StoreSession store =
                    fileSystem.openStore(profile, binding, legacy)) {
                failure = assertThrows(
                        MigrationFailure.class,
                        () -> new ConfigMigrationEngine(
                                        ConfigSchemaCatalog.load(),
                                        appearUnderLock)
                                .migrate(request, store),
                        conflict.toString());
            }

            assertTrue(injected.get(), conflict.toString());
            assertTrue(
                    failure.reason().contains(
                            conflict.getFileName().toString()),
                    conflict.toString());
            BasicFileAttributes after = Files.readAttributes(
                    artifacts.lock(), BasicFileAttributes.class);
            assertEquals(
                    before.fileKey(),
                    after.fileKey(),
                    conflict.toString());
            assertEquals(
                    0,
                    Files.size(artifacts.lock()),
                    conflict.toString());
            assertTrue(Files.exists(conflict), conflict.toString());
        }
    }

    @Test
    void realContendedZeroLengthLockFailsNonblockingThenRecoversSameInode()
            throws IOException {
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        Path legacy = temporaryDirectory.resolve("iamzombieq-common.toml");
        try (InputStream input = getClass().getResourceAsStream(
                "/dev/molang/iamzombieq/config/migration/parser/"
                        + "legacy-complete.toml")) {
            assertTrue(input != null, "legacy fixture must be available");
            Files.write(legacy, input.readAllBytes());
        }
        Path target = temporaryDirectory.resolve("iamzombieq-server.toml");
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        Files.createFile(artifacts.lock());
        BasicFileAttributes before = Files.readAttributes(
                artifacts.lock(), BasicFileAttributes.class);
        MigrationBinding binding =
                MigrationBinding.capture(fileSystem.observeBinding(target));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);
        ConfigMigrationEngine.Request request =
                new ConfigMigrationEngine.Request(
                        MigrationTarget.SERVER,
                        legacy,
                        target,
                        binding,
                        profile,
                        Optional.empty(),
                        false);

        try (java.nio.channels.FileChannel blocker =
                        java.nio.channels.FileChannel.open(
                                artifacts.lock(),
                                StandardOpenOption.READ,
                                StandardOpenOption.WRITE);
                java.nio.channels.FileLock ignored = blocker.lock();
                JdkMigrationFileSystem.StoreSession store =
                        fileSystem.openStore(profile, binding, legacy)) {
            MigrationFailure contention = assertThrows(
                    MigrationFailure.class,
                    () -> new ConfigMigrationEngine(
                                    ConfigSchemaCatalog.load(),
                                    MigrationFaultInjector.none())
                            .migrate(request, store));
            assertTrue(contention.reason().contains("contended"));
        }

        assertEquals(0, Files.size(artifacts.lock()));
        assertFalse(Files.exists(artifacts.journal()));
        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy)) {
            assertEquals(
                    MigrationTargetState.Outcome.MIGRATED,
                    new ConfigMigrationEngine(
                                    ConfigSchemaCatalog.load(),
                                    MigrationFaultInjector.none())
                            .migrate(request, store)
                            .outcome());
        }
        BasicFileAttributes after = Files.readAttributes(
                artifacts.lock(), BasicFileAttributes.class);
        assertEquals(before.fileKey(), after.fileKey());
    }

    @Test
    void observablePermanentLockPathnameReplacementFailsClosed()
            throws IOException {
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        Path legacy = temporaryDirectory.resolve("iamzombieq-common.toml");
        try (InputStream input = getClass().getResourceAsStream(
                "/dev/molang/iamzombieq/config/migration/parser/"
                        + "legacy-complete.toml")) {
            assertTrue(input != null, "legacy fixture must be available");
            Files.write(legacy, input.readAllBytes());
        }
        Path target = temporaryDirectory.resolve("iamzombieq-server.toml");
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        Path displacedLock = temporaryDirectory.resolve("displaced-lock");
        MigrationBinding binding =
                MigrationBinding.capture(fileSystem.observeBinding(target));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);
        ConfigMigrationEngine.Request request =
                new ConfigMigrationEngine.Request(
                        MigrationTarget.SERVER,
                        legacy,
                        target,
                        binding,
                        profile,
                        Optional.empty(),
                        false);
        AtomicBoolean replaced = new AtomicBoolean();
        MigrationFaultInjector replaceAfterAcquisition = point -> {
            if (point.operation()
                            == MigrationFaultInjector.Operation.LOCK_ACQUIRE
                    && point.timing()
                            == MigrationFaultInjector.Timing.AFTER
                    && replaced.compareAndSet(false, true)) {
                try {
                    byte[] payload = Files.readAllBytes(artifacts.lock());
                    Files.move(
                            artifacts.lock(),
                            displacedLock,
                            StandardCopyOption.ATOMIC_MOVE);
                    Files.write(
                            artifacts.lock(),
                            payload,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                } catch (IOException failure) {
                    throw new IllegalStateException(
                            "could not simulate lock pathname replacement",
                            failure);
                }
            }
        };

        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy)) {
            MigrationFailure failure = assertThrows(
                    MigrationFailure.class,
                    () -> new ConfigMigrationEngine(
                                    ConfigSchemaCatalog.load(),
                                    replaceAfterAcquisition)
                            .migrate(request, store));
            assertEquals(
                    "permanent-lock-revalidation",
                    failure.operation());
            assertTrue(failure.reason().contains("identity"));
        }

        assertTrue(replaced.get());
        assertTrue(Files.isRegularFile(artifacts.lock()));
        assertTrue(Files.isRegularFile(displacedLock));
        assertFalse(Files.exists(target));
        assertFalse(Files.exists(artifacts.marker()));
    }

    @Test
    void realSecureStoreBindsSeparateLegacyAndWorldTargetParents()
            throws IOException {
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        Path globalConfig = Files.createDirectory(
                temporaryDirectory.resolve("global-config"));
        Path worldServerConfig = Files.createDirectories(
                temporaryDirectory.resolve("world").resolve("serverconfig"));
        Path legacy = globalConfig.resolve("iamzombieq-common.toml");
        Path target = worldServerConfig.resolve("iamzombieq-server.toml");
        byte[] legacyBytes;
        try (InputStream input = getClass().getResourceAsStream(
                "/dev/molang/iamzombieq/config/migration/parser/"
                        + "legacy-complete.toml")) {
            assertTrue(input != null, "legacy fixture must be available");
            legacyBytes = input.readAllBytes();
            Files.write(legacy, legacyBytes);
        }

        MigrationBinding binding =
                MigrationBinding.capture(fileSystem.observeBinding(target));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);
        ConfigMigrationEngine engine = new ConfigMigrationEngine(
                ConfigSchemaCatalog.load(), MigrationFaultInjector.none());
        ConfigMigrationEngine.Request request = new ConfigMigrationEngine.Request(
                MigrationTarget.SERVER,
                legacy,
                target,
                binding,
                profile,
                Optional.empty(),
                false);

        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy)) {
            assertEquals(
                    MigrationTargetState.Outcome.MIGRATED,
                    engine.migrate(request, store).outcome());
        }

        MigrationFileSystem.ArtifactPaths worldArtifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        assertTrue(Files.isRegularFile(target));
        assertTrue(Files.isRegularFile(worldArtifacts.lock()));
        assertTrue(Files.isRegularFile(worldArtifacts.journal()));
        assertTrue(Files.isRegularFile(worldArtifacts.backup()));
        assertTrue(Files.isRegularFile(worldArtifacts.initial()));
        assertTrue(Files.isRegularFile(worldArtifacts.marker()));
        assertEquals(
                MigrationPathState.ABSENT,
                fileSystem.classify(
                        globalConfig.resolve("iamzombieq-server.toml")));
        assertEquals(
                MigrationPathState.ABSENT,
                fileSystem.classify(
                        globalConfig.resolve(
                                "iamzombieq-server.toml.migration.lock")));
        assertEquals(
                java.util.Arrays.toString(legacyBytes),
                java.util.Arrays.toString(Files.readAllBytes(legacy)));
    }

    @Test
    void cooperativeMigratorsContendOnTheSamePermanentInode()
            throws Exception {
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        Path legacy = temporaryDirectory.resolve("iamzombieq-common.toml");
        try (InputStream input = getClass().getResourceAsStream(
                "/dev/molang/iamzombieq/config/migration/parser/"
                        + "legacy-complete.toml")) {
            assertTrue(input != null, "legacy fixture must be available");
            Files.write(legacy, input.readAllBytes());
        }
        Path target = temporaryDirectory.resolve("iamzombieq-server.toml");
        MigrationBinding binding =
                MigrationBinding.capture(fileSystem.observeBinding(target));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);
        ConfigMigrationEngine.Request request = new ConfigMigrationEngine.Request(
                MigrationTarget.SERVER,
                legacy,
                target,
                binding,
                profile,
                Optional.empty(),
                false);
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        CountDownLatch firstReachedJournal = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        MigrationFaultInjector pauseAfterPreparation = point -> {
            if (point.artifact() == AtomicConfigPublisher.Artifact.JOURNAL
                    && point.operation()
                            == MigrationFaultInjector.Operation.STAGE_CREATE
                    && point.timing()
                            == MigrationFaultInjector.Timing.BEFORE) {
                firstReachedJournal.countDown();
                try {
                    if (!releaseFirst.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "timed out waiting to release first migrator");
                    }
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "first migrator was interrupted", interruption);
                }
            }
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<MigrationTargetState> first = executor.submit(() -> {
            try (JdkMigrationFileSystem.StoreSession store =
                    fileSystem.openStore(profile, binding, legacy)) {
                return new ConfigMigrationEngine(
                                ConfigSchemaCatalog.load(),
                                pauseAfterPreparation)
                        .migrate(request, store);
            }
        });
        try {
            assertTrue(
                    firstReachedJournal.await(10, TimeUnit.SECONDS),
                    "first migrator must hold the permanent lock");
            assertTrue(Files.isRegularFile(artifacts.lock()));
            assertFalse(Files.exists(artifacts.journal()));
            BasicFileAttributes before = Files.readAttributes(
                    artifacts.lock(), BasicFileAttributes.class);
            byte[] lockBytes = Files.readAllBytes(artifacts.lock());

            Files.writeString(legacy, "corrupt after first preparation");
            try (JdkMigrationFileSystem.StoreSession competingStore =
                    fileSystem.openStore(profile, binding, legacy)) {
                MigrationFailure contention = assertThrows(
                        MigrationFailure.class,
                        () -> new ConfigMigrationEngine(
                                        ConfigSchemaCatalog.load(),
                                        MigrationFaultInjector.none())
                                .migrate(request, competingStore));
                assertTrue(contention.reason().contains("contended"));
            }

            BasicFileAttributes afterContention = Files.readAttributes(
                    artifacts.lock(), BasicFileAttributes.class);
            assertEquals(before.fileKey(), afterContention.fileKey());
            assertEquals(
                    java.util.Arrays.toString(lockBytes),
                    java.util.Arrays.toString(
                            Files.readAllBytes(artifacts.lock())));
            assertFalse(Files.exists(artifacts.journal()));
            assertFalse(Files.exists(artifacts.backup()));
            assertFalse(Files.exists(artifacts.initial()));
            assertFalse(Files.exists(target));
            assertFalse(Files.exists(artifacts.marker()));

            releaseFirst.countDown();
            assertEquals(
                    MigrationTargetState.Outcome.MIGRATED,
                    first.get(10, TimeUnit.SECONDS).outcome());
            BasicFileAttributes afterCompletion = Files.readAttributes(
                    artifacts.lock(), BasicFileAttributes.class);
            assertEquals(before.fileKey(), afterCompletion.fileKey());

            try (JdkMigrationFileSystem.StoreSession recoveryStore =
                    fileSystem.openStore(profile, binding, legacy)) {
                assertEquals(
                        MigrationTargetState.Outcome.COMPLETE,
                        new ConfigMigrationEngine(
                                        ConfigSchemaCatalog.load(),
                                        MigrationFaultInjector.none())
                                .migrate(request, recoveryStore)
                                .outcome());
            }
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void certifiedLinuxBindingCannotOpenLexicalBasicStore()
            throws IOException {
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        Path legacy = Files.writeString(
                temporaryDirectory.resolve("iamzombieq-common.toml"),
                "legacy");
        Path target = temporaryDirectory.resolve("iamzombieq-server.toml");
        MigrationBinding binding =
                MigrationBinding.capture(fileSystem.observeBinding(target));

        assertThrows(
                IllegalStateException.class,
                () -> {
                    try (JdkMigrationFileSystem.StoreSession ignored =
                            fileSystem.openStore(
                                    MigrationAccessProfile.BASIC,
                                    binding,
                                    legacy)) {
                        throw new AssertionError(
                                "Linux BASIC store unexpectedly opened");
                    }
                });
    }

    @Test
    void strongLockDirectoryFaultCannotResumeAsBasic()
            throws IOException {
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        Path legacy = temporaryDirectory.resolve("iamzombieq-common.toml");
        try (InputStream input = getClass().getResourceAsStream(
                "/dev/molang/iamzombieq/config/migration/parser/"
                        + "legacy-complete.toml")) {
            assertTrue(input != null, "legacy fixture must be available");
            Files.write(legacy, input.readAllBytes());
        }
        Path target = temporaryDirectory.resolve("iamzombieq-server.toml");
        MigrationBinding binding =
                MigrationBinding.capture(fileSystem.observeBinding(target));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);
        ConfigMigrationEngine.Request request =
                new ConfigMigrationEngine.Request(
                        MigrationTarget.SERVER,
                        legacy,
                        target,
                        binding,
                        profile,
                        Optional.empty(),
                        true);
        MigrationFaultInjector directoryFault = point -> {
            if (point.artifact() == null
                    && point.operation()
                            == MigrationFaultInjector.Operation
                                    .DIRECTORY_DURABILITY
                    && point.timing()
                            == MigrationFaultInjector.Timing.BEFORE) {
                throw new IllegalStateException(
                        "synthetic lock directory durability");
            }
        };

        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy)) {
            MigrationFailure first = assertThrows(
                    MigrationFailure.class,
                    () -> new ConfigMigrationEngine(
                                    ConfigSchemaCatalog.load(),
                                    directoryFault)
                            .migrate(request, store));
            assertTrue(first.synthetic());
        }

        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        assertTrue(Files.isRegularFile(artifacts.lock()));
        assertFalse(Files.exists(artifacts.journal()));
        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy)) {
            MigrationFailure restart = assertThrows(
                    MigrationFailure.class,
                    () -> new ConfigMigrationEngine(
                                    ConfigSchemaCatalog.load(),
                                    MigrationFaultInjector.none())
                            .migrate(request, store));
            assertTrue(restart.reason().contains(
                    "STRONG permanent lock durability"));
        }
        assertFalse(Files.exists(artifacts.journal()));
    }

    private record EmptyLockFaultCase(
            String id,
            MigrationFaultInjector.Operation operation,
            MigrationFaultInjector.Timing timing,
            int occurrence,
            boolean strong,
            boolean recoverable) {}
}
