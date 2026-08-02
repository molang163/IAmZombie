package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class MigrationMutationGuardTest {
    private static final Path CONFIG_SOURCE =
            Path.of("src/main/java/dev/molang/iamzombieq/config")
                    .toAbsolutePath()
                    .normalize();

    @Test
    void fileForceRemovedAndAtomicMoveFallbackMutantsCannotPass()
            throws IOException {
        String publish = between(
                source("AtomicConfigPublisher.java"),
                "Publication publish(Request request)",
                "Publication resume(Request request)");

        assertTrue(publish.indexOf("port.forceFile(request.stage())")
                        < publish.indexOf(
                                "port.atomicMove(request.stage(), request.destination())"),
                "file force must precede the sole atomic publication");
        assertEquals(
                1,
                occurrences(
                        publish,
                        "port.atomicMove(request.stage(), request.destination())"));
        assertFalse(publish.contains("Files.move("));
        assertFalse(publish.contains("Files.copy("));
        assertFalse(publish.contains("copy-delete"));
        assertFalse(publish.contains("ordinaryMove"));
        assertFalse(publish.contains("delete("));
    }

    @Test
    void journalUnlinksPriorGenerationMutantCannotPass() throws IOException {
        String journal = between(
                source("ConfigMigrationEngine.java"),
                "private void publishJournal(",
                "private MigrationJournal readJournal()");

        assertTrue(journal.contains(
                "AtomicConfigPublisher.DestinationExpectation.exactPrior("));
        assertTrue(journal.contains("store.identity(paths.journal())"));
        assertFalse(journal.contains("remove("));
        assertFalse(journal.contains("delete("));
        assertFalse(journal.contains("truncate"));
        assertFalse(journal.contains("unlink"));
    }

    @Test
    void journalIdentityOutsideWorldGuardMutantCannotPass() throws IOException {
        String journal = between(
                source("ConfigMigrationEngine.java"),
                "private void publishJournal(",
                "private MigrationJournal readJournal()");

        assertTrue(journal.contains("withWorldGuard("));
        assertTrue(journal.contains("NOFOLLOW identity "));
        assertTrue(journal.contains("verifyBinding()"));
        assertTrue(journal.indexOf("verifyBinding()")
                < journal.indexOf("store.identity(paths.journal())"));
        assertTrue(journal.lastIndexOf("verifyBinding()")
                > journal.indexOf("store.identity(paths.journal())"));
    }

    @Test
    void earlyCanonicalAdoptionMutantCannotPass() throws IOException {
        String recovery = between(
                source("ConfigMigrationEngine.java"),
                "private void verifyPhaseArtifactPresence(",
                "private MigrationTargetState continueFromJournal(");

        assertTrue(recovery.contains("case PREPARED"));
        assertTrue(recovery.contains("case BACKUP_PUBLISHED"));
        assertTrue(recovery.contains("paths.initial()"));
        assertTrue(recovery.contains("paths.target()"));
        assertTrue(recovery.contains("\"artifact-phase-consistency\""));
    }

    @Test
    void markerBeforeCanonicalValidationAndCompleteRevalidationMutantsCannotPass()
            throws IOException {
        String completion = between(
                source("ConfigMigrationEngine.java"),
                "phase = MigrationTargetState.Phase.COMPLETE;",
                "return state(");

        int targetValidation =
                completion.indexOf("validateCanonicalEvidence(");
        int markerPublication = completion.indexOf("ensureMarker(journal");
        int successfulReturnGuard =
                completion.indexOf("beforeSuccessfulReturn()");
        assertTrue(targetValidation >= 0);
        assertTrue(markerPublication > targetValidation);
        assertTrue(successfulReturnGuard > markerPublication);
        assertTrue(completion.contains("paths.target()"));
        assertTrue(completion.contains("journal.evidence()"));
    }

    @Test
    void frozenFaultVocabularyRetainsEveryPublicationBoundary() {
        assertEquals(
                EnumSet.of(
                        MigrationFaultInjector.Operation.METADATA,
                        MigrationFaultInjector.Operation.PROFILE,
                        MigrationFaultInjector.Operation.LOCK_CREATE,
                        MigrationFaultInjector.Operation.LOCK_OPEN,
                        MigrationFaultInjector.Operation.LOCK_ACQUIRE,
                        MigrationFaultInjector.Operation.LOCK_TRY_LOCK,
                        MigrationFaultInjector.Operation.LOCK_IDENTITY,
                        MigrationFaultInjector.Operation.LOCK_READ,
                        MigrationFaultInjector.Operation.LOCK_VALIDATE,
                        MigrationFaultInjector.Operation
                                .LOCK_PAYLOAD_VALIDATION,
                        MigrationFaultInjector.Operation.STAGE_CREATE,
                        MigrationFaultInjector.Operation.WRITE,
                        MigrationFaultInjector.Operation.FILE_FORCE,
                        MigrationFaultInjector.Operation.DESTINATION_CHECK,
                        MigrationFaultInjector.Operation.ATOMIC_MOVE,
                        MigrationFaultInjector.Operation.CANONICAL_REOPEN,
                        MigrationFaultInjector.Operation.CANONICAL_REPARSE,
                        MigrationFaultInjector.Operation.SCHEMA_SHA_VALIDATION,
                        MigrationFaultInjector.Operation.DIRECTORY_DURABILITY,
                        MigrationFaultInjector.Operation.MARKER_PUBLISH,
                        MigrationFaultInjector.Operation.BINDING_REVALIDATION),
                EnumSet.allOf(MigrationFaultInjector.Operation.class));
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
