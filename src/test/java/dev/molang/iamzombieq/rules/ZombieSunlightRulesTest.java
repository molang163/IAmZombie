package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieForm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(true, 1.0F, random, true, true));

        assertEquals(2, samples.get());
    }

    @Test
    void vanillaSunBurnTickRequiresSkyVisibilityAndNoWaterRainOrPowderSnow() {
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(true, 1.0F, 0.0F, false, false));
        assertFalse(ZombieSunlightRules.isVanillaSunBurnTick(true, 1.0F, 0.0F, true, true));
    }
}
