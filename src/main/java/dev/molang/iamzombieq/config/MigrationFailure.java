package dev.molang.iamzombieq.config;

import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

final class MigrationFailure extends IllegalStateException {
    static final String OPERATOR_RECOVERY =
            "C1-F1-STOP-PRESERVE-v1: Stop the server; preserve the target, "
                    + "permanent lock, journal, backup, initial, marker, and "
                    + "every fixed stage byte-for-byte; record a SHA-256 "
                    + "manifest; do not change the target, evidence, parent, "
                    + "or ancestors. If the reported cause is solely an "
                    + "external permission or availability condition, correct "
                    + "only that condition under the same platform and frozen "
                    + "profile without changing any evidence byte, path, "
                    + "binding, or namespace. Restart only after that external "
                    + "condition is fixed and every preserved byte, path, "
                    + "binding, and profile still matches. If the condition "
                    + "persists or recovery requires changing target or "
                    + "evidence state, keep the server stopped; open "
                    + "https://github.com/molang163/IAmZombie/issues and "
                    + "request C1-MANUAL-RECOVERY-v1 from the IAmZombieQ "
                    + "maintainer, providing the complete failure, mod "
                    + "version, OS, JDK, binding, profile, and SHA-256 "
                    + "manifest. Accept only a maintainer-signed directive "
                    + "that names the absolute target, binding, artifact "
                    + "hashes, and exact permitted steps.";

    private final Path legacy;
    private final Path target;
    private final MigrationTargetState.Phase phase;
    private final String artifact;
    private final String operation;
    private final String reason;
    private final String recovery;
    private final boolean synthetic;

    MigrationFailure(
            Path legacy,
            Path target,
            MigrationTargetState.Phase phase,
            String artifact,
            String operation,
            String reason,
            String recovery,
            boolean synthetic,
            Throwable cause) {
        super(
                render(
                        legacy,
                        target,
                        phase,
                        artifact,
                        operation,
                        reason,
                        recovery,
                        synthetic),
                cause);
        this.legacy = normalizedAbsolute(legacy, "legacy");
        this.target = normalizedAbsolute(target, "target");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.artifact = nonBlank(artifact, "artifact");
        this.operation = nonBlank(operation, "operation");
        this.reason = nonBlank(reason, "reason");
        this.recovery = nonBlank(recovery, "recovery");
        this.synthetic = synthetic;
    }

    Path legacy() {
        return legacy;
    }

    Path target() {
        return target;
    }

    MigrationTargetState.Phase phase() {
        return phase;
    }

    String artifact() {
        return artifact;
    }

    String operation() {
        return operation;
    }

    String reason() {
        return reason;
    }

    String recovery() {
        return recovery;
    }

    boolean synthetic() {
        return synthetic;
    }

    static MigrationFailure operational(
            Path legacy,
            Path target,
            MigrationTargetState.Phase phase,
            String artifact,
            String operation,
            String context,
            Throwable cause) {
        String checkedContext = nonBlank(context, "context");
        String reason = cause == null
                ? checkedContext
                : checkedContext + ": " + describe(cause);
        return new MigrationFailure(
                legacy,
                target,
                phase,
                artifact,
                operation,
                reason,
                OPERATOR_RECOVERY,
                false,
                cause);
    }

    static String orphanStageRecovery(Path stage, Path target) {
        Path checkedStage = normalizedAbsolute(stage, "stage");
        Path checkedTarget = normalizedAbsolute(target, "target");
        if (!Objects.equals(checkedStage.getParent(), checkedTarget.getParent())) {
            throw new IllegalArgumentException(
                    "Orphan stage must be a sibling of its target");
        }
        Path targetParent = Objects.requireNonNull(
                checkedTarget.getParent(), "target parent");
        Path recoveryParent = targetParent.resolveSibling(
                targetParent.getFileName() + ".iamzombieq-recovery");
        Path evidenceCopy = recoveryParent.resolve(
                checkedStage.getFileName());
        return "Stop the server. Inspect "
                + recoveryParent
                + " with NOFOLLOW metadata: if absent, create the recovery "
                + "directory at exactly that path without following links; "
                + "if present, require it to be a real directory and not a "
                + "symlink, junction, or reparse point. Otherwise leave the "
                + "original stage untouched and keep the server stopped. "
                + "Require the evidence path "
                + evidenceCopy
                + " to be absent with NOFOLLOW metadata, then create it new "
                + "without overwrite and copy the orphan fixed stage "
                + checkedStage
                + " byte-for-byte without following links to "
                + evidenceCopy
                + ". Then verify that the evidence copy is a regular non-link file "
                + "whose byte length and SHA-256 exactly match the original "
                + "stage before changing anything; "
                + "preserve the target, permanent lock, journal, backup, "
                + "initial, marker, and every other stage unchanged; remove "
                + "only the original orphan fixed stage "
                + checkedStage
                + " after that verified no-overwrite copy; do not rename, "
                + "replace, or retarget the target parent or any ancestor; "
                + "then restart once. If any directory creation, metadata, "
                + "copy, verification, or removal step fails, leave the "
                + "original stage untouched and keep the server stopped.";
    }

    static String describe(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        StringBuilder description = new StringBuilder();
        Set<Throwable> visited = Collections.newSetFromMap(
                new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && visited.add(current)) {
            if (!description.isEmpty()) {
                description.append(" caused by ");
            }
            description.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                description.append(": ").append(message);
            }
            current = current.getCause();
        }
        return description.toString();
    }

    private static String render(
            Path legacy,
            Path target,
            MigrationTargetState.Phase phase,
            String artifact,
            String operation,
            String reason,
            String recovery,
            boolean synthetic) {
        return "C1 migration blocked"
                + (synthetic ? " by synthetic test fault" : "")
                + "\nLegacy: "
                + normalizedAbsolute(legacy, "legacy")
                + "\nTarget: "
                + normalizedAbsolute(target, "target")
                + "\nPhase: "
                + Objects.requireNonNull(phase, "phase")
                + "\nArtifact: "
                + nonBlank(artifact, "artifact")
                + "\nOperation: "
                + nonBlank(operation, "operation")
                + "\nReason: "
                + nonBlank(reason, "reason")
                + "\nRecovery: "
                + nonBlank(recovery, "recovery");
    }

    private static Path normalizedAbsolute(Path path, String field) {
        Objects.requireNonNull(path, field);
        Path normalized = path.toAbsolutePath().normalize();
        if (!path.isAbsolute() || !path.equals(normalized)) {
            throw new IllegalArgumentException(
                    field + " must be normalized and absolute: " + path);
        }
        return path;
    }

    private static String nonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
