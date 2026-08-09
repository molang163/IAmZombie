package dev.molang.iamzombieq.internal.mount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

class SpiderVehicleMovementContextTest {
    @Test
    void groundFormulaUsesTheFastestObservedAccelerationAndRetention() {
        SpiderVehicleHorizontalEnvelope.MotionBound bound =
                resolve(inputs(0.3, 0.6, 0.98, 1.0, 0.0, 1.0, false, false));

        float friction = 0.6F;
        float expectedAcceleration =
                0.3F
                        * ((float)
                                        SpiderVehicleMovementContext
                                                .VANILLA_GROUND_ACCELERATION_NUMERATOR
                                / (friction * friction * friction));
        float expectedRetention =
                (float)
                        SpiderVehicleMovementContext
                                .VANILLA_GROUND_DRAG_FACTOR;
        assertTrue(
                bound.maxAccelerationPerQuantum()
                        >= expectedAcceleration);
        assertEquals(
                Math.nextUp((double) expectedRetention),
                bound.maxRetention());
    }

    @Test
    void waterAttributesAndDolphinsGraceAreAppliedWithoutChosenAllowance() {
        SpiderVehicleHorizontalEnvelope.MotionBound bound =
                resolve(inputs(0.5, 1.0, 0.6, 1.0, 1.0, 2.0, false, true));

        assertTrue(bound.maxAccelerationPerQuantum() >= 1.0);
        assertEquals(
                Math.nextUp(
                        (double)
                                (float)
                                        SpiderVehicleMovementContext
                                                .VANILLA_DOLPHINS_GRACE_SLOWDOWN),
                bound.maxRetention());
    }

    @Test
    void sourceSpeedFactorAboveOneRemainsConservativeInsteadOfBeingClamped() {
        SpiderVehicleHorizontalEnvelope.MotionBound bound =
                resolve(inputs(0.3, 0.6, 0.6, 1.25, 0.0, 1.0, true, false));

        assertEquals(Math.nextUp(1.25), bound.maxRetention());
    }

    @Test
    void frictionAirDragAndMovementEfficiencyUseTheExactLivingEntityTransforms() {
        SpiderVehicleHorizontalEnvelope.MotionBound bound =
                resolve(
                        inputs(
                                0.3,
                                0.6,
                                0.6,
                                0.4,
                                0.0,
                                1.0,
                                0.0,
                                0.0,
                                0.0,
                                0.0,
                                1.0,
                                false,
                                false));

        assertTrue(
                bound.maxAccelerationPerQuantum()
                        >= 0.3F
                                * (float)
                                        SpiderVehicleMovementContext
                                                .VANILLA_GROUND_ACCELERATION_NUMERATOR);
        assertEquals(
                Math.nextUp(1.0),
                bound.maxRetention(),
                "modifier=0 makes air drag one and movementEfficiency=1 removes the slow-block factor");
    }

    @Test
    void observableEntityAndBlockRestitutionBoundHorizontalBounce() {
        SpiderVehicleHorizontalEnvelope.MotionBound entityBounce =
                resolve(
                        inputs(
                                0.3,
                                0.6,
                                0.6,
                                1.0,
                                0.0,
                                0.0,
                                1.0,
                                1.0,
                                1.0,
                                0.0,
                                1.0,
                                false,
                                false));
        SpiderVehicleHorizontalEnvelope.MotionBound blockBounce =
                resolve(
                        inputs(
                                0.3,
                                0.6,
                                0.6,
                                1.0,
                                0.0,
                                0.0,
                                1.0,
                                1.0,
                                0.0,
                                1.25,
                                1.0,
                                false,
                                false));

        assertEquals(Math.nextUp(1.0), entityBounce.maxRetention());
        assertEquals(Math.nextUp(1.25), blockBounce.maxRetention());
    }

    @Test
    void pre262NativeInputsKeepLowFrictionCubicAndNeutralHorizontalBounce() {
        SpiderVehicleHorizontalEnvelope.MotionBound modern =
                resolve(lowFrictionInputs(false));
        SpiderVehicleHorizontalEnvelope.MotionBound legacy =
                resolve(lowFrictionInputs(true));

        float friction = 0.4F;
        float legacyGroundAcceleration =
                0.3F
                        * ((float)
                                        SpiderVehicleMovementContext
                                                .VANILLA_GROUND_ACCELERATION_NUMERATOR
                                / (friction * friction * friction));
        assertEquals(
                upperDriven(0.3F),
                modern.maxAccelerationPerQuantum(),
                "26.2 keeps its <=0.6 configured-speed branch");
        assertEquals(
                upperDriven(legacyGroundAcceleration),
                legacy.maxAccelerationPerQuantum(),
                "pre-26.2 always uses the native cubic ground-speed formula");
        assertTrue(
                legacy.maxAccelerationPerQuantum()
                        > modern.maxAccelerationPerQuantum());
        assertEquals(
                Math.nextUp(
                        (double)
                                (float)
                                        SpiderVehicleMovementContext
                                                .VANILLA_GROUND_DRAG_FACTOR),
                legacy.maxRetention(),
                "neutral legacy drag and zero horizontal restitution add no invented retention");
    }

    @Test
    void invalidDynamicContextIsRejectedInsteadOfInventingFallbackCoefficients() {
        assertTrue(
                SpiderVehicleMovementContext.fromInputs(
                                inputs(
                                        0.3,
                                        0.0,
                                        0.6,
                                        1.0,
                                        0.0,
                                        1.0,
                                        false,
                                        false))
                        .isEmpty());
    }

    @Test
    void defaultBoundCoversTheExactVanillaFloatEvaluationOrder() {
        assertFloatRecurrenceIsCovered(
                0.30F,
                0.60F,
                0.98F,
                1.0F,
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                1.0F,
                false,
                false);
    }

    @Test
    void boundaryBoundCoversFloatCastsProductsAndLerps() {
        assertFloatRecurrenceIsCovered(
                1.0F,
                Math.nextUp(0.60F),
                Math.nextDown(1.0F),
                Math.nextDown(1.25F),
                Math.nextDown(1.0F),
                Math.nextDown(1.0F),
                Math.nextUp(0.0F),
                Math.nextDown(1.0F),
                Math.nextDown(2.0F),
                true,
                false);
    }

    @Test
    void yawRotationBoundExhaustsTheVanillaFloatSinCosTable() {
        double maximum = 0.0;
        int mask =
                SpiderVehicleMovementContext.VANILLA_YAW_TABLE_SIZE
                        - 1;
        for (int index = 0;
                index
                        < SpiderVehicleMovementContext
                                .VANILLA_YAW_TABLE_SIZE;
                index++) {
            float sin =
                    (float)
                            Math.sin(
                                    index
                                            / SpiderVehicleMovementContext
                                                    .VANILLA_YAW_TABLE_SCALE);
            int cosineIndex =
                    (index
                                            + SpiderVehicleMovementContext
                                                    .VANILLA_YAW_COS_OFFSET)
                                    & mask;
            float cos =
                    (float)
                            Math.sin(
                                    cosineIndex
                                            / SpiderVehicleMovementContext
                                                    .VANILLA_YAW_TABLE_SCALE);
            double norm = Math.hypot(sin, cos);
            maximum = Math.max(maximum, norm);
            assertTrue(
                    SpiderVehicleMovementContext
                                    .VANILLA_YAW_ROTATION_NORM_BOUND
                            >= norm);
        }

        assertEquals(
                Math.nextUp(maximum),
                SpiderVehicleMovementContext
                        .VANILLA_YAW_ROTATION_NORM_BOUND);
        assertTrue(
                SpiderVehicleMovementContext
                                .VANILLA_YAW_ROTATION_NORM_BOUND
                        > 1.0,
                "quantized sin/cos can enlarge a normalized input");
    }

    @Test
    void fluidCurrentMatrixAddsOnlySourceDerivedNormalizedImpulses() {
        double dry =
                resolve(fluidInputs(true, false, false, false))
                        .maxAccelerationPerQuantum();
        double notPushed =
                resolve(fluidInputs(false, true, true, true))
                        .maxAccelerationPerQuantum();
        double water =
                resolve(fluidInputs(true, true, false, false))
                        .maxAccelerationPerQuantum();
        double slowLava =
                resolve(fluidInputs(true, false, true, false))
                        .maxAccelerationPerQuantum();
        double fastLava =
                resolve(fluidInputs(true, false, true, true))
                        .maxAccelerationPerQuantum();
        double both =
                resolve(fluidInputs(true, true, true, true))
                        .maxAccelerationPerQuantum();

        assertEquals(dry, notPushed);
        double waterCurrent =
                Math.nextUp(
                        SpiderVehicleMovementContext
                                .VANILLA_WATER_CURRENT_SCALE);
        double slowLavaCurrent =
                Math.nextUp(
                        SpiderVehicleMovementContext
                                .VANILLA_CURRENT_MIN_IMPULSE);
        double fastLavaCurrent =
                Math.nextUp(
                        SpiderVehicleMovementContext
                                .VANILLA_FAST_LAVA_CURRENT_SCALE);
        assertEquals(upperSum(dry, waterCurrent), water);
        assertEquals(
                upperSum(dry, slowLavaCurrent),
                slowLava,
                "slow lava uses EntityFluidInteraction's 0.0045 floor");
        assertEquals(upperSum(dry, fastLavaCurrent), fastLava);
        assertEquals(
                upperSum(
                        dry,
                        upperSum(
                                waterCurrent,
                                fastLavaCurrent)),
                both,
                "water and lava trackers apply independently and may stack");
    }

    private static SpiderVehicleMovementContext.FormulaInputs fluidInputs(
            boolean pushedByFluid,
            boolean water,
            boolean lava,
            boolean fastLava) {
        return new SpiderVehicleMovementContext.FormulaInputs(
                0.30F,
                1.0F,
                1.0F,
                1.0F,
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                0.0,
                0.0,
                1.0F,
                false,
                false,
                pushedByFluid,
                water,
                lava,
                false,
                fastLava);
    }

    private static SpiderVehicleMovementContext.FormulaInputs lowFrictionInputs(
            boolean pre262NativeMotion) {
        return new SpiderVehicleMovementContext.FormulaInputs(
                0.3F,
                0.4F,
                0.4F,
                1.0F,
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                0.0,
                0.0,
                1.0F,
                false,
                false,
                false,
                false,
                false,
                pre262NativeMotion,
                false);
    }

    private static void assertFloatRecurrenceIsCovered(
            float configuredSpeed,
            float minFriction,
            float maxFriction,
            float maxSpeedFactor,
            float waterEfficiency,
            float movementEfficiency,
            float frictionModifier,
            float airDragModifier,
            float swimSpeed,
            boolean discardFriction,
            boolean dolphinsGrace) {
        SpiderVehicleHorizontalEnvelope.MotionBound bound =
                resolve(
                        inputs(
                                configuredSpeed,
                                minFriction,
                                maxFriction,
                                maxSpeedFactor,
                                waterEfficiency,
                                movementEfficiency,
                                frictionModifier,
                                airDragModifier,
                                0.0,
                                0.0,
                                swimSpeed,
                                discardFriction,
                                dolphinsGrace));

        float modifiedMin =
                modifiedFriction(minFriction, frictionModifier);
        float modifiedMax =
                modifiedFriction(maxFriction, frictionModifier);
        float groundAcceleration =
                modifiedMin > 0.6
                        ? configuredSpeed
                                * (0.21600002F
                                        / (modifiedMin
                                                * modifiedMin
                                                * modifiedMin))
                        : configuredSpeed;
        float airAcceleration = configuredSpeed * 0.1F;
        float waterAcceleration = 0.02F;
        if (waterEfficiency > 0.0F) {
            waterAcceleration +=
                    (configuredSpeed - waterAcceleration)
                            * waterEfficiency;
        }
        waterAcceleration *= swimSpeed;
        double actualAcceleration =
                Math.max(
                        Math.max(
                                groundAcceleration,
                                airAcceleration),
                        Math.max(waterAcceleration, 0.02F));

        float airDrag =
                modifiedFriction(0.91F, airDragModifier);
        float blockSpeedFactor =
                Mth.lerp(
                        movementEfficiency,
                        maxSpeedFactor,
                        1.0F);
        float groundDrag = modifiedMax * airDrag;
        double actualGroundRetention =
                (double) groundDrag * blockSpeedFactor;
        double actualAirRetention =
                (discardFriction ? 1.0 : airDrag)
                        * blockSpeedFactor;
        float offGroundWaterEfficiency = waterEfficiency * 0.5F;
        float waterSlowdown = 0.9F;
        if (offGroundWaterEfficiency > 0.0F) {
            waterSlowdown +=
                    (0.54600006F - waterSlowdown)
                            * offGroundWaterEfficiency;
        }
        if (dolphinsGrace) {
            waterSlowdown = 0.96F;
        }
        double actualWaterRetention =
                (double) waterSlowdown * blockSpeedFactor;
        double actualLavaRetention = 0.5 * blockSpeedFactor;
        double actualRetention =
                Math.max(
                        Math.max(
                                actualGroundRetention,
                                actualAirRetention),
                        Math.max(
                                actualWaterRetention,
                                actualLavaRetention));

        assertTrue(
                bound.maxAccelerationPerQuantum()
                        >= actualAcceleration,
                () ->
                        "acceleration bound "
                                + bound.maxAccelerationPerQuantum()
                                + " < float recurrence "
                                + actualAcceleration);
        assertTrue(
                bound.maxRetention() >= actualRetention,
                () ->
                        "retention bound "
                                + bound.maxRetention()
                                + " < float recurrence "
                                + actualRetention);
    }

    private static float modifiedFriction(float friction, float modifier) {
        return Mth.clamp(
                1.0F - (1.0F - friction) * modifier,
                0.0F,
                1.0F);
    }

    private static SpiderVehicleMovementContext.FormulaInputs inputs(
            double speed,
            double minFriction,
            double maxFriction,
            double speedFactor,
            double waterEfficiency,
            double swimSpeed,
            boolean discardFriction,
            boolean dolphinsGrace) {
        return new SpiderVehicleMovementContext.FormulaInputs(
                (float) speed,
                (float) minFriction,
                (float) maxFriction,
                (float) speedFactor,
                (float) waterEfficiency,
                0.0F,
                1.0F,
                1.0F,
                0.0,
                0.0,
                (float) swimSpeed,
                discardFriction,
                dolphinsGrace,
                true,
                false,
                false,
                false,
                false);
    }

    private static SpiderVehicleMovementContext.FormulaInputs inputs(
            double speed,
            double minFriction,
            double maxFriction,
            double speedFactor,
            double waterEfficiency,
            double movementEfficiency,
            double frictionModifier,
            double airDragModifier,
            double entityBounciness,
            double maxBlockBounciness,
            double swimSpeed,
            boolean discardFriction,
            boolean dolphinsGrace) {
        return new SpiderVehicleMovementContext.FormulaInputs(
                (float) speed,
                (float) minFriction,
                (float) maxFriction,
                (float) speedFactor,
                (float) waterEfficiency,
                (float) movementEfficiency,
                (float) frictionModifier,
                (float) airDragModifier,
                entityBounciness,
                maxBlockBounciness,
                (float) swimSpeed,
                discardFriction,
                dolphinsGrace,
                true,
                false,
                false,
                false,
                false);
    }

    private static SpiderVehicleHorizontalEnvelope.MotionBound resolve(
            SpiderVehicleMovementContext.FormulaInputs inputs) {
        return SpiderVehicleMovementContext.fromInputs(inputs).orElseThrow();
    }

    private static double upperSum(double left, double right) {
        if (left == 0.0) {
            return right;
        }
        if (right == 0.0) {
            return left;
        }
        return Math.nextUp(left + right);
    }

    private static double upperDriven(float sourceAcceleration) {
        double source = Math.nextUp((double) sourceAcceleration);
        return Math.nextUp(
                source
                        * SpiderVehicleMovementContext
                                .VANILLA_YAW_ROTATION_NORM_BOUND);
    }
}
