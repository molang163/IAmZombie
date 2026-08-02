package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MigrationAccessProfileTest {
    @Test
    void linuxDefaultProviderWithFullRelativeCapabilitySelectsSecure() {
        assertEquals(
                MigrationAccessProfile.SECURE,
                MigrationAccessProfile.select(capabilities("Linux", 25, true, true), false));
    }

    @Test
    void onlyExactWindowsJdk25DefaultProviderTupleSelectsBasic() {
        assertEquals(
                MigrationAccessProfile.BASIC,
                MigrationAccessProfile.select(capabilities("Windows 11", 25, true, false), false));

        for (MigrationAccessProfile.Capabilities unsupported : new MigrationAccessProfile.Capabilities[] {
            capabilities("Windows 11", 24, true, false),
            capabilities("Windows 11", 25, false, false),
            capabilities("Linux", 25, true, false),
            capabilities("Plan9", 25, true, false)
        }) {
            assertThrows(
                    IllegalStateException.class,
                    () -> MigrationAccessProfile.select(unsupported, false));
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
}
