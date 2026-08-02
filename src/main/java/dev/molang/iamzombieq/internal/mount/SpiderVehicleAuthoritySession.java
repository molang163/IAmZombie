package dev.molang.iamzombieq.internal.mount;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.Vec3;

/**
 * Package-private runtime facade for the vehicle-envelope model.
 *
 * <p>The packet-listener mixin resolves these exact package-private signatures through a private
 * method-handle lookup. This keeps every model type outside the protected Mixin package without
 * adding a public JVM API.
 */
final class SpiderVehicleAuthoritySession {
    static final int ALLOW = 0;
    static final int REJECT = 1;
    static final int REBASE = 2;

    private final SpiderVehicleHorizontalEnvelope.Session model;
    private SpiderVehicleHorizontalEnvelope.Assessment pending;

    private SpiderVehicleAuthoritySession(
            SpiderVehicleHorizontalEnvelope.Session model) {
        this.model = model;
    }

    static SpiderVehicleAuthoritySession start(
            ServerLevel level, Spider spider, long monotonicNanos) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spider, "spider");
        Vec3 position = spider.position();
        return new SpiderVehicleAuthoritySession(
                SpiderVehicleHorizontalEnvelope.start(
                        clock(level, monotonicNanos),
                        position.x,
                        position.y,
                        position.z,
                        spider.getDeltaMovement().horizontalDistance()));
    }

    int assess(
            ServerLevel level,
            Spider spider,
            Vec3 candidate,
            float configuredSpeed,
            long monotonicNanos) {
        Objects.requireNonNull(candidate, "candidate");
        pending = null;
        SpiderVehicleHorizontalEnvelope.Clock clock =
                clock(level, monotonicNanos);
        var motion =
                SpiderVehicleMovementContext.resolve(
                        level, spider, candidate, configuredSpeed);
        if (motion.isEmpty()) {
            rebase(level, spider, monotonicNanos);
            return REBASE;
        }

        Vec3 serverPosition = spider.position();
        SpiderVehicleHorizontalEnvelope.Assessment assessment =
                model.assess(
                        new SpiderVehicleHorizontalEnvelope.Frame(
                                clock,
                                serverPosition.x,
                                serverPosition.y,
                                serverPosition.z,
                                candidate.x,
                                candidate.y,
                                candidate.z),
                        motion.orElseThrow());
        return switch (assessment.outcome()) {
            case ALLOW -> {
                pending = assessment;
                yield ALLOW;
            }
            case REJECT -> REJECT;
            case REBASE -> {
                rebase(level, spider, monotonicNanos);
                yield REBASE;
            }
        };
    }

    void commitAccepted() {
        SpiderVehicleHorizontalEnvelope.Assessment assessment =
                Objects.requireNonNull(
                        pending, "No admitted spider movement is pending");
        pending = null;
        model.commitAccepted(assessment);
    }

    void rebase(
            ServerLevel level, Spider spider, long monotonicNanos) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spider, "spider");
        double pendingVelocityBound =
                pending == null
                        ? 0.0
                        : pending.resultingVelocityBound();
        pending = null;
        Vec3 position = spider.position();
        double observedVelocityBound =
                Math.max(
                        pendingVelocityBound,
                        spider.getDeltaMovement()
                                .horizontalDistance());
        model.rebase(
                clock(level, monotonicNanos),
                position.x,
                position.y,
                position.z,
                observedVelocityBound);
    }

    boolean matchesAuthoritativePosition(Spider spider) {
        Vec3 position = spider.position();
        SpiderVehicleHorizontalEnvelope.Snapshot snapshot = model.snapshot();
        return same(position.x, snapshot.acceptedX())
                && same(position.y, snapshot.acceptedY())
                && same(position.z, snapshot.acceptedZ());
    }

    boolean hasPendingAdmission() {
        return pending != null;
    }

    private static SpiderVehicleHorizontalEnvelope.Clock clock(
            ServerLevel level, long monotonicNanos) {
        return new SpiderVehicleHorizontalEnvelope.Clock(
                level.getGameTime(), monotonicNanos);
    }

    private static boolean same(double left, double right) {
        return Double.doubleToLongBits(left)
                == Double.doubleToLongBits(right);
    }
}
