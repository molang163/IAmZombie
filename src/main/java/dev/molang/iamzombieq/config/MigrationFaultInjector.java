package dev.molang.iamzombieq.config;

import java.util.Objects;

interface MigrationFaultInjector {
    void checkpoint(Point point);

    default void inject(Point point) {
        Objects.requireNonNull(point, "point");
        try {
            checkpoint(point);
        } catch (SyntheticFault failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new SyntheticFault(point, failure);
        }
    }

    static MigrationFaultInjector none() {
        return NoFaults.INSTANCE;
    }

    enum Operation {
        METADATA,
        PROFILE,
        LOCK_CREATE,
        LOCK_OPEN,
        LOCK_ACQUIRE,
        LOCK_TRY_LOCK,
        LOCK_IDENTITY,
        LOCK_READ,
        LOCK_VALIDATE,
        LOCK_PAYLOAD_VALIDATION,
        STAGE_CREATE,
        WRITE,
        FILE_FORCE,
        DESTINATION_CHECK,
        ATOMIC_MOVE,
        CANONICAL_REOPEN,
        CANONICAL_REPARSE,
        SCHEMA_SHA_VALIDATION,
        DIRECTORY_DURABILITY,
        MARKER_PUBLISH,
        BINDING_REVALIDATION
    }

    record Point(
            MigrationTargetState.Phase phase,
            AtomicConfigPublisher.Artifact artifact,
            Operation operation,
            Timing timing) {
        public Point {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(timing, "timing");
        }
    }

    enum Timing {
        BEFORE,
        AFTER
    }

    final class SyntheticFault extends IllegalStateException {
        private final Point point;

        SyntheticFault(Point point, RuntimeException cause) {
            super(
                    "synthetic migration fault at "
                            + Objects.requireNonNull(point, "point"),
                    Objects.requireNonNull(cause, "cause"));
            this.point = point;
        }

        Point point() {
            return point;
        }
    }

    enum NoFaults implements MigrationFaultInjector {
        INSTANCE;

        @Override
        public void checkpoint(Point point) {
            Objects.requireNonNull(point, "point");
        }
    }
}
