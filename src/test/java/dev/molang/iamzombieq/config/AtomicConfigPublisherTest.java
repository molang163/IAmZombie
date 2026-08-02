package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AtomicConfigPublisherTest {
    @Test
    void fileForcePrecedesExactlyOneAtomicMove() {
        for (AtomicConfigPublisher.Artifact artifact
                : AtomicConfigPublisher.Artifact.values()) {
            RecordingPort port = new RecordingPort();
            AtomicConfigPublisher.Publication result =
                    new AtomicConfigPublisher(port).publish(request(artifact));
            assertEquals(1, count(port.events, "force-file"));
            assertEquals(1, count(port.events, "atomic-move"));
            assertTrue(port.events.indexOf("force-file")
                    < port.events.indexOf("atomic-move"));
            assertEquals(MigrationEvidence.Durability.BASIC, result.durability());
        }
    }

    @Test
    void fileForceFaultInjectionBracketsTheActualForce() {
        for (MigrationFaultInjector.Timing timing
                : MigrationFaultInjector.Timing.values()) {
            RecordingPort port = new RecordingPort();
            MigrationFaultInjector injector = point -> {
                if (point.operation()
                                == MigrationFaultInjector.Operation.FILE_FORCE
                        && point.timing() == timing) {
                    throw new IllegalStateException(
                            "synthetic file-force " + timing);
                }
            };

            assertThrows(
                    MigrationFaultInjector.SyntheticFault.class,
                    () -> new AtomicConfigPublisher(
                                    port,
                                    injector,
                                    MigrationTargetState.Phase.PREPARED)
                            .publish(request(
                                    AtomicConfigPublisher.Artifact.BACKUP)));

            assertEquals(
                    timing == MigrationFaultInjector.Timing.BEFORE ? 0 : 1,
                    count(port.events, "force-file"));
            assertEquals(0, count(port.events, "atomic-move"));
        }
    }

    @Test
    void atomicMoveFailureHasNoFallbackOrSecondPublication() {
        RecordingPort port = new RecordingPort();
        port.moveFailure = new IOException("atomic move unsupported");
        assertThrows(
                IllegalStateException.class,
                () -> new AtomicConfigPublisher(port)
                        .publish(request(AtomicConfigPublisher.Artifact.TARGET)));
        assertEquals(1, count(port.events, "atomic-move"));
        assertEquals(0, count(port.events, "ordinary-move"));
        assertEquals(0, count(port.events, "copy-delete"));
    }

    @Test
    void destinationFinalCheckConflictsFailBeforeMoveForAllArtifacts() {
        for (AtomicConfigPublisher.Artifact artifact
                : AtomicConfigPublisher.Artifact.values()) {
            RecordingPort port = new RecordingPort();
            port.destinationConflict = true;
            assertThrows(
                    IllegalStateException.class,
                    () -> new AtomicConfigPublisher(port).publish(request(artifact)));
            assertEquals(0, count(port.events, "atomic-move"));
        }
    }

    @Test
    void journalGenerationAtomicallyReplacesExpectedPriorWithoutUnlink() {
        RecordingPort port = new RecordingPort();
        byte[] prior = "generation-one".getBytes(StandardCharsets.UTF_8);
        port.canonical = prior.clone();
        AtomicConfigPublisher.Request request = new AtomicConfigPublisher.Request(
                AtomicConfigPublisher.Artifact.JOURNAL,
                "journal.stage",
                "journal",
                "generation-two".getBytes(StandardCharsets.UTF_8),
                AtomicConfigPublisher.DestinationExpectation.exactPrior(
                        prior, "journal-identity-1"),
                false);
        new AtomicConfigPublisher(port).publish(request);

        assertEquals(0, count(port.events, "unlink"));
        assertEquals(0, count(port.events, "truncate-canonical"));
        assertEquals(0, count(port.events, "write-canonical"));
        assertEquals(1, count(port.events, "atomic-move"));
        assertEquals("generation-two", new String(port.canonical, StandardCharsets.UTF_8));
    }

    @Test
    void committedThenReportedFailedMoveNeverRepublishes() {
        RecordingPort port = new RecordingPort();
        port.commitThenFail = true;
        AtomicConfigPublisher publisher = new AtomicConfigPublisher(port);
        AtomicConfigPublisher.Request request =
                request(AtomicConfigPublisher.Artifact.MARKER);
        assertThrows(IllegalStateException.class, () -> publisher.publish(request));
        assertFalse(port.stageExists);
        assertTrue(Arrays.equals(request.bytes(), port.canonical));

        AtomicConfigPublisher.Publication resumed = publisher.resume(request);
        assertEquals(1, count(port.events, "atomic-move"));
        assertEquals(MigrationEvidence.Durability.BASIC, resumed.durability());
    }

    @Test
    void fixedStageOnRestartIsManualRecoveryAndMarkerCannotSelfCertifyStrong() {
        RecordingPort orphaned = new RecordingPort();
        orphaned.stageExists = true;
        assertThrows(
                IllegalStateException.class,
                () -> new AtomicConfigPublisher(orphaned)
                        .resume(request(AtomicConfigPublisher.Artifact.INITIAL)));
        assertEquals(0, count(orphaned.events, "atomic-move"));

        RecordingPort marker = new RecordingPort();
        AtomicConfigPublisher.Request strongMarker =
                request(AtomicConfigPublisher.Artifact.MARKER).withStrongRequired(true);
        marker.directoryForceFailure = new IOException("not durable");
        assertThrows(
                IllegalStateException.class,
                () -> new AtomicConfigPublisher(marker).publish(strongMarker));
        assertTrue(Arrays.equals(strongMarker.bytes(), marker.canonical));
        assertEquals(1, count(marker.events, "force-directory"));
    }

    private static AtomicConfigPublisher.Request request(
            AtomicConfigPublisher.Artifact artifact) {
        String name = artifact.name().toLowerCase(java.util.Locale.ROOT);
        return new AtomicConfigPublisher.Request(
                artifact,
                name + ".stage",
                name,
                ("bytes-" + name).getBytes(StandardCharsets.UTF_8),
                AtomicConfigPublisher.DestinationExpectation.absent(),
                false);
    }

    private static long count(List<String> values, String expected) {
        return values.stream().filter(expected::equals).count();
    }

    private static final class RecordingPort implements AtomicConfigPublisher.Port {
        private final List<String> events = new ArrayList<>();
        private byte[] staged;
        private byte[] canonical;
        private boolean stageExists;
        private boolean destinationConflict;
        private boolean commitThenFail;
        private IOException moveFailure;
        private IOException directoryForceFailure;

        @Override
        public void createNew(String stage) {
            events.add("create-new");
            stageExists = true;
        }

        @Override
        public void write(String stage, byte[] bytes) {
            events.add("write-stage");
            staged = bytes.clone();
        }

        @Override
        public void forceFile(String stage) {
            events.add("force-file");
        }

        @Override
        public void closeStage(String stage) {
            events.add("close-stage");
        }

        @Override
        public void verifyDestination(
                String destination,
                AtomicConfigPublisher.DestinationExpectation expectation) {
            events.add("destination-final-check");
            if (destinationConflict) {
                throw new IllegalStateException("destination conflict");
            }
        }

        @Override
        public void atomicMove(String stage, String destination) throws IOException {
            events.add("atomic-move");
            if (commitThenFail) {
                canonical = staged.clone();
                stageExists = false;
                throw new AtomicConfigPublisher.CommittedMoveException("reported failure");
            }
            if (moveFailure != null) {
                throw moveFailure;
            }
            canonical = staged.clone();
            stageExists = false;
        }

        @Override
        public byte[] reopenNofollow(String destination) {
            events.add("reopen-nofollow");
            return canonical.clone();
        }

        @Override
        public void validate(String destination, byte[] expected) {
            events.add("validate");
            if (!Arrays.equals(canonical, expected)) {
                throw new IllegalStateException("validation mismatch");
            }
        }

        @Override
        public void forceDirectory() throws IOException {
            events.add("force-directory");
            if (directoryForceFailure != null) {
                throw directoryForceFailure;
            }
        }

        @Override
        public boolean stageExists(String stage) {
            return stageExists;
        }

        @Override
        public boolean canonicalMatches(String destination, byte[] expected) {
            return Arrays.equals(canonical, expected);
        }
    }
}
