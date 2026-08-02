package dev.molang.iamzombieq.internal.mount;

import java.util.Objects;
import net.minecraft.SharedConstants;

/**
 * Source-derived horizontal rate envelope for an owner-controlled spider.
 *
 * <p>This type deliberately knows nothing about packets, connections, configuration holders, or
 * Minecraft entities. A caller supplies the conservative maximum acceleration and retention
 * derived from the swept server-visible movement context, assesses a candidate before vanilla's
 * move, and commits it only after vanilla accepted that candidate. Merely assessing or rejecting a
 * candidate never changes the authoritative position sample.
 */
final class SpiderVehicleHorizontalEnvelope {
    private static final long TICK_NANOS =
            Math.multiplyExact(
                    (long) SharedConstants.MILLIS_PER_TICK, 1_000_000L);

    private SpiderVehicleHorizontalEnvelope() {}

    static Session start(
            Clock origin,
            double serverAcceptedX,
            double serverAcceptedY,
            double serverAcceptedZ,
            double horizontalVelocityBound) {
        return new Session(
                Objects.requireNonNull(origin, "origin"),
                finite(serverAcceptedX, "serverAcceptedX"),
                finite(serverAcceptedY, "serverAcceptedY"),
                finite(serverAcceptedZ, "serverAcceptedZ"),
                nonnegativeFinite(
                        horizontalVelocityBound,
                        "horizontalVelocityBound"));
    }

    enum Outcome {
        ALLOW,
        REJECT,
        REBASE
    }

    record Clock(long serverTick, long monotonicNanos) {}

    record Frame(
            Clock clock,
            double serverX,
            double serverY,
            double serverZ,
            double candidateX,
            double candidateY,
            double candidateZ) {
        Frame {
            Objects.requireNonNull(clock, "clock");
            serverX = finite(serverX, "serverX");
            serverY = finite(serverY, "serverY");
            serverZ = finite(serverZ, "serverZ");
            candidateX = finite(candidateX, "candidateX");
            candidateY = finite(candidateY, "candidateY");
            candidateZ = finite(candidateZ, "candidateZ");
        }
    }

    /**
     * Maximum source-derived motion coefficients for every context in the swept horizontal
     * segment. Retention is the post-move velocity multiplier, not an additive tolerance.
     */
    record MotionBound(
            double maxAccelerationPerQuantum, double maxRetention) {
        MotionBound {
            maxAccelerationPerQuantum =
                    nonnegativeFinite(
                            maxAccelerationPerQuantum,
                            "maxAccelerationPerQuantum");
            maxRetention =
                    nonnegativeFinite(maxRetention, "maxRetention");
        }
    }

    record Snapshot(
            Clock origin,
            Clock lastObservedClock,
            Clock lastAcceptedClock,
            long mintedTotal,
            long consumedTotal,
            long carry,
            double acceptedX,
            double acceptedY,
            double acceptedZ,
            double velocityBound) {}

    static final class Assessment {
        private final Session owner;
        private final Outcome outcome;
        private final Frame frame;
        private final long generation;
        private final long consumedAtAssessment;
        private final long requiredQuanta;
        private final long mintedTotal;
        private final long carryBeforeCommit;
        private final double resultingVelocityBound;

        private Assessment(
                Session owner,
                Outcome outcome,
                Frame frame,
                long generation,
                long consumedAtAssessment,
                long requiredQuanta,
                long mintedTotal,
                long carryBeforeCommit,
                double resultingVelocityBound) {
            this.owner = owner;
            this.outcome = outcome;
            this.frame = frame;
            this.generation = generation;
            this.consumedAtAssessment = consumedAtAssessment;
            this.requiredQuanta = requiredQuanta;
            this.mintedTotal = mintedTotal;
            this.carryBeforeCommit = carryBeforeCommit;
            this.resultingVelocityBound = resultingVelocityBound;
        }

        Outcome outcome() {
            return outcome;
        }

        long requiredQuanta() {
            return requiredQuanta;
        }

        long mintedTotal() {
            return mintedTotal;
        }

        long carryBeforeCommit() {
            return carryBeforeCommit;
        }

        double resultingVelocityBound() {
            return resultingVelocityBound;
        }
    }

    static final class Session {
        private Clock origin;
        private Clock lastObservedClock;
        private Clock lastAcceptedClock;
        private long mintedTotal;
        private long consumedTotal;
        private double acceptedX;
        private double acceptedY;
        private double acceptedZ;
        private double velocityBound;
        private long generation;

        private Session(
                Clock origin,
                double acceptedX,
                double acceptedY,
                double acceptedZ,
                double velocityBound) {
            requireUsableOrigin(origin);
            this.origin = origin;
            this.lastObservedClock = origin;
            this.lastAcceptedClock = origin;
            this.acceptedX = acceptedX;
            this.acceptedY = acceptedY;
            this.acceptedZ = acceptedZ;
            this.velocityBound = velocityBound;
        }

        Assessment assess(Frame frame, MotionBound motion) {
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(motion, "motion");

            if (!same(frame.serverX(), acceptedX)
                    || !same(frame.serverY(), acceptedY)
                    || !same(frame.serverZ(), acceptedZ)) {
                return decision(
                        Outcome.REBASE, frame, 0, carry(), velocityBound);
            }

            ClockTotals totals = totals(frame.clock());
            if (totals == null) {
                return decision(
                        Outcome.REBASE, frame, 0, carry(), velocityBound);
            }

            lastObservedClock = frame.clock();
            mintedTotal = totals.mintedTotal();
            long available = carry();
            double dx = frame.candidateX() - frame.serverX();
            double dz = frame.candidateZ() - frame.serverZ();
            double distance = Math.hypot(dx, dz);
            if (!Double.isFinite(distance)) {
                return decision(
                        Outcome.REBASE, frame, 0, available, velocityBound);
            }

            MotionTransform oneQuantum =
                    MotionTransform.oneQuantum(motion);
            long required =
                    minimumCoveringQuanta(
                            distance,
                            available,
                            velocityBound,
                            oneQuantum);
            if (required < 0) {
                return decision(
                        Outcome.REJECT, frame, 0, available, velocityBound);
            }
            if (required == 0) {
                // A real accepted vehicle packet represents at least one
                // client simulation slot even when its displacement is zero.
                // Consuming that slot prevents a stream of stationary packets
                // from preserving every elapsed quantum for a same-tick
                // movement burst, while untouched elapsed time still remains
                // available for a genuinely delayed packet batch. A same-tick
                // duplicate with no available slot and no displacement remains
                // harmless and does not manufacture or consume credit.
                required = available == 0 ? 0 : 1;
            }

            MotionTransform consumedMotion =
                    oneQuantum.repeat(required);
            double nextVelocity =
                    distance == 0.0
                            ? consumedMotion.retainedVelocityAfter(
                                    velocityBound)
                            : consumedMotion.velocityAfter(velocityBound);
            return decision(
                    Outcome.ALLOW,
                    frame,
                    required,
                    available,
                    nextVelocity);
        }

        void commitAccepted(Assessment assessment) {
            Objects.requireNonNull(assessment, "assessment");
            if (assessment.owner != this) {
                throw new IllegalArgumentException(
                        "Assessment belongs to another envelope session");
            }
            if (assessment.outcome != Outcome.ALLOW) {
                throw new IllegalStateException(
                        "Only an allowed assessment can be committed");
            }
            if (assessment.generation != generation
                    || assessment.consumedAtAssessment != consumedTotal
                    || assessment.mintedTotal != mintedTotal
                    || !assessment.frame.clock().equals(lastObservedClock)) {
                throw new IllegalStateException(
                        "Assessment is stale relative to the accepted sample");
            }

            long nextConsumed;
            try {
                nextConsumed =
                        Math.addExact(
                                consumedTotal,
                                assessment.requiredQuanta);
            } catch (ArithmeticException overflow) {
                throw new IllegalStateException(
                        "Consumed movement quanta overflowed", overflow);
            }
            if (nextConsumed > mintedTotal) {
                throw new IllegalStateException(
                        "Assessment consumes movement time that was not minted");
            }

            consumedTotal = nextConsumed;
            acceptedX = assessment.frame.candidateX();
            acceptedY = assessment.frame.candidateY();
            acceptedZ = assessment.frame.candidateZ();
            lastAcceptedClock = assessment.frame.clock();
            velocityBound = assessment.resultingVelocityBound;
            generation = Math.incrementExact(generation);
        }

        void rebase(
                Clock newOrigin,
                double serverAcceptedX,
                double serverAcceptedY,
                double serverAcceptedZ,
                double observedHorizontalVelocityBound) {
            Objects.requireNonNull(newOrigin, "newOrigin");
            requireUsableOrigin(newOrigin);
            double observedVelocity =
                    nonnegativeFinite(
                            observedHorizontalVelocityBound,
                            "observedHorizontalVelocityBound");
            origin = newOrigin;
            lastObservedClock = newOrigin;
            lastAcceptedClock = newOrigin;
            mintedTotal = 0;
            consumedTotal = 0;
            acceptedX = finite(serverAcceptedX, "serverAcceptedX");
            acceptedY = finite(serverAcceptedY, "serverAcceptedY");
            acceptedZ = finite(serverAcceptedZ, "serverAcceptedZ");
            velocityBound = Math.max(velocityBound, observedVelocity);
            generation = Math.incrementExact(generation);
        }

        Snapshot snapshot() {
            return new Snapshot(
                    origin,
                    lastObservedClock,
                    lastAcceptedClock,
                    mintedTotal,
                    consumedTotal,
                    carry(),
                    acceptedX,
                    acceptedY,
                    acceptedZ,
                    velocityBound);
        }

        private Assessment decision(
                Outcome outcome,
                Frame frame,
                long requiredQuanta,
                long available,
                double resultingVelocityBound) {
            return new Assessment(
                    this,
                    outcome,
                    frame,
                    generation,
                    consumedTotal,
                    requiredQuanta,
                    mintedTotal,
                    available,
                    resultingVelocityBound);
        }

        private ClockTotals totals(Clock current) {
            if (current.serverTick() < origin.serverTick()
                    || current.serverTick()
                            < lastObservedClock.serverTick()
                    || current.monotonicNanos()
                            < lastObservedClock.monotonicNanos()) {
                return null;
            }

            long serverDelta;
            long monotonicDelta;
            try {
                serverDelta =
                        Math.subtractExact(
                                current.serverTick(),
                                origin.serverTick());
                monotonicDelta =
                        Math.subtractExact(
                                current.monotonicNanos(),
                                origin.monotonicNanos());
            } catch (ArithmeticException overflow) {
                return null;
            }
            if (serverDelta < 0 || monotonicDelta < 0) {
                return null;
            }

            long wallQuanta;
            try {
                wallQuanta = ceilDiv(monotonicDelta, TICK_NANOS);
            } catch (ArithmeticException overflow) {
                return null;
            }
            long representableServerQuanta =
                    wallQuanta == Long.MAX_VALUE
                            ? Long.MAX_VALUE
                            : wallQuanta + 1;
            if (serverDelta > representableServerQuanta) {
                // The cumulative ceiling plus one boundary at the unknown
                // origin phase is the complete source-derived allowance. A
                // server counter beyond it is a tick discontinuity, not
                // movement credit.
                return null;
            }
            long currentMinted = Math.max(serverDelta, wallQuanta);
            if (currentMinted < mintedTotal
                    || currentMinted < consumedTotal) {
                return null;
            }
            return new ClockTotals(currentMinted);
        }

        private long carry() {
            return mintedTotal - consumedTotal;
        }
    }

    private record ClockTotals(long mintedTotal) {}

    /**
     * Nonnegative affine upper bound for repeated vanilla horizontal movement:
     *
     * <pre>
     * displacement = velocity + acceleration
     * nextVelocity = displacement * retention
     * </pre>
     *
     * Composition uses only upward-rounded additions and multiplications. It therefore avoids
     * subtractive cancellation and remains conservative in O(log quanta), including very large
     * banked-time values.
     */
    private record MotionTransform(
            double velocityCoefficient,
            double velocityConstant,
            double distanceCoefficient,
            double distanceConstant) {
        private static final MotionTransform IDENTITY =
                new MotionTransform(1.0, 0.0, 0.0, 0.0);

        static MotionTransform oneQuantum(MotionBound motion) {
            double acceleration =
                    motion.maxAccelerationPerQuantum();
            double retention = motion.maxRetention();
            return new MotionTransform(
                    retention,
                    upMultiply(retention, acceleration),
                    1.0,
                    acceleration);
        }

        MotionTransform repeat(long quanta) {
            if (quanta < 0) {
                throw new IllegalArgumentException(
                        "Movement quanta cannot be negative: " + quanta);
            }
            MotionTransform result = IDENTITY;
            MotionTransform power = this;
            long remaining = quanta;
            while (remaining != 0) {
                if ((remaining & 1L) != 0) {
                    result = compose(power, result);
                }
                remaining >>>= 1;
                if (remaining != 0) {
                    power = compose(power, power);
                }
            }
            return result;
        }

        double distanceFor(double initialVelocity) {
            double raw =
                    upAdd(
                            upMultiply(
                                    distanceCoefficient,
                                    initialVelocity),
                            distanceConstant);
            return expandOneUlp(raw);
        }

        double velocityAfter(double initialVelocity) {
            return upAdd(
                    upMultiply(
                            velocityCoefficient, initialVelocity),
                    velocityConstant);
        }

        double retainedVelocityAfter(double initialVelocity) {
            return upMultiply(
                    velocityCoefficient, initialVelocity);
        }

        private static MotionTransform compose(
                MotionTransform after, MotionTransform before) {
            double nextVelocityCoefficient =
                    upMultiply(
                            after.velocityCoefficient,
                            before.velocityCoefficient);
            double nextVelocityConstant =
                    upAdd(
                            upMultiply(
                                    after.velocityCoefficient,
                                    before.velocityConstant),
                            after.velocityConstant);
            double nextDistanceCoefficient =
                    upAdd(
                            before.distanceCoefficient,
                            upMultiply(
                                    after.distanceCoefficient,
                                    before.velocityCoefficient));
            double nextDistanceConstant =
                    upAdd(
                            before.distanceConstant,
                            upAdd(
                                    upMultiply(
                                            after.distanceCoefficient,
                                            before.velocityConstant),
                                    after.distanceConstant));
            return new MotionTransform(
                    nextVelocityCoefficient,
                    nextVelocityConstant,
                    nextDistanceCoefficient,
                    nextDistanceConstant);
        }
    }

    private static long minimumCoveringQuanta(
            double distance,
            long available,
            double initialVelocity,
            MotionTransform oneQuantum) {
        if (distance == 0.0) {
            return 0;
        }
        if (available == 0
                || distance
                        > oneQuantum
                                .repeat(available)
                                .distanceFor(initialVelocity)) {
            return -1;
        }

        long low = 1;
        long high = available;
        while (low < high) {
            long middle = low + ((high - low) >>> 1);
            double bound =
                    oneQuantum
                            .repeat(middle)
                            .distanceFor(initialVelocity);
            if (distance <= bound) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    private static long ceilDiv(long dividend, long divisor) {
        long quotient = dividend / divisor;
        if (dividend % divisor == 0) {
            return quotient;
        }
        return Math.incrementExact(quotient);
    }

    private static double upAdd(double left, double right) {
        if (left == 0.0) {
            return right;
        }
        if (right == 0.0) {
            return left;
        }
        return expandOneUlp(left + right);
    }

    private static double upMultiply(double left, double right) {
        if (left == 0.0 || right == 0.0) {
            return 0.0;
        }
        if (left == 1.0) {
            return right;
        }
        if (right == 1.0) {
            return left;
        }
        return expandOneUlp(left * right);
    }

    private static double expandOneUlp(double value) {
        if (value == 0.0 || value == Double.POSITIVE_INFINITY) {
            return value;
        }
        return Math.nextUp(value);
    }

    private static void requireUsableOrigin(Clock origin) {
        if (origin.serverTick() < 0) {
            throw new IllegalArgumentException(
                    "Origin server tick cannot be negative: "
                            + origin.serverTick());
        }
    }

    private static boolean same(double left, double right) {
        return Double.doubleToLongBits(left)
                == Double.doubleToLongBits(right);
    }

    private static double finite(double value, String description) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    description + " must be finite: " + value);
        }
        return value;
    }

    private static double nonnegativeFinite(
            double value, String description) {
        finite(value, description);
        if (value < 0.0) {
            throw new IllegalArgumentException(
                    description + " cannot be negative: " + value);
        }
        return value;
    }
}
