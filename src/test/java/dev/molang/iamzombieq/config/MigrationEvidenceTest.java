package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MigrationEvidenceTest {
    @Test
    void evidenceBindsEveryRequiredTargetParentProfileLockHashAndDurabilityField() {
        MigrationEvidence evidence = sample(
                MigrationTarget.SERVER,
                Path.of("/config/iamzombieq-server.toml"));

        assertEquals("1.1.0", evidence.schemaVersion());
        assertEquals(MigrationAccessProfile.SECURE, evidence.profile());
        assertEquals("lock-dev:7:ino:11", evidence.lockIdentity());
        assertEquals("TARGET_PUBLISHED", evidence.phase());
        assertEquals(5, evidence.artifactHashes().size());
        assertEquals(5, evidence.artifactDurability().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> evidence.artifactHashes().put("other", "0".repeat(64)));
        evidence.verifyBoundTo(evidence.target(), evidence.binding(), evidence.profile());
    }

    @Test
    void serverPreferencesAndWorldEvidenceRemainIndependent() {
        MigrationEvidence global = sample(
                MigrationTarget.SERVER,
                Path.of("/config/iamzombieq-server.toml"));
        MigrationEvidence world = sample(
                MigrationTarget.SERVER,
                Path.of("/world-a/serverconfig/iamzombieq-server.toml"));
        MigrationEvidence preferences = sample(
                MigrationTarget.PREFERENCES,
                Path.of("/config/iamzombieq-preferences-client.toml"));

        assertNotEquals(global.target(), world.target());
        assertNotEquals(global.target(), preferences.target());
        assertThrows(
                IllegalStateException.class,
                () -> global.verifyBoundTo(
                        world.target(), world.binding(), MigrationAccessProfile.SECURE));
        assertThrows(
                IllegalStateException.class,
                () -> global.verifyBoundTo(
                        preferences.target(),
                        preferences.binding(),
                        MigrationAccessProfile.SECURE));
    }

    static MigrationEvidence sample(MigrationTarget targetKind, Path target) {
        MigrationBinding.Observation observation =
                MigrationBindingTest.observation("dir-1", "store-1")
                        .withTarget(target)
                        .withLogicalParent(target.getParent())
                        .withPhysicalParent(Path.of("/physical")
                                .resolve(target.getParent().getFileName().toString()));
        MigrationBinding binding = MigrationBinding.capture(observation);
        return MigrationEvidence.builder(targetKind)
                .target(target)
                .binding(binding)
                .schemaVersion("1.1.0")
                .profile(MigrationAccessProfile.SECURE)
                .lockIdentity("lock-dev:7:ino:11")
                .phase("TARGET_PUBLISHED")
                .projectionSha256("a".repeat(64))
                .rawLegacySha256("b".repeat(64))
                .artifactHashes(Map.of(
                        "journal", "c".repeat(64),
                        "backup", "d".repeat(64),
                        "initial", "e".repeat(64),
                        "target", "f".repeat(64),
                        "marker", "1".repeat(64)))
                .artifactDurability(Map.of(
                        "journal", MigrationEvidence.Durability.BASIC,
                        "backup", MigrationEvidence.Durability.BASIC,
                        "initial", MigrationEvidence.Durability.STRONG,
                        "target", MigrationEvidence.Durability.STRONG,
                        "marker", MigrationEvidence.Durability.BASIC))
                .build();
    }
}
