package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MigrationRev1EmptyLockMutationGuardTest {
    private static final Path CONFIG_SOURCE =
            Path.of("src/main/java/dev/molang/iamzombieq/config")
                    .toAbsolutePath()
                    .normalize();

    @Test
    void emptyFirstCreationLockRejectedMutantCannotPass()
            throws IOException {
        String engine = source("ConfigMigrationEngine.java");
        String recovery = between(
                engine,
                "private MigrationTargetState recover(",
                "private void verifyPhaseArtifactPresence(");
        assertTrue(recovery.contains(
                "allowEmptyFirstCreationRecovery"));
        assertTrue(recovery.contains(
                "lease.recoveredEmptyFirstCreation()"));
        assertTrue(recovery.contains("rejectOrphanStages()"));
        assertTrue(recovery.contains("onlyPermanentLock()"));
        assertTrue(recovery.contains("stateOf(request.legacy())"));
        assertTrue(recovery.contains("startFromAcquiredFreshLock(lease)"));

        String lock = between(
                source("PermanentMigrationLock.java"),
                "private Acquisition validateExistingLock(",
                "private Acquisition initializeExistingEmptyLock(");
        assertTrue(lock.contains("actual.length == 0"));
        assertTrue(lock.contains(
                "request.allowEmptyFirstCreationRecovery()"));
        assertTrue(lock.contains("requireTargetAbsent(targetGate)"));
        assertTrue(lock.contains(
                "initializeExistingEmptyLock(request, identity)"));
        assertTrue(occurrences(lock, "verifyBound(request, identity, actual)")
                >= 2);
        assertFalse(lock.contains("createNew("));
        assertFalse(lock.contains("unlink"));
        assertFalse(lock.contains("replace"));

        String jdk = between(
                source("JdkMigrationFileSystem.java"),
                "public ConfigMigrationEngine.LockLease acquirePermanentLock(",
                "private boolean isSafelyAbsent(");
        assertTrue(jdk.contains("hasEmptyFirstCreationPortrait(request)"));
        assertTrue(jdk.contains("artifacts.fixedStages()"));
        assertTrue(jdk.contains("request.legacy()"));
        assertTrue(jdk.contains("acquired.recoveredEmptyFirstCreation()"));
        assertFalse(jdk.contains("Files.delete"));
        assertFalse(jdk.contains("Files.move"));
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

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int index = source.indexOf(needle);
                index >= 0;
                index = source.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }
}
