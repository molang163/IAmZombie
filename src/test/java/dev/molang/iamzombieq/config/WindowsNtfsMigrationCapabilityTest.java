package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Destructive, Windows-only capability gate for the Windows BASIC identity policy.
 *
 * <p>This test deliberately fails, rather than being skipped, when it is run on
 * Windows without the exact runner-provided local NTFS environment. Non-Windows
 * test runs skip the whole class before any setup is attempted.
 */
@EnabledOnOs(OS.WINDOWS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WindowsNtfsMigrationCapabilityTest {
    private static final String GATE_ROOT_ENV =
            "IAMZOMBIEQ_WINDOWS_GATE_ROOT";
    private static final String DRIVE_TYPE_ENV =
            "IAMZOMBIEQ_WINDOWS_GATE_DRIVE_TYPE";
    private static final String DRIVE_FORMAT_ENV =
            "IAMZOMBIEQ_WINDOWS_GATE_DRIVE_FORMAT";
    private static final String WINDOWS_PROVIDER =
            "sun.nio.fs.WindowsFileSystemProvider";
    private static final String NTFS = "NTFS";
    private static final String FIXED = "Fixed";
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);
    private static final byte[] NEVER_PARSE_LEGACY =
            "C1_WINDOWS_JUNCTION_MUST_FAIL_BEFORE_LEGACY_CONTENT\n"
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] CORRUPT_LEGACY =
            "debugLogging = [\n"
                    .getBytes(StandardCharsets.UTF_8);
    private static final String APPEARANCE_BASENAME =
            "iamzombieq-client.toml";
    private static final byte[] EXISTING_APPEARANCE = ("# Existing 1.0.3 appearance preferences.\n"
                    + "playerSkinMode = \"PLAYER_SKIN\"\n"
                    + "firstPersonArmSkinMode = \"MONSTER_TEXTURE\"\n")
            .getBytes(StandardCharsets.UTF_8);

    private Path gateRoot;
    private Path suiteRoot;

    @BeforeAll
    void createIsolatedGateRoot() throws Exception {
        assertTrue(
                System.getProperty("os.name", "unknown").startsWith("Windows"),
                "the Windows capability gate must run only on Windows");
        int javaFeature = Runtime.version().feature();
        assertTrue(
                MigrationJavaRuntimeMatrix.supportsBasicProfile(javaFeature),
                "the Windows capability gate requires a BASIC runtime approved "
                        + "by this Stonecutter node; actual feature="
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
                "run scripts/run-windows-xver-winbasic-capability.ps1; "
                        + "an unarmed Windows run is a failed gate, not a pass");
        assertEquals(
                FIXED,
                System.getenv(DRIVE_TYPE_ENV),
                "the PowerShell runner must certify a local fixed drive");
        assertEquals(
                NTFS,
                System.getenv(DRIVE_FORMAT_ENV),
                "the PowerShell runner must certify an NTFS drive");

        String configured = System.getenv(GATE_ROOT_ENV);
        assertNotNull(
                configured,
                "the PowerShell runner must provide " + GATE_ROOT_ENV);
        assertFalse(configured.isBlank(), GATE_ROOT_ENV + " must not be blank");
        assertFalse(
                configured.matches(".*[&|<>^%!()].*"),
                "the disposable gate path must not contain cmd.exe metacharacters");

        gateRoot = Path.of(configured);
        assertTrue(gateRoot.isAbsolute(), "gate root must be absolute");
        assertEquals(
                gateRoot.toAbsolutePath().normalize(),
                gateRoot,
                "gate root must already be normalized");
        assertFalse(isUnc(gateRoot), "gate root must not be UNC");

        var provider = gateRoot.getFileSystem().provider();
        assertTrue(
                provider == FileSystems.getDefault().provider(),
                "gate root must use the exact default provider instance");
        assertEquals("file", provider.getScheme());
        assertEquals(WINDOWS_PROVIDER, provider.getClass().getName());

        BasicFileAttributes rootAttributes = nofollow(gateRoot);
        assertTrue(rootAttributes.isDirectory(), "gate root must be a directory");
        assertFalse(rootAttributes.isSymbolicLink(), "gate root must not be a symlink");
        assertFalse(rootAttributes.isOther(), "gate root must not be a reparse point");
        assertEquals(null, rootAttributes.fileKey(), "Windows fileKey must be null");

        FileStore store = Files.getFileStore(gateRoot);
        assertEquals(NTFS, store.type(), "gate root must be on exact NTFS");
        assertTrue(
                isEmptyDirectory(gateRoot),
                "runner-provided gate root must be disposable and empty");
        suiteRoot = Files.createDirectory(
                gateRoot.resolve("junit-" + UUID.randomUUID()));
    }

    @AfterAll
    void removeOnlyTheCreatedSuiteTree() throws IOException {
        if (suiteRoot != null
                && suiteRoot.getParent() != null
                && suiteRoot.getParent().equals(gateRoot)
                && suiteRoot.getFileName().toString().startsWith("junit-")
                && Files.exists(suiteRoot, LinkOption.NOFOLLOW_LINKS)) {
            deleteTreeNofollow(suiteRoot);
        }
    }

    @Test
    @Order(1)
    void nodeApprovedJavaNtfsTupleProducesRealTaggedFingerprints()
            throws Exception {
        Path directory = Files.createDirectory(suiteRoot.resolve("capability"));
        Path regular = Files.writeString(
                directory.resolve("ordinary-file.txt"),
                "ordinary Windows NTFS file\n",
                StandardCharsets.UTF_8);
        Path driveRoot = directory.getRoot();
        assertNotNull(driveRoot, "drive root must be available");
        assertFalse(isUnc(driveRoot), "drive root must not be UNC");

        BasicFileAttributes driveAttributes = nofollow(driveRoot);
        BasicFileAttributes directoryAttributes = nofollow(directory);
        BasicFileAttributes regularAttributes = nofollow(regular);

        assertSafeDirectory(driveRoot, driveAttributes);
        assertSafeDirectory(directory, directoryAttributes);
        assertTrue(regularAttributes.isRegularFile());
        assertFalse(regularAttributes.isSymbolicLink());
        assertFalse(regularAttributes.isOther());
        assertEquals(null, regularAttributes.fileKey());

        for (Path path : new Path[] {driveRoot, directory, regular}) {
            FileStore store = Files.getFileStore(path);
            assertEquals(NTFS, store.type(), "FileStore type for " + path);
        }

        Object firstVsn = Files.getFileStore(driveRoot)
                .getAttribute("volume:vsn");
        Object secondVsn = Files.getFileStore(driveRoot)
                .getAttribute("volume:vsn");
        assertNotNull(firstVsn, "volume:vsn must be available");
        assertEquals(
                Integer.class,
                firstVsn.getClass(),
                "OpenJDK Windows volume:vsn must be an Integer");
        assertEquals(
                Integer.class,
                secondVsn == null ? null : secondVsn.getClass(),
                "both volume:vsn reads must have the same exact type");
        assertEquals(firstVsn, secondVsn, "volume:vsn must be stable");
        assertEquals(
                firstVsn,
                Files.getFileStore(directory).getAttribute("volume:vsn"),
                "ordinary directory must remain on the drive-root volume");
        assertEquals(
                firstVsn,
                Files.getFileStore(regular).getAttribute("volume:vsn"),
                "ordinary file must remain on the drive-root volume");

        MigrationIdentityPolicy policy = new MigrationIdentityPolicy();
        String rootIdentity = policy.directoryIdentity(driveRoot);
        String directoryIdentity = policy.directoryIdentity(directory);
        MigrationPathState.Metadata fileMetadata =
                policy.regularFileMetadata(regular);

        assertTagged(rootIdentity);
        assertTagged(directoryIdentity);
        assertTagged(fileMetadata.identity());
        assertNotEquals(rootIdentity, directoryIdentity);
        assertTrue(fileMetadata.regularFile());
        assertFalse(fileMetadata.symbolicLink());
        assertEquals(Files.size(regular), fileMetadata.size());
    }

    @Test
    @Order(2)
    void directParentJunctionFailsBeforeLegacyContentOrPublication()
            throws Exception {
        Path scenario = Files.createDirectory(suiteRoot.resolve("direct-parent"));
        Path physicalConfig = Files.createDirectory(
                scenario.resolve("physical-config"));
        Path legacy = Files.write(
                physicalConfig.resolve(ActualTargetResolver.LEGACY_BASENAME),
                NEVER_PARSE_LEGACY);
        Path logicalConfig = scenario.resolve("config-junction");
        createJunction(logicalConfig, physicalConfig);
        try {
            assertJunction(logicalConfig);
            assertMigrationRejectedBeforePublication(
                    logicalConfig, physicalConfig, legacy);
        } finally {
            deleteCreatedJunction(logicalConfig);
        }
    }

    @Test
    @Order(3)
    void intermediateAncestorJunctionFailsBeforeLegacyContentOrPublication()
            throws Exception {
        Path scenario = Files.createDirectory(
                suiteRoot.resolve("intermediate-ancestor"));
        Path physicalBranch = Files.createDirectory(
                scenario.resolve("physical-branch"));
        Path physicalConfig = Files.createDirectory(
                physicalBranch.resolve("config"));
        Path legacy = Files.write(
                physicalConfig.resolve(ActualTargetResolver.LEGACY_BASENAME),
                NEVER_PARSE_LEGACY);
        Path junction = scenario.resolve("branch-junction");
        createJunction(junction, physicalBranch);
        Path logicalConfig = junction.resolve("config");
        try {
            assertJunction(junction);
            BasicFileAttributes logicalAttributes = nofollow(logicalConfig);
            assertTrue(logicalAttributes.isDirectory());
            assertFalse(logicalAttributes.isSymbolicLink());
            assertFalse(
                    logicalAttributes.isOther(),
                    "the direct parent is ordinary; the unsafe component is intermediate");
            assertMigrationRejectedBeforePublication(
                    logicalConfig, physicalConfig, legacy);
        } finally {
            deleteCreatedJunction(junction);
        }
    }

    @Test
    @Order(4)
    void realLegacyFixtureMigratesServerAndPreferencesWithoutTouchingAppearance()
            throws Exception {
        Path scenario = Files.createDirectory(
                suiteRoot.resolve("real-three-config-migration"));
        Path global = Files.createDirectory(scenario.resolve("config"));
        Path worldRoot = Files.createDirectory(scenario.resolve("world"));
        Path worldServerConfig = worldRoot.resolve("serverconfig");
        byte[] fixture = LegacyConfigParserTest.fixtureBytes();
        Path legacy = Files.write(
                global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                fixture);
        Path appearance = Files.write(
                global.resolve(APPEARANCE_BASENAME),
                EXISTING_APPEARANCE);
        FilePortrait appearanceBefore =
                FilePortrait.from(appearance, nofollow(appearance));
        Path preferencesTarget = target(
                global, MigrationTarget.PREFERENCES);
        Path serverTarget = target(global, MigrationTarget.SERVER);
        byte[] expectedPreferences = canonical(MigrationTarget.PREFERENCES);
        byte[] expectedServer = canonical(MigrationTarget.SERVER);

        MigrationTargetState preferences = ProductionConfigMigration
                .migratePreferences(global)
                .orElseThrow();
        MigrationTargetState server = ProductionConfigMigration
                .migrateServer(global, worldServerConfig)
                .orElseThrow();

        assertMigrated(
                preferences,
                MigrationTarget.PREFERENCES,
                preferencesTarget);
        assertMigrated(server, MigrationTarget.SERVER, serverTarget);
        assertArrayEquals(fixture, Files.readAllBytes(legacy));
        assertArrayEquals(
                EXISTING_APPEARANCE,
                Files.readAllBytes(appearance),
                "the existing appearance config must remain byte-for-byte unchanged");
        assertEquals(
                appearanceBefore,
                FilePortrait.from(appearance, nofollow(appearance)),
                "the existing appearance config must retain its type, size, "
                        + "timestamps, and SHA-256");
        assertPermanentEvidence(
                preferencesTarget,
                MigrationTarget.PREFERENCES,
                fixture,
                expectedPreferences,
                expectedPreferences);
        assertPermanentEvidence(
                serverTarget,
                MigrationTarget.SERVER,
                fixture,
                expectedServer,
                expectedServer);
        assertFalse(
                Files.exists(
                        worldServerConfig, LinkOption.NOFOLLOW_LINKS),
                "global legacy migration must not create serverconfig");
    }

    @Test
    @Order(6)
    void freshInstallCreatesNoTargetOrMigrationArtifact()
            throws Exception {
        Path preferencesScenario = Files.createDirectory(
                suiteRoot.resolve("fresh-preferences"));
        Path preferencesGlobal = Files.createDirectory(
                preferencesScenario.resolve("config"));
        Path preferencesTarget = target(
                preferencesGlobal, MigrationTarget.PREFERENCES);

        assertTrue(
                ProductionConfigMigration.migratePreferences(
                                preferencesGlobal)
                        .isEmpty());
        assertNoPublication(preferencesTarget);
        assertTrue(
                isEmptyDirectory(preferencesGlobal),
                "fresh preferences config must remain empty");

        Path serverScenario = Files.createDirectory(
                suiteRoot.resolve("fresh-server"));
        Path serverGlobal = Files.createDirectory(
                serverScenario.resolve("config"));
        Path worldRoot = Files.createDirectory(
                serverScenario.resolve("world"));
        Path worldServerConfig = worldRoot.resolve("serverconfig");
        Path globalServerTarget = target(
                serverGlobal, MigrationTarget.SERVER);
        Path worldServerTarget = worldServerConfig.resolve(
                ActualTargetResolver.SERVER_BASENAME);

        assertTrue(
                ProductionConfigMigration.migrateServer(
                                serverGlobal, worldServerConfig)
                        .isEmpty());
        assertNoPublication(globalServerTarget);
        assertNoPublication(worldServerTarget);
        assertFalse(
                Files.exists(
                        worldServerConfig, LinkOption.NOFOLLOW_LINKS),
                "fresh server migration must not create serverconfig");
        assertTrue(
                isEmptyDirectory(serverGlobal),
                "fresh global config must remain empty");
    }

    @Test
    @Order(7)
    void existingCanonicalTargetIsNeverOverwrittenAndCreatesNoArtifact()
            throws Exception {
        for (MigrationTarget kind : MigrationTarget.values()) {
            Path scenario = Files.createDirectory(suiteRoot.resolve(
                    "existing-" + kind.name().toLowerCase(java.util.Locale.ROOT)));
            Path global = Files.createDirectory(scenario.resolve("config"));
            Path worldRoot = Files.createDirectory(scenario.resolve("world"));
            Path worldServerConfig = worldRoot.resolve("serverconfig");
            Path actualTarget = target(global, kind);
            byte[] canonical = canonical(kind);
            Files.write(actualTarget, canonical);
            Files.write(
                    global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                    CORRUPT_LEGACY);
            Map<String, FilePortrait> before = portrait(global);

            MigrationTargetState state = migrate(
                    kind, global, worldServerConfig);

            assertEquals(
                    MigrationTargetState.Outcome.EXISTING_VALID,
                    state.outcome(),
                    kind.name());
            assertEquals(MigrationTargetState.Phase.NO_EVIDENCE, state.phase());
            assertEquals(actualTarget, state.actualTarget());
            assertArrayEquals(canonical, Files.readAllBytes(actualTarget));
            assertEquals(
                    before,
                    portrait(global),
                    "existing target and invalid legacy must remain byte-for-byte "
                            + "and metadata unchanged for "
                            + kind);
            assertNoArtifacts(actualTarget);
            assertFalse(
                    Files.exists(
                            worldServerConfig, LinkOption.NOFOLLOW_LINKS),
                    "existing global target must not create serverconfig");
        }
    }

    @Test
    @Order(8)
    void completeRestartAcceptsLegalPreferenceValueEditWithoutRepublishing()
            throws Exception {
        Path scenario = Files.createDirectory(
                suiteRoot.resolve("complete-value-edit"));
        Path global = Files.createDirectory(scenario.resolve("config"));
        Path legacy = Files.write(
                global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                LegacyConfigParserTest.fixtureBytes());
        Path actualTarget = target(global, MigrationTarget.PREFERENCES);
        byte[] canonical = canonical(MigrationTarget.PREFERENCES);

        MigrationTargetState migrated = ProductionConfigMigration
                .migratePreferences(global)
                .orElseThrow();
        assertEquals(MigrationTargetState.Outcome.MIGRATED, migrated.outcome());
        assertPermanentEvidence(
                actualTarget,
                MigrationTarget.PREFERENCES,
                Files.readAllBytes(legacy),
                canonical,
                canonical);

        String canonicalText = new String(canonical, StandardCharsets.UTF_8);
        String editedText = canonicalText.replace(
                "herobrineHeartbeatEnabled = false\n",
                "herobrineHeartbeatEnabled = true\n");
        assertNotEquals(
                canonicalText,
                editedText,
                "the fixture must expose the preference value selected for editing");
        byte[] edited = editedText.getBytes(StandardCharsets.UTF_8);
        assertTrue(
                new TargetConfigValidator(ConfigSchemaCatalog.load())
                        .validateEncoded(
                                MigrationTarget.PREFERENCES,
                                editedText)
                        .valid(),
                "the edited preference value must remain semantically valid");
        Files.write(actualTarget, edited);
        Map<String, FilePortrait> beforeRestart = portrait(global);

        MigrationTargetState restarted = ProductionConfigMigration
                .migratePreferences(global)
                .orElseThrow();

        assertEquals(MigrationTargetState.Outcome.COMPLETE, restarted.outcome());
        assertArrayEquals(edited, Files.readAllBytes(actualTarget));
        assertEquals(
                beforeRestart,
                portrait(global),
                "COMPLETE restart must not republish target or evidence");
        assertPermanentEvidence(
                actualTarget,
                MigrationTarget.PREFERENCES,
                Files.readAllBytes(legacy),
                canonical,
                edited);
    }

    @Test
    @Order(9)
    void corruptLegacyIsExplicitF1AndNeverPublishesTarget()
            throws Exception {
        Path scenario = Files.createDirectory(
                suiteRoot.resolve("corrupt-legacy"));
        Path global = Files.createDirectory(scenario.resolve("config"));
        Path legacy = Files.write(
                global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                CORRUPT_LEGACY);
        Path actualTarget = target(global, MigrationTarget.PREFERENCES);

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> ProductionConfigMigration.migratePreferences(global));

        assertEquals(MigrationTargetState.Phase.PREPARED, failure.phase());
        assertEquals("legacy", failure.artifact());
        assertEquals("legacy-parse-and-projection", failure.operation());
        assertFalse(failure.synthetic());
        assertTrue(failure.reason().contains("malformed or invalid"));
        assertTrue(failure.recovery().contains("C1-F1-STOP-PRESERVE-v1"));
        assertArrayEquals(CORRUPT_LEGACY, Files.readAllBytes(legacy));
        assertOnlyInitializedLock(actualTarget);
    }

    @Test
    @Order(10)
    void preexistingZeroLengthWindowsBasicLockIsManualF1AndUntouched()
            throws Exception {
        Path scenario = Files.createDirectory(
                suiteRoot.resolve("preexisting-zero-lock"));
        Path global = Files.createDirectory(scenario.resolve("config"));
        Path legacy = Files.write(
                global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                CORRUPT_LEGACY);
        Path actualTarget = target(global, MigrationTarget.PREFERENCES);
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(actualTarget);
        Path lock = Files.createFile(artifacts.lock());
        FilePortrait lockBefore = FilePortrait.from(lock, nofollow(lock));
        Map<String, FilePortrait> before = portrait(global);

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> ProductionConfigMigration.migratePreferences(global));

        assertEquals(MigrationTargetState.Phase.LOCKED, failure.phase());
        assertEquals("migration-core", failure.artifact());
        assertEquals("engine-execution", failure.operation());
        assertFalse(failure.synthetic());
        String reason = failure.reason().toLowerCase(java.util.Locale.ROOT);
        assertTrue(reason.contains("pre-existing zero-length permanent lock"));
        assertTrue(reason.contains("windows basic fingerprint"));
        assertTrue(reason.contains("c1-manual-recovery-v1"));
        assertTrue(failure.recovery().contains("C1-MANUAL-RECOVERY-v1"));
        assertFalse(
                reason.contains("legacy data is malformed"),
                "manual lock F1 must precede legacy parsing");

        FilePortrait lockAfter = FilePortrait.from(lock, nofollow(lock));
        assertEquals(
                lockBefore,
                lockAfter,
                "zero lock bytes, creation time, mtime, size, and type "
                        + "must remain unchanged");
        assertEquals(
                before,
                portrait(global),
                "manual F1 must leave the lock, invalid legacy, and parent "
                        + "directory unchanged");
        assertArrayEquals(CORRUPT_LEGACY, Files.readAllBytes(legacy));
        assertEquals(0, Files.size(lock));
        assertNoArtifactsExceptLock(actualTarget);
    }

    @Test
    @Order(11)
    void sameBytesReplacementCannotSeparateHeldLockFromCanonicalPathname()
            throws Exception {
        Path scenario = Files.createDirectory(
                suiteRoot.resolve("same-bytes-lock-replacement"));
        Path global = Files.createDirectory(scenario.resolve("config"));
        Path legacy = Files.write(
                global.resolve(ActualTargetResolver.LEGACY_BASENAME),
                LegacyConfigParserTest.fixtureBytes());
        Path actualTarget = target(global, MigrationTarget.PREFERENCES);
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(actualTarget);
        Path displacedLock = global.resolve("displaced-permanent-lock");
        JdkMigrationFileSystem fileSystem = new JdkMigrationFileSystem();
        MigrationBinding binding = MigrationBinding.capture(
                fileSystem.observeBinding(actualTarget));
        MigrationAccessProfile profile = MigrationAccessProfile.select(
                fileSystem.capabilities(binding), false);
        assertEquals(
                MigrationAccessProfile.BASIC,
                profile,
                "held-channel overlap oracle is a Windows BASIC gate");
        ConfigMigrationEngine.Request request =
                new ConfigMigrationEngine.Request(
                        MigrationTarget.PREFERENCES,
                        legacy,
                        actualTarget,
                        binding,
                        profile,
                        Optional.empty(),
                        false);
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        byte[] baseLockPayload = lockBasePayload(request, schema);
        MigrationIdentityPolicy identityPolicy = new MigrationIdentityPolicy();
        AtomicBoolean replacedBeforeIdentity = new AtomicBoolean();
        AtomicBoolean mirroredBeforeValidation = new AtomicBoolean();
        AtomicReference<String> replacementIdentity = new AtomicReference<>();
        AtomicReference<byte[]> replacementPayload = new AtomicReference<>();
        MigrationFaultInjector separatePathnameAndHeldChannel = point -> {
            try {
                if (point.operation()
                                == MigrationFaultInjector.Operation.LOCK_IDENTITY
                        && point.timing()
                                == MigrationFaultInjector.Timing.BEFORE
                        && replacedBeforeIdentity.compareAndSet(false, true)) {
                    Files.move(
                            artifacts.lock(),
                            displacedLock,
                            StandardCopyOption.ATOMIC_MOVE);
                    Files.createFile(artifacts.lock());
                    String identity = identityPolicy
                            .regularFileMetadata(artifacts.lock())
                            .identity();
                    replacementIdentity.set(identity);
                    replacementPayload.set(
                            PermanentMigrationLock.payloadWithIdentity(
                                    baseLockPayload, identity));
                }
                if (point.operation()
                                == MigrationFaultInjector.Operation.LOCK_VALIDATE
                        && point.timing()
                                == MigrationFaultInjector.Timing.BEFORE
                        && replacedBeforeIdentity.get()
                        && mirroredBeforeValidation.compareAndSet(false, true)) {
                    byte[] mirrored = replacementPayload.get();
                    if (mirrored == null) {
                        throw new IllegalStateException(
                                "replacement payload was not prepared");
                    }
                    Files.write(
                            artifacts.lock(),
                            mirrored,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE);
                }
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "could not simulate a same-bytes Windows BASIC lock "
                                + "replacement",
                        failure);
            }
        };

        MigrationFailure failure;
        try (JdkMigrationFileSystem.StoreSession store =
                fileSystem.openStore(profile, binding, legacy)) {
            failure = assertThrows(
                    MigrationFailure.class,
                    () -> new ConfigMigrationEngine(
                                    schema,
                                    separatePathnameAndHeldChannel)
                            .migrate(request, store));
        }

        assertEquals(MigrationTargetState.Phase.LOCKED, failure.phase());
        assertEquals("migration-core", failure.artifact());
        assertEquals("engine-execution", failure.operation());
        assertTrue(
                failure.reason().contains("held file"),
                "the BASIC overlap oracle must reject a canonical pathname "
                        + "that no longer names the held lock: "
                        + failure.reason());
        assertTrue(replacedBeforeIdentity.get());
        assertTrue(mirroredBeforeValidation.get());
        assertTagged(replacementIdentity.get());
        assertArrayEquals(
                replacementPayload.get(),
                Files.readAllBytes(artifacts.lock()));
        assertArrayEquals(
                Files.readAllBytes(displacedLock),
                Files.readAllBytes(artifacts.lock()));
        assertArrayEquals(
                LegacyConfigParserTest.fixtureBytes(),
                Files.readAllBytes(legacy));
        assertNoArtifactsExceptLock(actualTarget);
        assertFalse(Files.exists(artifacts.journal(), LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.exists(artifacts.marker(), LinkOption.NOFOLLOW_LINKS));
    }

    private static byte[] lockBasePayload(
            ConfigMigrationEngine.Request request,
            ConfigSchemaCatalog schema) {
        StringBuilder output = new StringBuilder();
        output.append("IAMZOMBIEQ-LOCK\n");
        output.append("version=1\n");
        output.append("target=")
                .append(request.actualTarget())
                .append('\n');
        output.append("logicalParent=")
                .append(request.binding().logicalParent())
                .append('\n');
        output.append("physicalParent=")
                .append(request.binding().physicalParent())
                .append('\n');
        output.append("ancestorCount=")
                .append(request.binding().ancestors().size())
                .append('\n');
        for (int index = 0;
                index < request.binding().ancestors().size();
                index++) {
            MigrationBinding.Ancestor ancestor =
                    request.binding().ancestors().get(index);
            output.append("ancestor.")
                    .append(index)
                    .append(".path=")
                    .append(ancestor.path())
                    .append('\n');
            output.append("ancestor.")
                    .append(index)
                    .append(".identity=")
                    .append(ancestor.identity())
                    .append('\n');
        }
        output.append("directoryIdentity=")
                .append(request.binding().directoryIdentity())
                .append('\n');
        output.append("providerIdentity=")
                .append(request.binding().providerIdentity())
                .append('\n');
        output.append("fileStoreIdentity=")
                .append(request.binding().fileStoreIdentity())
                .append('\n');
        output.append("profile=").append(request.profile()).append('\n');
        output.append("durabilityProfile=")
                .append(request.strongDurability()
                        ? MigrationEvidence.Durability.STRONG
                        : MigrationEvidence.Durability.BASIC)
                .append('\n');
        output.append("schemaVersion=")
                .append(schema.version())
                .append('\n');
        output.append("force=file-force+atomic-move\n");
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void assertMigrated(
            MigrationTargetState state,
            MigrationTarget kind,
            Path actualTarget) {
        assertEquals(MigrationTargetState.Outcome.MIGRATED, state.outcome());
        assertEquals(MigrationTargetState.Phase.COMPLETE, state.phase());
        assertEquals(kind, state.targetKind());
        assertEquals(actualTarget, state.actualTarget());
        assertEquals(MigrationEvidence.Durability.BASIC, state.commitProfile());
    }

    private static MigrationTargetState migrate(
            MigrationTarget kind, Path global, Path worldServerConfig) {
        return switch (kind) {
            case PREFERENCES -> ProductionConfigMigration
                    .migratePreferences(global)
                    .orElseThrow();
            case SERVER -> ProductionConfigMigration
                    .migrateServer(global, worldServerConfig)
                    .orElseThrow();
        };
    }

    private static Path target(Path global, MigrationTarget kind) {
        return global.resolve(switch (kind) {
            case PREFERENCES -> ActualTargetResolver.PREFERENCES_BASENAME;
            case SERVER -> ActualTargetResolver.SERVER_BASENAME;
        });
    }

    private static byte[] canonical(MigrationTarget kind) throws IOException {
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        Map<String, Object> projection = ConfigProjection.project(
                kind,
                LegacyConfigParser.parse(LegacyConfigParserTest.fixtureBytes()),
                schema);
        return ConfigProjectionCodec.encode(kind, projection, schema)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void assertPermanentEvidence(
            Path target,
            MigrationTarget kind,
            byte[] legacy,
            byte[] canonical,
            byte[] currentTarget) throws Exception {
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        for (Path permanent : new Path[] {
                target,
                artifacts.lock(),
                artifacts.journal(),
                artifacts.backup(),
                artifacts.initial(),
                artifacts.marker()
        }) {
            assertTaggedRegularFile(permanent);
        }
        assertTrue(
                Files.size(artifacts.lock()) > 0,
                "initialized permanent lock must be non-empty");
        assertArrayEquals(legacy, Files.readAllBytes(artifacts.backup()));
        assertArrayEquals(canonical, Files.readAllBytes(artifacts.initial()));
        assertArrayEquals(currentTarget, Files.readAllBytes(target));

        MigrationJournal journal = MigrationJournal.decode(
                Files.readAllBytes(artifacts.journal()));
        MigrationMarker marker = MigrationMarker.decode(
                Files.readAllBytes(artifacts.marker()));
        assertEquals(MigrationTargetState.Phase.COMPLETE, journal.phase());
        assertEquals(MigrationTargetState.Phase.COMPLETE, marker.phase());
        assertEquals(journal.evidence(), marker.evidence());
        MigrationEvidence evidence = journal.evidence();
        assertEquals(kind, evidence.targetKind());
        assertEquals(target, evidence.target());
        assertEquals(MigrationAccessProfile.BASIC, evidence.profile());
        assertEquals(
                MigrationEvidence.Durability.BASIC,
                evidence.commitProfile());
        assertTagged(evidence.lockIdentity());
        assertTagged(evidence.binding().directoryIdentity());
        for (MigrationBinding.Ancestor ancestor
                : evidence.binding().ancestors()) {
            assertTagged(ancestor.identity());
        }
        for (Path stage : artifacts.fixedStages()) {
            assertFalse(
                    Files.exists(stage, LinkOption.NOFOLLOW_LINKS),
                    "fixed publication stage must not remain: " + stage);
        }
    }

    private static void assertTaggedRegularFile(Path path) throws Exception {
        BasicFileAttributes attributes = nofollow(path);
        assertTrue(attributes.isRegularFile(), "expected regular file: " + path);
        assertFalse(attributes.isSymbolicLink(), "unexpected symlink: " + path);
        assertFalse(attributes.isOther(), "unexpected reparse point: " + path);
        assertEquals(null, attributes.fileKey(), "fileKey must be null: " + path);
        MigrationPathState.Metadata metadata =
                new MigrationIdentityPolicy().regularFileMetadata(path);
        assertTrue(metadata.regularFile());
        assertFalse(metadata.symbolicLink());
        assertTagged(metadata.identity());
        assertEquals(Files.size(path), metadata.size());
    }

    private static void assertOnlyInitializedLock(Path target)
            throws Exception {
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        assertTaggedRegularFile(artifacts.lock());
        assertTrue(Files.size(artifacts.lock()) > 0);
        assertNoArtifactsExceptLock(target);
    }

    private static void assertNoArtifactsExceptLock(Path target) {
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        assertFalse(Files.exists(target, LinkOption.NOFOLLOW_LINKS));
        for (Path artifact : new Path[] {
                artifacts.journal(),
                artifacts.backup(),
                artifacts.initial(),
                artifacts.marker()
        }) {
            assertFalse(
                    Files.exists(artifact, LinkOption.NOFOLLOW_LINKS),
                    "migration artifact must remain absent: " + artifact);
        }
        for (Path stage : artifacts.fixedStages()) {
            assertFalse(
                    Files.exists(stage, LinkOption.NOFOLLOW_LINKS),
                    "fixed stage must remain absent: " + stage);
        }
    }

    private static void assertMigrationRejectedBeforePublication(
            Path logicalConfig, Path physicalConfig, Path legacy)
            throws Exception {
        Path logicalTarget = logicalConfig.resolve(
                ActualTargetResolver.PREFERENCES_BASENAME);
        Path physicalTarget = physicalConfig.resolve(
                ActualTargetResolver.PREFERENCES_BASENAME);
        Map<String, FilePortrait> before = portrait(physicalConfig);
        byte[] legacyBefore = Files.readAllBytes(legacy);

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> ProductionConfigMigration.migratePreferences(
                        logicalConfig));

        assertEquals(MigrationTargetState.Phase.NO_EVIDENCE, failure.phase());
        assertEquals(
                "bootstrap-profile-selection",
                failure.operation(),
                "the junction must fail during namespace/profile binding, "
                        + "before a migration session can read legacy content");
        assertFalse(failure.synthetic());
        String description = MigrationFailure.describe(failure).toLowerCase();
        assertTrue(
                description.contains("junction")
                        || description.contains("reparse"),
                "failure must identify the rejected junction/reparse point: "
                        + description);

        assertArrayEquals(
                legacyBefore,
                Files.readAllBytes(legacy),
                "legacy bytes must remain unchanged");
        assertEquals(
                before,
                portrait(physicalConfig),
                "no target or migration artifact may be created or modified");
        assertNoPublication(logicalTarget);
        assertNoPublication(physicalTarget);
    }

    private static void assertNoPublication(Path target) {
        assertFalse(
                Files.exists(target, LinkOption.NOFOLLOW_LINKS),
                "target must remain absent: " + target);
        assertNoArtifacts(target);
    }

    private static void assertNoArtifacts(Path target) {
        MigrationFileSystem.ArtifactPaths artifacts =
                MigrationFileSystem.ArtifactPaths.forTarget(target);
        for (Path artifact : artifacts.fixedCandidates()) {
            assertFalse(
                    Files.exists(artifact, LinkOption.NOFOLLOW_LINKS),
                    "migration artifact must remain absent: " + artifact);
        }
    }

    private static void assertSafeDirectory(
            Path path, BasicFileAttributes attributes) {
        assertTrue(attributes.isDirectory(), "expected directory: " + path);
        assertFalse(attributes.isSymbolicLink(), "unexpected symlink: " + path);
        assertFalse(attributes.isOther(), "unexpected reparse point: " + path);
        assertEquals(null, attributes.fileKey(), "fileKey must be null: " + path);
    }

    private static void assertTagged(String identity) {
        assertNotNull(identity);
        assertTrue(
                identity.startsWith(
                        MigrationIdentityPolicy.WINDOWS_BASIC_FINGERPRINT_V1
                                + ":"),
                "expected a tagged Windows BASIC fingerprint: " + identity);
        assertTrue(
                identity.length()
                        > MigrationIdentityPolicy.WINDOWS_BASIC_FINGERPRINT_V1
                                .length()
                                + 1);
    }

    private static void createJunction(Path junction, Path target)
            throws Exception {
        assertFalse(Files.exists(junction, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS));
        String commandProcessor = System.getenv("ComSpec");
        if (commandProcessor == null || commandProcessor.isBlank()) {
            commandProcessor = "cmd.exe";
        }
        Process process = new ProcessBuilder(
                        commandProcessor,
                        "/D",
                        "/C",
                        "mklink",
                        "/J",
                        junction.toString(),
                        target.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(
                COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            fail("cmd.exe mklink /J timed out after " + COMMAND_TIMEOUT);
        }
        byte[] output = process.getInputStream().readAllBytes();
        assertEquals(
                0,
                process.exitValue(),
                "cmd.exe mklink /J failed; the Windows junction gate "
                        + "must abort rather than skip:\n"
                        + new String(output, Charset.defaultCharset()));
        assertTrue(
                Files.exists(junction, LinkOption.NOFOLLOW_LINKS),
                "mklink reported success without creating the junction");
    }

    private static void assertJunction(Path junction) throws IOException {
        BasicFileAttributes attributes = nofollow(junction);
        assertTrue(
                attributes.isDirectory(),
                "an NTFS directory junction should retain the directory bit");
        assertFalse(
                attributes.isSymbolicLink(),
                "an mklink /J junction must not be classified as a symlink");
        assertTrue(
                attributes.isOther(),
                "OpenJDK Windows must expose an mklink /J reparse point through isOther");
    }

    private static void deleteCreatedJunction(Path junction) throws IOException {
        if (!Files.exists(junction, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        BasicFileAttributes attributes = nofollow(junction);
        if (!attributes.isDirectory()
                || !attributes.isOther()
                || attributes.isSymbolicLink()) {
            throw new IOException(
                    "Refusing to delete a path that is no longer the created "
                            + "directory junction: "
                            + junction);
        }
        Files.delete(junction);
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

    private static boolean isEmptyDirectory(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
    }

    private static Map<String, FilePortrait> portrait(Path root)
            throws IOException {
        Map<String, FilePortrait> result = new TreeMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory, BasicFileAttributes attributes)
                    throws IOException {
                result.put(
                        relative(root, directory),
                        FilePortrait.from(directory, attributes));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(
                    Path file, BasicFileAttributes attributes)
                    throws IOException {
                result.put(
                        relative(root, file),
                        FilePortrait.from(file, attributes));
                return FileVisitResult.CONTINUE;
            }
        });
        return Map.copyOf(result);
    }

    private static String relative(Path root, Path path) {
        String value = root.relativize(path).toString();
        return value.isEmpty() ? "." : value.replace('\\', '/');
    }

    private static void deleteTreeNofollow(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (directory.equals(root)) {
                    if (attributes.isSymbolicLink() || attributes.isOther()) {
                        throw new IOException(
                                "Refusing to traverse a replaced suite root: "
                                        + directory);
                    }
                    return FileVisitResult.CONTINUE;
                }
                if (attributes.isSymbolicLink() || attributes.isOther()) {
                    Files.delete(directory);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(
                    Path file, BasicFileAttributes attributes)
                    throws IOException {
                if (file.equals(root)) {
                    throw new IOException(
                            "Refusing to delete a replaced suite root: " + file);
                }
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
            String hash = attributes.isRegularFile() ? sha256(path) : "";
            return new FilePortrait(
                    attributes.isRegularFile(),
                    attributes.isDirectory(),
                    attributes.isSymbolicLink(),
                    attributes.isOther(),
                    attributes.size(),
                    attributes.creationTime().toString(),
                    attributes.lastModifiedTime().toString(),
                    hash);
        }

        private static String sha256(Path path) throws IOException {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(
                        digest.digest(Files.readAllBytes(path)));
            } catch (NoSuchAlgorithmException impossible) {
                throw new AssertionError("SHA-256 is required by Java", impossible);
            }
        }
    }
}
