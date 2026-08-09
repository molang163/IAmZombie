package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class JdkMigrationFileSystemTest {
    private static final int NODE_JAVA_FEATURE = Integer.parseInt(
            System.getProperty("iamzombieq.test.runtimeJavaFeature"));

    @TempDir
    Path temporaryDirectory;

    @Test
    void bindingAlwaysIncludesDriveOrFilesystemRootThroughLogicalParent()
            throws IOException {
        Path parent = Files.createDirectories(
                temporaryDirectory.resolve("nested/config"));
        Path target = parent.resolve("iamzombieq-server.toml");

        MigrationBinding binding = MigrationBinding.capture(
                new JdkMigrationFileSystem().observeBinding(target));

        assertEquals(target.getRoot(), binding.ancestors().getFirst().path());
        assertEquals(parent, binding.ancestors().getLast().path());
    }

    private static JdkMigrationFileSystem certifiedFileSystem() {
        return new JdkMigrationFileSystem();
    }

    private static JdkMigrationFileSystem certifiedFileSystem(
            JdkMigrationFileSystem.ContentOpenHook hook) {
        return new JdkMigrationFileSystem(hook);
    }

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
    @EnabledOnOs(OS.LINUX)
    void defaultObservationRetainsTheCertifiedNodeRuntimeFeature()
            throws IOException {
        Path legacy = temporaryDirectory.resolve("iamzombieq-common.toml");
        Path target = temporaryDirectory.resolve("iamzombieq-server.toml");
        String legacySentinel = "legacy-preservation-sentinel";
        Files.writeString(legacy, legacySentinel);
        JdkMigrationFileSystem defaultFileSystem =
                new JdkMigrationFileSystem();
        MigrationBinding defaultBinding = MigrationBinding.capture(
                defaultFileSystem.observeBinding(target));
        MigrationAccessProfile.Capabilities defaultCapabilities =
                defaultFileSystem.capabilities(defaultBinding);

        assertEquals(NODE_JAVA_FEATURE, Runtime.version().feature());
        assertEquals(NODE_JAVA_FEATURE, defaultBinding.javaFeature());
        assertEquals(NODE_JAVA_FEATURE, defaultCapabilities.javaFeature());
        assertEquals("Linux", defaultCapabilities.operatingSystem());
        assertEquals("file", defaultCapabilities.providerScheme());
        assertEquals(
                "sun.nio.fs.LinuxFileSystemProvider",
                defaultCapabilities.providerClass());
        assertTrue(defaultCapabilities.defaultProvider());
        assertTrue(defaultCapabilities.secureDirectoryStream());
        assertTrue(defaultCapabilities.nofollowMetadata());
        assertTrue(defaultCapabilities.nofollowOpen());
        assertTrue(defaultCapabilities.atomicMove());
        assertEquals(
                MigrationAccessProfile.SECURE,
                MigrationAccessProfile.select(
                        defaultCapabilities, false));
        try (JdkMigrationFileSystem.StoreSession ignored =
                defaultFileSystem.openStore(
                        MigrationAccessProfile.SECURE,
                        defaultBinding,
                        legacy)) {
            assertFalse(Files.exists(target));
        }

        JdkMigrationFileSystem certifiedFixture = certifiedFileSystem();
        MigrationBinding certifiedBinding = MigrationBinding.capture(
                certifiedFixture.observeBinding(target));
        assertEquals(NODE_JAVA_FEATURE, certifiedBinding.javaFeature());
        assertEquals(
                MigrationAccessProfile.SECURE,
                MigrationAccessProfile.select(
                        certifiedFixture.capabilities(certifiedBinding),
                        false));
        assertEquals(legacySentinel, Files.readString(legacy));
        assertFalse(Files.exists(target));
        for (Path artifact :
                MigrationFileSystem.ArtifactPaths.forTarget(target).fixedCandidates()) {
            assertFalse(Files.exists(artifact));
        }
    }

    @Test
    void capabilityObservationFixtureRemainsPackagePrivate()
            throws NoSuchMethodException {
        int classModifiers = JdkMigrationFileSystem.class.getModifiers();
        JdkMigrationFileSystem.class.getDeclaredConstructor();
        JdkMigrationFileSystem.class.getDeclaredConstructor(
                JdkMigrationFileSystem.ContentOpenHook.class);
        int constructorModifiers = JdkMigrationFileSystem.class
                .getDeclaredConstructor(
                        JdkMigrationFileSystem.ContentOpenHook.class,
                        int.class)
                .getModifiers();

        assertFalse(Modifier.isPublic(classModifiers));
        assertFalse(Modifier.isProtected(classModifiers));
        assertFalse(Modifier.isPublic(constructorModifiers));
        assertFalse(Modifier.isProtected(constructorModifiers));
        assertFalse(Modifier.isPrivate(constructorModifiers));
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
                    certifiedFileSystem();
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
    @EnabledOnOs(OS.LINUX)
    void publisherMetadataFailureRetainsTheConcreteBackendCause()
            throws IOException {
        JdkMigrationFileSystem fileSystem = certifiedFileSystem();
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
        Path physicalTarget = target.toRealPath();
        AtomicBoolean swapped = new AtomicBoolean();
        AtomicBoolean restored = new AtomicBoolean();
        JdkMigrationFileSystem.ContentOpenHook hook = (point, path) -> {
            if (!path.equals(physicalTarget)) {
                return;
            }
            if (point
                    == JdkMigrationFileSystem.ContentOpenPoint
                            .BEFORE_PRIMARY_OPEN) {
                Files.move(target, held, StandardCopyOption.ATOMIC_MOVE);
                Files.move(alternate, target, StandardCopyOption.ATOMIC_MOVE);
                swapped.set(true);
            } else if (point
                    == JdkMigrationFileSystem.ContentOpenPoint
                            .AFTER_PRIMARY_READ) {
                Files.move(target, alternate, StandardCopyOption.ATOMIC_MOVE);
                Files.move(held, target, StandardCopyOption.ATOMIC_MOVE);
                restored.set(true);
            }
        };
        JdkMigrationFileSystem fileSystem = certifiedFileSystem(hook);
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

        assertTrue(swapped.get(), "the replacement hook must use the bound real path");
        assertTrue(restored.get(), "the replacement hook must restore the original leaf");
        assertEquals("AAAA", Files.readString(target));
        assertEquals("BBBB", Files.readString(alternate));
    }

    @Test
    void realBoundStoreMigratesAndRecoversWithoutLegacyReseed()
            throws IOException {
        JdkMigrationFileSystem fileSystem = certifiedFileSystem();
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
    @EnabledOnOs(OS.LINUX)
    void realCommittedPublicationFaultsResumeEveryArtifactWithoutRepublish()
            throws IOException {
        for (AtomicConfigPublisher.Artifact artifact
                : AtomicConfigPublisher.Artifact.values()) {
            Path directory = Files.createDirectory(
                    temporaryDirectory.resolve(
                            "committed-" + artifact.name().toLowerCase()));
            Path legacy = Files.write(
                    directory.resolve("iamzombieq-common.toml"),
                    LegacyConfigParserTest.fixtureBytes());
            Path target = directory.resolve("iamzombieq-server.toml");
            MigrationFileSystem.ArtifactPaths artifacts =
                    MigrationFileSystem.ArtifactPaths.forTarget(target);
            AtomicBoolean fired = new AtomicBoolean();
            MigrationFaultInjector committedFault = point -> {
                if (point.artifact() == artifact
                        && point.operation()
                                == MigrationFaultInjector.Operation.ATOMIC_MOVE
                        && point.timing()
                                == MigrationFaultInjector.Timing.AFTER
                        && point.phase() == committedFaultPhase(artifact)
                        && fired.compareAndSet(false, true)) {
                    throw new IllegalStateException(
                            "synthetic committed publication " + artifact);
                }
            };

            RealMigrationRun first = realMigrationRun(
                    new JdkMigrationFileSystem(), legacy, target, true);
            try (JdkMigrationFileSystem.StoreSession store =
                    first.fileSystem().openStore(
                            first.profile(), first.binding(), legacy)) {
                MigrationFailure failure = assertThrows(
                        MigrationFailure.class,
                        () -> new ConfigMigrationEngine(
                                        ConfigSchemaCatalog.load(),
                                        committedFault)
                                .migrate(first.request(), store),
                        artifact.name());
                assertTrue(failure.synthetic(), artifact.name());
            }
            assertTrue(fired.get(), artifact.name());

            Path destination = committedDestination(artifacts, artifact);
            Path stage = committedStage(artifacts, artifact);
            assertTrue(Files.isRegularFile(destination), artifact.name());
            assertFalse(Files.exists(stage), artifact.name());
            RealFileSnapshot committed = realFileSnapshot(destination);
            Files.writeString(
                    legacy,
                    "corrupt live legacy after committed " + artifact);

            AtomicInteger legacyContentOpens = new AtomicInteger();
            AtomicBoolean republished = new AtomicBoolean();
            JdkMigrationFileSystem restartedFileSystem =
                    new JdkMigrationFileSystem((point, path) -> {
                        if (path.equals(legacy)) {
                            legacyContentOpens.incrementAndGet();
                        }
                    });
            RealMigrationRun restarted = realMigrationRun(
                    restartedFileSystem, legacy, target, true);
            MigrationFaultInjector publicationRecorder = point -> {
                if (point.artifact() == artifact
                        && point.operation()
                                == MigrationFaultInjector.Operation.ATOMIC_MOVE) {
                    republished.set(true);
                }
            };
            MigrationTargetState recovered;
            try (JdkMigrationFileSystem.StoreSession store =
                    restarted.fileSystem().openStore(
                            restarted.profile(), restarted.binding(), legacy)) {
                recovered = new ConfigMigrationEngine(
                                ConfigSchemaCatalog.load(),
                                publicationRecorder)
                        .migrate(restarted.request(), store);
            }

            assertEquals(
                    MigrationTargetState.Outcome.COMPLETE,
                    recovered.outcome(),
                    artifact.name());
            assertEquals(
                    MigrationEvidence.Durability.STRONG,
                    recovered.commitProfile(),
                    artifact.name());
            assertTrue(
                    recovered.artifactDurability().values().stream()
                            .allMatch(value ->
                                    value == MigrationEvidence.Durability.STRONG),
                    artifact.name());
            assertEquals(0, legacyContentOpens.get(), artifact.name());
            assertFalse(republished.get(), artifact.name());
            assertRealFileSnapshotUnchanged(
                    committed, destination, artifact.name());
            for (Path fixedStage : artifacts.fixedStages()) {
                assertFalse(Files.exists(fixedStage), artifact.name());
            }
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void realPrecommitStagesForEveryArtifactRemainManualRecoveryEvidence()
            throws IOException {
        for (AtomicConfigPublisher.Artifact artifact
                : AtomicConfigPublisher.Artifact.values()) {
            Path directory = Files.createDirectory(
                    temporaryDirectory.resolve(
                            "precommit-" + artifact.name().toLowerCase()));
            Path legacy = Files.write(
                    directory.resolve("iamzombieq-common.toml"),
                    LegacyConfigParserTest.fixtureBytes());
            Path target = directory.resolve("iamzombieq-server.toml");
            MigrationFileSystem.ArtifactPaths artifacts =
                    MigrationFileSystem.ArtifactPaths.forTarget(target);
            AtomicBoolean fired = new AtomicBoolean();
            MigrationFaultInjector precommitFault = point -> {
                if (point.artifact() == artifact
                        && point.operation()
                                == MigrationFaultInjector.Operation.ATOMIC_MOVE
                        && point.timing()
                                == MigrationFaultInjector.Timing.BEFORE
                        && fired.compareAndSet(false, true)) {
                    throw new IllegalStateException(
                            "synthetic precommit interruption " + artifact);
                }
            };

            RealMigrationRun first = realMigrationRun(
                    new JdkMigrationFileSystem(), legacy, target, true);
            try (JdkMigrationFileSystem.StoreSession store =
                    first.fileSystem().openStore(
                            first.profile(), first.binding(), legacy)) {
                MigrationFailure failure = assertThrows(
                        MigrationFailure.class,
                        () -> new ConfigMigrationEngine(
                                        ConfigSchemaCatalog.load(),
                                        precommitFault)
                                .migrate(first.request(), store),
                        artifact.name());
                assertTrue(failure.synthetic(), artifact.name());
            }
            assertTrue(fired.get(), artifact.name());
            Path stage = committedStage(artifacts, artifact);
            Path destination = committedDestination(artifacts, artifact);
            assertTrue(Files.isRegularFile(stage), artifact.name());
            assertTrue(Files.size(stage) > 0, artifact.name());
            assertFalse(Files.exists(destination), artifact.name());
            RealFileSnapshot orphan = realFileSnapshot(stage);

            RealMigrationRun restarted = realMigrationRun(
                    new JdkMigrationFileSystem(), legacy, target, true);
            try (JdkMigrationFileSystem.StoreSession store =
                    restarted.fileSystem().openStore(
                            restarted.profile(), restarted.binding(), legacy)) {
                MigrationFailure failure = assertThrows(
                        MigrationFailure.class,
                        () -> new ConfigMigrationEngine(
                                        ConfigSchemaCatalog.load(),
                                        MigrationFaultInjector.none())
                                .migrate(restarted.request(), store),
                        artifact.name());
                assertFalse(failure.synthetic(), artifact.name());
                assertEquals(
                        "orphan-stage-check",
                        failure.operation(),
                        artifact.name());
            }
            assertRealFileSnapshotUnchanged(
                    orphan, stage, artifact.name());
            assertFalse(Files.exists(destination), artifact.name());
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void realCompleteEditsRemainReadOnlyAndNeverReseedFromLegacy()
            throws IOException {
        for (boolean valid : new boolean[] {true, false}) {
            Path directory = Files.createDirectory(
                    temporaryDirectory.resolve(
                            valid ? "complete-valid" : "complete-invalid"));
            Path legacy = Files.write(
                    directory.resolve("iamzombieq-common.toml"),
                    LegacyConfigParserTest.fixtureBytes());
            Path target = directory.resolve("iamzombieq-server.toml");
            MigrationFileSystem.ArtifactPaths artifacts =
                    MigrationFileSystem.ArtifactPaths.forTarget(target);
            RealMigrationRun first = realMigrationRun(
                    new JdkMigrationFileSystem(), legacy, target, true);
            try (JdkMigrationFileSystem.StoreSession store =
                    first.fileSystem().openStore(
                            first.profile(), first.binding(), legacy)) {
                assertEquals(
                        MigrationTargetState.Outcome.MIGRATED,
                        new ConfigMigrationEngine(
                                        ConfigSchemaCatalog.load(),
                                        MigrationFaultInjector.none())
                                .migrate(first.request(), store)
                                .outcome());
            }

            List<Path> evidencePaths = List.of(
                    artifacts.lock(),
                    artifacts.journal(),
                    artifacts.backup(),
                    artifacts.initial(),
                    artifacts.marker());
            List<RealFileSnapshot> evidenceBefore = new ArrayList<>();
            for (Path evidence : evidencePaths) {
                evidenceBefore.add(realFileSnapshot(evidence));
            }
            String canonical = Files.readString(target);
            String replacement = valid
                    ? "startingRottenFlesh = 10"
                    : "startingRottenFlesh = 65";
            String edited = canonical.replace(
                    "startingRottenFlesh = 9", replacement);
            assertFalse(canonical.equals(edited));
            Files.writeString(target, edited);
            RealFileSnapshot editedTarget = realFileSnapshot(target);
            Files.writeString(legacy, "corrupt live legacy after COMPLETE");

            AtomicInteger legacyContentOpens = new AtomicInteger();
            JdkMigrationFileSystem restartedFileSystem =
                    new JdkMigrationFileSystem((point, path) -> {
                        if (path.equals(legacy)) {
                            legacyContentOpens.incrementAndGet();
                        }
                    });
            RealMigrationRun restarted = realMigrationRun(
                    restartedFileSystem, legacy, target, true);
            try (JdkMigrationFileSystem.StoreSession store =
                    restarted.fileSystem().openStore(
                            restarted.profile(), restarted.binding(), legacy)) {
                ConfigMigrationEngine engine = new ConfigMigrationEngine(
                        ConfigSchemaCatalog.load(),
                        MigrationFaultInjector.none());
                if (valid) {
                    assertEquals(
                            MigrationTargetState.Outcome.COMPLETE,
                            engine.migrate(restarted.request(), store).outcome());
                } else {
                    MigrationFailure failure = assertThrows(
                            MigrationFailure.class,
                            () -> engine.migrate(restarted.request(), store));
                    assertFalse(failure.synthetic());
                    assertEquals(
                            MigrationTargetState.Phase.COMPLETE,
                            failure.phase());
                    assertEquals(
                            "complete-target-validation",
                            failure.operation());
                }
            }

            assertEquals(0, legacyContentOpens.get());
            assertRealFileSnapshotUnchanged(
                    editedTarget, target, "COMPLETE edited target");
            for (int index = 0; index < evidencePaths.size(); index++) {
                assertRealFileSnapshotUnchanged(
                        evidenceBefore.get(index),
                        evidencePaths.get(index),
                        "COMPLETE evidence " + evidencePaths.get(index));
            }
            for (Path stage : artifacts.fixedStages()) {
                assertFalse(Files.exists(stage));
            }
        }
    }

    @Test
    void processDeathReleasesPermanentLockAndResumesCommittedTarget()
            throws Exception {
        assumeTrue(System.getProperty("os.name").equals("Linux"));
        for (MigrationProcessDeathPeer.Mode mode
                : List.of(
                        MigrationProcessDeathPeer.Mode.LOCK_AFTER_OS_ACQUIRE,
                        MigrationProcessDeathPeer.Mode.TARGET_AFTER_ATOMIC_MOVE)) {
            Path directory = Files.createDirectory(
                    temporaryDirectory.resolve(
                            "process-" + mode.name().toLowerCase()));
            Path legacy = Files.write(
                    directory.resolve("iamzombieq-common.toml"),
                    LegacyConfigParserTest.fixtureBytes());
            Path target = directory.resolve("iamzombieq-server.toml");
            MigrationFileSystem.ArtifactPaths artifacts =
                    MigrationFileSystem.ArtifactPaths.forTarget(target);
            Path stdout = temporaryDirectory.resolve(
                    mode.name().toLowerCase() + ".stdout.log");
            Path stderr = temporaryDirectory.resolve(
                    mode.name().toLowerCase() + ".stderr.log");
            String runtimeClasspath = System.getProperty(
                    "iamzombieq.test.runtimeClasspath");
            assertTrue(runtimeClasspath != null
                    && !runtimeClasspath.isBlank());
            Path java = Path.of(
                    System.getProperty("java.home"), "bin", "java");
            assertTrue(Files.isExecutable(java));

            Process process = new ProcessBuilder(
                            java.toString(),
                            "-Xms16m",
                            "-Xmx128m",
                            "-cp",
                            runtimeClasspath,
                            MigrationProcessDeathPeer.class.getName(),
                            mode.name(),
                            directory.toString(),
                            Long.toString(ProcessHandle.current().pid()))
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile())
                    .start();
            try {
                assertTrue(
                        process.waitFor(30, TimeUnit.SECONDS),
                        () -> "peer timed out: " + mode);
                assertEquals(
                        mode.exitCode(),
                        process.exitValue(),
                        () -> mode
                                + " stderr: "
                                + readIfPresent(stderr));
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(10, TimeUnit.SECONDS);
                }
            }

            if (mode
                    == MigrationProcessDeathPeer.Mode.LOCK_AFTER_OS_ACQUIRE) {
                assertTrue(Files.isRegularFile(artifacts.lock()));
                assertEquals(0, Files.size(artifacts.lock()));
                String lockFileKey = Files.readAttributes(
                                artifacts.lock(), BasicFileAttributes.class)
                        .fileKey()
                        .toString();
                RealMigrationRun restarted = realMigrationRun(
                        new JdkMigrationFileSystem(), legacy, target, true);
                try (JdkMigrationFileSystem.StoreSession store =
                        restarted.fileSystem().openStore(
                                restarted.profile(),
                                restarted.binding(),
                                legacy)) {
                    assertEquals(
                            MigrationTargetState.Outcome.MIGRATED,
                            new ConfigMigrationEngine(
                                            ConfigSchemaCatalog.load(),
                                            MigrationFaultInjector.none())
                                    .migrate(restarted.request(), store)
                                    .outcome());
                }
                assertEquals(
                        lockFileKey,
                        Files.readAttributes(
                                        artifacts.lock(),
                                        BasicFileAttributes.class)
                                .fileKey()
                                .toString());
                assertTrue(Files.size(artifacts.lock()) > 0);
            } else {
                assertTrue(Files.isRegularFile(target));
                assertFalse(Files.exists(committedStage(
                        artifacts, AtomicConfigPublisher.Artifact.TARGET)));
                RealFileSnapshot committedTarget = realFileSnapshot(target);
                Files.writeString(
                        legacy,
                        "corrupt live legacy after target process death");
                AtomicInteger legacyContentOpens = new AtomicInteger();
                JdkMigrationFileSystem restartedFileSystem =
                        new JdkMigrationFileSystem((point, path) -> {
                            if (path.equals(legacy)) {
                                legacyContentOpens.incrementAndGet();
                            }
                        });
                RealMigrationRun restarted = realMigrationRun(
                        restartedFileSystem, legacy, target, true);
                try (JdkMigrationFileSystem.StoreSession store =
                        restarted.fileSystem().openStore(
                                restarted.profile(),
                                restarted.binding(),
                                legacy)) {
                    assertEquals(
                            MigrationTargetState.Outcome.COMPLETE,
                            new ConfigMigrationEngine(
                                            ConfigSchemaCatalog.load(),
                                            MigrationFaultInjector.none())
                                    .migrate(restarted.request(), store)
                                    .outcome());
                }
                assertEquals(0, legacyContentOpens.get());
                assertRealFileSnapshotUnchanged(
                        committedTarget, target, "process-death target");
            }
            assertTrue(Files.isRegularFile(target));
            assertTrue(Files.isRegularFile(artifacts.marker()));
            for (Path stage : artifacts.fixedStages()) {
                assertFalse(Files.exists(stage));
            }
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void realPreparedEvidenceCannotCrossActualJavaRuntime()
            throws Exception {
        if (!MigrationJavaRuntimeMatrix.runtimeFeatures()
                .equals(java.util.Set.of(22, 25))) {
            return;
        }
        assertEquals("Linux", System.getProperty("os.name"));
        int currentFeature = Runtime.version().feature();
        int evidenceFeature = currentFeature == 22 ? 25 : 22;
        Path evidenceJava = Path.of(System.getProperty(
                "iamzombieq.test.javaExecutable." + evidenceFeature));
        assertTrue(Files.isExecutable(evidenceJava));

        Path directory = Files.createDirectory(
                temporaryDirectory.resolve(
                        "cross-runtime-" + evidenceFeature + "-to-"
                                + currentFeature));
        Path legacy = Files.write(
                directory.resolve("iamzombieq-common.toml"),
                LegacyConfigParserTest.fixtureBytes());
        Path target = directory.resolve("iamzombieq-server.toml");
        Path stdout = directory.resolve("producer.stdout.log");
        Path stderr = directory.resolve("producer.stderr.log");
        String runtimeClasspath = System.getProperty(
                "iamzombieq.test.runtimeClasspath");
        assertTrue(runtimeClasspath != null && !runtimeClasspath.isBlank());

        Process producer = new ProcessBuilder(
                        evidenceJava.toString(),
                        "-Xms16m",
                        "-Xmx128m",
                        "-cp",
                        runtimeClasspath,
                        MigrationProcessDeathPeer.class.getName(),
                        MigrationProcessDeathPeer.Mode
                                .PREPARED_AFTER_JOURNAL_MOVE
                                .name(),
                        directory.toString(),
                        Long.toString(ProcessHandle.current().pid()))
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();
        try {
            assertTrue(
                    producer.waitFor(30, TimeUnit.SECONDS),
                    () -> "cross-runtime producer timed out: "
                            + readIfPresent(stderr));
            assertEquals(
                    MigrationProcessDeathPeer.PREPARED_EXIT,
                    producer.exitValue(),
                    () -> "cross-runtime producer failed: "
                            + readIfPresent(stderr));
        } finally {
            if (producer.isAlive()) {
                producer.destroyForcibly();
                producer.waitFor(10, TimeUnit.SECONDS);
            }
        }

        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        assertTrue(Files.isRegularFile(artifacts.lock()));
        assertTrue(Files.isRegularFile(artifacts.journal()));
        MigrationJournal journal = MigrationJournal.decode(
                Files.readAllBytes(artifacts.journal()));
        assertEquals(
                MigrationTargetState.Phase.PREPARED, journal.phase());
        assertEquals(
                evidenceFeature,
                journal.evidence().binding().javaFeature());

        List<Path> migrationPaths = new ArrayList<>(List.of(
                artifacts.lock(),
                artifacts.journal(),
                artifacts.backup(),
                artifacts.initial(),
                artifacts.target(),
                artifacts.marker()));
        migrationPaths.addAll(artifacts.fixedStages());
        java.util.Map<Path, RealFileSnapshot> snapshots =
                new java.util.LinkedHashMap<>();
        for (Path path : migrationPaths) {
            if (Files.exists(path)) {
                snapshots.put(path, realFileSnapshot(path));
            }
        }
        Files.writeString(legacy, "corrupt legacy before feature mismatch");

        AtomicInteger legacyContentOpens = new AtomicInteger();
        AtomicBoolean republished = new AtomicBoolean();
        JdkMigrationFileSystem currentFileSystem =
                new JdkMigrationFileSystem((point, path) -> {
                    if (path.equals(legacy)) {
                        legacyContentOpens.incrementAndGet();
                    }
                });
        RealMigrationRun restarted = realMigrationRun(
                currentFileSystem, legacy, target, true);
        assertEquals(currentFeature, restarted.binding().javaFeature());
        MigrationFaultInjector publicationRecorder = point -> {
            if (point.operation()
                    == MigrationFaultInjector.Operation.ATOMIC_MOVE) {
                republished.set(true);
            }
        };
        try (JdkMigrationFileSystem.StoreSession store =
                restarted.fileSystem().openStore(
                        restarted.profile(), restarted.binding(), legacy)) {
            MigrationFailure failure = assertThrows(
                    MigrationFailure.class,
                    () -> new ConfigMigrationEngine(
                                    ConfigSchemaCatalog.load(),
                                    publicationRecorder)
                            .migrate(restarted.request(), store));
            assertFalse(failure.synthetic());
            assertEquals(
                    MigrationTargetState.Phase.PREPARED,
                    failure.phase());
            assertEquals(
                    "evidence-binding-validation", failure.operation());
        }

        assertEquals(0, legacyContentOpens.get());
        assertFalse(republished.get());
        for (Path path : migrationPaths) {
            assertEquals(snapshots.containsKey(path), Files.exists(path));
        }
        for (var snapshot : snapshots.entrySet()) {
            assertRealFileSnapshotUnchanged(
                    snapshot.getValue(),
                    snapshot.getKey(),
                    evidenceFeature + "->" + currentFeature);
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void realRestartRecoversZeroLengthFirstCreationLockOnSameInode()
            throws IOException {
        JdkMigrationFileSystem fileSystem = certifiedFileSystem();
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
    @EnabledOnOs(OS.LINUX)
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
                    certifiedFileSystem();
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
    @EnabledOnOs(OS.LINUX)
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
                    certifiedFileSystem();
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
    @EnabledOnOs(OS.LINUX)
    void realContendedZeroLengthLockFailsNonblockingThenRecoversSameInode()
            throws IOException {
        JdkMigrationFileSystem fileSystem = certifiedFileSystem();
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
    @EnabledOnOs(OS.LINUX)
    void observablePermanentLockPathnameReplacementFailsClosed()
            throws IOException {
        JdkMigrationFileSystem fileSystem = certifiedFileSystem();
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
        JdkMigrationFileSystem fileSystem = certifiedFileSystem();
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
    @EnabledOnOs(OS.LINUX)
    void cooperativeMigratorsContendOnTheSamePermanentInode()
            throws Exception {
        JdkMigrationFileSystem fileSystem = certifiedFileSystem();
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
    @EnabledOnOs(OS.LINUX)
    void certifiedLinuxBindingCannotOpenLexicalBasicStore()
            throws IOException {
        JdkMigrationFileSystem fileSystem = certifiedFileSystem();
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
    @EnabledOnOs(OS.LINUX)
    void strongLockDirectoryFaultCannotResumeAsBasic()
            throws IOException {
        JdkMigrationFileSystem fileSystem = certifiedFileSystem();
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

    private static RealMigrationRun realMigrationRun(
            JdkMigrationFileSystem fileSystem,
            Path legacy,
            Path target,
            boolean strong) throws IOException {
        MigrationBinding binding = MigrationBinding.capture(
                fileSystem.observeBinding(target));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);
        return new RealMigrationRun(
                fileSystem,
                binding,
                profile,
                new ConfigMigrationEngine.Request(
                        MigrationTarget.SERVER,
                        legacy,
                        target,
                        binding,
                        profile,
                        Optional.empty(),
                        strong));
    }

    private static MigrationTargetState.Phase committedFaultPhase(
            AtomicConfigPublisher.Artifact artifact) {
        return switch (artifact) {
            case JOURNAL -> MigrationTargetState.Phase.TARGET_PUBLISHED;
            case BACKUP -> MigrationTargetState.Phase.PREPARED;
            case INITIAL -> MigrationTargetState.Phase.BACKUP_PUBLISHED;
            case TARGET -> MigrationTargetState.Phase.INITIAL_PUBLISHED;
            case MARKER -> MigrationTargetState.Phase.COMPLETE;
        };
    }

    private static Path committedDestination(
            MigrationFileSystem.ArtifactPaths paths,
            AtomicConfigPublisher.Artifact artifact) {
        return switch (artifact) {
            case JOURNAL -> paths.journal();
            case BACKUP -> paths.backup();
            case INITIAL -> paths.initial();
            case TARGET -> paths.target();
            case MARKER -> paths.marker();
        };
    }

    private static Path committedStage(
            MigrationFileSystem.ArtifactPaths paths,
            AtomicConfigPublisher.Artifact artifact) {
        return paths.fixedStages().get(artifact.ordinal());
    }

    private static RealFileSnapshot realFileSnapshot(Path path)
            throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class);
        assertTrue(attributes.isRegularFile());
        assertTrue(attributes.fileKey() != null);
        return new RealFileSnapshot(
                attributes.fileKey().toString(),
                attributes.size(),
                attributes.lastModifiedTime(),
                Files.readAllBytes(path));
    }

    private static String readIfPresent(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path) : "ABSENT";
        } catch (IOException failure) {
            return "UNREADABLE: " + failure;
        }
    }

    private static void assertRealFileSnapshotUnchanged(
            RealFileSnapshot expected, Path path, String description)
            throws IOException {
        RealFileSnapshot actual = realFileSnapshot(path);
        assertEquals(expected.fileKey(), actual.fileKey(), description);
        assertEquals(expected.size(), actual.size(), description);
        assertEquals(expected.modified(), actual.modified(), description);
        assertArrayEquals(expected.bytes(), actual.bytes(), description);
    }

    private record RealMigrationRun(
            JdkMigrationFileSystem fileSystem,
            MigrationBinding binding,
            MigrationAccessProfile profile,
            ConfigMigrationEngine.Request request) {}

    private record RealFileSnapshot(
            String fileKey, long size, FileTime modified, byte[] bytes) {}
}
