package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MigrationIdentityPolicyTest {
    private static final String WINDOWS_TAG =
            "WINDOWS_BASIC_FINGERPRINT_V1";
    private static final String WINDOWS_PROVIDER =
            "sun.nio.fs.WindowsFileSystemProvider";
    private static final String LINUX_PROVIDER =
            "sun.nio.fs.LinuxFileSystemProvider";
    private static final Path DRIVE_ROOT =
            Path.of("").toAbsolutePath().normalize().getRoot();
    private static final Path DIRECTORY = DRIVE_ROOT.resolve("game/config");
    private static final Path REGULAR = DIRECTORY.resolve("iamzombieq-common.toml");
    private static final FileTime CREATED = FileTime.fromMillis(1_700_000_000_123L);

    @Test
    void exactNodeWindowsRuntimeMatrixTagsRootDirectoryAndRegularNullFileKeys()
            throws IOException {
        for (int javaFeature : MigrationJavaRuntimeMatrix.runtimeFeatures()) {
            FakeProbe probe = windowsProbe(javaFeature)
                    .add(DRIVE_ROOT, directory(null), windowsContext(DRIVE_ROOT))
                    .add(DIRECTORY, directory(null), windowsContext(DIRECTORY))
                    .add(REGULAR, regular(null), windowsContext(REGULAR));
            MigrationIdentityPolicy policy = new MigrationIdentityPolicy(probe);

            String rootIdentity = policy.directoryIdentity(DRIVE_ROOT);
            String directoryIdentity = policy.directoryIdentity(DIRECTORY);
            MigrationPathState.Metadata regularMetadata =
                    policy.regularFileMetadata(REGULAR);

            assertTagged(rootIdentity);
            assertTagged(directoryIdentity);
            assertTagged(regularMetadata.identity());
            assertNotEquals(rootIdentity, directoryIdentity);
            assertTrue(regularMetadata.regularFile());
            assertFalse(regularMetadata.symbolicLink());
            assertEquals(37, regularMetadata.size());
            assertEquals(
                    6,
                    probe.volumeSerialReads,
                    "each Windows fingerprint must prove a stable VSN on Java "
                            + javaFeature);
        }
    }

    @Test
    void windowsAlwaysUsesTheTaggedFingerprintEvenIfAFileKeyAppears()
            throws IOException {
        FakeProbe probe = windowsProbe().add(
                DIRECTORY,
                directory("provider-key-must-not-be-used"),
                windowsContext(DIRECTORY));

        String identity =
                new MigrationIdentityPolicy(probe).directoryIdentity(DIRECTORY);

        assertTagged(identity);
        assertNotEquals("provider-key-must-not-be-used", identity);
    }

    @Test
    void linuxPreservesRawFileKeyWithoutWindowsContextOrVolumeIo()
            throws IOException {
        FakeProbe probe = new FakeProbe(linuxPlatform())
                .add(DIRECTORY, directory("linux-dir-key"), null)
                .add(REGULAR, regular("linux-file-key"), null);
        MigrationIdentityPolicy policy = new MigrationIdentityPolicy(probe);

        assertEquals("linux-dir-key", policy.directoryIdentity(DIRECTORY));
        assertEquals(
                "linux-file-key",
                policy.regularFileMetadata(REGULAR).identity());
        assertEquals(0, probe.windowsContextReads);
        assertEquals(0, probe.volumeSerialReads);
    }

    @Test
    void linuxAndUnknownProvidersStillRejectNullFileKeys() {
        FakeProbe linux = new FakeProbe(linuxPlatform())
                .add(DIRECTORY, directory(null), null)
                .add(REGULAR, regular(null), null);
        MigrationIdentityPolicy linuxPolicy = new MigrationIdentityPolicy(linux);

        assertThrows(
                IOException.class,
                () -> linuxPolicy.directoryIdentity(DIRECTORY));
        MigrationPathState.Metadata linuxRegular =
                assertDoesNotThrowMetadata(linuxPolicy, REGULAR);
        assertEquals("", linuxRegular.identity());
        assertEquals(
                MigrationPathState.UNKNOWN,
                MigrationPathState.classify(() -> linuxRegular));
        assertEquals(0, linux.windowsContextReads);
        assertEquals(0, linux.volumeSerialReads);

        MigrationIdentityPolicy.Platform unknownPlatform =
                new MigrationIdentityPolicy.Platform(
                        "Plan9", 25, true, "file", "third.party.Provider");
        FakeProbe unknown = new FakeProbe(unknownPlatform)
                .add(DIRECTORY, directory(null), null)
                .add(REGULAR, regular(null), null);

        assertThrows(
                IOException.class,
                () -> new MigrationIdentityPolicy(unknown)
                        .directoryIdentity(DIRECTORY));
        MigrationPathState.Metadata unknownRegular = assertDoesNotThrowMetadata(
                new MigrationIdentityPolicy(unknown), REGULAR);
        assertEquals("", unknownRegular.identity());
        assertEquals(
                MigrationPathState.UNKNOWN,
                MigrationPathState.classify(() -> unknownRegular));
        assertEquals(0, unknown.windowsContextReads);
        assertEquals(0, unknown.volumeSerialReads);
    }

    @Test
    void windowsBasicAdmissionFollowsTheGeneratedExactNodeMatrix() {
        for (int javaFeature = 21; javaFeature <= 26; javaFeature++) {
            FakeProbe probe = windowsProbe(javaFeature)
                    .add(DIRECTORY, directory(null), windowsContext(DIRECTORY));
            MigrationIdentityPolicy policy = new MigrationIdentityPolicy(probe);

            if (MigrationJavaRuntimeMatrix.runtimeFeatures()
                    .contains(javaFeature)) {
                try {
                    assertTagged(policy.directoryIdentity(DIRECTORY));
                } catch (IOException failure) {
                    throw new AssertionError(
                            "approved Windows BASIC Java " + javaFeature
                                    + " was rejected",
                            failure);
                }
                assertEquals(2, probe.windowsContextReads);
                assertEquals(2, probe.volumeSerialReads);
            } else {
                assertThrows(
                        IOException.class,
                        () -> policy.directoryIdentity(DIRECTORY),
                        "unapproved Windows BASIC Java " + javaFeature);
                assertEquals(0, probe.windowsContextReads);
                assertEquals(0, probe.volumeSerialReads);
            }
        }
    }

    @Test
    void windowsEligibilityRequiresDefaultFileProviderAndExactClass() {
        for (MigrationIdentityPolicy.Platform platform : List.of(
                new MigrationIdentityPolicy.Platform(
                        "Windows 11", 25, false, "file", WINDOWS_PROVIDER),
                new MigrationIdentityPolicy.Platform(
                        "Windows 11", 25, true, "jar", WINDOWS_PROVIDER),
                new MigrationIdentityPolicy.Platform(
                        "Windows 11",
                        25,
                        true,
                        "file",
                        "third.party.WindowsFileSystemProvider"))) {
            FakeProbe probe = new FakeProbe(platform)
                    .add(DIRECTORY, directory(null), windowsContext(DIRECTORY));

            assertThrows(
                    IOException.class,
                    () -> new MigrationIdentityPolicy(probe)
                            .directoryIdentity(DIRECTORY),
                    platform.toString());
            assertEquals(0, probe.windowsContextReads, platform.toString());
            assertEquals(0, probe.volumeSerialReads, platform.toString());
        }

        FakeProbe nonWindows = new FakeProbe(
                        new MigrationIdentityPolicy.Platform(
                                "Linux", 25, true, "file", WINDOWS_PROVIDER))
                .add(DIRECTORY, directory(null), windowsContext(DIRECTORY));
        assertThrows(
                IOException.class,
                () -> new MigrationIdentityPolicy(nonWindows)
                        .directoryIdentity(DIRECTORY));
        assertEquals(0, nonWindows.windowsContextReads);
        assertEquals(0, nonWindows.volumeSerialReads);
    }

    @Test
    void directorySymlinkJunctionAndNondirectoryAreRejectedBeforeVolumeProbe() {
        List<FakeAttributes> unsafe = List.of(
                new FakeAttributes(
                        false, true, true, false, 0, CREATED, null),
                // A junction/reparse fake deliberately also reports directory=true.
                new FakeAttributes(
                        false, true, false, true, 0, CREATED, null),
                new FakeAttributes(
                        true, false, false, false, 37, CREATED, null));

        for (FakeAttributes attributes : unsafe) {
            FakeProbe probe = windowsProbe().add(
                    DIRECTORY, attributes, windowsContext(DIRECTORY));

            assertThrows(
                    IOException.class,
                    () -> new MigrationIdentityPolicy(probe)
                            .directoryIdentity(DIRECTORY),
                    attributes.toString());
            assertEquals(
                    0,
                    probe.volumeSerialReads,
                    "unsafe directory type reached the VSN probe: " + attributes);
        }
    }

    @Test
    void regularSymlinkIsOtherAndNonregularAreUnsafeBeforeVolumeProbe()
            throws IOException {
        List<FakeAttributes> unsafe = List.of(
                new FakeAttributes(
                        true, false, true, false, 37, CREATED, null),
                new FakeAttributes(
                        true, false, false, true, 37, CREATED, null),
                new FakeAttributes(
                        false, true, false, false, 0, CREATED, null));

        for (FakeAttributes attributes : unsafe) {
            FakeProbe probe = windowsProbe().add(
                    REGULAR, attributes, windowsContext(REGULAR));

            MigrationPathState.Metadata metadata =
                    new MigrationIdentityPolicy(probe)
                            .regularFileMetadata(REGULAR);
            assertEquals(
                    MigrationPathState.UNSAFE,
                    MigrationPathState.classify(() -> metadata),
                    attributes.toString());
            if (attributes.isSymbolicLink()) {
                assertTrue(metadata.symbolicLink(), attributes.toString());
            }
            if (attributes.isOther()) {
                assertFalse(metadata.regularFile(), attributes.toString());
            }
            assertEquals(
                    0,
                    probe.volumeSerialReads,
                    "unsafe regular-file type reached the VSN probe: "
                            + attributes);
        }
    }

    @Test
    void uncNonNtfsMissingThrowingAndUnstableVolumesAreRejected() {
        MigrationIdentityPolicy.WindowsContext unc = new MigrationIdentityPolicy.WindowsContext(
                true,
                DIRECTORY,
                DIRECTORY,
                DRIVE_ROOT,
                "Windows volume E",
                "NTFS",
                "sun.nio.fs.WindowsFileStore");
        FakeProbe uncProbe = windowsProbe().add(DIRECTORY, directory(null), unc);
        assertThrows(
                IOException.class,
                () -> new MigrationIdentityPolicy(uncProbe)
                        .directoryIdentity(DIRECTORY));
        assertEquals(0, uncProbe.volumeSerialReads);

        MigrationIdentityPolicy.WindowsContext fat = new MigrationIdentityPolicy.WindowsContext(
                false,
                DIRECTORY,
                DIRECTORY,
                DRIVE_ROOT,
                "Windows volume E",
                "FAT32",
                "sun.nio.fs.WindowsFileStore");
        FakeProbe fatProbe = windowsProbe().add(DIRECTORY, directory(null), fat);
        assertThrows(
                IOException.class,
                () -> new MigrationIdentityPolicy(fatProbe)
                        .directoryIdentity(DIRECTORY));
        assertEquals(0, fatProbe.volumeSerialReads);

        FakeProbe missing = windowsProbe()
                .add(DIRECTORY, directory(null), windowsContext(DIRECTORY))
                .withVolumeSerials((Object) null);
        assertThrows(
                IOException.class,
                () -> new MigrationIdentityPolicy(missing)
                        .directoryIdentity(DIRECTORY));

        FakeProbe throwing = windowsProbe()
                .add(DIRECTORY, directory(null), windowsContext(DIRECTORY))
                .withVolumeFailure(new IOException("volume:vsn unavailable"));
        assertThrows(
                IOException.class,
                () -> new MigrationIdentityPolicy(throwing)
                        .directoryIdentity(DIRECTORY));

        FakeProbe unstable = windowsProbe()
                .add(DIRECTORY, directory(null), windowsContext(DIRECTORY))
                .withVolumeSerials(41L, 42L);
        assertThrows(
                IOException.class,
                () -> new MigrationIdentityPolicy(unstable)
                        .directoryIdentity(DIRECTORY));
        assertEquals(2, unstable.volumeSerialReads);
    }

    @Test
    void everyFingerprintFieldDriftChangesIdentityOrFailsClosed()
            throws IOException {
        FakeAttributes attributes = directory(null);
        MigrationIdentityPolicy.Platform platform = windowsPlatform();
        MigrationIdentityPolicy.WindowsContext context = windowsContext(DIRECTORY);
        String baseline = fingerprint(
                DIRECTORY, attributes, platform, context, 41L);

        Path otherPath = DRIVE_ROOT.resolve("game/other-config");
        assertDrift(baseline, () -> fingerprint(
                otherPath,
                attributes,
                platform,
                windowsContext(otherPath),
                41L));
        assertDrift(baseline, () -> fingerprint(
                DIRECTORY,
                attributes,
                platform,
                contextWith(
                        DIRECTORY,
                        DRIVE_ROOT.resolve("physical/config"),
                        DRIVE_ROOT,
                        "Windows volume E",
                        "NTFS",
                        "sun.nio.fs.WindowsFileStore"),
                41L));
        assertDrift(baseline, () -> fingerprint(
                DIRECTORY,
                attributes,
                platform,
                contextWith(
                        DIRECTORY,
                        DIRECTORY,
                        DRIVE_ROOT.resolve("alternate-root"),
                        "Windows volume E",
                        "NTFS",
                        "sun.nio.fs.WindowsFileStore"),
                41L));
        assertDrift(baseline, () -> fingerprint(
                DIRECTORY,
                attributes,
                new MigrationIdentityPolicy.Platform(
                        "Windows 11", 25, true, "jar", WINDOWS_PROVIDER),
                context,
                41L));
        assertDrift(baseline, () -> fingerprint(
                DIRECTORY,
                attributes,
                new MigrationIdentityPolicy.Platform(
                        "Windows 11", 25, true, "file", "other.Provider"),
                context,
                41L));
        assertDrift(baseline, () -> fingerprint(
                DIRECTORY,
                attributes,
                platform,
                contextWith(
                        DIRECTORY,
                        DIRECTORY,
                        DRIVE_ROOT,
                        "renamed volume",
                        "NTFS",
                        "sun.nio.fs.WindowsFileStore"),
                41L));
        assertDrift(baseline, () -> fingerprint(
                DIRECTORY,
                attributes,
                platform,
                contextWith(
                        DIRECTORY,
                        DIRECTORY,
                        DRIVE_ROOT,
                        "Windows volume E",
                        "ReFS",
                        "sun.nio.fs.WindowsFileStore"),
                41L));
        assertDrift(baseline, () -> fingerprint(
                DIRECTORY,
                attributes,
                platform,
                contextWith(
                        DIRECTORY,
                        DIRECTORY,
                        DRIVE_ROOT,
                        "Windows volume E",
                        "NTFS",
                        "different.WindowsFileStore"),
                41L));
        assertDrift(baseline, () -> fingerprint(
                DIRECTORY, attributes, platform, context, 42L));
        assertDrift(baseline, () -> fingerprint(
                DIRECTORY,
                new FakeAttributes(
                        false,
                        true,
                        false,
                        false,
                        0,
                        FileTime.fromMillis(CREATED.toMillis() + 1),
                        null),
                platform,
                context,
                41L));
        assertDrift(baseline, () -> fingerprint(
                DIRECTORY,
                attributes,
                new MigrationIdentityPolicy.Platform(
                        "Windows 10", 25, true, "file", WINDOWS_PROVIDER),
                context,
                41L));
        assertDrift(baseline, () -> fingerprint(
                DIRECTORY,
                attributes,
                new MigrationIdentityPolicy.Platform(
                        "Windows 11", 22, true, "file", WINDOWS_PROVIDER),
                context,
                41L));
    }

    @Test
    void identicalRecaptureProducesTheSameTaggedFingerprint()
            throws IOException {
        FakeProbe probe = windowsProbe().add(
                DIRECTORY, directory(null), windowsContext(DIRECTORY));
        MigrationIdentityPolicy policy = new MigrationIdentityPolicy(probe);

        String first = policy.directoryIdentity(DIRECTORY);
        String second = policy.directoryIdentity(DIRECTORY);

        assertEquals(first, second);
        assertTagged(first);
        assertEquals(4, probe.volumeSerialReads);
    }

    @Test
    void integerVolumeSerialAndUnlabelledNtfsVolumeAreAccepted()
            throws IOException {
        MigrationIdentityPolicy.WindowsContext context = contextWith(
                DIRECTORY,
                DIRECTORY,
                DRIVE_ROOT,
                "",
                "NTFS",
                "sun.nio.fs.WindowsFileStore");
        FakeProbe probe = new FakeProbe(windowsPlatform())
                .add(DIRECTORY, directory(null), context)
                .withVolumeSerials(Integer.valueOf(41));

        assertTagged(new MigrationIdentityPolicy(probe)
                .directoryIdentity(DIRECTORY));
    }

    @Test
    void mixedAttributeOrContextSnapshotsFailClosed() {
        FakeAttributes changedCreation = new FakeAttributes(
                false,
                true,
                false,
                false,
                0,
                FileTime.fromMillis(CREATED.toMillis() + 1),
                null);
        FakeProbe attributeDrift = windowsProbe()
                .add(DIRECTORY, directory(null), windowsContext(DIRECTORY))
                .withAttributeSequence(directory(null), changedCreation);
        assertThrows(
                IOException.class,
                () -> new MigrationIdentityPolicy(attributeDrift)
                        .directoryIdentity(DIRECTORY));

        MigrationIdentityPolicy.WindowsContext renamedStore = contextWith(
                DIRECTORY,
                DIRECTORY,
                DRIVE_ROOT,
                "renamed volume",
                "NTFS",
                "sun.nio.fs.WindowsFileStore");
        FakeProbe contextDrift = windowsProbe()
                .add(DIRECTORY, directory(null), windowsContext(DIRECTORY))
                .withContextSequence(
                        windowsContext(DIRECTORY), renamedStore);
        assertThrows(
                IOException.class,
                () -> new MigrationIdentityPolicy(contextDrift)
                        .directoryIdentity(DIRECTORY));
    }

    @Test
    void missingCreationTimeFailsClosed() {
        FakeAttributes missingCreationTime = new FakeAttributes(
                false, true, false, false, 0, null, null);
        FakeProbe probe = windowsProbe().add(
                DIRECTORY,
                missingCreationTime,
                windowsContext(DIRECTORY));

        IOException failure = assertThrows(
                IOException.class,
                () -> new MigrationIdentityPolicy(probe)
                        .directoryIdentity(DIRECTORY));

        assertTrue(failure.getMessage().contains("creationTime"));
    }

    @Test
    void secondAttributeSampleBecomingSymlinkFailsClosed() {
        FakeAttributes symlink = new FakeAttributes(
                false, true, true, false, 0, CREATED, null);
        FakeProbe probe = windowsProbe()
                .add(DIRECTORY, directory(null), windowsContext(DIRECTORY))
                .withAttributeSequence(directory(null), symlink);

        IOException failure = assertThrows(
                IOException.class,
                () -> new MigrationIdentityPolicy(probe)
                        .directoryIdentity(DIRECTORY));

        assertTrue(failure.getMessage().contains("symbolic link"));
    }

    @Test
    void secondAttributeSampleBecomingIsOtherFailsClosed() {
        FakeAttributes isOther = new FakeAttributes(
                false, true, false, true, 0, CREATED, null);
        FakeProbe probe = windowsProbe()
                .add(DIRECTORY, directory(null), windowsContext(DIRECTORY))
                .withAttributeSequence(directory(null), isOther);

        IOException failure = assertThrows(
                IOException.class,
                () -> new MigrationIdentityPolicy(probe)
                        .directoryIdentity(DIRECTORY));

        assertTrue(failure.getMessage().contains("junction"));
    }

    @Test
    void taggedFingerprintSerializesEverySignedTupleField()
            throws IOException {
        MigrationIdentityPolicy.WindowsContext context =
                windowsContext(DIRECTORY);
        String identity = fingerprint(
                DIRECTORY,
                directory(null),
                windowsPlatform(),
                context,
                Integer.valueOf(41));

        String[] encoded = identity.split(":", -1);
        assertEquals(16, encoded.length);
        List<String> fields = new ArrayList<>();
        Base64.Decoder decoder = Base64.getUrlDecoder();
        for (int index = 1; index < encoded.length; index++) {
            fields.add(new String(
                    decoder.decode(encoded[index]), StandardCharsets.UTF_8));
        }
        assertEquals(
                List.of(
                        "DIRECTORY",
                        "Windows 11",
                        "25",
                        "true",
                        "file",
                        WINDOWS_PROVIDER,
                        DIRECTORY.toString(),
                        DIRECTORY.toString(),
                        DRIVE_ROOT.toString(),
                        "Windows volume E",
                        "NTFS",
                        "sun.nio.fs.WindowsFileStore",
                        Integer.class.getName(),
                        "41",
                        CREATED.toString()),
                fields);
    }

    @Test
    void canonicalRealPathKeepsLeafIdentityStableAcrossAccessPathSpelling()
            throws IOException {
        Path logical = DRIVE_ROOT.resolve("GAME/config/iamzombieq-common.toml");
        Path bound = DRIVE_ROOT.resolve("Game/config/iamzombieq-common.toml");
        Path canonical = DRIVE_ROOT.resolve("Game/config/iamzombieq-common.toml");
        FakeProbe probe = windowsProbe()
                .add(
                        logical,
                        regular(null),
                        contextWith(
                                logical,
                                canonical,
                                DRIVE_ROOT,
                                "Windows volume E",
                                "NTFS",
                                "sun.nio.fs.WindowsFileStore"))
                .add(
                        bound,
                        regular(null),
                        contextWith(
                                bound,
                                canonical,
                                DRIVE_ROOT,
                                "Windows volume E",
                                "NTFS",
                                "sun.nio.fs.WindowsFileStore"));
        MigrationIdentityPolicy policy = new MigrationIdentityPolicy(probe);

        assertEquals(
                policy.regularFileMetadata(logical).identity(),
                policy.regularFileMetadata(bound).identity());
    }

    @Test
    void bindingStoreUsesCanonicalWindowsRootSnapshotButPreservesLinuxStore()
            throws IOException {
        Path logical = DRIVE_ROOT.resolve("GAME/config");
        Path physical = DRIVE_ROOT.resolve("Game/config");
        MigrationIdentityPolicy.WindowsContext logicalContext = contextWith(
                logical,
                physical,
                DRIVE_ROOT,
                "Windows volume E",
                "NTFS",
                "sun.nio.fs.WindowsFileStore");
        MigrationIdentityPolicy.WindowsContext physicalContext = contextWith(
                physical,
                physical,
                DRIVE_ROOT,
                "Windows volume E",
                "NTFS",
                "sun.nio.fs.WindowsFileStore");
        FakeProbe windows = windowsProbe()
                .add(logical, directory(null), logicalContext)
                .add(physical, directory(null), physicalContext);
        MigrationIdentityPolicy windowsPolicy =
                new MigrationIdentityPolicy(windows);
        String parentIdentity = windowsPolicy.directoryIdentity(logical);

        assertEquals(
                "Windows volume E|NTFS|sun.nio.fs.WindowsFileStore",
                windowsPolicy.bindingFileStoreIdentity(
                        logical, physical, parentIdentity));
        assertEquals(0, windows.fileStoreIdentityReads);

        FakeProbe unstable = windowsProbe()
                .add(logical, directory(null), logicalContext)
                .add(physical, directory(null), physicalContext)
                .withVolumeSerials(41L, 41L, 41L, 42L);
        MigrationIdentityPolicy unstablePolicy =
                new MigrationIdentityPolicy(unstable);
        String unstableParent = unstablePolicy.directoryIdentity(logical);
        assertThrows(
                IOException.class,
                () -> unstablePolicy.bindingFileStoreIdentity(
                        logical, physical, unstableParent));

        FakeProbe linux = new FakeProbe(linuxPlatform())
                .add(logical, directory("linux-directory-key"), null);
        assertEquals(
                "test-store|test-type|test.FileStore",
                new MigrationIdentityPolicy(linux)
                        .bindingFileStoreIdentity(
                                logical, physical, "linux-directory-key"));
        assertEquals(1, linux.fileStoreIdentityReads);
        assertEquals(physical, linux.fileStoreIdentityPath);
    }

    @Test
    void onlySecureRawFileKeyBindingsPermitPreExistingEmptyLockRecovery()
            throws IOException {
        FakeProbe captureProbe = windowsProbe().add(
                DIRECTORY, directory(null), windowsContext(DIRECTORY));
        MigrationIdentityPolicy capturePolicy =
                new MigrationIdentityPolicy(captureProbe);
        Path target = DIRECTORY.resolve("iamzombieq-server.toml");
        MigrationBinding raw = new MigrationBinding(
                target,
                DIRECTORY,
                DIRECTORY,
                List.of(
                        new MigrationBinding.Ancestor(DRIVE_ROOT, "root-file-key"),
                        new MigrationBinding.Ancestor(DIRECTORY, "directory-file-key")),
                "directory-file-key",
                "file:" + LINUX_PROVIDER,
                "overlay|overlay|sun.nio.fs.LinuxFileStore",
                25,
                "Linux");
        String fingerprint = capturePolicy.directoryIdentity(DIRECTORY);
        MigrationBinding windowsFingerprint = new MigrationBinding(
                target,
                DIRECTORY,
                DIRECTORY,
                List.of(
                        new MigrationBinding.Ancestor(DRIVE_ROOT, fingerprint),
                        new MigrationBinding.Ancestor(DIRECTORY, fingerprint)),
                fingerprint,
                "file:" + WINDOWS_PROVIDER,
                "Windows volume E|NTFS|sun.nio.fs.WindowsFileStore",
                25,
                "Windows 11");
        MigrationBinding mixed = new MigrationBinding(
                target,
                DIRECTORY,
                DIRECTORY,
                List.of(
                        new MigrationBinding.Ancestor(DRIVE_ROOT, "root-file-key"),
                        new MigrationBinding.Ancestor(DIRECTORY, fingerprint)),
                "directory-file-key",
                "file:" + LINUX_PROVIDER,
                "overlay|overlay|sun.nio.fs.LinuxFileStore",
                25,
                "Linux");

        assertEquals(
                MigrationIdentityPolicy.EmptyLockRecoveryPolicy.EXACT_FILE_KEY,
                capturePolicy.emptyLockRecoveryPolicy(
                        MigrationAccessProfile.SECURE, raw));
        assertEquals(
                MigrationIdentityPolicy.EmptyLockRecoveryPolicy.MANUAL_ONLY,
                capturePolicy.emptyLockRecoveryPolicy(
                        MigrationAccessProfile.BASIC, raw));
        assertEquals(
                MigrationIdentityPolicy.EmptyLockRecoveryPolicy.MANUAL_ONLY,
                capturePolicy.emptyLockRecoveryPolicy(
                        MigrationAccessProfile.SECURE, windowsFingerprint));
        assertEquals(
                MigrationIdentityPolicy.EmptyLockRecoveryPolicy.MANUAL_ONLY,
                capturePolicy.emptyLockRecoveryPolicy(
                        MigrationAccessProfile.SECURE, mixed));
    }

    private static MigrationPathState.Metadata assertDoesNotThrowMetadata(
            MigrationIdentityPolicy policy, Path path) {
        try {
            return policy.regularFileMetadata(path);
        } catch (IOException failure) {
            throw new AssertionError(
                    "regular metadata should retain UNKNOWN/UNSAFE classification",
                    failure);
        }
    }

    private static String fingerprint(
            Path path,
            FakeAttributes attributes,
            MigrationIdentityPolicy.Platform platform,
            MigrationIdentityPolicy.WindowsContext context,
            Object volumeSerial)
            throws IOException {
        FakeProbe probe = new FakeProbe(platform)
                .add(path, attributes, context)
                .withVolumeSerials(volumeSerial);
        return new MigrationIdentityPolicy(probe).directoryIdentity(path);
    }

    private static void assertDrift(String baseline, IdentityCapture capture) {
        try {
            assertNotEquals(baseline, capture.capture());
        } catch (IOException rejected) {
            assertTrue(
                    rejected.getMessage() == null
                            || !rejected.getMessage().isBlank(),
                    "drift rejection should retain an explanatory reason");
        }
    }

    private static void assertTagged(String identity) {
        assertEquals(
                WINDOWS_TAG,
                identity.split(":", 2)[0],
                () -> "unexpected Windows BASIC identity tag: " + identity);
        assertTrue(
                identity.startsWith(WINDOWS_TAG + ":"),
                () -> "missing Windows BASIC identity tag: " + identity);
        assertTrue(identity.length() > WINDOWS_TAG.length());
    }

    private static FakeProbe windowsProbe() {
        return windowsProbe(25);
    }

    private static FakeProbe windowsProbe(int javaFeature) {
        return new FakeProbe(new MigrationIdentityPolicy.Platform(
                        "Windows 11",
                        javaFeature,
                        true,
                        "file",
                        WINDOWS_PROVIDER))
                .withVolumeSerials(41L);
    }

    private static MigrationIdentityPolicy.Platform windowsPlatform() {
        return new MigrationIdentityPolicy.Platform(
                "Windows 11", 25, true, "file", WINDOWS_PROVIDER);
    }

    private static MigrationIdentityPolicy.Platform linuxPlatform() {
        return new MigrationIdentityPolicy.Platform(
                "Linux", 25, true, "file", LINUX_PROVIDER);
    }

    private static MigrationIdentityPolicy.WindowsContext windowsContext(Path path) {
        return contextWith(
                path,
                path,
                DRIVE_ROOT,
                "Windows volume E",
                "NTFS",
                "sun.nio.fs.WindowsFileStore");
    }

    private static MigrationIdentityPolicy.WindowsContext contextWith(
            Path absolute,
            Path real,
            Path root,
            String storeName,
            String storeType,
            String storeClass) {
        return new MigrationIdentityPolicy.WindowsContext(
                false,
                absolute,
                real,
                root,
                storeName,
                storeType,
                storeClass);
    }

    private static FakeAttributes directory(Object fileKey) {
        return new FakeAttributes(
                false, true, false, false, 0, CREATED, fileKey);
    }

    private static FakeAttributes regular(Object fileKey) {
        return new FakeAttributes(
                true, false, false, false, 37, CREATED, fileKey);
    }

    @FunctionalInterface
    private interface IdentityCapture {
        String capture() throws IOException;
    }

    private static final class FakeProbe implements MigrationIdentityPolicy.Probe {
        private final MigrationIdentityPolicy.Platform platform;
        private final Map<Path, BasicFileAttributes> attributes = new HashMap<>();
        private final Map<Path, MigrationIdentityPolicy.WindowsContext> contexts =
                new HashMap<>();
        private final List<Object> volumeSerials = new ArrayList<>();
        private final List<BasicFileAttributes> attributeSequence =
                new ArrayList<>();
        private final List<MigrationIdentityPolicy.WindowsContext>
                contextSequence = new ArrayList<>();
        private IOException volumeFailure;
        private int attributeReads;
        private int windowsContextReads;
        private int volumeSerialReads;
        private int fileStoreIdentityReads;
        private Path fileStoreIdentityPath;

        private FakeProbe(MigrationIdentityPolicy.Platform platform) {
            this.platform = platform;
        }

        private FakeProbe add(
                Path path,
                BasicFileAttributes value,
                MigrationIdentityPolicy.WindowsContext context) {
            attributes.put(path, value);
            if (context != null) {
                contexts.put(path, context);
            }
            return this;
        }

        private FakeProbe withVolumeSerials(Object... values) {
            volumeSerials.clear();
            for (Object value : values) {
                volumeSerials.add(value);
            }
            return this;
        }

        private FakeProbe withVolumeFailure(IOException failure) {
            volumeFailure = failure;
            return this;
        }

        private FakeProbe withAttributeSequence(
                BasicFileAttributes... values) {
            attributeSequence.clear();
            attributeSequence.addAll(List.of(values));
            attributeReads = 0;
            return this;
        }

        private FakeProbe withContextSequence(
                MigrationIdentityPolicy.WindowsContext... values) {
            contextSequence.clear();
            contextSequence.addAll(List.of(values));
            windowsContextReads = 0;
            return this;
        }

        @Override
        public BasicFileAttributes readNofollowAttributes(Path path)
                throws IOException {
            if (!attributeSequence.isEmpty()) {
                int index = Math.min(
                        attributeReads++, attributeSequence.size() - 1);
                return attributeSequence.get(index);
            }
            BasicFileAttributes value = attributes.get(path);
            if (value == null) {
                throw new NoSuchFileException(path.toString());
            }
            return value;
        }

        @Override
        public MigrationIdentityPolicy.Platform platform(Path path) {
            return platform;
        }

        @Override
        public MigrationIdentityPolicy.WindowsContext windowsContext(Path path)
                throws IOException {
            windowsContextReads++;
            if (!contextSequence.isEmpty()) {
                int index = Math.min(
                        windowsContextReads - 1, contextSequence.size() - 1);
                return contextSequence.get(index);
            }
            MigrationIdentityPolicy.WindowsContext value = contexts.get(path);
            if (value == null) {
                throw new IOException("missing Windows context for " + path);
            }
            return value;
        }

        @Override
        public Object volumeSerialNumber(Path path) throws IOException {
            volumeSerialReads++;
            if (volumeFailure != null) {
                throw volumeFailure;
            }
            if (volumeSerials.isEmpty()) {
                return null;
            }
            int index = Math.min(volumeSerialReads - 1, volumeSerials.size() - 1);
            return volumeSerials.get(index);
        }

        @Override
        public String fileStoreIdentity(Path path) {
            fileStoreIdentityReads++;
            fileStoreIdentityPath = path;
            return "test-store|test-type|test.FileStore";
        }
    }

    private record FakeAttributes(
            boolean regular,
            boolean directory,
            boolean symbolicLink,
            boolean other,
            long size,
            FileTime creationTime,
            Object fileKey)
            implements BasicFileAttributes {

        @Override
        public FileTime lastModifiedTime() {
            return creationTime;
        }

        @Override
        public FileTime lastAccessTime() {
            return creationTime;
        }

        @Override
        public boolean isRegularFile() {
            return regular;
        }

        @Override
        public boolean isDirectory() {
            return directory;
        }

        @Override
        public boolean isSymbolicLink() {
            return symbolicLink;
        }

        @Override
        public boolean isOther() {
            return other;
        }
    }
}
