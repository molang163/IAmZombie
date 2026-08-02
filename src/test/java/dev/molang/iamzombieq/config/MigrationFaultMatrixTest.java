package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class MigrationFaultMatrixTest {
    private static final Path ROOT =
            Path.of("/migration-engine").toAbsolutePath().normalize();
    private static final Path LEGACY =
            ROOT.resolve("config/iamzombieq-common.toml");
    private static final Path TARGET =
            ROOT.resolve("world/serverconfig/iamzombieq-server.toml");

    @Test
    void journalStageCreateFault() {
        assertStageCreateRetry(AtomicConfigPublisher.Artifact.JOURNAL, false);
        assertStageCreateRetry(AtomicConfigPublisher.Artifact.JOURNAL, true);
    }

    @Test
    void journalWriteFault() {
        assertOrphanedStage(AtomicConfigPublisher.Artifact.JOURNAL, FaultAt.WRITE, false);
        assertOrphanedStage(AtomicConfigPublisher.Artifact.JOURNAL, FaultAt.WRITE, true);
    }

    @Test
    void journalForceFault() {
        assertOrphanedStage(AtomicConfigPublisher.Artifact.JOURNAL, FaultAt.FILE_FORCE, false);
    }

    @Test
    void journalDestinationConflictFailsBeforeMove() {
        assertDestinationConflict(AtomicConfigPublisher.Artifact.JOURNAL);
    }

    @Test
    void journalAtomicMoveFailsBeforeCommit() {
        assertOrphanedStage(
                AtomicConfigPublisher.Artifact.JOURNAL, FaultAt.MOVE_PRECOMMIT, false);
    }

    @Test
    void journalAtomicMoveCommitsThenReportsFailure() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.JOURNAL, FaultAt.MOVE_COMMITTED);
    }

    @Test
    void journalReopenFault() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.JOURNAL, FaultAt.CANONICAL_REOPEN);
    }

    @Test
    void journalReparseFault() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.JOURNAL, FaultAt.CANONICAL_REPARSE);
    }

    @Test
    void journalValidationFault() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.JOURNAL, FaultAt.CANONICAL_VALIDATE);
    }

    @Test
    void journalDirectoryDurabilityFault() {
        assertDirectoryDurabilityRetry(AtomicConfigPublisher.Artifact.JOURNAL);
    }

    @Test
    void backupStageCreateFault() {
        assertStageCreateRetry(AtomicConfigPublisher.Artifact.BACKUP, false);
    }

    @Test
    void backupWriteFault() {
        assertOrphanedStage(AtomicConfigPublisher.Artifact.BACKUP, FaultAt.WRITE, false);
    }

    @Test
    void backupForceFault() {
        assertOrphanedStage(
                AtomicConfigPublisher.Artifact.BACKUP, FaultAt.FILE_FORCE, false);
    }

    @Test
    void backupDestinationConflictFailsBeforeMove() {
        assertDestinationConflict(AtomicConfigPublisher.Artifact.BACKUP);
    }

    @Test
    void backupAtomicMoveFailsBeforeCommit() {
        assertOrphanedStage(
                AtomicConfigPublisher.Artifact.BACKUP, FaultAt.MOVE_PRECOMMIT, false);
    }

    @Test
    void backupAtomicMoveCommitsThenReportsFailure() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.BACKUP, FaultAt.MOVE_COMMITTED);
    }

    @Test
    void backupReopenFault() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.BACKUP, FaultAt.CANONICAL_REOPEN);
    }

    @Test
    void backupReparseFault() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.BACKUP, FaultAt.CANONICAL_REPARSE);
    }

    @Test
    void backupValidationFault() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.BACKUP, FaultAt.CANONICAL_VALIDATE);
    }

    @Test
    void backupDirectoryDurabilityFault() {
        assertDirectoryDurabilityRetry(AtomicConfigPublisher.Artifact.BACKUP);
    }

    @Test
    void initialStageCreateFault() {
        assertStageCreateRetry(AtomicConfigPublisher.Artifact.INITIAL, false);
    }

    @Test
    void initialWriteFault() {
        assertOrphanedStage(AtomicConfigPublisher.Artifact.INITIAL, FaultAt.WRITE, false);
    }

    @Test
    void initialForceFault() {
        assertOrphanedStage(
                AtomicConfigPublisher.Artifact.INITIAL, FaultAt.FILE_FORCE, false);
    }

    @Test
    void initialDestinationConflictFailsBeforeMove() {
        assertDestinationConflict(AtomicConfigPublisher.Artifact.INITIAL);
    }

    @Test
    void initialAtomicMoveFailsBeforeCommit() {
        assertOrphanedStage(
                AtomicConfigPublisher.Artifact.INITIAL, FaultAt.MOVE_PRECOMMIT, false);
    }

    @Test
    void initialAtomicMoveCommitsThenReportsFailure() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.INITIAL, FaultAt.MOVE_COMMITTED);
    }

    @Test
    void initialReopenFault() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.INITIAL, FaultAt.CANONICAL_REOPEN);
    }

    @Test
    void initialReparseFault() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.INITIAL, FaultAt.CANONICAL_REPARSE);
    }

    @Test
    void initialValidationFault() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.INITIAL, FaultAt.CANONICAL_VALIDATE);
    }

    @Test
    void initialDirectoryDurabilityFault() {
        assertDirectoryDurabilityRetry(AtomicConfigPublisher.Artifact.INITIAL);
    }

    @Test
    void targetStageCreateFault() {
        assertStageCreateRetry(AtomicConfigPublisher.Artifact.TARGET, false);
    }

    @Test
    void targetWriteFault() {
        assertOrphanedStage(AtomicConfigPublisher.Artifact.TARGET, FaultAt.WRITE, false);
    }

    @Test
    void targetForceFault() {
        assertOrphanedStage(
                AtomicConfigPublisher.Artifact.TARGET, FaultAt.FILE_FORCE, false);
    }

    @Test
    void targetDestinationConflictFailsBeforeMove() {
        assertDestinationConflict(AtomicConfigPublisher.Artifact.TARGET);
    }

    @Test
    void targetAtomicMoveFailsBeforeCommit() {
        assertOrphanedStage(
                AtomicConfigPublisher.Artifact.TARGET, FaultAt.MOVE_PRECOMMIT, false);
    }

    @Test
    void targetAtomicMoveCommitsThenReportsFailure() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.TARGET, FaultAt.MOVE_COMMITTED);
    }

    @Test
    void targetReopenFault() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.TARGET, FaultAt.CANONICAL_REOPEN);
    }

    @Test
    void targetReparseFault() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.TARGET, FaultAt.CANONICAL_REPARSE);
    }

    @Test
    void targetValidationFault() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.TARGET, FaultAt.CANONICAL_VALIDATE);
    }

    @Test
    void targetDirectoryDurabilityFault() {
        assertDirectoryDurabilityRetry(AtomicConfigPublisher.Artifact.TARGET);
    }

    @Test
    void markerStageCreateFault() {
        assertStageCreateRetry(AtomicConfigPublisher.Artifact.MARKER, false);
    }

    @Test
    void markerWriteFault() {
        assertOrphanedStage(AtomicConfigPublisher.Artifact.MARKER, FaultAt.WRITE, false);
    }

    @Test
    void markerForceFault() {
        assertOrphanedStage(
                AtomicConfigPublisher.Artifact.MARKER, FaultAt.FILE_FORCE, false);
    }

    @Test
    void markerDestinationConflictFailsBeforeMove() {
        assertDestinationConflict(AtomicConfigPublisher.Artifact.MARKER);
    }

    @Test
    void markerAtomicMoveFailsBeforeCommit() {
        assertOrphanedStage(
                AtomicConfigPublisher.Artifact.MARKER, FaultAt.MOVE_PRECOMMIT, false);
    }

    @Test
    void markerAtomicMoveCommitsThenReportsFailure() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.MARKER, FaultAt.MOVE_COMMITTED);
    }

    @Test
    void markerReopenFault() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.MARKER, FaultAt.CANONICAL_REOPEN);
    }

    @Test
    void markerReparseFault() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.MARKER, FaultAt.CANONICAL_REPARSE);
    }

    @Test
    void markerValidationFault() {
        assertCommittedPublicationResumes(
                AtomicConfigPublisher.Artifact.MARKER, FaultAt.CANONICAL_VALIDATE);
    }

    @Test
    void markerDirectoryDurabilityFault() {
        assertDirectoryDurabilityRetry(AtomicConfigPublisher.Artifact.MARKER);
    }

    @Test
    void committedThenReportedFailedMoveNeverRepublishes() {
        for (AtomicConfigPublisher.Artifact artifact
                : AtomicConfigPublisher.Artifact.values()) {
            assertCommittedPublicationResumes(artifact, FaultAt.MOVE_COMMITTED);
        }
    }

    @Test
    void injectorExposesEveryPublicationBoundaryAsSynthetic() {
        List<MigrationFaultInjector.Operation> operations = List.of(
                MigrationFaultInjector.Operation.STAGE_CREATE,
                MigrationFaultInjector.Operation.WRITE,
                MigrationFaultInjector.Operation.FILE_FORCE,
                MigrationFaultInjector.Operation.DESTINATION_CHECK,
                MigrationFaultInjector.Operation.ATOMIC_MOVE,
                MigrationFaultInjector.Operation.CANONICAL_REOPEN,
                MigrationFaultInjector.Operation.CANONICAL_REPARSE,
                MigrationFaultInjector.Operation.SCHEMA_SHA_VALIDATION,
                MigrationFaultInjector.Operation.DIRECTORY_DURABILITY);

        for (MigrationFaultInjector.Operation operation : operations) {
            FaultPort port = new FaultPort();
            AtomicConfigPublisher.Request request = request(
                    AtomicConfigPublisher.Artifact.TARGET,
                    false,
                    operation
                            == MigrationFaultInjector.Operation
                                    .DIRECTORY_DURABILITY);
            MigrationFaultInjector injector = point -> {
                if (point.operation() == operation
                        && point.timing()
                                == MigrationFaultInjector.Timing.BEFORE) {
                    throw new IllegalStateException(
                            "synthetic " + operation);
                }
            };

            MigrationFaultInjector.SyntheticFault failure = assertThrows(
                    MigrationFaultInjector.SyntheticFault.class,
                    () -> new AtomicConfigPublisher(
                                    port,
                                    injector,
                                    MigrationTargetState.Phase
                                            .INITIAL_PUBLISHED)
                            .publish(request));

            assertEquals(operation, failure.point().operation());
            assertEquals(
                    AtomicConfigPublisher.Artifact.TARGET,
                    failure.point().artifact());
            assertEquals(
                    MigrationTargetState.Phase.INITIAL_PUBLISHED,
                    failure.point().phase());
            assertTrue(failure.getMessage().contains("synthetic"));
            assertNoFallback(port);
        }
    }

    @Test
    void engineStageAndPrecommitFaultsLeaveOnlyDeterministicRestartStates()
            throws IOException {
        for (AtomicConfigPublisher.Artifact artifact
                : AtomicConfigPublisher.Artifact.values()) {
            assertCleanEngineRetry(
                    artifact,
                    MigrationFaultInjector.Operation.STAGE_CREATE,
                    MigrationFaultInjector.Timing.BEFORE);
            assertOrphanedEngineStage(
                    artifact,
                    MigrationFaultInjector.Operation.STAGE_CREATE,
                    MigrationFaultInjector.Timing.AFTER);
            for (MigrationFaultInjector.Operation operation : List.of(
                    MigrationFaultInjector.Operation.WRITE,
                    MigrationFaultInjector.Operation.FILE_FORCE,
                    MigrationFaultInjector.Operation.DESTINATION_CHECK)) {
                for (MigrationFaultInjector.Timing timing
                        : MigrationFaultInjector.Timing.values()) {
                    assertOrphanedEngineStage(artifact, operation, timing);
                }
            }
            assertOrphanedEngineStage(
                    artifact,
                    MigrationFaultInjector.Operation.ATOMIC_MOVE,
                    MigrationFaultInjector.Timing.BEFORE);
        }
    }

    @Test
    void engineCommittedFaultsResumeWithoutRepublishingCommittedBytes()
            throws IOException {
        for (AtomicConfigPublisher.Artifact artifact
                : AtomicConfigPublisher.Artifact.values()) {
            assertCommittedEngineRetry(
                    artifact,
                    MigrationFaultInjector.Operation.ATOMIC_MOVE,
                    MigrationFaultInjector.Timing.AFTER,
                    false);
            for (MigrationFaultInjector.Operation operation : List.of(
                    MigrationFaultInjector.Operation.CANONICAL_REOPEN,
                    MigrationFaultInjector.Operation.CANONICAL_REPARSE,
                    MigrationFaultInjector.Operation
                            .SCHEMA_SHA_VALIDATION)) {
                for (MigrationFaultInjector.Timing timing
                        : MigrationFaultInjector.Timing.values()) {
                    assertCommittedEngineRetry(
                            artifact, operation, timing, false);
                }
            }
            for (MigrationFaultInjector.Timing timing
                    : MigrationFaultInjector.Timing.values()) {
                assertCommittedEngineRetry(
                        artifact,
                        MigrationFaultInjector.Operation
                                .DIRECTORY_DURABILITY,
                        timing,
                        true);
            }
        }
    }

    @Test
    void engineMarkerPublishBoundaryAlwaysRevalidatesOnRestart()
            throws IOException {
        assertMarkerPublishRetry(MigrationFaultInjector.Timing.BEFORE);
        assertMarkerPublishRetry(MigrationFaultInjector.Timing.AFTER);
    }

    private static void assertCleanEngineRetry(
            AtomicConfigPublisher.Artifact artifact,
            MigrationFaultInjector.Operation operation,
            MigrationFaultInjector.Timing timing)
            throws IOException {
        EngineScenario scenario =
                faultEngine(artifact, operation, timing, false);
        assertFalse(
                scenario.store().files.containsKey(stage(scenario.paths(), artifact)),
                () -> "stage unexpectedly exists after "
                        + artifact
                        + " "
                        + operation
                        + " "
                        + timing);
        assertFalse(
                scenario.store().files.containsKey(
                        destination(scenario.paths(), artifact)));
        assertJournalPhase(
                scenario.store(), scenario.paths(), priorPhase(artifact));

        scenario.store().events.clear();
        if (artifact == AtomicConfigPublisher.Artifact.JOURNAL) {
            MigrationFailure restart = assertThrows(
                    MigrationFailure.class,
                    () -> engine(MigrationFaultInjector.none())
                            .migrate(
                                    scenario.request(),
                                    scenario.store()));
            assertEquals("locked-recovery", restart.operation());
            assertFalse(scenario.store().events.contains(
                    "read:LEGACY:" + LEGACY));
            assertFalse(scenario.store().events.stream()
                    .anyMatch(event -> event.startsWith("publish:")));
            return;
        }

        MigrationTargetState recovered = engine(MigrationFaultInjector.none())
                .migrate(scenario.request(), scenario.store());

        assertEquals(MigrationTargetState.Outcome.COMPLETE, recovered.outcome());
        assertTrue(scenario.paths().fixedStages().stream()
                .noneMatch(scenario.store().files::containsKey));
    }

    private static void assertOrphanedEngineStage(
            AtomicConfigPublisher.Artifact artifact,
            MigrationFaultInjector.Operation operation,
            MigrationFaultInjector.Timing timing)
            throws IOException {
        EngineScenario scenario =
                faultEngine(artifact, operation, timing, false);
        Path stage = stage(scenario.paths(), artifact);
        assertTrue(
                scenario.store().files.containsKey(stage),
                () -> "fixed stage missing after "
                        + artifact
                        + " "
                        + operation
                        + " "
                        + timing);
        assertFalse(
                scenario.store().files.containsKey(
                        destination(scenario.paths(), artifact)));
        assertJournalPhase(
                scenario.store(), scenario.paths(), priorPhase(artifact));
        int movesBeforeRestart = scenario.store().atomicMoves.size();

        MigrationFailure restart = assertThrows(
                MigrationFailure.class,
                () -> engine(MigrationFaultInjector.none())
                        .migrate(scenario.request(), scenario.store()));

        assertEquals("orphan-stage-check", restart.operation());
        assertFalse(restart.synthetic());
        assertEquals(movesBeforeRestart, scenario.store().atomicMoves.size());
        assertTrue(scenario.store().files.containsKey(stage));
    }

    private static void assertCommittedEngineRetry(
            AtomicConfigPublisher.Artifact artifact,
            MigrationFaultInjector.Operation operation,
            MigrationFaultInjector.Timing timing,
            boolean strong)
            throws IOException {
        EngineScenario scenario =
                faultEngine(artifact, operation, timing, strong);
        Path destination = destination(scenario.paths(), artifact);
        assertFalse(
                scenario.store().files.containsKey(stage(scenario.paths(), artifact)));
        assertTrue(scenario.store().files.containsKey(destination));
        assertJournalPhase(
                scenario.store(),
                scenario.paths(),
                committedPhase(artifact));
        String fingerprint = scenario.store().atomicMoves.stream()
                .filter(value -> value.startsWith(artifact + ":"))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertEquals(
                1,
                scenario.store().atomicMoves.stream()
                        .filter(fingerprint::equals)
                        .count());

        if (artifact != AtomicConfigPublisher.Artifact.JOURNAL) {
            scenario.store().put(
                    LEGACY,
                    "corrupt live legacy after committed artifact"
                            .getBytes(StandardCharsets.UTF_8));
        }
        scenario.store().events.clear();
        MigrationTargetState recovered = engine(MigrationFaultInjector.none())
                .migrate(scenario.request(), scenario.store());

        assertEquals(MigrationTargetState.Outcome.COMPLETE, recovered.outcome());
        assertEquals(
                1,
                scenario.store().atomicMoves.stream()
                        .filter(fingerprint::equals)
                        .count(),
                () -> "restart republished committed bytes for "
                        + artifact
                        + " "
                        + operation
                        + " "
                        + timing);
        if (artifact != AtomicConfigPublisher.Artifact.JOURNAL) {
            assertFalse(
                    scenario.store().events.contains("read:LEGACY:" + LEGACY));
        }
        assertTrue(scenario.paths().fixedStages().stream()
                .noneMatch(scenario.store().files::containsKey));
    }

    private static void assertMarkerPublishRetry(
            MigrationFaultInjector.Timing timing) throws IOException {
        EngineScenario scenario = faultEngine(
                AtomicConfigPublisher.Artifact.MARKER,
                MigrationFaultInjector.Operation.MARKER_PUBLISH,
                timing,
                false);
        boolean committed =
                timing == MigrationFaultInjector.Timing.AFTER;
        assertEquals(
                committed,
                scenario.store().files.containsKey(scenario.paths().marker()));
        assertFalse(
                scenario.store().files.containsKey(
                        scenario.paths().fixedStages().get(4)));
        scenario.store().put(
                LEGACY,
                "corrupt live legacy after complete target"
                        .getBytes(StandardCharsets.UTF_8));
        scenario.store().events.clear();
        int markerMoves = (int) scenario.store().atomicMoves.stream()
                .filter(value -> value.startsWith(
                        AtomicConfigPublisher.Artifact.MARKER + ":"))
                .count();

        MigrationTargetState recovered = engine(MigrationFaultInjector.none())
                .migrate(scenario.request(), scenario.store());

        assertEquals(MigrationTargetState.Outcome.COMPLETE, recovered.outcome());
        assertFalse(
                scenario.store().events.contains("read:LEGACY:" + LEGACY));
        assertEquals(
                committed ? markerMoves : markerMoves + 1,
                scenario.store().atomicMoves.stream()
                        .filter(value -> value.startsWith(
                                AtomicConfigPublisher.Artifact.MARKER + ":"))
                        .count());
    }

    private static EngineScenario faultEngine(
            AtomicConfigPublisher.Artifact artifact,
            MigrationFaultInjector.Operation operation,
            MigrationFaultInjector.Timing timing,
            boolean strong)
            throws IOException {
        MigrationEngineTestStore store = new MigrationEngineTestStore();
        store.put(LEGACY, LegacyConfigParserTest.fixtureBytes());
        ConfigMigrationEngine.Request request = request(strong);
        AtomicBoolean fired = new AtomicBoolean();
        MigrationFaultInjector injector = point -> {
            if (point.artifact() == artifact
                    && point.operation() == operation
                    && point.timing() == timing
                    && fired.compareAndSet(false, true)) {
                throw new IllegalStateException(
                        "synthetic engine matrix fault "
                                + artifact
                                + " "
                                + operation
                                + " "
                                + timing);
            }
        };

        MigrationFailure failure = assertThrows(
                MigrationFailure.class,
                () -> engine(injector).migrate(request, store),
                () -> "fault did not stop "
                        + artifact
                        + " "
                        + operation
                        + " "
                        + timing);
        assertTrue(fired.get());
        assertTrue(failure.synthetic());
        assertEquals(artifact.name().toLowerCase(
                        java.util.Locale.ROOT), failure.artifact());
        assertEquals(operation.name(), failure.operation());
        return new EngineScenario(
                store,
                request,
                ConfigMigrationEngineTest.paths(TARGET));
    }

    private static ConfigMigrationEngine engine(
            MigrationFaultInjector injector) {
        return new ConfigMigrationEngine(
                ConfigSchemaCatalog.load(), injector);
    }

    private static ConfigMigrationEngine.Request request(boolean strong) {
        ConfigMigrationEngine.Request base =
                ConfigMigrationEngineTest.request(TARGET);
        return new ConfigMigrationEngine.Request(
                base.targetKind(),
                base.legacy(),
                base.actualTarget(),
                base.binding(),
                base.profile(),
                base.worldGuard(),
                strong);
    }

    private static void assertJournalPhase(
            MigrationEngineTestStore store,
            MigrationFileSystem.ArtifactPaths paths,
            MigrationTargetState.Phase expected) {
        byte[] journal = store.files.get(paths.journal());
        if (expected == null) {
            assertTrue(journal == null);
        } else {
            assertTrue(journal != null);
            assertEquals(
                    expected, MigrationJournal.decode(journal).phase());
        }
    }

    private static MigrationTargetState.Phase priorPhase(
            AtomicConfigPublisher.Artifact artifact) {
        return switch (artifact) {
            case JOURNAL -> null;
            case BACKUP -> MigrationTargetState.Phase.PREPARED;
            case INITIAL -> MigrationTargetState.Phase.BACKUP_PUBLISHED;
            case TARGET -> MigrationTargetState.Phase.INITIAL_PUBLISHED;
            case MARKER -> MigrationTargetState.Phase.COMPLETE;
        };
    }

    private static MigrationTargetState.Phase committedPhase(
            AtomicConfigPublisher.Artifact artifact) {
        return artifact == AtomicConfigPublisher.Artifact.JOURNAL
                ? MigrationTargetState.Phase.PREPARED
                : priorPhase(artifact);
    }

    private static Path stage(
            MigrationFileSystem.ArtifactPaths paths,
            AtomicConfigPublisher.Artifact artifact) {
        return paths.fixedStages().get(artifact.ordinal());
    }

    private static Path destination(
            MigrationFileSystem.ArtifactPaths paths,
            AtomicConfigPublisher.Artifact artifact) {
        return switch (artifact) {
            case JOURNAL -> paths.journal();
            case BACKUP -> paths.backup();
            case INITIAL -> paths.initial();
            case TARGET -> paths.target();
            case MARKER -> paths.marker();
        };
    }

    private record EngineScenario(
            MigrationEngineTestStore store,
            ConfigMigrationEngine.Request request,
            MigrationFileSystem.ArtifactPaths paths) {}

    private static void assertStageCreateRetry(
            AtomicConfigPublisher.Artifact artifact, boolean withPriorJournal) {
        FaultPort port = port(withPriorJournal);
        AtomicConfigPublisher.Request request =
                request(artifact, withPriorJournal, false);
        port.fault = FaultAt.STAGE_CREATE;

        assertThrows(
                IllegalStateException.class,
                () -> new AtomicConfigPublisher(port).publish(request));

        assertFalse(port.stageExists);
        assertCanonicalBeforeMove(port, withPriorJournal);
        assertNoMutationAfterFault(port);
        assertEquals(0, count(port.events, "atomic-move"));

        port.fault = null;
        new AtomicConfigPublisher(port).publish(request);
        assertArrayEquals(request.bytes(), port.canonical);
        assertEquals(1, count(port.events, "atomic-move"));
        assertNoFallback(port);
    }

    private static void assertOrphanedStage(
            AtomicConfigPublisher.Artifact artifact,
            FaultAt fault,
            boolean withPriorJournal) {
        FaultPort port = port(withPriorJournal);
        AtomicConfigPublisher.Request request =
                request(artifact, withPriorJournal, false);
        port.fault = fault;

        assertThrows(
                IllegalStateException.class,
                () -> new AtomicConfigPublisher(port).publish(request));

        assertTrue(port.stageExists);
        assertCanonicalBeforeMove(port, withPriorJournal);
        assertNoMutationAfterFault(port);
        int movesBeforeRestart = (int) count(port.events, "atomic-move");
        assertThrows(
                IllegalStateException.class,
                () -> new AtomicConfigPublisher(port).resume(request));
        assertEquals(movesBeforeRestart, count(port.events, "atomic-move"));
        assertNoFallback(port);
    }

    private static void assertDestinationConflict(
            AtomicConfigPublisher.Artifact artifact) {
        FaultPort port = new FaultPort();
        byte[] conflict = "unexpected-destination".getBytes(StandardCharsets.UTF_8);
        port.canonical = conflict.clone();
        port.canonicalIdentity = "conflict-identity";
        port.fault = FaultAt.DESTINATION_FINAL_CHECK;
        AtomicConfigPublisher.Request request = request(artifact, false, false);

        assertThrows(
                IllegalStateException.class,
                () -> new AtomicConfigPublisher(port).publish(request));

        assertTrue(port.stageExists);
        assertArrayEquals(conflict, port.canonical);
        assertEquals(0, count(port.events, "atomic-move"));
        assertNoMutationAfterFault(port);
        assertNoFallback(port);
    }

    private static void assertCommittedPublicationResumes(
            AtomicConfigPublisher.Artifact artifact, FaultAt fault) {
        FaultPort port = new FaultPort();
        AtomicConfigPublisher.Request request = request(artifact, false, false);
        port.fault = fault;

        assertThrows(
                IllegalStateException.class,
                () -> new AtomicConfigPublisher(port).publish(request));

        assertFalse(port.stageExists);
        assertArrayEquals(request.bytes(), port.canonical);
        assertNoMutationAfterFault(port);
        port.fault = null;
        AtomicConfigPublisher.Publication resumed =
                new AtomicConfigPublisher(port).resume(request);
        assertEquals(MigrationEvidence.Durability.BASIC, resumed.durability());
        assertEquals(1, count(port.events, "atomic-move"));
        assertNoFallback(port);
    }

    private static void assertDirectoryDurabilityRetry(
            AtomicConfigPublisher.Artifact artifact) {
        FaultPort port = new FaultPort();
        AtomicConfigPublisher.Request request = request(artifact, false, true);
        port.fault = FaultAt.DIRECTORY_DURABILITY;

        assertThrows(
                IllegalStateException.class,
                () -> new AtomicConfigPublisher(port).publish(request));

        assertFalse(port.stageExists);
        assertArrayEquals(request.bytes(), port.canonical);
        assertNoMutationAfterFault(port);
        port.fault = null;
        AtomicConfigPublisher.Publication resumed =
                new AtomicConfigPublisher(port).resume(request);
        assertEquals(MigrationEvidence.Durability.STRONG, resumed.durability());
        assertEquals(1, count(port.events, "atomic-move"));
        assertEquals(2, count(port.events, "force-directory"));
        assertNoFallback(port);
    }

    private static AtomicConfigPublisher.Request request(
            AtomicConfigPublisher.Artifact artifact,
            boolean withPriorJournal,
            boolean strong) {
        String name = artifact.name().toLowerCase(java.util.Locale.ROOT);
        AtomicConfigPublisher.DestinationExpectation expectation =
                withPriorJournal
                        ? AtomicConfigPublisher.DestinationExpectation.exactPrior(
                                FaultPort.PRIOR, FaultPort.PRIOR_IDENTITY)
                        : AtomicConfigPublisher.DestinationExpectation.absent();
        return new AtomicConfigPublisher.Request(
                artifact,
                name + ".stage",
                name,
                ("next-" + name).getBytes(StandardCharsets.UTF_8),
                expectation,
                strong);
    }

    private static FaultPort port(boolean withPriorJournal) {
        FaultPort port = new FaultPort();
        if (withPriorJournal) {
            port.canonical = FaultPort.PRIOR.clone();
            port.canonicalIdentity = FaultPort.PRIOR_IDENTITY;
        }
        return port;
    }

    private static void assertCanonicalBeforeMove(
            FaultPort port, boolean withPriorJournal) {
        if (withPriorJournal) {
            assertArrayEquals(FaultPort.PRIOR, port.canonical);
            assertEquals(FaultPort.PRIOR_IDENTITY, port.canonicalIdentity);
        } else {
            assertTrue(port.canonical == null);
        }
        assertEquals(0, count(port.events, "unlink"));
        assertEquals(0, count(port.events, "truncate-canonical"));
        assertEquals(0, count(port.events, "write-canonical"));
    }

    private static void assertNoMutationAfterFault(FaultPort port) {
        assertTrue(port.faultEventIndex >= 0, "the configured fault must fire");
        List<String> tail =
                port.events.subList(port.faultEventIndex + 1, port.events.size());
        assertTrue(
                tail.stream().allMatch("close-stage"::equals),
                () -> "later operation followed injected failure: " + tail);
    }

    private static void assertNoFallback(FaultPort port) {
        assertEquals(0, count(port.events, "ordinary-move"));
        assertEquals(0, count(port.events, "copy-delete"));
        assertTrue(count(port.events, "atomic-move") <= 1);
    }

    private static long count(List<String> events, String expected) {
        return events.stream().filter(expected::equals).count();
    }

    private enum FaultAt {
        STAGE_CREATE,
        WRITE,
        FILE_FORCE,
        DESTINATION_FINAL_CHECK,
        MOVE_PRECOMMIT,
        MOVE_COMMITTED,
        CANONICAL_REOPEN,
        CANONICAL_REPARSE,
        CANONICAL_VALIDATE,
        DIRECTORY_DURABILITY
    }

    private static final class FaultPort implements AtomicConfigPublisher.Port {
        private static final byte[] PRIOR =
                "prior-journal-generation".getBytes(StandardCharsets.UTF_8);
        private static final String PRIOR_IDENTITY = "journal-generation-identity";

        private final List<String> events = new ArrayList<>();
        private FaultAt fault;
        private int faultEventIndex = -1;
        private byte[] staged;
        private byte[] canonical;
        private String canonicalIdentity;
        private boolean stageExists;

        @Override
        public void createNew(String stage) throws IOException {
            events.add("create-new");
            if (fault == FaultAt.STAGE_CREATE) {
                throw fault("stage create");
            }
            if (stageExists) {
                throw new IOException("fixed stage already exists");
            }
            stageExists = true;
        }

        @Override
        public void write(String stage, byte[] bytes) throws IOException {
            events.add("write-stage");
            if (fault == FaultAt.WRITE) {
                staged = Arrays.copyOf(bytes, Math.max(1, bytes.length / 2));
                throw fault("stage write");
            }
            staged = bytes.clone();
        }

        @Override
        public void forceFile(String stage) throws IOException {
            events.add("force-file");
            if (fault == FaultAt.FILE_FORCE) {
                throw fault("file force");
            }
        }

        @Override
        public void closeStage(String stage) {
            events.add("close-stage");
        }

        @Override
        public void verifyDestination(
                String destination,
                AtomicConfigPublisher.DestinationExpectation expectation)
                throws IOException {
            events.add("destination-final-check");
            if (fault == FaultAt.DESTINATION_FINAL_CHECK) {
                throw fault("destination conflict");
            }
            if (expectation.state() == AtomicConfigPublisher.ExpectedState.ABSENT) {
                if (canonical != null) {
                    throw new IOException("destination is unexpectedly present");
                }
                return;
            }
            if (!Arrays.equals(canonical, expectation.priorBytes())
                    || !PRIOR_IDENTITY.equals(expectation.priorIdentity())
                    || !PRIOR_IDENTITY.equals(canonicalIdentity)) {
                throw new IOException("prior journal generation changed");
            }
        }

        @Override
        public void atomicMove(String stage, String destination)
                throws IOException {
            events.add("atomic-move");
            if (fault == FaultAt.MOVE_PRECOMMIT) {
                throw fault("atomic move before commit");
            }
            canonical = staged.clone();
            canonicalIdentity = "committed-identity";
            staged = null;
            stageExists = false;
            if (fault == FaultAt.MOVE_COMMITTED) {
                faultEventIndex = events.size() - 1;
                throw new AtomicConfigPublisher.CommittedMoveException(
                        "atomic move committed and reported failure");
            }
        }

        @Override
        public byte[] reopenNofollow(String destination) throws IOException {
            events.add("canonical-reopen");
            if (fault == FaultAt.CANONICAL_REOPEN) {
                throw fault("canonical reopen");
            }
            return canonical.clone();
        }

        @Override
        public void validate(String destination, byte[] expected)
                throws IOException {
            events.add("canonical-validate");
            if (fault == FaultAt.CANONICAL_VALIDATE) {
                throw fault("canonical validation");
            }
            if (!Arrays.equals(canonical, expected)) {
                throw new IOException("canonical bytes differ");
            }
        }

        @Override
        public void reparse(String destination, byte[] expected)
                throws IOException {
            events.add("canonical-reparse");
            if (fault == FaultAt.CANONICAL_REPARSE) {
                throw fault("canonical reparse");
            }
        }

        @Override
        public void forceDirectory() throws IOException {
            events.add("force-directory");
            if (fault == FaultAt.DIRECTORY_DURABILITY) {
                throw fault("directory durability");
            }
        }

        @Override
        public boolean stageExists(String stage) {
            events.add("stage-exists");
            return stageExists;
        }

        @Override
        public boolean canonicalMatches(String destination, byte[] expected) {
            events.add("canonical-matches");
            return Arrays.equals(canonical, expected);
        }

        private IOException fault(String operation) {
            faultEventIndex = events.size() - 1;
            return new IOException("synthetic " + operation + " fault");
        }
    }
}
