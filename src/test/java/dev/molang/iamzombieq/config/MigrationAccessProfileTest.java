package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class MigrationAccessProfileTest {
    @Test
    void exactNodeLinuxRuntimeMatrixSelectsSecure() {
        for (int javaFeature : MigrationJavaRuntimeMatrix.runtimeFeatures()) {
            assertEquals(
                    MigrationAccessProfile.SECURE,
                    MigrationAccessProfile.select(
                            capabilities(
                                    "Linux",
                                    javaFeature,
                                    true,
                                    true),
                            false));
        }
    }

    @Test
    void exactNodeWindowsRuntimeMatrixSelectsBasic() {
        for (int javaFeature = 21; javaFeature <= 26; javaFeature++) {
            MigrationAccessProfile.Capabilities candidate =
                    capabilities("Windows 11", javaFeature, true, false);
            if (MigrationJavaRuntimeMatrix.runtimeFeatures()
                    .contains(javaFeature)) {
                assertEquals(
                        MigrationAccessProfile.BASIC,
                        MigrationAccessProfile.select(candidate, false));
            } else {
                assertThrows(
                        IllegalStateException.class,
                        () -> MigrationAccessProfile.select(candidate, false));
            }
        }

        for (MigrationAccessProfile.Capabilities unsupported : new MigrationAccessProfile.Capabilities[] {
            capabilities("Windows 11", 22, false, false),
            capabilities("Windows 11", 25, false, false),
            capabilities("Linux", 21, true, true),
            capabilities("Linux", 23, true, true),
            capabilities("Linux", 24, true, true),
            capabilities("Linux", 26, true, true),
            capabilities("Linux", 25, true, false),
            capabilities("Plan9", 25, true, false)
        }) {
            assertThrows(
                    IllegalStateException.class,
                    () -> MigrationAccessProfile.select(unsupported, false));
        }
    }

    @Test
    void windowsBasicRejectsEveryIndependentEligibilityFieldDrift() {
        MigrationAccessProfile.Capabilities eligible = windowsBasic(25);
        Map<String, MigrationAccessProfile.Capabilities> unsupported =
                new LinkedHashMap<>();
        unsupported.put(
                "operatingSystem",
                tuple("Linux", 25, "file",
                        "sun.nio.fs.WindowsFileSystemProvider",
                        true, false, true, true, true));
        unsupported.put(
                "javaFeature",
                tuple("Windows 11", 24, "file",
                        "sun.nio.fs.WindowsFileSystemProvider",
                        true, false, true, true, true));
        unsupported.put(
                "defaultProvider",
                tuple("Windows 11", 25, "file",
                        "sun.nio.fs.WindowsFileSystemProvider",
                        false, false, true, true, true));
        unsupported.put(
                "providerScheme",
                tuple("Windows 11", 25, "jar",
                        "sun.nio.fs.WindowsFileSystemProvider",
                        true, false, true, true, true));
        unsupported.put(
                "providerClass",
                tuple("Windows 11", 25, "file",
                        "third.party.Provider",
                        true, false, true, true, true));
        unsupported.put(
                "secureDirectoryStream",
                tuple("Windows 11", 25, "file",
                        "sun.nio.fs.WindowsFileSystemProvider",
                        true, true, true, true, true));
        unsupported.put(
                "nofollowMetadata",
                tuple("Windows 11", 25, "file",
                        "sun.nio.fs.WindowsFileSystemProvider",
                        true, false, false, true, true));
        unsupported.put(
                "nofollowOpen",
                tuple("Windows 11", 25, "file",
                        "sun.nio.fs.WindowsFileSystemProvider",
                        true, false, true, false, true));
        unsupported.put(
                "atomicMove",
                tuple("Windows 11", 25, "file",
                        "sun.nio.fs.WindowsFileSystemProvider",
                        true, false, true, true, false));

        for (Map.Entry<String, MigrationAccessProfile.Capabilities> entry
                : unsupported.entrySet()) {
            assertEquals(
                    1,
                    differingFields(eligible, entry.getValue()),
                    entry.getKey() + " negative must change exactly one field");
            assertThrows(
                    IllegalStateException.class,
                    () -> MigrationAccessProfile.select(
                            entry.getValue(), false),
                    entry.getKey());
        }
    }

    @Test
    void profileIsFrozenBeforeFirstArtifact() {
        assertThrows(
                IllegalStateException.class,
                () -> MigrationAccessProfile.select(
                        capabilities("Linux", 25, true, true), true));
        MigrationAccessProfile.Frozen frozen = MigrationAccessProfile.freeze(
                MigrationAccessProfile.SECURE);
        assertThrows(
                IllegalStateException.class,
                () -> frozen.transitionTo(MigrationAccessProfile.BASIC));
    }

    static MigrationAccessProfile.Capabilities capabilities(
            String os, int javaFeature, boolean defaultProvider, boolean secure) {
        String defaultProviderClass = os.startsWith("Windows")
                ? "sun.nio.fs.WindowsFileSystemProvider"
                : "sun.nio.fs.LinuxFileSystemProvider";
        return new MigrationAccessProfile.Capabilities(
                os,
                javaFeature,
                "file",
                defaultProvider ? defaultProviderClass : "third.party.Provider",
                defaultProvider,
                secure,
                true,
                true,
                true);
    }

    private static MigrationAccessProfile.Capabilities windowsBasic(
            int javaFeature) {
        return tuple(
                "Windows 11",
                javaFeature,
                "file",
                "sun.nio.fs.WindowsFileSystemProvider",
                true,
                false,
                true,
                true,
                true);
    }

    private static MigrationAccessProfile.Capabilities tuple(
            String operatingSystem,
            int javaFeature,
            String providerScheme,
            String providerClass,
            boolean defaultProvider,
            boolean secureDirectoryStream,
            boolean nofollowMetadata,
            boolean nofollowOpen,
            boolean atomicMove) {
        return new MigrationAccessProfile.Capabilities(
                operatingSystem,
                javaFeature,
                providerScheme,
                providerClass,
                defaultProvider,
                secureDirectoryStream,
                nofollowMetadata,
                nofollowOpen,
                atomicMove);
    }

    private static int differingFields(
            MigrationAccessProfile.Capabilities first,
            MigrationAccessProfile.Capabilities second) {
        int differences = 0;
        differences += Objects.equals(
                first.operatingSystem(), second.operatingSystem()) ? 0 : 1;
        differences += first.javaFeature() == second.javaFeature() ? 0 : 1;
        differences += Objects.equals(
                first.providerScheme(), second.providerScheme()) ? 0 : 1;
        differences += Objects.equals(
                first.providerClass(), second.providerClass()) ? 0 : 1;
        differences += first.defaultProvider() == second.defaultProvider()
                ? 0 : 1;
        differences += first.secureDirectoryStream()
                        == second.secureDirectoryStream()
                ? 0 : 1;
        differences += first.nofollowMetadata() == second.nofollowMetadata()
                ? 0 : 1;
        differences += first.nofollowOpen() == second.nofollowOpen() ? 0 : 1;
        differences += first.atomicMove() == second.atomicMove() ? 0 : 1;
        return differences;
    }
}
