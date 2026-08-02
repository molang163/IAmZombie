package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MigrationRev1CompleteMutationGuardTest {
    private static final Path CONFIG_SOURCE =
            Path.of("src/main/java/dev/molang/iamzombieq/config")
                    .toAbsolutePath()
                    .normalize();

    @Test
    void completeRequiresHistoricalTargetHashMutantCannotPass()
            throws IOException {
        String engine = source("ConfigMigrationEngine.java");
        String completion = between(
                engine,
                "phase = MigrationTargetState.Phase.COMPLETE;",
                "return state(");
        int completedEntry =
                completion.indexOf("if (resumePermissions.marker())");
        int liveValidation =
                completion.indexOf("validateCompleteTarget(", completedEntry);
        int historicalRecovery =
                completion.indexOf(
                        "validateCanonicalEvidence(", liveValidation);
        int marker = completion.indexOf("ensureMarker(journal");

        assertTrue(completedEntry >= 0);
        assertTrue(liveValidation > completedEntry);
        assertTrue(historicalRecovery > liveValidation);
        assertTrue(marker > historicalRecovery);

        String liveValidator = between(
                engine,
                "private void validateCompleteTarget(",
                "private void validateCurrentTargetBytes(");
        assertTrue(liveValidator.contains("stateOf(path)"));
        assertTrue(liveValidator.contains("read(path, kind)"));
        assertFalse(liveValidator.contains("requireArtifactHash"));
        assertFalse(liveValidator.contains("projectionSha256"));
        assertFalse(liveValidator.contains("validateCanonicalEvidence"));

        String targetPublished = between(
                engine,
                "if (journal.phase()\n"
                        + "                    == MigrationTargetState.Phase"
                        + ".TARGET_PUBLISHED) {",
                "if (journal.phase() != MigrationTargetState.Phase.COMPLETE)");
        assertTrue(targetPublished.contains("validateCanonicalEvidence("));
        assertTrue(targetPublished.indexOf("validateCanonicalEvidence(")
                < targetPublished.indexOf(
                        "MigrationTargetState.Phase.COMPLETE"));
    }

    private static String source(String basename) throws IOException {
        Path path = CONFIG_SOURCE.resolve(basename).normalize();
        if (!path.startsWith(CONFIG_SOURCE) || !Files.isRegularFile(path)) {
            throw new IOException("missing bounded config source " + path);
        }
        return Files.readString(path);
    }

    private static String between(
            String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        if (start < 0 || end < 0 || end <= start) {
            throw new IllegalArgumentException(
                    "source markers are missing or out of order: "
                            + startMarker
                            + " -> "
                            + endMarker);
        }
        return source.substring(start, end);
    }
}
