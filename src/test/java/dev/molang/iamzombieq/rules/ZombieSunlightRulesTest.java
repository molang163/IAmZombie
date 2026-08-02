package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieForm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

class ZombieSunlightRulesTest {
    @Test
    void normalAndDrownedBurnWhenExposedWithoutHeadProtection() {
        assertTrue(ZombieSunlightRules.shouldBurn(ZombieForm.NORMAL, true, HeadProtection.NONE));
        assertTrue(ZombieSunlightRules.shouldBurn(ZombieForm.DROWNED, true, HeadProtection.NONE));
    }

    @Test
    void huskNeverBurnsInSunlight() {
        assertFalse(ZombieSunlightRules.shouldBurn(ZombieForm.HUSK, true, HeadProtection.NONE));
    }

    @Test
    void zombifiedPiglinNeverBurnsInSunlight() {
        assertFalse(ZombieSunlightRules.shouldBurn(ZombieForm.ZOMBIFIED_PIGLIN, true, HeadProtection.NONE));
    }

    @Test
    void huskDoesNotSpendHeadProtectionDurabilityInSunlight() {
        assertFalse(ZombieSunlightRules.shouldDamageHeadProtection(ZombieForm.HUSK, true, HeadProtection.STEVE_HEAD));
        assertFalse(ZombieSunlightRules.shouldDamageHeadProtection(ZombieForm.HUSK, true, HeadProtection.OTHER_HELMET));
    }

    @Test
    void zombifiedPiglinDoesNotSpendHeadProtectionDurabilityInSunlight() {
        assertFalse(ZombieSunlightRules.shouldDamageHeadProtection(ZombieForm.ZOMBIFIED_PIGLIN, true, HeadProtection.STEVE_HEAD));
        assertFalse(ZombieSunlightRules.shouldDamageHeadProtection(ZombieForm.ZOMBIFIED_PIGLIN, true, HeadProtection.OTHER_HELMET));
    }

    @Test
    void protectedSunVulnerableFormsSpendDamageableHeadProtection() {
        assertTrue(ZombieSunlightRules.shouldDamageHeadProtection(ZombieForm.NORMAL, true, HeadProtection.STEVE_HEAD));
        assertTrue(ZombieSunlightRules.shouldDamageHeadProtection(ZombieForm.DROWNED, true, HeadProtection.OTHER_HELMET));
        assertFalse(ZombieSunlightRules.shouldDamageHeadProtection(ZombieForm.NORMAL, true, HeadProtection.NONE));
        assertFalse(ZombieSunlightRules.shouldDamageHeadProtection(ZombieForm.NORMAL, false, HeadProtection.STEVE_HEAD));
    }

    @Test
    void headProtectionBlocksSunlightBurning() {
        assertFalse(ZombieSunlightRules.shouldBurn(ZombieForm.NORMAL, true, HeadProtection.PUMPKIN));
        assertFalse(ZombieSunlightRules.shouldBurn(ZombieForm.NORMAL, true, HeadProtection.STEVE_HEAD));
        assertFalse(ZombieSunlightRules.shouldBurn(ZombieForm.NORMAL, true, HeadProtection.OTHER_HELMET));
    }

    @Test
    void noSkyExposureNeverBurns() {
        assertFalse(ZombieSunlightRules.shouldBurn(ZombieForm.NORMAL, false, HeadProtection.NONE));
    }

    @Test
    void vanillaSunBurnTickRequiresMonsterBurnEnvironment() {
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(false, 1.0F, 0.0F, true, false));
    }

    @Test
    void vanillaSunBurnTickRequiresBrightnessAboveVanillaThreshold() {
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(true, 0.5F, 0.0F, true, false));
        assertTrue(ZombieSunlightRules.isVanillaSunBurnTick(true, 0.51F, 0.0F, true, false));
    }

    @Test
    void vanillaSunBurnTickUsesVanillaRandomChanceFormula() {
        assertTrue(ZombieSunlightRules.isVanillaSunBurnTick(true, 1.0F, 0.03F, true, false));
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(true, 1.0F, 0.05F, true, false));
    }

    @Test
    void vanillaSunBurnTickDoesNotSampleRandomBeforeVanillaPreconditionsPass() {
        AtomicInteger samples = new AtomicInteger();
        DoubleSupplier random = () -> {
            samples.incrementAndGet();
            return 0.0D;
        };

        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(false, 1.0F, random, true, false));
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(true, 0.5F, random, true, false));

        assertEquals(0, samples.get());
    }

    @Test
    void vanillaSunBurnTickSamplesRandomOnceAfterVanillaPreconditionsPass() {
        AtomicInteger samples = new AtomicInteger();
        DoubleSupplier random = () -> {
            samples.incrementAndGet();
            return 0.0D;
        };

        assertTrue(ZombieSunlightRules.isVanillaSunBurnTick(true, 1.0F, random, true, false));

        assertEquals(1, samples.get());
    }

    @Test
    void vanillaSunBurnTickSamplesRandomBeforeLaterSkyAndWaterChecks() {
        AtomicInteger samples = new AtomicInteger();
        DoubleSupplier random = () -> {
            samples.incrementAndGet();
            return 0.0D;
        };

        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(true, 1.0F, random, false, false));
        assertEquals(1, samples.get(), "the roll should be sampled before the sky check");
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(true, 1.0F, random, true, true));

        assertEquals(2, samples.get(), "the roll should be sampled before the wet-state check");
    }

    @Test
    void vanillaSunBurnTickRequiresSkyVisibilityAndNoWaterRainOrPowderSnow() {
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(true, 1.0F, 0.0F, false, false));
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(true, 1.0F, 0.0F, true, true));
    }

    @Test
    void sunBurnContextCorePreservesVanillaBoundaries() {
        float brightness = 1.0F;
        double randomBoundary = ((brightness - 0.4F) * 2.0F) / 30.0D;

        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(
                new SunBurnContext(false, brightness, 0.0D, true, false)));
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(
                new SunBurnContext(true, 0.5F, 0.0D, true, false)));
        assertTrue(ZombieSunlightRules.isVanillaSunBurnTick(
                new SunBurnContext(true, Math.nextUp(0.5F), 0.0D, true, false)));
        assertTrue(ZombieSunlightRules.isVanillaSunBurnTick(
                new SunBurnContext(true, brightness, Math.nextDown(randomBoundary), true, false)));
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(
                new SunBurnContext(true, brightness, randomBoundary, true, false)));
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(
                new SunBurnContext(true, brightness, Math.nextUp(randomBoundary), true, false)));
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(
                new SunBurnContext(true, brightness, 0.0D, false, false)));
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(
                new SunBurnContext(true, brightness, 0.0D, true, true)));
    }

    @Test
    void legacyOverloadsMatchContextCoreAcrossInputMatrix() {
        for (boolean monstersBurn : new boolean[] {false, true}) {
            for (float brightness : new float[] {0.5F, Math.nextUp(0.5F), 1.0F}) {
                for (float randomFloat : new float[] {0.0F, 0.039F, 0.041F}) {
                    for (boolean canSeeSky : new boolean[] {false, true}) {
                        for (boolean wet : new boolean[] {false, true}) {
                            boolean core = ZombieSunlightRules.isVanillaSunBurnTick(new SunBurnContext(
                                    monstersBurn, brightness, randomFloat, canSeeSky, wet));
                            String inputs = "monstersBurn=" + monstersBurn
                                    + ", brightness=" + brightness
                                    + ", randomFloat=" + randomFloat
                                    + ", canSeeSky=" + canSeeSky
                                    + ", wet=" + wet;

                            assertEquals(core, ZombieSunlightRules.isVanillaSunBurnTick(
                                    monstersBurn, brightness, randomFloat, canSeeSky, wet), "float bridge: " + inputs);
                            assertEquals(core, ZombieSunlightRules.isVanillaSunBurnTick(
                                    monstersBurn, brightness, () -> randomFloat, canSeeSky, wet),
                                    "DoubleSupplier bridge: " + inputs);
                        }
                    }
                }
            }
        }
    }

    @Test
    void doubleSupplierPreservesSubFloatPrecision() {
        float brightness = 1.0F;
        double randomBoundary = ((brightness - 0.4F) * 2.0F) / 30.0D;
        double highPrecisionRoll = Math.nextDown(randomBoundary);

        assertTrue(ZombieSunlightRules.isVanillaSunBurnTick(
                new SunBurnContext(true, brightness, highPrecisionRoll, true, false)));
        assertTrue(ZombieSunlightRules.isVanillaSunBurnTick(
                true, brightness, () -> highPrecisionRoll, true, false));
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(
                true, brightness, (float) highPrecisionRoll, true, false),
                "narrowing the supplier result to float would cross the strict random boundary");
    }

    @Test
    void doubleSupplierPropagatesOriginalExceptionBeforeLaterEnvironmentGates() {
        IllegalStateException failure = new IllegalStateException("random failure");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                ZombieSunlightRules.isVanillaSunBurnTick(
                        true,
                        1.0F,
                        () -> {
                            throw failure;
                        },
                        false,
                        true));

        assertSame(failure, thrown, "the compatibility bridge must not catch or wrap supplier failures");
    }

    @Test
    void publicSunBurnTickOverloadsRetainDescriptorsAndNamedRecordShape() throws NoSuchMethodException {
        Method floatBridge = ZombieSunlightRules.class.getMethod(
                "isVanillaSunBurnTick",
                boolean.class,
                float.class,
                float.class,
                boolean.class,
                boolean.class);
        Method supplierBridge = ZombieSunlightRules.class.getMethod(
                "isVanillaSunBurnTick",
                boolean.class,
                float.class,
                DoubleSupplier.class,
                boolean.class,
                boolean.class);
        Method contextCore = ZombieSunlightRules.class.getMethod(
                "isVanillaSunBurnTick",
                SunBurnContext.class);

        for (Method entry : new Method[] {floatBridge, supplierBridge, contextCore}) {
            assertEquals(boolean.class, entry.getReturnType());
            assertTrue(Modifier.isPublic(entry.getModifiers()));
            assertTrue(Modifier.isStatic(entry.getModifiers()));
        }
        assertTrue(SunBurnContext.class.isRecord());
        assertArrayEquals(
                new String[] {
                    "monstersBurn",
                    "brightness",
                    "randomValue",
                    "canSeeSky",
                    "inWaterRainOrPowderSnow"
                },
                Arrays.stream(SunBurnContext.class.getRecordComponents())
                        .map(component -> component.getName())
                        .toArray(String[]::new));
        assertArrayEquals(
                new Class<?>[] {boolean.class, float.class, double.class, boolean.class, boolean.class},
                Arrays.stream(SunBurnContext.class.getRecordComponents())
                        .map(component -> component.getType())
                        .toArray(Class<?>[]::new));
    }
}
