package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationFailureContractTest {
    private static final Path ROOT =
            Path.of("/migration-failure-contract")
                    .toAbsolutePath()
                    .normalize();
    private static final Path GLOBAL = ROOT.resolve("config");
    private static final Path WORLD = ROOT.resolve("world/serverconfig");
    private static final Path LEGACY =
            GLOBAL.resolve(ActualTargetResolver.LEGACY_BASENAME);
    private static final Path WORLD_TARGET =
            WORLD.resolve(ActualTargetResolver.SERVER_BASENAME);
    private static final Path GLOBAL_TARGET =
            GLOBAL.resolve(ActualTargetResolver.SERVER_BASENAME);

    @Test
    void operationalFactoryRetainsPathsPhaseCauseAndRecoveryContract() {
        AccessDeniedException denied = new AccessDeniedException(
                WORLD_TARGET.toString(), null, "permission denied");
        IllegalStateException wrapper =
                new IllegalStateException("metadata wrapper", denied);

        MigrationFailure failure = MigrationFailure.operational(
                LEGACY,
                WORLD_TARGET,
                MigrationTargetState.Phase.PREPARED,
                "target",
                "nofollow-metadata",
                "Could not classify target metadata",
                wrapper);

        assertEquals(LEGACY, failure.legacy());
        assertEquals(WORLD_TARGET, failure.target());
        assertEquals(MigrationTargetState.Phase.PREPARED, failure.phase());
        assertEquals("target", failure.artifact());
        assertEquals("nofollow-metadata", failure.operation());
        assertTrue(failure.reason().contains("metadata wrapper"));
        assertTrue(failure.reason().contains("AccessDeniedException"));
        assertTrue(failure.reason().contains("permission denied"));
        assertSame(wrapper, failure.getCause());
        assertFalse(failure.synthetic());

        String rendered = failure.getMessage();
        for (String required : new String[] {
            LEGACY.toString(),
            WORLD_TARGET.toString(),
            "Phase: PREPARED",
            "Artifact: target",
            "Operation: nofollow-metadata",
            "Reason:",
            "Recovery:"
        }) {
            assertTrue(
                    rendered.contains(required),
                    () -> "missing failure-contract field " + required);
        }
        for (String step : new String[] {
            "Stop",
            "preserve",
            "SHA-256",
            "C1-F1-STOP-PRESERVE-v1",
            "C1-MANUAL-RECOVERY-v1",
            "https://github.com/molang163/IAmZombie/issues",
            "maintainer-signed",
            "Restart only after"
        }) {
            assertTrue(
                    failure.recovery().contains(step),
                    () -> "generic recovery omits step " + step);
        }
    }

    @Test
    void causeFreeLateWorldConflictNamesItsAbsoluteCandidateAndPhase() {
        AtomicInteger worldQueries = new AtomicInteger();
        ActualTargetResolver resolver = new ActualTargetResolver(path -> {
            if (path.equals(WORLD_TARGET)
                    && worldQueries.incrementAndGet() > 1) {
                return MigrationPathState.Observation.fromState(
                        MigrationPathState.PRESENT);
            }
            return MigrationPathState.Observation.fromState(
                    MigrationPathState.ABSENT);
        });
        ActualTargetResolver.Resolution resolution =
                resolver.resolveServer(GLOBAL, WORLD);

        MigrationFailure failure;
        try {
            resolution.worldGuard()
                    .orElseThrow()
                    .beforeSuccessfulReturn(
                            MigrationTargetState.Phase.TARGET_PUBLISHED);
            throw new AssertionError("late world conflict was accepted");
        } catch (MigrationFailure expected) {
            failure = expected;
        }

        assertEquals(LEGACY, failure.legacy());
        assertEquals(GLOBAL_TARGET, failure.target());
        assertEquals(
                MigrationTargetState.Phase.TARGET_PUBLISHED,
                failure.phase());
        assertTrue(failure.reason().contains(WORLD_TARGET.toString()));
        assertTrue(failure.getMessage().contains(WORLD_TARGET.toString()));
    }

    @Test
    void orphanProcedureNamesOneExactStageAndForbidsParentRetarget() {
        Path stage = GLOBAL.resolve(
                "iamzombieq-server.toml.iamzombieq-migration-v1.target.stage");
        String recovery =
                MigrationFailure.orphanStageRecovery(stage, GLOBAL_TARGET);

        assertTrue(recovery.contains(stage.toString()));
        assertTrue(recovery.contains(
                GLOBAL.resolveSibling("config.iamzombieq-recovery").toString()));
        assertTrue(recovery.contains("create the recovery directory"));
        assertTrue(recovery.contains("not a symlink"));
        assertTrue(recovery.contains("NOFOLLOW metadata"));
        assertTrue(recovery.contains("without overwrite"));
        assertTrue(recovery.contains("byte length"));
        assertTrue(recovery.contains("SHA-256"));
        assertTrue(recovery.contains(
                "remove only the original orphan fixed stage"));
        assertTrue(recovery.contains("do not rename, replace, or retarget"));
        assertTrue(recovery.contains("then restart"));

        int parentMetadata = recovery.indexOf("NOFOLLOW metadata");
        int parentCreation =
                recovery.indexOf("create the recovery directory");
        int evidenceAbsence =
                recovery.indexOf("Require the evidence path");
        int noOverwrite = recovery.indexOf("without overwrite");
        int copy = recovery.indexOf("copy the orphan fixed stage");
        int verify = recovery.indexOf("Then verify");
        int remove = recovery.indexOf(
                "remove only the original orphan fixed stage");
        int restart = recovery.indexOf("then restart");
        assertTrue(parentMetadata < parentCreation);
        assertTrue(parentCreation < evidenceAbsence);
        assertTrue(evidenceAbsence < noOverwrite);
        assertTrue(noOverwrite < copy);
        assertTrue(copy < verify);
        assertTrue(verify < remove);
        assertTrue(remove < restart);
    }

    @Test
    void orphanProcedureWorksWhenRecoveryDirectoryStartsAbsent(
            @TempDir Path temporary) throws IOException {
        Path targetParent = temporary.resolve("config");
        Files.createDirectory(targetParent);
        Path target = targetParent.resolve(
                ActualTargetResolver.SERVER_BASENAME);
        Path stage = targetParent.resolve(
                "iamzombieq-server.toml.iamzombieq-migration-v1.target.stage");
        byte[] evidence = "partial-stage".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(stage, evidence);
        Path recoveryParent = targetParent.resolveSibling(
                "config.iamzombieq-recovery");
        Path evidenceCopy = recoveryParent.resolve(stage.getFileName());
        assertFalse(Files.exists(recoveryParent, LinkOption.NOFOLLOW_LINKS));

        String recovery =
                MigrationFailure.orphanStageRecovery(stage, target);
        assertTrue(recovery.contains(recoveryParent.toString()));
        assertTrue(recovery.contains(evidenceCopy.toString()));
        Files.createDirectory(recoveryParent);
        assertTrue(Files.isDirectory(
                recoveryParent, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.isSymbolicLink(recoveryParent));
        Files.copy(stage, evidenceCopy, LinkOption.NOFOLLOW_LINKS);
        assertArrayEquals(evidence, Files.readAllBytes(evidenceCopy));
        assertArrayEquals(
                Files.readAllBytes(stage), Files.readAllBytes(evidenceCopy));
        Files.delete(stage);

        assertFalse(Files.exists(stage, LinkOption.NOFOLLOW_LINKS));
        assertArrayEquals(evidence, Files.readAllBytes(evidenceCopy));
    }

    @Test
    void genericRecoveryTerminatesSafelyUntilItsConditionIsResolved() {
        String recovery = MigrationFailure.OPERATOR_RECOVERY;

        assertTrue(recovery.contains("C1-F1-STOP-PRESERVE-v1"));
        assertTrue(recovery.contains("do not change the target, evidence, "
                + "parent, or ancestors"));
        assertTrue(recovery.contains("Restart only after"));
        assertTrue(recovery.contains("keep the server stopped"));
        assertFalse(recovery.contains("then restart."));
    }
}
