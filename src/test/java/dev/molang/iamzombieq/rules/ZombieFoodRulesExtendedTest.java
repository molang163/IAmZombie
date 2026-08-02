package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.food.EffectId;
import dev.molang.iamzombieq.rules.food.FoodTier;
import dev.molang.iamzombieq.rules.food.FoodRule;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;
import dev.molang.iamzombieq.util.SourceScan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * Extra coverage for {@link ZombieFoodRules} resolution, complementing {@link ZombieFoodRulesTest}.
 *
 * <p>The test runtime classpath is JUnit-only (no Minecraft/NeoForge): resolving any id in the explicit §3 table eagerly
 * runs its lambda, which resolves EffectIds via the effect resolver into {@code MobEffects.*} holders and throws without
 * a mod bootstrap. So this file only exercises the genuinely Minecraft-free paths -- the two catch-all branches
 * ({@code ruleFor} config-set -> CARRION, unknown -> HUMAN_COOKED) by actually invoking
 * {@link ZombieFoodRules#ruleFor(String, Set, Function)} with a throwing resolver (which those paths never call), and
 * {@code defaultRuleFor}'s delegation wiring. T2: the explicit §3 table's own content (tiers, effects, sweet/non-sweet
 * HUMAN_COOKED, the DEFAULT_ZOMBIE_FOODS-&gt;CARRION mapping) is pinned as real behavior against the pure
 * {@code ZombieFoodTable} in {@code dev.molang.iamzombieq.rules.food} ({@code ZombieFoodTableDataTest},
 * {@code EffectIdTableTest}) rather than here, since this class's package cannot see that package-private table.
 */
class ZombieFoodRulesExtendedTest {
    private static final Path RULES_SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/rules/food/ZombieFoodRules.java");

    // These Minecraft-free catch-all paths never resolve an EffectId, so the resolver must never be called.
    private static final Function<EffectId, dev.molang.iamzombieq.rules.EffectSpec> NEVER_RESOLVED =
            id -> {
                throw new AssertionError("Minecraft-free catch-all path must not resolve an effect: " + id);
            };
    // These same catch-all paths build no EffectId, so they never consult the config resolver either.
    private static final ToIntFunction<String> NEVER_CONFIG =
            key -> {
                throw new AssertionError("Minecraft-free catch-all path must not read config: " + key);
            };

    // ---- Minecraft-free runtime paths (no §3 explicit entry, so no EffectSpec/MobEffects is built) ----

    @Test
    void everyConfiguredZombieFoodAbsentFromTheExplicitTableResolvesToCarrion() {
        // A modpack-added raw meat that is only known via config ZOMBIE_FOODS resolves to CARRION with no debuff.
        for (String id : new String[] {"modid:raw_venison", "modid:raw_horse", "another:custom_carrion"}) {
            FoodRule rule = ZombieFoodRules.ruleFor(id, Set.of(id), NEVER_RESOLVED, NEVER_CONFIG);
            assertEquals(FoodTier.CARRION, rule.tier(), id + " configured as a zombie food should be CARRION");
            assertFalse(rule.appliesHumanFoodPunishment(), id + " (CARRION) must not punish like human food");
            assertTrue(rule.buffs().isEmpty(), id + " config-only CARRION carries no static buff");
            assertTrue(rule.debuffs().isEmpty(), id + " config-only CARRION carries no debuff");
        }
    }

    @Test
    void caseIsNormalizedWhenMatchingTheConfiguredZombieFoodSet() {
        // ruleFor lower-cases the id; an upper-case query still matches a lower-case config entry.
        FoodRule rule = ZombieFoodRules.ruleFor("MODID:RAW_VENISON", Set.of("modid:raw_venison"), NEVER_RESOLVED, NEVER_CONFIG);
        assertEquals(FoodTier.CARRION, rule.tier());
    }

    @Test
    void unknownNonConfiguredFoodFallsBackToHumanCookedAndPunishes() {
        FoodRule rule = ZombieFoodRules.ruleFor("modid:totally_unknown_pie", Set.of(), NEVER_RESOLVED, NEVER_CONFIG);
        assertEquals(FoodTier.HUMAN_COOKED, rule.tier(), "an unknown food defaults to HUMAN_COOKED");
        assertTrue(rule.appliesHumanFoodPunishment(), "the HUMAN_COOKED default applies the human-food punishment");
        assertFalse(rule.suppressesVanillaPositiveEffects());
        assertTrue(rule.debuffs().isEmpty(), "the non-sweet default carries no static Slowness debuff");
    }

    @Test
    void configuredZombieFoodTakesPrecedenceOverTheHumanCookedDefault() {
        // The same unknown id is CARRION when configured and HUMAN_COOKED when not, proving the config branch is decisive.
        assertEquals(FoodTier.CARRION, ZombieFoodRules.ruleFor("modid:edge", Set.of("modid:edge"), NEVER_RESOLVED, NEVER_CONFIG).tier());
        assertEquals(FoodTier.HUMAN_COOKED, ZombieFoodRules.ruleFor("modid:edge", Set.of(), NEVER_RESOLVED, NEVER_CONFIG).tier());
    }

    @Test
    void defaultRuleForUsesTheBuiltInZombieFoodSet() throws IOException {
        // defaultRuleFor delegates to ruleFor(id, DEFAULT_ZOMBIE_FOODS); confirm the wiring in source (calling it for a
        // §3 id would touch MobEffects), and exercise the Minecraft-free unknown-food branch through it.
        String rules = Files.readString(RULES_SOURCE);
        assertTrue(rules.contains("return ruleFor(itemId, DEFAULT_ZOMBIE_FOODS, effectResolver, configResolver);"),
                "defaultRuleFor should resolve against the built-in default zombie food set");
        assertEquals(FoodTier.HUMAN_COOKED, ZombieFoodRules.defaultRuleFor("modid:unknown_to_defaults", NEVER_RESOLVED, NEVER_CONFIG).tier(),
                "an id outside both the explicit table and the default set defaults to HUMAN_COOKED");
    }

    // ---- T2-REV1: bridge guard -- ruleFor/ruleForStack must both delegate an explicit-table hit to toFoodRule, and
    // toFoodRule must wire tier/buffs/debuffs/both flags into the FoodRule unchanged. Comment-stripped so a stray
    // mention in a comment cannot fool the guard. ----

    @Test
    void toFoodRuleBridgeWiresTierBuffsDebuffsAndBothFlags() throws IOException {
        String rules = SourceScan.stripComments(Files.readString(RULES_SOURCE));

        String bridge = SourceScan.methodBody(rules, "private static FoodRule toFoodRule(");
        assertTrue(bridge.contains("row.resolveBuffs(effectResolver, configResolver)"),
                "toFoodRule must resolve buffs through the row's resolveBuffs");
        assertTrue(bridge.contains("row.resolveDebuffs(effectResolver, configResolver)"),
                "toFoodRule must resolve debuffs through the row's resolveDebuffs");
        int buffsResolved = bridge.indexOf("row.resolveBuffs(effectResolver, configResolver)");
        int debuffsResolved = bridge.indexOf("row.resolveDebuffs(effectResolver, configResolver)");
        assertTrue(buffsResolved >= 0 && debuffsResolved > buffsResolved,
                "buffs must be resolved before debuffs (old Java left-to-right evaluation order)");
        assertTrue(bridge.contains(
                        "new FoodRule(row.tier(), buffs, debuffs, row.restoresBabyState(), row.suppressesVanillaPositiveEffects())"),
                "toFoodRule must wire tier, buffs, debuffs, and both flags into the FoodRule unchanged");

        String ruleForBody = SourceScan.methodBody(rules, "public static FoodRule ruleFor(");
        assertTrue(ruleForBody.contains("toFoodRule(row, effectResolver, configResolver)"),
                "ruleFor must delegate an explicit-table hit to toFoodRule");

        String ruleForStackBody = SourceScan.methodBody(rules, "public static FoodRule ruleForStack(");
        assertTrue(ruleForStackBody.contains("toFoodRule(row, effectResolver, configResolver)"),
                "ruleForStack must delegate an explicit-table hit to toFoodRule");
    }
}
