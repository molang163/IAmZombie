package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Real-process crash/restart gate for node-approved Windows BASIC publication.
 *
 * <p>Each case starts a separate same-feature Java process on the runner-provided local
 * NTFS drive. The child halts at the production {@code ATOMIC_MOVE} checkpoint;
 * the parent then validates the on-disk portrait and restarts the real engine.
 */
@EnabledOnOs(OS.WINDOWS)
@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WindowsNtfsMigrationProcessDeathTest {
    private static final String GATE_ROOT_ENV =
            "IAMZOMBIEQ_WINDOWS_GATE_ROOT";
    private static final String WINDOWS_PROVIDER =
            "sun.nio.fs.WindowsFileSystemProvider";
    private static final String NTFS = "NTFS";
    private static final String FIXED = "Fixed";
    private static final Duration CHILD_TIMEOUT = Duration.ofSeconds(45);
    private static final int EXPECTED_CASES = 11;

    private final Set<String> completedCases = new HashSet<>();
    private Path gateRoot;
    private Path suiteRoot;

    @BeforeAll
    void createIsolatedGateRoot() throws Exception {
        assertTrue(
                System.getProperty("os.name", "unknown").startsWith("Windows"),
                "the process-death gate must run only on Windows");
        int javaFeature = Runtime.version().feature();
        assertTrue(
                MigrationJavaRuntimeMatrix.supportsBasicProfile(javaFeature),
                "the process-death gate requires a BASIC runtime approved by "
                        + "this Stonecutter node; actual feature="
                        + javaFeature
                        + ", approved="
                        + MigrationJavaRuntimeMatrix.runtimeFeatures());
        assertEquals(
                Integer.toString(javaFeature),
                System.getProperty("iamzombieq.test.runtimeJavaFeature"),
                "Gradle runtime declaration must match the actual test worker");
        assertEquals(
                "1",
                System.getenv("IAMZOMBIEQ_WINDOWS_GATE_ARMED"),
                "run the disposable Windows gate; an unarmed Windows run fails");
        assertEquals(
                "1",
                System.getenv("IAMZOMBIEQ_WINDOWS_PROCESS_DEATH_ARMED"),
                "the real process-death matrix requires its explicit arm variable");
        assertEquals(
                FIXED,
                System.getenv("IAMZOMBIEQ_WINDOWS_GATE_DRIVE_TYPE"),
                "the runner must certify a local fixed drive");
        assertEquals(
                NTFS,
                System.getenv("IAMZOMBIEQ_WINDOWS_GATE_DRIVE_FORMAT"),
                "the runner must certify exact NTFS");

        String configured = System.getenv(GATE_ROOT_ENV);
        assertNotNull(configured, "runner must provide " + GATE_ROOT_ENV);
        assertFalse(configured.isBlank(), GATE_ROOT_ENV + " must not be blank");
        gateRoot = Path.of(configured);
        assertTrue(gateRoot.isAbsolute(), "gate root must be absolute");
        assertEquals(
                gateRoot.toAbsolutePath().normalize(),
                gateRoot,
                "gate root must already be normalized");
        assertFalse(isUnc(gateRoot), "gate root must not be UNC");
        assertTrue(
                gateRoot.getFileSystem().provider()
                        == java.nio.file.FileSystems.getDefault().provider(),
                "gate root must use the default provider instance");
        assertEquals(
                WINDOWS_PROVIDER,
                gateRoot.getFileSystem().provider().getClass().getName());

        BasicFileAttributes attributes = nofollow(gateRoot);
        assertTrue(attributes.isDirectory());
        assertFalse(attributes.isSymbolicLink());
        assertFalse(attributes.isOther());
        assertEquals(null, attributes.fileKey());
        FileStore store = Files.getFileStore(gateRoot);
        assertEquals(NTFS, store.type());
        Object firstVsn = store.getAttribute("volume:vsn");
        Object secondVsn = Files.getFileStore(gateRoot)
                .getAttribute("volume:vsn");
        assertNotNull(firstVsn, "volume:vsn must be available");
        assertEquals(Integer.class, firstVsn.getClass());
        assertEquals(firstVsn, secondVsn, "volume:vsn must be stable");
        assertTrue(isEmptyDirectory(gateRoot), "gate root must start empty");

        suiteRoot = Files.createDirectory(
                gateRoot.resolve("process-death-" + UUID.randomUUID()));
    }

    @AfterAll
    void removeSuccessfulSuiteOnly() throws IOException {
        if (suiteRoot == null
                || !Files.exists(suiteRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (completedCases.size() != EXPECTED_CASES) {
            System.err.println(
                    "Preserving incomplete process-death suite for inspection: "
                            + suiteRoot
                            + " completed="
                            + completedCases);
            return;
        }
        deleteTreeNofollow(suiteRoot);
    }

    @Test
    void journalBeforeAtomicMoveIsPreservedAsF1() throws Exception {
        runCrashCase(new CrashCase(
                AtomicConfigPublisher.Artifact.JOURNAL,
                MigrationFaultInjector.Timing.BEFORE));
    }

    @Test
    void journalAfterAtomicMoveRecoversWithoutRepublishingGeneration()
            throws Exception {
        runCrashCase(new CrashCase(
                AtomicConfigPublisher.Artifact.JOURNAL,
                MigrationFaultInjector.Timing.AFTER));
    }

    @Test
    void backupBeforeAtomicMoveIsPreservedAsF1() throws Exception {
        runCrashCase(new CrashCase(
                AtomicConfigPublisher.Artifact.BACKUP,
                MigrationFaultInjector.Timing.BEFORE));
    }

    @Test
    void backupAfterAtomicMoveRecoversWithoutRepublishing()
            throws Exception {
        runCrashCase(new CrashCase(
                AtomicConfigPublisher.Artifact.BACKUP,
                MigrationFaultInjector.Timing.AFTER));
    }

    @Test
    void initialBeforeAtomicMoveIsPreservedAsF1() throws Exception {
        runCrashCase(new CrashCase(
                AtomicConfigPublisher.Artifact.INITIAL,
                MigrationFaultInjector.Timing.BEFORE));
    }

    @Test
    void initialAfterAtomicMoveRecoversWithoutRepublishing()
            throws Exception {
        runCrashCase(new CrashCase(
                AtomicConfigPublisher.Artifact.INITIAL,
                MigrationFaultInjector.Timing.AFTER));
    }

    @Test
    void targetBeforeAtomicMoveIsPreservedAsF1() throws Exception {
        runCrashCase(new CrashCase(
                AtomicConfigPublisher.Artifact.TARGET,
                MigrationFaultInjector.Timing.BEFORE));
    }

    @Test
    void targetAfterAtomicMoveRecoversWithoutRepublishing()
            throws Exception {
        runCrashCase(new CrashCase(
                AtomicConfigPublisher.Artifact.TARGET,
                MigrationFaultInjector.Timing.AFTER));
    }

    @Test
    void markerBeforeAtomicMoveIsPreservedAsF1() throws Exception {
        runCrashCase(new CrashCase(
                AtomicConfigPublisher.Artifact.MARKER,
                MigrationFaultInjector.Timing.BEFORE));
    }

    @Test
    void markerAfterAtomicMoveRecoversWithoutRepublishing()
            throws Exception {
        runCrashCase(new CrashCase(
                AtomicConfigPublisher.Artifact.MARKER,
                MigrationFaultInjector.Timing.AFTER));
    }

    @Test
    void preparedEvidenceCannotCrossActualJavaRuntime() throws Exception {
        if (!MigrationJavaRuntimeMatrix.runtimeFeatures()
                .equals(Set.of(22, 25))) {
            assertTrue(completedCases.add("CROSS_RUNTIME_NOT_APPLICABLE"));
            return;
        }
        int currentFeature = Runtime.version().feature();
        int evidenceFeature = currentFeature == 22 ? 25 : 22;
        Path evidenceJava = Path.of(System.getProperty(
                "iamzombieq.test.javaExecutable." + evidenceFeature));
        assertTrue(
                Files.isRegularFile(evidenceJava),
                "alternate Java executable is missing: " + evidenceJava);

        Path scenario = Files.createDirectory(suiteRoot.resolve(
                "cross-runtime-" + evidenceFeature + "-to-" + currentFeature));
        Path global = Files.createDirectory(scenario.resolve("config"));
        Path legacy = Files.write(
                global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                LegacyConfigParserTest.fixtureBytes(),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        Path target = global.resolve(
                ActualTargetResolver.PREFERENCES_BASENAME);
        Path classPathJar = createTestClassPathJar(
                scenario.resolve("cross-runtime-classpath.jar"));

        Process producer = new ProcessBuilder(
                        evidenceJava.toString(),
                        "-cp",
                        classPathJar.toString(),
                        WindowsMigrationCrashProcessMain.class.getName(),
                        legacy.toString(),
                        target.toString(),
                        AtomicConfigPublisher.Artifact.JOURNAL.name(),
                        MigrationFaultInjector.Timing.AFTER.name(),
                        "1")
                .redirectErrorStream(true)
                .start();
        boolean finished = producer.waitFor(
                CHILD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            producer.destroyForcibly();
            producer.waitFor(10, TimeUnit.SECONDS);
            fail("Cross-runtime evidence producer timed out");
        }
        byte[] producerOutput = producer.getInputStream().readAllBytes();
        Files.delete(classPathJar);
        assertEquals(
                WindowsMigrationCrashProcessMain.CRASH_EXIT_CODE,
                producer.exitValue(),
                () -> "Cross-runtime producer failed:\n"
                        + new String(producerOutput, Charset.defaultCharset()));

        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        MigrationJournal prepared = MigrationJournal.decode(
                Files.readAllBytes(artifacts.journal()));
        assertEquals(MigrationTargetState.Phase.PREPARED, prepared.phase());
        assertEquals(evidenceFeature, prepared.evidence().binding().javaFeature());

        Files.writeString(
                legacy,
                "corrupt legacy before Windows Java feature mismatch");
        Map<String, FilePortrait> beforeRestart = portrait(global);
        AtomicInteger legacyContentOpens = new AtomicInteger();
        AtomicBoolean republished = new AtomicBoolean();
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem(
                (point, openedPath) -> {
                    if (openedPath.equals(legacy)) {
                        legacyContentOpens.incrementAndGet();
                    }
                });
        MigrationBinding binding = MigrationBinding.capture(
                fileSystem.observeBinding(target));
        assertEquals(currentFeature, binding.javaFeature());
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);
        assertEquals(MigrationAccessProfile.BASIC, profile);
        ConfigMigrationEngine.Request request =
                new ConfigMigrationEngine.Request(
                        MigrationTarget.PREFERENCES,
                        legacy,
                        target,
                        binding,
                        profile,
                        Optional.empty(),
                        false);
        MigrationFaultInjector publicationAudit = point -> {
            if (point.operation()
                    == MigrationFaultInjector.Operation.ATOMIC_MOVE) {
                republished.set(true);
            }
        };

        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy)) {
            MigrationFailure failure = assertThrows(
                    MigrationFailure.class,
                    () -> new ConfigMigrationEngine(
                                    ConfigSchemaCatalog.load(),
                                    publicationAudit)
                            .migrate(request, store));
            assertEquals(MigrationTargetState.Phase.LOCKED, failure.phase());
            assertEquals("migration-core", failure.artifact());
            assertEquals("engine-execution", failure.operation());
            assertTrue(
                    failure.reason().contains(
                            "Pre-existing permanent lock payload is not trusted"),
                    failure.reason());
            assertFalse(failure.synthetic());
        }
        assertEquals(0, legacyContentOpens.get());
        assertFalse(republished.get());
        assertEquals(
                beforeRestart,
                portrait(global),
                "Java feature mismatch must preserve every migration artifact");
        assertTrue(completedCases.add("CROSS_RUNTIME_FEATURE_MISMATCH"));
    }

    private void runCrashCase(CrashCase crashCase) throws Exception {
        Path scenario = Files.createDirectory(
                suiteRoot.resolve(crashCase.id().toLowerCase(
                        java.util.Locale.ROOT)));
        Path global = Files.createDirectory(scenario.resolve("config"));
        byte[] legacyBytes = LegacyConfigParserTest.fixtureBytes();
        Path legacy = Files.write(
                global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                legacyBytes,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        Path target = global.resolve(
                ActualTargetResolver.PREFERENCES_BASENAME);
        byte[] canonical = canonicalPreferences(legacyBytes);
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);

        ChildResult child = launchCrashChild(
                scenario, legacy, target, crashCase);
        assertEquals(
                WindowsMigrationCrashProcessMain.CRASH_EXIT_CODE,
                child.exitCode(),
                crashCase + " did not terminate through Runtime.halt:\n" + child.output());
        assertTrue(
                child.output().contains(
                        "HALT "
                                + crashCase.artifact()
                                + " "
                                + crashCase.timing()),
                crashCase + " child did not report its armed halt:\n" + child.output());
        assertArrayEquals(
                legacyBytes,
                Files.readAllBytes(legacy),
                crashCase + " changed immutable legacy bytes");
        assertCrashLandscape(crashCase, artifacts);

        if (crashCase.timing() == MigrationFaultInjector.Timing.BEFORE) {
            assertPrecommitF1Portrait(
                    crashCase, legacy, target, legacyBytes, canonical, artifacts);
        } else {
            assertPostcommitRecovery(
                    crashCase, legacy, target, legacyBytes, canonical, artifacts);
        }
        assertTrue(completedCases.add(crashCase.id()), "duplicate matrix case");
    }

    private static void assertPrecommitF1Portrait(
            CrashCase crashCase,
            Path legacy,
            Path target,
            byte[] legacyBytes,
            byte[] canonical,
            MigrationFileSystem.ArtifactPaths artifacts) throws Exception {
        Path stage = stage(artifacts, crashCase.artifact());
        Path destination = destination(artifacts, crashCase.artifact());
        assertTrue(
                Files.exists(stage, LinkOption.NOFOLLOW_LINKS),
                crashCase + " must retain its exact fixed stage");
        assertFalse(
                Files.exists(destination, LinkOption.NOFOLLOW_LINKS),
                crashCase + " destination must remain absent before commit");
        assertStagedPayload(
                crashCase.artifact(), stage, artifacts, legacyBytes, canonical);
        assertCrashEvidence(
                crashCase.artifact(), artifacts, legacyBytes, canonical, false);

        Map<String, FilePortrait> beforeRestart = portrait(target.getParent());
        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> migrate(legacy, target, MigrationFaultInjector.none()));

        assertEquals(MigrationTargetState.Phase.NO_EVIDENCE, failure.phase());
        assertEquals(stage.getFileName().toString(), failure.artifact());
        assertEquals("orphan-stage-check", failure.operation());
        assertFalse(failure.synthetic());
        assertTrue(
                failure.reason().contains("cannot be adopted or deleted"),
                crashCase + " must fail closed without stage cleanup");
        assertTrue(failure.recovery().contains(stage.toString()));
        assertEquals(
                beforeRestart,
                portrait(target.getParent()),
                crashCase + " restart must preserve the complete F1 portrait");
        assertTrue(
                Files.exists(stage, LinkOption.NOFOLLOW_LINKS),
                crashCase + " restart must leave the fixed stage in place");
        assertArrayEquals(legacyBytes, Files.readAllBytes(legacy));
        assertStagedPayload(
                crashCase.artifact(), stage, artifacts, legacyBytes, canonical);
    }

    private static void assertPostcommitRecovery(
            CrashCase crashCase,
            Path legacy,
            Path target,
            byte[] legacyBytes,
            byte[] canonical,
            MigrationFileSystem.ArtifactPaths artifacts) throws Exception {
        Path stage = stage(artifacts, crashCase.artifact());
        Path destination = destination(artifacts, crashCase.artifact());
        assertFalse(
                Files.exists(stage, LinkOption.NOFOLLOW_LINKS),
                crashCase + " committed move must consume the fixed stage");
        assertTrue(
                Files.exists(destination, LinkOption.NOFOLLOW_LINKS),
                crashCase + " committed destination must be present");
        assertPublishedPayload(
                crashCase.artifact(), destination, artifacts, legacyBytes, canonical);
        assertCrashEvidence(
                crashCase.artifact(), artifacts, legacyBytes, canonical, true);

        FilePortrait committedPortrait = FilePortrait.from(
                destination, nofollow(destination));
        String committedSha = sha256(destination);
        PublicationAudit audit = new PublicationAudit(artifacts);
        MigrationTargetState recovered = migrate(legacy, target, audit);

        assertEquals(MigrationTargetState.Outcome.COMPLETE, recovered.outcome());
        assertEquals(MigrationTargetState.Phase.COMPLETE, recovered.phase());
        assertEquals(MigrationEvidence.Durability.BASIC, recovered.commitProfile());
        assertEquals(
                expectedRecoveryMoves(crashCase.artifact()),
                audit.counts(),
                crashCase + " restart publication set differs");
        int targetMovesBeforeHalt = switch (crashCase.artifact()) {
            case TARGET, MARKER -> 1;
            case JOURNAL, BACKUP, INITIAL -> 0;
        };
        assertEquals(
                1,
                targetMovesBeforeHalt
                        + audit.count(AtomicConfigPublisher.Artifact.TARGET),
                crashCase + " must publish the target exactly once across crash/restart");
        if (crashCase.artifact()
                == AtomicConfigPublisher.Artifact.JOURNAL) {
            assertFalse(
                    audit.stageHashes(AtomicConfigPublisher.Artifact.JOURNAL)
                            .contains(committedSha),
                    "journal recovery may publish later generations but must not "
                            + "republish the committed PREPARED journal");
        } else {
            assertEquals(
                    0,
                    audit.count(crashCase.artifact()),
                    crashCase + " committed artifact must not be published twice");
            assertEquals(
                    committedPortrait,
                    FilePortrait.from(destination, nofollow(destination)),
                    crashCase + " committed artifact must not be overwritten");
        }

        assertArrayEquals(legacyBytes, Files.readAllBytes(legacy));
        assertCompleteEvidence(artifacts, legacyBytes, canonical);
        for (Path fixedStage : artifacts.fixedStages()) {
            assertFalse(
                    Files.exists(fixedStage, LinkOption.NOFOLLOW_LINKS),
                    "successful recovery must leave no fixed stage: " + fixedStage);
        }
    }

    private static ChildResult launchCrashChild(
            Path scenario,
            Path legacy,
            Path target,
            CrashCase crashCase) throws Exception {
        Path classPathJar = createTestClassPathJar(
                scenario.resolve("crash-test-classpath.jar"));
        Path java = Path.of(
                System.getProperty("java.home"), "bin", "java.exe");
        assertTrue(Files.isRegularFile(java), "Java executable missing: " + java);

        Process process = new ProcessBuilder(
                        java.toString(),
                        "-cp",
                        classPathJar.toString(),
                        WindowsMigrationCrashProcessMain.class.getName(),
                        legacy.toString(),
                        target.toString(),
                        crashCase.artifact().name(),
                        crashCase.timing().name(),
                        "1")
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(
                CHILD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            fail("Crash child timed out after " + CHILD_TIMEOUT + ": " + crashCase);
        }
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.exitValue();
        Files.delete(classPathJar);
        return new ChildResult(
                exitCode, new String(output, Charset.defaultCharset()));
    }

    private static Path createTestClassPathJar(Path jar) throws Exception {
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        addCodeSource(entries, WindowsMigrationCrashProcessMain.class);
        addCodeSource(entries, ConfigMigrationEngine.class);
        for (String entry : System.getProperty("java.class.path", "")
                .split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                entries.add(Path.of(entry).toAbsolutePath().normalize()
                        .toUri()
                        .toASCIIString());
            }
        }
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(
                Attributes.Name.CLASS_PATH, String.join(" ", entries));
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(
                        jar,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE),
                manifest)) {
            // The manifest-only pathing JAR keeps the Windows command line short.
        }
        return jar;
    }

    private static void addCodeSource(Set<String> entries, Class<?> type)
            throws URISyntaxException {
        var protectionDomain = type.getProtectionDomain();
        var codeSource = protectionDomain == null
                ? null
                : protectionDomain.getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new IllegalStateException(
                    "Test classpath has no code source for " + type.getName());
        }
        entries.add(codeSource.getLocation().toURI().toASCIIString());
    }

    private static MigrationTargetState migrate(
            Path legacy,
            Path target,
            MigrationFaultInjector faults) throws Exception {
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        MigrationBinding binding = MigrationBinding.capture(
                fileSystem.observeBinding(target));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);
        assertEquals(
                MigrationAccessProfile.BASIC,
                profile,
                "restart must use the real Windows BASIC production path");
        ConfigMigrationEngine.Request request =
                new ConfigMigrationEngine.Request(
                        MigrationTarget.PREFERENCES,
                        legacy,
                        target,
                        binding,
                        profile,
                        Optional.empty(),
                        false);
        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy)) {
            return new ConfigMigrationEngine(ConfigSchemaCatalog.load(), faults)
                    .migrate(request, store);
        }
    }

    private static byte[] canonicalPreferences(byte[] legacy) {
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        Map<String, Object> projection = ConfigProjection.project(
                MigrationTarget.PREFERENCES,
                LegacyConfigParser.parse(legacy),
                schema);
        return ConfigProjectionCodec.encode(
                        MigrationTarget.PREFERENCES, projection, schema)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void assertStagedPayload(
            AtomicConfigPublisher.Artifact artifact,
            Path stage,
            MigrationFileSystem.ArtifactPaths artifacts,
            byte[] legacy,
            byte[] canonical) throws Exception {
        byte[] actual = Files.readAllBytes(stage);
        switch (artifact) {
            case JOURNAL -> {
                MigrationJournal journal = MigrationJournal.decode(actual);
                assertEquals(1, journal.generation());
                assertEquals(MigrationTargetState.Phase.PREPARED, journal.phase());
                assertEvidenceHashes(journal.evidence(), artifacts, legacy, canonical);
            }
            case BACKUP -> assertArrayEquals(legacy, actual);
            case INITIAL, TARGET -> assertArrayEquals(canonical, actual);
            case MARKER -> {
                MigrationMarker marker = MigrationMarker.decode(actual);
                assertEquals(MigrationTargetState.Phase.COMPLETE, marker.phase());
                assertEvidenceHashes(marker.evidence(), artifacts, legacy, canonical);
            }
        }
    }

    private static void assertPublishedPayload(
            AtomicConfigPublisher.Artifact artifact,
            Path destination,
            MigrationFileSystem.ArtifactPaths artifacts,
            byte[] legacy,
            byte[] canonical) throws Exception {
        byte[] actual = Files.readAllBytes(destination);
        switch (artifact) {
            case JOURNAL -> {
                MigrationJournal journal = MigrationJournal.decode(actual);
                assertEquals(1, journal.generation());
                assertEquals(MigrationTargetState.Phase.PREPARED, journal.phase());
                assertEvidenceHashes(journal.evidence(), artifacts, legacy, canonical);
            }
            case BACKUP -> assertArrayEquals(legacy, actual);
            case INITIAL, TARGET -> assertArrayEquals(canonical, actual);
            case MARKER -> {
                MigrationMarker marker = MigrationMarker.decode(actual);
                assertEquals(MigrationTargetState.Phase.COMPLETE, marker.phase());
                assertEvidenceHashes(marker.evidence(), artifacts, legacy, canonical);
            }
        }
    }

    private static void assertCrashEvidence(
            AtomicConfigPublisher.Artifact crashArtifact,
            MigrationFileSystem.ArtifactPaths artifacts,
            byte[] legacy,
            byte[] canonical,
            boolean committed) throws Exception {
        if (crashArtifact == AtomicConfigPublisher.Artifact.JOURNAL
                && !committed) {
            assertFalse(Files.exists(
                    artifacts.journal(), LinkOption.NOFOLLOW_LINKS));
            return;
        }
        MigrationJournal journal = MigrationJournal.decode(
                Files.readAllBytes(artifacts.journal()));
        MigrationTargetState.Phase expected = switch (crashArtifact) {
            case JOURNAL, BACKUP -> MigrationTargetState.Phase.PREPARED;
            case INITIAL -> MigrationTargetState.Phase.BACKUP_PUBLISHED;
            case TARGET -> MigrationTargetState.Phase.INITIAL_PUBLISHED;
            case MARKER -> MigrationTargetState.Phase.COMPLETE;
        };
        assertEquals(expected, journal.phase());
        assertEvidenceHashes(journal.evidence(), artifacts, legacy, canonical);
        if (crashArtifact == AtomicConfigPublisher.Artifact.MARKER
                && committed) {
            MigrationMarker marker = MigrationMarker.decode(
                    Files.readAllBytes(artifacts.marker()));
            assertEquals(journal.evidence(), marker.evidence());
        }
    }

    private static void assertCompleteEvidence(
            MigrationFileSystem.ArtifactPaths artifacts,
            byte[] legacy,
            byte[] canonical) throws Exception {
        assertArrayEquals(legacy, Files.readAllBytes(artifacts.backup()));
        assertArrayEquals(canonical, Files.readAllBytes(artifacts.initial()));
        assertArrayEquals(canonical, Files.readAllBytes(artifacts.target()));
        MigrationJournal journal = MigrationJournal.decode(
                Files.readAllBytes(artifacts.journal()));
        MigrationMarker marker = MigrationMarker.decode(
                Files.readAllBytes(artifacts.marker()));
        assertEquals(5, journal.generation());
        assertEquals(MigrationTargetState.Phase.COMPLETE, journal.phase());
        assertEquals(journal.evidence(), marker.evidence());
        assertEvidenceHashes(journal.evidence(), artifacts, legacy, canonical);
    }

    private static void assertEvidenceHashes(
            MigrationEvidence evidence,
            MigrationFileSystem.ArtifactPaths artifacts,
            byte[] legacy,
            byte[] canonical) throws Exception {
        assertEquals(MigrationTarget.PREFERENCES, evidence.targetKind());
        assertEquals(artifacts.target(), evidence.target());
        assertEquals(MigrationAccessProfile.BASIC, evidence.profile());
        assertEquals(
                MigrationEvidence.Durability.BASIC,
                evidence.commitProfile());
        assertTrue(
                evidence.lockIdentity().startsWith(
                        MigrationIdentityPolicy.WINDOWS_BASIC_FINGERPRINT_V1
                                + ":"));
        assertTrue(
                evidence.binding().directoryIdentity().startsWith(
                        MigrationIdentityPolicy.WINDOWS_BASIC_FINGERPRINT_V1
                                + ":"));
        for (MigrationBinding.Ancestor ancestor :
                evidence.binding().ancestors()) {
            assertTrue(
                    ancestor.identity().startsWith(
                            MigrationIdentityPolicy.WINDOWS_BASIC_FINGERPRINT_V1
                                    + ":"));
        }
        assertEquals(sha256(legacy), evidence.rawLegacySha256());
        assertEquals(projectionSha(canonical), evidence.projectionSha256());

        Set<String> expectedArtifactKeys = switch (
                MigrationTargetState.Phase.valueOf(evidence.phase())) {
            case PREPARED -> Set.of("lock");
            case BACKUP_PUBLISHED -> Set.of("lock", "backup");
            case INITIAL_PUBLISHED -> Set.of("lock", "backup", "initial");
            case TARGET_PUBLISHED, COMPLETE ->
                    Set.of("lock", "backup", "initial", "target");
            case NO_EVIDENCE, LOCKED -> throw new AssertionError(
                    "transient phase cannot be persisted in evidence");
        };
        assertEquals(expectedArtifactKeys, evidence.artifactHashes().keySet());

        Map<String, Path> paths = Map.of(
                "lock", artifacts.lock(),
                "backup", artifacts.backup(),
                "initial", artifacts.initial(),
                "target", artifacts.target());
        for (Map.Entry<String, String> hash :
                evidence.artifactHashes().entrySet()) {
            Path path = paths.get(hash.getKey());
            assertNotNull(path, "unknown evidence artifact " + hash.getKey());
            assertTrue(
                    Files.exists(path, LinkOption.NOFOLLOW_LINKS),
                    "evidence-bound artifact must exist: " + path);
            assertEquals(hash.getValue(), sha256(path));
            assertEquals(
                    MigrationEvidence.Durability.BASIC,
                    evidence.artifactDurability().get(hash.getKey()));
        }
        assertEquals(
                evidence.artifactHashes().keySet(),
                evidence.artifactDurability().keySet());
    }

    private static void assertCrashLandscape(
            CrashCase crashCase,
            MigrationFileSystem.ArtifactPaths artifacts) throws Exception {
        assertTrue(
                Files.size(artifacts.lock()) > 0,
                crashCase + " must leave an initialized permanent lock");
        for (AtomicConfigPublisher.Artifact artifact :
                AtomicConfigPublisher.Artifact.values()) {
            boolean earlier = artifact.ordinal() < crashCase.artifact().ordinal();
            boolean currentCommitted = artifact == crashCase.artifact()
                    && crashCase.timing() == MigrationFaultInjector.Timing.AFTER;
            assertEquals(
                    earlier || currentCommitted,
                    Files.exists(
                            destination(artifacts, artifact),
                            LinkOption.NOFOLLOW_LINKS),
                    crashCase + " unexpected canonical presence for " + artifact);
            assertEquals(
                    artifact == crashCase.artifact()
                            && crashCase.timing()
                                    == MigrationFaultInjector.Timing.BEFORE,
                    Files.exists(stage(artifacts, artifact),
                            LinkOption.NOFOLLOW_LINKS),
                    crashCase + " unexpected fixed-stage presence for " + artifact);
        }
    }

    private static String projectionSha(byte[] canonical) {
        Map<String, Object> values = LegacyConfigParser.parse(canonical)
                .rawValues();
        String typed = ConfigProjectionCodec.typedSha256(
                MigrationTarget.PREFERENCES,
                values,
                ConfigSchemaCatalog.load());
        return typed.substring(typed.lastIndexOf(':') + 1);
    }

    private static Map<AtomicConfigPublisher.Artifact, Integer>
            expectedRecoveryMoves(AtomicConfigPublisher.Artifact crashed) {
        return switch (crashed) {
            case JOURNAL -> Map.of(
                    AtomicConfigPublisher.Artifact.JOURNAL,
                    4,
                    AtomicConfigPublisher.Artifact.BACKUP,
                    1,
                    AtomicConfigPublisher.Artifact.INITIAL,
                    1,
                    AtomicConfigPublisher.Artifact.TARGET,
                    1,
                    AtomicConfigPublisher.Artifact.MARKER,
                    1);
            case BACKUP -> Map.of(
                    AtomicConfigPublisher.Artifact.JOURNAL,
                    4,
                    AtomicConfigPublisher.Artifact.INITIAL,
                    1,
                    AtomicConfigPublisher.Artifact.TARGET,
                    1,
                    AtomicConfigPublisher.Artifact.MARKER,
                    1);
            case INITIAL -> Map.of(
                    AtomicConfigPublisher.Artifact.JOURNAL,
                    3,
                    AtomicConfigPublisher.Artifact.TARGET,
                    1,
                    AtomicConfigPublisher.Artifact.MARKER,
                    1);
            case TARGET -> Map.of(
                    AtomicConfigPublisher.Artifact.JOURNAL,
                    2,
                    AtomicConfigPublisher.Artifact.MARKER,
                    1);
            case MARKER -> Map.of();
        };
    }

    private static Path stage(
            MigrationFileSystem.ArtifactPaths artifacts,
            AtomicConfigPublisher.Artifact artifact) {
        return switch (artifact) {
            case JOURNAL -> artifacts.fixedStages().get(0);
            case BACKUP -> artifacts.fixedStages().get(1);
            case INITIAL -> artifacts.fixedStages().get(2);
            case TARGET -> artifacts.fixedStages().get(3);
            case MARKER -> artifacts.fixedStages().get(4);
        };
    }

    private static Path destination(
            MigrationFileSystem.ArtifactPaths artifacts,
            AtomicConfigPublisher.Artifact artifact) {
        return switch (artifact) {
            case JOURNAL -> artifacts.journal();
            case BACKUP -> artifacts.backup();
            case INITIAL -> artifacts.initial();
            case TARGET -> artifacts.target();
            case MARKER -> artifacts.marker();
        };
    }

    private static Map<String, FilePortrait> portrait(Path root)
            throws IOException {
        Map<String, FilePortrait> result = new TreeMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory, BasicFileAttributes attributes)
                    throws IOException {
                rejectReparse(directory, attributes);
                result.put(relative(root, directory),
                        FilePortrait.from(directory, attributes));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(
                    Path file, BasicFileAttributes attributes)
                    throws IOException {
                rejectReparse(file, attributes);
                result.put(relative(root, file),
                        FilePortrait.from(file, attributes));
                return FileVisitResult.CONTINUE;
            }
        });
        return Map.copyOf(result);
    }

    private static void rejectReparse(
            Path path, BasicFileAttributes attributes) throws IOException {
        if (attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("Unexpected reparse point in crash portrait: " + path);
        }
    }

    private static String relative(Path root, Path path) {
        String value = root.relativize(path).toString();
        return value.isEmpty() ? "." : value.replace('\\', '/');
    }

    private static BasicFileAttributes nofollow(Path path) throws IOException {
        return Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean isUnc(Path path) {
        Path root = path.getRoot();
        return root != null && root.toString().startsWith("\\\\");
    }

    private static boolean isEmptyDirectory(Path directory)
            throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
    }

    private static String sha256(Path path) throws IOException {
        return sha256(Files.readAllBytes(path));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 is required by Java", impossible);
        }
    }

    private static void deleteTreeNofollow(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory, BasicFileAttributes attributes)
                    throws IOException {
                rejectReparse(directory, attributes);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(
                    Path file, BasicFileAttributes attributes)
                    throws IOException {
                rejectReparse(file, attributes);
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(
                    Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private record CrashCase(
            AtomicConfigPublisher.Artifact artifact,
            MigrationFaultInjector.Timing timing) {
        private String id() {
            return artifact.name() + "_" + timing.name();
        }

        @Override
        public String toString() {
            return id();
        }
    }

    private record ChildResult(int exitCode, String output) {}

    private record FilePortrait(
            boolean regularFile,
            boolean directory,
            boolean symbolicLink,
            boolean other,
            long size,
            String creationTime,
            String lastModifiedTime,
            String sha256) {
        private static FilePortrait from(
                Path path, BasicFileAttributes attributes) throws IOException {
            return new FilePortrait(
                    attributes.isRegularFile(),
                    attributes.isDirectory(),
                    attributes.isSymbolicLink(),
                    attributes.isOther(),
                    attributes.size(),
                    attributes.creationTime().toString(),
                    attributes.lastModifiedTime().toString(),
                    attributes.isRegularFile()
                            ? WindowsNtfsMigrationProcessDeathTest.sha256(path)
                            : "");
        }
    }

    private static final class PublicationAudit
            implements MigrationFaultInjector {
        private final MigrationFileSystem.ArtifactPaths artifacts;
        private final EnumMap<AtomicConfigPublisher.Artifact, List<String>>
                stageHashes = new EnumMap<>(
                        AtomicConfigPublisher.Artifact.class);

        private PublicationAudit(
                MigrationFileSystem.ArtifactPaths artifacts) {
            this.artifacts = artifacts;
        }

        @Override
        public void checkpoint(Point point) {
            if (point.artifact() == null
                    || point.operation() != Operation.ATOMIC_MOVE
                    || point.timing() != Timing.BEFORE) {
                return;
            }
            Path fixedStage = stage(artifacts, point.artifact());
            try {
                BasicFileAttributes attributes = nofollow(fixedStage);
                if (!attributes.isRegularFile()
                        || attributes.isSymbolicLink()
                        || attributes.isOther()) {
                    throw new IOException(
                            "Publication stage is not a safe regular file: "
                                    + fixedStage);
                }
                stageHashes
                        .computeIfAbsent(point.artifact(), ignored -> new ArrayList<>())
                        .add(sha256(fixedStage));
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Could not audit restart publication " + point.artifact(),
                        failure);
            }
        }

        private int count(AtomicConfigPublisher.Artifact artifact) {
            return stageHashes.getOrDefault(artifact, List.of()).size();
        }

        private List<String> stageHashes(
                AtomicConfigPublisher.Artifact artifact) {
            return List.copyOf(
                    stageHashes.getOrDefault(artifact, List.of()));
        }

        private Map<AtomicConfigPublisher.Artifact, Integer> counts() {
            EnumMap<AtomicConfigPublisher.Artifact, Integer> counts =
                    new EnumMap<>(AtomicConfigPublisher.Artifact.class);
            stageHashes.forEach((artifact, hashes) -> {
                if (!hashes.isEmpty()) {
                    counts.put(artifact, hashes.size());
                }
            });
            return Map.copyOf(counts);
        }
    }
}
