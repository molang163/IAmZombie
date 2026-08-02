package dev.molang.iamzombieq.internal.mount;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * Converts the server-visible swept segment into the coefficients used by the horizontal envelope.
 *
 * <p>The constants below are names for the exact formulas in 26.2
 * {@code LivingEntity#travelInAir}, {@code #travelInFluid}, and
 * {@code #getFrictionInfluencedSpeed}; none is an acceptance allowance. The scan follows every
 * voxel-face event interval of the continuously swept vehicle box and refuses unloaded or
 * custom-fluid context instead of guessing at it.
 */
final class SpiderVehicleMovementContext {
    static final double VANILLA_GROUND_ACCELERATION_NUMERATOR = 0.21600002F;
    static final double VANILLA_GROUND_DRAG_FACTOR = 0.91F;
    static final double VANILLA_AIR_ACCELERATION_FACTOR = 0.1F;
    static final double VANILLA_WATER_BASE_ACCELERATION = 0.02F;
    static final double VANILLA_WATER_BASE_SLOWDOWN = 0.8F;
    static final double VANILLA_WATER_SPRINT_SLOWDOWN = 0.9F;
    static final double VANILLA_WATER_EFFICIENT_SLOWDOWN = 0.54600006F;
    static final double VANILLA_DOLPHINS_GRACE_SLOWDOWN = 0.96F;
    static final double VANILLA_LAVA_ACCELERATION = 0.02F;
    static final double VANILLA_LAVA_SLOWDOWN = 0.5;
    static final double VANILLA_WATER_CURRENT_SCALE = 0.014;
    static final double VANILLA_FAST_LAVA_CURRENT_SCALE = 0.007;
    static final double VANILLA_SLOW_LAVA_CURRENT_SCALE =
            0.0023333333333333335;
    static final double VANILLA_CURRENT_MIN_IMPULSE =
            0.0045000000000000005;
    static final int VANILLA_YAW_TABLE_SIZE = 65_536;
    static final int VANILLA_YAW_COS_OFFSET = 16_384;
    static final double VANILLA_YAW_TABLE_SCALE = 10430.378350470453;
    static final double VANILLA_YAW_ROTATION_NORM_BOUND =
            computeYawRotationNormBound();

    private SpiderVehicleMovementContext() {}

    static Optional<SpiderVehicleHorizontalEnvelope.MotionBound> resolve(
            ServerLevel level,
            Spider spider,
            Vec3 candidate,
            float configuredSpeed) {
        if (!Float.isFinite(configuredSpeed)
                || configuredSpeed <= 0.0
                || !finite(candidate)) {
            return Optional.empty();
        }

        Vec3 current = spider.position();
        int viewDistance = level.getServer().getPlayerList().getViewDistance();
        int currentChunkX = Mth.floor(current.x) >> 4;
        int currentChunkZ = Mth.floor(current.z) >> 4;
        int candidateChunkX = Mth.floor(candidate.x) >> 4;
        int candidateChunkZ = Mth.floor(candidate.z) >> 4;
        if (Math.abs((long) candidateChunkX - currentChunkX) > viewDistance
                || Math.abs((long) candidateChunkZ - currentChunkZ) > viewDistance
                || candidate.y < level.getMinY()
                || candidate.y >= level.getMaxY()) {
            return Optional.empty();
        }

        Scan scan = scan(level, spider, current, candidate);
        if (scan == null) {
            return Optional.empty();
        }

        float waterEfficiency =
                (float)
                        spider.getAttributeValue(
                                Attributes.WATER_MOVEMENT_EFFICIENCY);
        float movementEfficiency =
                (float)
                        spider.getAttributeValue(
                                Attributes.MOVEMENT_EFFICIENCY);
        float frictionModifier =
                (float)
                        spider.getAttributeValue(
                                Attributes.FRICTION_MODIFIER);
        float airDragModifier =
                (float)
                        spider.getAttributeValue(
                                Attributes.AIR_DRAG_MODIFIER);
        double entityBounciness =
                spider.getAttributeValue(Attributes.BOUNCINESS);
        float swimSpeed =
                (float) spider.getAttributeValue(NeoForgeMod.SWIM_SPEED);
        return fromInputs(
                new FormulaInputs(
                        configuredSpeed,
                        scan.minFriction,
                        scan.maxFriction,
                        scan.maxSpeedFactor,
                        waterEfficiency,
                        movementEfficiency,
                        frictionModifier,
                        airDragModifier,
                        entityBounciness,
                        scan.maxBlockBounciness,
                        swimSpeed,
                        spider.shouldDiscardFriction(),
                        spider.hasEffect(MobEffects.DOLPHINS_GRACE),
                        spider.isPushedByFluid(),
                        scan.hasWater,
                        scan.hasLava,
                        level.environmentAttributes()
                                .getDimensionValue(
                                        EnvironmentAttributes.FAST_LAVA)));
    }

    static Optional<SpiderVehicleHorizontalEnvelope.MotionBound> fromInputs(
            FormulaInputs inputs) {
        if (!nonnegativeFinite(inputs.configuredSpeed)
                || inputs.configuredSpeed == 0.0
                || !nonnegativeFinite(inputs.minFriction)
                || inputs.minFriction == 0.0
                || !nonnegativeFinite(inputs.maxFriction)
                || !nonnegativeFinite(inputs.maxSpeedFactor)
                || !nonnegativeFinite(inputs.waterEfficiency)
                || inputs.waterEfficiency > 1.0
                || !nonnegativeFinite(inputs.movementEfficiency)
                || inputs.movementEfficiency > 1.0
                || !nonnegativeFinite(inputs.frictionModifier)
                || !nonnegativeFinite(inputs.airDragModifier)
                || !nonnegativeFinite(inputs.entityBounciness)
                || !nonnegativeFinite(inputs.maxBlockBounciness)
                || !nonnegativeFinite(inputs.swimSpeed)) {
            return Optional.empty();
        }

        float minFriction =
                computeModifiedFriction(
                        inputs.minFriction, inputs.frictionModifier);
        float maxFriction =
                computeModifiedFriction(
                        inputs.maxFriction, inputs.frictionModifier);
        if (minFriction == 0.0) {
            return Optional.empty();
        }
        float groundAcceleration =
                minFriction > 0.6
                        ? inputs.configuredSpeed
                                * ((float)
                                                VANILLA_GROUND_ACCELERATION_NUMERATOR
                                        / (minFriction
                                                * minFriction
                                                * minFriction))
                        : inputs.configuredSpeed;
        float airAcceleration =
                inputs.configuredSpeed
                        * (float) VANILLA_AIR_ACCELERATION_FACTOR;
        float waterAcceleration =
                waterAcceleration(
                        inputs.configuredSpeed,
                        inputs.waterEfficiency,
                        inputs.swimSpeed);
        double sourceAcceleration =
                maxFinite(
                        upperFloat(groundAcceleration),
                        upperFloat(airAcceleration),
                        upperFloat(waterAcceleration),
                        upperFloat((float) VANILLA_LAVA_ACCELERATION));
        double drivenAcceleration =
                upperProduct(
                        sourceAcceleration,
                        VANILLA_YAW_ROTATION_NORM_BOUND);
        double maxAcceleration =
                upperSum(
                        drivenAcceleration,
                        fluidCurrentAcceleration(inputs));
        if (!nonnegativeFinite(maxAcceleration)) {
            return Optional.empty();
        }

        float airDrag =
                computeModifiedFriction(
                        (float) VANILLA_GROUND_DRAG_FACTOR,
                        inputs.airDragModifier);
        float effectiveSpeedFactor =
                Mth.lerp(
                        inputs.movementEfficiency,
                        inputs.maxSpeedFactor,
                        1.0F);
        float groundDrag = maxFriction * airDrag;
        double groundRetention =
                upperProduct(groundDrag, effectiveSpeedFactor);
        double airRetention =
                upperProduct(
                        inputs.discardFriction ? 1.0F : airDrag,
                        effectiveSpeedFactor);
        // WATER_MOVEMENT_EFFICIENCY is halved off ground. Because it moves the
        // slowdown toward the smaller 0.54600006 value, the half-strength case
        // is the conservative source-derived retention.
        float halfWaterEfficiency =
                inputs.waterEfficiency * 0.5F;
        float ordinaryWaterRetention =
                Math.max(
                        waterSlowdown(
                                (float) VANILLA_WATER_BASE_SLOWDOWN,
                                halfWaterEfficiency),
                        waterSlowdown(
                                (float) VANILLA_WATER_SPRINT_SLOWDOWN,
                                halfWaterEfficiency));
        double waterRetention =
                upperProduct(
                        inputs.dolphinsGrace
                                ? (float) VANILLA_DOLPHINS_GRACE_SLOWDOWN
                                : ordinaryWaterRetention,
                        effectiveSpeedFactor);
        double lavaRetention =
                upperProduct(
                        VANILLA_LAVA_SLOWDOWN, effectiveSpeedFactor);
        double bounceRetention =
                upperProduct(
                        Math.max(
                                inputs.entityBounciness,
                                inputs.maxBlockBounciness),
                        effectiveSpeedFactor);
        double maxRetention =
                maxFinite(
                        groundRetention,
                        airRetention,
                        waterRetention,
                        lavaRetention,
                        bounceRetention);
        if (!nonnegativeFinite(maxRetention)) {
            return Optional.empty();
        }
        return Optional.of(
                new SpiderVehicleHorizontalEnvelope.MotionBound(
                        maxAcceleration, maxRetention));
    }

    private static Scan scan(
            ServerLevel level,
            Spider spider,
            Vec3 current,
            Vec3 candidate) {
        double dx = candidate.x - current.x;
        double dy = candidate.y - current.y;
        double dz = candidate.z - current.z;
        AABB origin = spider.getBoundingBox();
        double[] sampleFractions;
        try {
            sampleFractions =
                    SweptAabbVoxelSupercover.sampleFractions(
                            new SweptAabbVoxelSupercover.Box(
                                    origin.minX,
                                    origin.minY,
                                    origin.minZ,
                                    origin.maxX,
                                    origin.maxY,
                                    origin.maxZ),
                            dx,
                            dy,
                            dz);
        } catch (ArithmeticException | IllegalArgumentException failure) {
            return null;
        }

        Scan scan = new Scan();
        for (double fraction : sampleFractions) {
            AABB box =
                    origin.move(
                            dx * fraction,
                            dy * fraction,
                            dz * fraction);
            if (!scanBox(level, spider, box, scan)) {
                return null;
            }
        }
        return scan.complete() ? scan : null;
    }

    private static boolean scanBox(
            ServerLevel level, Spider spider, AABB box, Scan scan) {
        int minX = Mth.floor(box.minX);
        int minY = Math.max(level.getMinY(), Mth.floor(box.minY) - 1);
        int minZ = Mth.floor(box.minZ);
        int maxX = Mth.floor(Math.nextDown(box.maxX));
        int maxY =
                Math.min(
                        level.getMaxY() - 1,
                        Mth.floor(Math.nextDown(box.maxY)));
        int maxZ = Mth.floor(Math.nextDown(box.maxZ));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    if (!level.hasChunkAt(pos)) {
                        return false;
                    }
                    BlockState state = level.getBlockState(pos);
                    float friction = state.getFriction(level, pos, spider);
                    float speedFactor = state.getBlock().getSpeedFactor();
                    double blockBounciness =
                            state.getBounceRestitution(level, pos, spider);
                    if (!(friction > 0.0F)
                            || !Float.isFinite(friction)
                            || !(speedFactor >= 0.0F)
                            || !Float.isFinite(speedFactor)
                            || !nonnegativeFinite(blockBounciness)) {
                        return false;
                    }
                    scan.minFriction = Math.min(scan.minFriction, friction);
                    scan.maxFriction = Math.max(scan.maxFriction, friction);
                    scan.maxSpeedFactor =
                            Math.max(scan.maxSpeedFactor, speedFactor);
                    scan.maxBlockBounciness =
                            Math.max(
                                    scan.maxBlockBounciness,
                                    blockBounciness);

                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty()
                            && !fluid.is(FluidTags.WATER)
                            && !fluid.is(FluidTags.LAVA)) {
                        return false;
                    }
                    scan.hasWater |= fluid.is(FluidTags.WATER);
                    scan.hasLava |= fluid.is(FluidTags.LAVA);
                }
            }
        }
        return true;
    }

    private static double maxFinite(double... values) {
        double maximum = 0.0;
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return Double.NaN;
            }
            maximum = Math.max(maximum, value);
        }
        return maximum;
    }

    /**
     * Exact 26.2 LivingEntity.computeModifiedFriction formula.
     */
    private static float computeModifiedFriction(
            float friction, float modifier) {
        return Mth.clamp(
                1.0F - (1.0F - friction) * modifier,
                0.0F,
                1.0F);
    }

    private static float waterAcceleration(
            float configuredSpeed,
            float waterEfficiency,
            float swimSpeed) {
        float acceleration = (float) VANILLA_WATER_BASE_ACCELERATION;
        if (waterEfficiency > 0.0F) {
            acceleration +=
                    (configuredSpeed - acceleration)
                            * waterEfficiency;
        }
        acceleration *= swimSpeed;
        return acceleration;
    }

    private static float waterSlowdown(
            float initialSlowdown, float waterEfficiency) {
        float slowdown = initialSlowdown;
        if (waterEfficiency > 0.0F) {
            slowdown +=
                    ((float) VANILLA_WATER_EFFICIENT_SLOWDOWN - slowdown)
                            * waterEfficiency;
        }
        return slowdown;
    }

    private static double upperFloat(float value) {
        if (value == 0.0F || value == Float.POSITIVE_INFINITY) {
            return value;
        }
        return Math.nextUp((double) value);
    }

    private static double upperProduct(double left, double right) {
        if (left == 0.0 || right == 0.0) {
            return 0.0;
        }
        double product = left * right;
        return product == Double.POSITIVE_INFINITY
                ? product
                : Math.nextUp(product);
    }

    private static double upperSum(double left, double right) {
        if (left == 0.0) {
            return right;
        }
        if (right == 0.0) {
            return left;
        }
        double sum = left + right;
        return sum == Double.POSITIVE_INFINITY ? sum : Math.nextUp(sum);
    }

    private static double fluidCurrentAcceleration(FormulaInputs inputs) {
        if (!inputs.pushedByFluid) {
            return 0.0;
        }
        double bound = 0.0;
        if (inputs.waterPresent) {
            bound = Math.nextUp(VANILLA_WATER_CURRENT_SCALE);
        }
        if (inputs.lavaPresent) {
            double lava =
                    inputs.fastLava
                            ? VANILLA_FAST_LAVA_CURRENT_SCALE
                            : Math.max(
                                    VANILLA_SLOW_LAVA_CURRENT_SCALE,
                                    VANILLA_CURRENT_MIN_IMPULSE);
            bound = upperSum(bound, Math.nextUp(lava));
        }
        return bound;
    }

    /**
     * Exhausts the exact 65,536-entry float table construction used by
     * {@code Mth.SIN}. The yaw transform has matrix norm
     * {@code hypot(sin, cos)}; its largest quantized norm is enclosed upward
     * before it composes with the float acceleration result.
     */
    private static double computeYawRotationNormBound() {
        double maximum = 0.0;
        int mask = VANILLA_YAW_TABLE_SIZE - 1;
        for (int index = 0;
                index < VANILLA_YAW_TABLE_SIZE;
                index++) {
            float sin =
                    (float)
                            Math.sin(
                                    index
                                            / VANILLA_YAW_TABLE_SCALE);
            int cosineIndex =
                    (index + VANILLA_YAW_COS_OFFSET) & mask;
            float cos =
                    (float)
                            Math.sin(
                                    cosineIndex
                                            / VANILLA_YAW_TABLE_SCALE);
            maximum = Math.max(maximum, Math.hypot(sin, cos));
        }
        return Math.nextUp(maximum);
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static boolean nonnegativeFinite(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    private static final class Scan {
        private float minFriction = Float.POSITIVE_INFINITY;
        private float maxFriction;
        private float maxSpeedFactor;
        private double maxBlockBounciness;
        private boolean hasWater;
        private boolean hasLava;

        private boolean complete() {
            return Float.isFinite(minFriction)
                    && minFriction > 0.0
                    && Float.isFinite(maxFriction)
                    && Float.isFinite(maxSpeedFactor)
                    && Double.isFinite(maxBlockBounciness);
        }
    }

    record FormulaInputs(
            float configuredSpeed,
            float minFriction,
            float maxFriction,
            float maxSpeedFactor,
            float waterEfficiency,
            float movementEfficiency,
            float frictionModifier,
            float airDragModifier,
            double entityBounciness,
            double maxBlockBounciness,
            float swimSpeed,
            boolean discardFriction,
            boolean dolphinsGrace,
            boolean pushedByFluid,
            boolean waterPresent,
            boolean lavaPresent,
            boolean fastLava) {}
}
