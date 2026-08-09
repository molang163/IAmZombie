package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MigrationBindingTest {
    private static final Path TEST_ROOT = Path.of(
                    "build", "test-paths", "migration-binding")
            .toAbsolutePath()
            .normalize();

    @Test
    void capturesImmutableLogicalPhysicalAncestorProviderAndStoreIdentity() {
        MigrationBinding.Observation observation = observation("dir-1", "store-1");
        MigrationBinding binding = MigrationBinding.capture(observation);

        assertEquals(observation.target(), binding.target());
        assertEquals(observation.logicalParent(), binding.logicalParent());
        assertEquals(observation.physicalParent(), binding.physicalParent());
        assertEquals(observation.ancestors(), binding.ancestors());
        assertEquals("dir-1", binding.directoryIdentity());
        assertEquals("file:default-provider", binding.providerIdentity());
        assertEquals("store-1", binding.fileStoreIdentity());
        assertThrows(
                UnsupportedOperationException.class,
                () -> binding.ancestors().add(
                        new MigrationBinding.Ancestor(
                                absolutePath("other"), "other")));
    }

    @Test
    void everyObservableParentBindingDriftFailsClosed() {
        MigrationBinding original = MigrationBinding.capture(observation("dir-1", "store-1"));
        List<MigrationBinding.Observation> drifted = List.of(
                observation("dir-2", "store-1"),
                observation("dir-1", "store-2"),
                observation("dir-1", "store-1")
                        .withPhysicalParent(absolutePath("physical-2")),
                observation("dir-1", "store-1")
                        .withAncestors(List.of(
                                new MigrationBinding.Ancestor(
                                        TEST_ROOT.getRoot(), "root-2"))),
                observation("dir-1", "store-1").withProviderIdentity("jar:other-provider"),
                observation("dir-1", "store-1")
                        .withTarget(absolutePath(
                                "logical", "other-target.toml")),
                observation("dir-1", "store-1")
                        .withLogicalParent(absolutePath("other-logical")),
                observation("dir-1", "store-1").withJavaFeature(24),
                observation("dir-1", "store-1").withOperatingSystem("OtherOS"));

        for (MigrationBinding.Observation observation : drifted) {
            assertThrows(
                    IllegalStateException.class,
                    () -> original.verifyUnchanged(observation),
                    () -> "accepted binding drift " + observation);
        }
        original.verifyUnchanged(observation("dir-1", "store-1"));
    }

    @Test
    void distinctTargetsNeverShareBindingIdentity() {
        MigrationBinding server = MigrationBinding.capture(observation("server-dir", "store-1"));
        MigrationBinding preferences = MigrationBinding.capture(
                observation("preferences-dir", "store-1")
                        .withTarget(absolutePath(
                                "logical",
                                "iamzombieq-preferences-client.toml")));
        assertThrows(
                IllegalStateException.class,
                () -> server.verifyUnchanged(preferences.toObservation()));
    }

    @Test
    void java22And25EvidenceCannotResumeAcrossRuntimeFeatures() {
        MigrationBinding java22 = MigrationBinding.capture(
                observation("dir-1", "store-1").withJavaFeature(22));
        MigrationBinding java25 = MigrationBinding.capture(
                observation("dir-1", "store-1").withJavaFeature(25));

        assertThrows(
                IllegalStateException.class,
                () -> java22.verifyUnchanged(java25.toObservation()));
        assertThrows(
                IllegalStateException.class,
                () -> java25.verifyUnchanged(java22.toObservation()));
    }

    static MigrationBinding.Observation observation(String directoryIdentity, String store) {
        return new MigrationBinding.Observation(
                absolutePath("logical", "iamzombieq-server.toml"),
                absolutePath("logical"),
                absolutePath("physical"),
                List.of(new MigrationBinding.Ancestor(
                        TEST_ROOT.getRoot(), "root-1")),
                directoryIdentity,
                "file:default-provider",
                store,
                25,
                "Linux");
    }

    static Path absolutePath(String first, String... more) {
        return TEST_ROOT.resolve(Path.of(first, more)).normalize();
    }
}
