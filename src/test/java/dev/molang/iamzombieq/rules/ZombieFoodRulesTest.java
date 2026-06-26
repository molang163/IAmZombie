package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.food.FoodTier;
import dev.molang.iamzombieq.rules.food.FoodRule;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

// NOTE: the test source set has no Minecraft/NeoForge on its classpath (testRuntimeClasspath is JUnit only, matching
// every other test in this project). Constructing the explicit-table rules eagerly invokes their suppliers, which read
// MobEffects.* holders, so those entries cannot be resolved here without a NoClassDefFoundError. The catch-all paths
// that do not build an EffectSpec (config ZOMBIE_FOODS -> CARRION, unknown -> HUMAN_COOKED) ARE Minecraft-free and are
// exercised below, alongside the tier tooltip mapping, the always-edible set, and the preservation records.
class ZombieFoodRulesTest {

    private static FoodRule carrionRule() {
        // Reached via the config ZOMBIE_FOODS catch-all for an id absent from the explicit table: no EffectSpec built.
        return ZombieFoodRules.ruleFor("modid:custom_raw_meat", Set.of("modid:custom_raw_meat"));
    }

    private static FoodRule humanCookedDefaultRule() {
        // Reached via the DEFAULT catch-all for an unknown, non-sweet id: no EffectSpec built.
        return ZombieFoodRules.ruleFor("modid:some_unknown_food", Set.of());
    }

    private static FoodRule forageRule() {
        return new FoodRule(FoodTier.FORAGE, java.util.List.of(), java.util.List.of(), false, false);
    }

    private static FoodRule specialRule() {
        return new FoodRule(FoodTier.SPECIAL, java.util.List.of(), java.util.List.of(), false, true);
    }

    @Test
    void configuredZombieFoodListMakesUnknownItemsCarrionWithoutHumanPunishment() {
        FoodRule rule = carrionRule();

        assertEquals(FoodTier.CARRION, rule.tier());
        assertFalse(rule.appliesHumanFoodPunishment());
        assertFalse(rule.suppressesVanillaPositiveEffects());
        assertTrue(rule.buffs().isEmpty());
        assertTrue(rule.debuffs().isEmpty());
    }

    @Test
    void unknownFoodFallsBackToHumanCookedDefaultAndPunishes() {
        FoodRule rule = humanCookedDefaultRule();

        assertEquals(FoodTier.HUMAN_COOKED, rule.tier());
        assertTrue(rule.appliesHumanFoodPunishment());
        assertFalse(rule.suppressesVanillaPositiveEffects());
    }

    @Test
    void onlyHumanCookedTierAppliesTheHumanFoodPunishment() {
        assertFalse(new FoodRule(FoodTier.CARRION, java.util.List.of(), java.util.List.of(), false, false)
                .appliesHumanFoodPunishment());
        assertFalse(forageRule().appliesHumanFoodPunishment());
        assertTrue(new FoodRule(FoodTier.HUMAN_COOKED, java.util.List.of(), java.util.List.of(), false, false)
                .appliesHumanFoodPunishment());
        assertFalse(specialRule().appliesHumanFoodPunishment());
    }

    @Test
    void foodRuleCopiesBuffAndDebuffListsDefensively() {
        FoodRule rule = humanCookedDefaultRule();
        assertEquals(java.util.List.of(), rule.buffs());
        assertEquals(java.util.List.of(), rule.debuffs());
    }

    @Test
    void humanFoodPunishmentUsesConfigurableDefaults() {
        ZombieFoodRules.HumanFoodPunishmentSettings settings = ZombieFoodRules.humanFoodPunishmentSettings(20 * 12, 20 * 18, 1);

        assertEquals(20 * 12, settings.nauseaDurationTicks());
        assertEquals(20 * 18, settings.hungerDurationTicks());
        assertEquals(1, settings.hungerAmplifier());
    }

    @Test
    void zombieSafeFoodOnlyClearsFoodSideEffectsAddedByTheBite() {
        ZombieFoodRules.PreservedFoodPunishments preserved = ZombieFoodRules.preserveExistingFoodPunishments(
                new ZombieFoodRules.PreservedEffect(true, 20 * 60, 1),
                new ZombieFoodRules.PreservedEffect(true, 20 * 30, 0),
                new ZombieFoodRules.PreservedEffect(false, 0, 0)
        );

        assertTrue(preserved.hunger().present());
        assertEquals(20 * 60, preserved.hunger().durationTicks());
        assertEquals(1, preserved.hunger().amplifier());
        assertTrue(preserved.nausea().present());
        assertFalse(preserved.poison().present());
    }

    @Test
    void goldenAppleSuppressionCanPreservePreExistingPositiveEffects() {
        ZombieFoodRules.PreservedGoldenAppleEffects preserved = ZombieFoodRules.preserveExistingGoldenAppleEffects(
                new ZombieFoodRules.PreservedEffect(true, 20 * 40, 0),
                new ZombieFoodRules.PreservedEffect(false, 0, 0),
                new ZombieFoodRules.PreservedEffect(true, 20 * 80, 1),
                new ZombieFoodRules.PreservedEffect(true, 20 * 120, 0)
        );

        assertTrue(preserved.regeneration().present());
        assertFalse(preserved.absorption().present());
        assertEquals(1, preserved.resistance().amplifier());
        assertEquals(20 * 120, preserved.fireResistance().durationTicks());
    }

    @Test
    void suppressesVanillaPositiveEffectsIsCarriedOnTheSpecialRule() {
        assertTrue(specialRule().suppressesVanillaPositiveEffects());
        assertEquals(FoodTier.SPECIAL, specialRule().tier());
    }

    @Test
    void milkIsNotClassifiedAsFoodPunishment() {
        assertFalse(ZombieFoodRules.isFoodRuleTarget("minecraft:milk_bucket"));
        assertTrue(ZombieFoodRules.isFoodRuleTarget("minecraft:cooked_beef"));
    }

    @Test
    void specialBuffFoodsAreAlwaysEdibleForZombiesEvenAtFullHunger() {
        assertTrue(ZombieFoodRules.isAlwaysEdibleForZombies("minecraft:pufferfish"));
        assertTrue(ZombieFoodRules.isAlwaysEdibleForZombies("minecraft:spider_eye"));
        assertTrue(ZombieFoodRules.isAlwaysEdibleForZombies("minecraft:poisonous_potato"));
        assertTrue(ZombieFoodRules.isAlwaysEdibleForZombies(ZombieFoodRules.SUPER_ROTTEN_FLESH_ID));
    }

    @Test
    void isAlwaysEdibleForZombiesIsCaseInsensitiveAndNullSafe() {
        assertTrue(ZombieFoodRules.isAlwaysEdibleForZombies("MINECRAFT:PUFFERFISH"));
        assertFalse(ZombieFoodRules.isAlwaysEdibleForZombies(null));
    }

    @Test
    void ordinaryAndOtherZombieFoodsAreNotForcedEdibleAtFullHunger() {
        assertFalse(ZombieFoodRules.isAlwaysEdibleForZombies("minecraft:bread"));
        assertFalse(ZombieFoodRules.isAlwaysEdibleForZombies("minecraft:rotten_flesh"));
        assertFalse(ZombieFoodRules.isAlwaysEdibleForZombies("minecraft:golden_apple"));
        assertFalse(ZombieFoodRules.isAlwaysEdibleForZombies("minecraft:cod"));
    }

    @Test
    void tooltipKeysDescribeFoodTierClassification() {
        assertEquals("iamzombieq.tooltip.food.carrion", ZombieFoodRules.tooltipKey(carrionRule()));
        assertEquals("iamzombieq.tooltip.food.forage", ZombieFoodRules.tooltipKey(forageRule()));
        assertEquals("iamzombieq.tooltip.food.human_cooked", ZombieFoodRules.tooltipKey(humanCookedDefaultRule()));
        assertEquals("iamzombieq.tooltip.food.special", ZombieFoodRules.tooltipKey(specialRule()));
    }

    @Test
    void everyFoodTierExposesItsTooltipKey() {
        assertEquals("iamzombieq.tooltip.food.carrion", FoodTier.CARRION.tooltipKey());
        assertEquals("iamzombieq.tooltip.food.forage", FoodTier.FORAGE.tooltipKey());
        assertEquals("iamzombieq.tooltip.food.human_cooked", FoodTier.HUMAN_COOKED.tooltipKey());
        assertEquals("iamzombieq.tooltip.food.special", FoodTier.SPECIAL.tooltipKey());
    }
}
