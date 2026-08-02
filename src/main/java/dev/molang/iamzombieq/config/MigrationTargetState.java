package dev.molang.iamzombieq.config;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

record MigrationTargetState(
        MigrationTarget targetKind,
        Path actualTarget,
        Outcome outcome,
        Phase phase,
        String projectionSha256,
        MigrationEvidence.Durability commitProfile,
        Map<String, MigrationEvidence.Durability> artifactDurability) {

    MigrationTargetState {
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(actualTarget, "actualTarget");
        if (!actualTarget.isAbsolute()
                || !actualTarget.equals(actualTarget.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException(
                    "Actual target must be normalized and absolute: "
                            + actualTarget);
        }
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(phase, "phase");
        projectionSha256 =
                Objects.requireNonNull(projectionSha256, "projectionSha256");
        if (!projectionSha256.isEmpty()
                && !projectionSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Projection SHA must be empty or lowercase SHA-256");
        }
        Objects.requireNonNull(commitProfile, "commitProfile");
        artifactDurability = Map.copyOf(
                Objects.requireNonNull(
                        artifactDurability, "artifactDurability"));
    }

    enum Outcome {
        FRESH,
        EXISTING_VALID,
        MIGRATED,
        COMPLETE
    }

    enum Phase {
        NO_EVIDENCE,
        LOCKED,
        PREPARED,
        BACKUP_PUBLISHED,
        INITIAL_PUBLISHED,
        TARGET_PUBLISHED,
        COMPLETE
    }
}
