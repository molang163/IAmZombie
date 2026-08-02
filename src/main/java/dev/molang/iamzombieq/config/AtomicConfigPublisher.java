package dev.molang.iamzombieq.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

final class AtomicConfigPublisher {
    private final Port port;
    private final MigrationFaultInjector faults;
    private final MigrationTargetState.Phase phase;

    AtomicConfigPublisher(Port port) {
        this(
                port,
                MigrationFaultInjector.none(),
                MigrationTargetState.Phase.NO_EVIDENCE);
    }

    AtomicConfigPublisher(
            Port port,
            MigrationFaultInjector faults,
            MigrationTargetState.Phase phase) {
        this.port = Objects.requireNonNull(port, "port");
        this.faults = Objects.requireNonNull(faults, "faults");
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    Publication publish(Request request) {
        Objects.requireNonNull(request, "request");
        try {
            checkpoint(
                    request,
                    MigrationFaultInjector.Operation.STAGE_CREATE,
                    MigrationFaultInjector.Timing.BEFORE);
            port.createNew(request.stage());
            checkpoint(
                    request,
                    MigrationFaultInjector.Operation.STAGE_CREATE,
                    MigrationFaultInjector.Timing.AFTER);
            try {
                checkpoint(
                        request,
                        MigrationFaultInjector.Operation.WRITE,
                        MigrationFaultInjector.Timing.BEFORE);
                port.write(request.stage(), request.bytes());
                checkpoint(
                        request,
                        MigrationFaultInjector.Operation.WRITE,
                        MigrationFaultInjector.Timing.AFTER);
                checkpoint(
                        request,
                        MigrationFaultInjector.Operation.FILE_FORCE,
                        MigrationFaultInjector.Timing.BEFORE);
                port.forceFile(request.stage());
                checkpoint(
                        request,
                        MigrationFaultInjector.Operation.FILE_FORCE,
                        MigrationFaultInjector.Timing.AFTER);
            } finally {
                port.closeStage(request.stage());
            }

            checkpoint(
                    request,
                    MigrationFaultInjector.Operation.DESTINATION_CHECK,
                    MigrationFaultInjector.Timing.BEFORE);
            port.verifyDestination(
                    request.destination(), request.expectation());
            checkpoint(
                    request,
                    MigrationFaultInjector.Operation.DESTINATION_CHECK,
                    MigrationFaultInjector.Timing.AFTER);
            checkpoint(
                    request,
                    MigrationFaultInjector.Operation.ATOMIC_MOVE,
                    MigrationFaultInjector.Timing.BEFORE);
            port.atomicMove(request.stage(), request.destination());
            checkpoint(
                    request,
                    MigrationFaultInjector.Operation.ATOMIC_MOVE,
                    MigrationFaultInjector.Timing.AFTER);
            verifyCanonical(request);
            MigrationEvidence.Durability durability =
                    forceDirectoryIfRequired(request);
            return new Publication(
                    request.artifact(), request.destination(), durability);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Atomic publication failed for "
                            + request.artifact()
                            + " at "
                            + request.destination(),
                    failure);
        }
    }

    Publication resume(Request request) {
        Objects.requireNonNull(request, "request");
        try {
            if (port.stageExists(request.stage())) {
                throw new IllegalStateException(
                        "Fixed publication stage requires manual recovery: "
                                + request.stage());
            }
            if (!port.canonicalMatches(
                    request.destination(), request.bytes())) {
                throw new IllegalStateException(
                        "Canonical artifact is not the exact committed publication: "
                                + request.destination());
            }
            verifyCanonical(request);
            MigrationEvidence.Durability durability =
                    forceDirectoryIfRequired(request);
            return new Publication(
                    request.artifact(), request.destination(), durability);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Could not validate committed publication "
                            + request.destination(),
                    failure);
        }
    }

    private void verifyCanonical(Request request) throws IOException {
        checkpoint(
                request,
                MigrationFaultInjector.Operation.CANONICAL_REOPEN,
                MigrationFaultInjector.Timing.BEFORE);
        byte[] reopened = Objects.requireNonNull(
                port.reopenNofollow(request.destination()),
                "reopened canonical bytes");
        checkpoint(
                request,
                MigrationFaultInjector.Operation.CANONICAL_REOPEN,
                MigrationFaultInjector.Timing.AFTER);
        if (!Arrays.equals(reopened, request.bytes())) {
            throw new IllegalStateException(
                    "Canonical artifact bytes differ after atomic publication: "
                            + request.destination());
        }
        checkpoint(
                request,
                MigrationFaultInjector.Operation.CANONICAL_REPARSE,
                MigrationFaultInjector.Timing.BEFORE);
        port.reparse(request.destination(), request.bytes());
        checkpoint(
                request,
                MigrationFaultInjector.Operation.CANONICAL_REPARSE,
                MigrationFaultInjector.Timing.AFTER);
        checkpoint(
                request,
                MigrationFaultInjector.Operation.SCHEMA_SHA_VALIDATION,
                MigrationFaultInjector.Timing.BEFORE);
        port.validate(request.destination(), request.bytes());
        checkpoint(
                request,
                MigrationFaultInjector.Operation.SCHEMA_SHA_VALIDATION,
                MigrationFaultInjector.Timing.AFTER);
    }

    private MigrationEvidence.Durability forceDirectoryIfRequired(
            Request request) throws IOException {
        if (!request.strongRequired()) {
            return MigrationEvidence.Durability.BASIC;
        }
        checkpoint(
                request,
                MigrationFaultInjector.Operation.DIRECTORY_DURABILITY,
                MigrationFaultInjector.Timing.BEFORE);
        port.forceDirectory();
        checkpoint(
                request,
                MigrationFaultInjector.Operation.DIRECTORY_DURABILITY,
                MigrationFaultInjector.Timing.AFTER);
        return MigrationEvidence.Durability.STRONG;
    }

    private void checkpoint(
            Request request,
            MigrationFaultInjector.Operation operation,
            MigrationFaultInjector.Timing timing) {
        faults.inject(new MigrationFaultInjector.Point(
                phase, request.artifact(), operation, timing));
    }

    enum Artifact {
        JOURNAL,
        BACKUP,
        INITIAL,
        TARGET,
        MARKER
    }

    enum ExpectedState {
        ABSENT,
        EXACT_PRIOR
    }

    static final class DestinationExpectation {
        private final ExpectedState state;
        private final byte[] priorBytes;
        private final String priorIdentity;

        private DestinationExpectation(
                ExpectedState state, byte[] priorBytes, String priorIdentity) {
            this.state = Objects.requireNonNull(state, "state");
            this.priorBytes = priorBytes == null ? null : priorBytes.clone();
            this.priorIdentity = priorIdentity;
        }

        static DestinationExpectation absent() {
            return new DestinationExpectation(
                    ExpectedState.ABSENT, null, null);
        }

        static DestinationExpectation exactPrior(
                byte[] priorBytes, String priorIdentity) {
            Objects.requireNonNull(priorBytes, "priorBytes");
            Objects.requireNonNull(priorIdentity, "priorIdentity");
            if (priorIdentity.isBlank()) {
                throw new IllegalArgumentException(
                        "Prior destination identity must not be blank");
            }
            return new DestinationExpectation(
                    ExpectedState.EXACT_PRIOR, priorBytes, priorIdentity);
        }

        ExpectedState state() {
            return state;
        }

        byte[] priorBytes() {
            return priorBytes == null ? null : priorBytes.clone();
        }

        String priorIdentity() {
            return priorIdentity;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof DestinationExpectation that
                    && state == that.state
                    && Arrays.equals(priorBytes, that.priorBytes)
                    && Objects.equals(priorIdentity, that.priorIdentity);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(state, priorIdentity)
                    + Arrays.hashCode(priorBytes);
        }
    }

    record Request(
            Artifact artifact,
            String stage,
            String destination,
            byte[] bytes,
            DestinationExpectation expectation,
            boolean strongRequired) {

        Request {
            Objects.requireNonNull(artifact, "artifact");
            stage = MigrationDirectorySession.requireBasename(stage);
            destination =
                    MigrationDirectorySession.requireBasename(destination);
            if (stage.equals(destination)) {
                throw new IllegalArgumentException(
                        "Stage and canonical destination must differ");
            }
            bytes = Objects.requireNonNull(bytes, "bytes").clone();
            Objects.requireNonNull(expectation, "expectation");
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        Request withStrongRequired(boolean value) {
            return new Request(
                    artifact,
                    stage,
                    destination,
                    bytes,
                    expectation,
                    value);
        }
    }

    record Publication(
            Artifact artifact,
            String destination,
            MigrationEvidence.Durability durability) {
        Publication {
            Objects.requireNonNull(artifact, "artifact");
            destination =
                    MigrationDirectorySession.requireBasename(destination);
            Objects.requireNonNull(durability, "durability");
        }
    }

    interface Port {
        void createNew(String stage) throws IOException;

        void write(String stage, byte[] bytes) throws IOException;

        void forceFile(String stage) throws IOException;

        void closeStage(String stage) throws IOException;

        void verifyDestination(
                String destination, DestinationExpectation expectation)
                throws IOException;

        void atomicMove(String stage, String destination) throws IOException;

        byte[] reopenNofollow(String destination) throws IOException;

        void validate(String destination, byte[] expected) throws IOException;

        default void reparse(String destination, byte[] expected)
                throws IOException {}

        void forceDirectory() throws IOException;

        boolean stageExists(String stage) throws IOException;

        boolean canonicalMatches(String destination, byte[] expected)
                throws IOException;
    }

    static final class CommittedMoveException extends IOException {
        CommittedMoveException(String message) {
            super(message);
        }
    }
}
