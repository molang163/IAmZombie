package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.food.FoodTier;
import dev.molang.iamzombieq.rules.food.FoodRule;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Extra coverage for {@link ZombieFoodRules} resolution, complementing {@link ZombieFoodRulesTest}.
 *
 * <p>The test runtime classpath is JUnit-only (no Minecraft/NeoForge): resolving any id in the explicit §3 table eagerly
 * runs its supplier, which reads {@code MobEffects.*} holders and throws without a mod bootstrap. So the §3-table foods
 * (the configured zombie foods and the sweets) are pinned via a Minecraft-free SOURCE scan of {@code ZombieFoodRules.java};
 * only the two genuinely Minecraft-free catch-all paths ({@code ruleFor} config-set -> CARRION, unknown -> HUMAN_COOKED)
 * are exercised by actually invoking {@link ZombieFoodRules#ruleFor(String, Set)}.
 */
class ZombieFoodRulesExtendedTest {
    private static final Path RULES_SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/rules/food/ZombieFoodRules.java");

    // ---- Minecraft-free runtime paths (no §3 explicit entry, so no EffectSpec/MobEffects is built) ----

    @Test
    void everyConfiguredZombieFoodAbsentFromTheExplicitTableResolvesToCarrion() {
        // A modpack-added raw meat that is only known via config ZOMBIE_FOODS resolves to CARRION with no debuff.
        for (String id : new String[] {"modid:raw_venison", "modid:raw_horse", "another:custom_carrion"}) {
            FoodRule rule = ZombieFoodRules.ruleFor(id, Set.of(id));
            assertEquals(FoodTier.CARRION, rule.tier(), id + " configured as a zombie food should be CARRION");
            assertFalse(rule.appliesHumanFoodPunishment(), id + " (CARRION) must not punish like human food");
            assertTrue(rule.buffs().isEmpty(), id + " config-only CARRION carries no static buff");
            assertTrue(rule.debuffs().isEmpty(), id + " config-only CARRION carries no debuff");
        }
    }

    @Test
    void caseIsNormalizedWhenMatchingTheConfiguredZombieFoodSet() {
        // ruleFor lower-cases the id; an upper-case query still matches a lower-case config entry.
        FoodRule rule = ZombieFoodRules.ruleFor("MODID:RAW_VENISON", Set.of("modid:raw_venison"));
        assertEquals(FoodTier.CARRION, rule.tier());
    }

    @Test
    void unknownNonConfiguredFoodFallsBackToHumanCookedAndPunishes() {
        FoodRule rule = ZombieFoodRules.ruleFor("modid:totally_unknown_pie", Set.of());
        assertEquals(FoodTier.HUMAN_COOKED, rule.tier(), "an unknown food defaults to HUMAN_COOKED");
        assertTrue(rule.appliesHumanFoodPunishment(), "the HUMAN_COOKED default applies the human-food punishment");
        assertFalse(rule.suppressesVanillaPositiveEffects());
        assertTrue(rule.debuffs().isEmpty(), "the non-sweet default carries no static Slowness debuff");
    }

    @Test
    void configuredZombieFoodTakesPrecedenceOverTheHumanCookedDefault() {
        // The same unknown id is CARRION when configured and HUMAN_COOKED when not, proving the config branch is decisive.
        assertEquals(FoodTier.CARRION, ZombieFoodRules.ruleFor("modid:edge", Set.of("modid:edge")).tier());
        assertEquals(FoodTier.HUMAN_COOKED, ZombieFoodRules.ruleFor("modid:edge", Set.of()).tier());
    }

    // ---- §3 explicit table pinned via source scan (resolving these at runtime would touch MobEffects holders) ----

    @Test
    void sweetFoodsMapToHumanCookedSweetSoTheyApplyPunishmentPlusSlowness() throws IOException {
        String rules = Files.readString(RULES_SOURCE);
        // The three sweet desserts must pass sweet=true so humanCooked(true) appends the SWEET Slowness debuff on top of
        // the standard HUMAN_COOKED Hunger II + Nausea punishment (appliesHumanFoodPunishment() == true for HUMAN_COOKED).
        assertTrue(rules.contains("Map.entry(\"minecraft:cake\", () -> humanCooked(true))"),
                "cake must be HUMAN_COOKED sweet so it punishes AND adds Slowness");
        assertTrue(rules.contains("Map.entry(\"minecraft:cookie\", () -> humanCooked(true))"),
                "cookie must be HUMAN_COOKED sweet");
        assertTrue(rules.contains("Map.entry(\"minecraft:pumpkin_pie\", () -> humanCooked(true))"),
                "pumpkin pie must be HUMAN_COOKED sweet");
        // The sweet branch of humanCooked is exactly the SWEET_SLOWNESS_DURATION_TICKS Slowness EffectSpec.
        assertTrue(rules.contains("EffectSpec.of(MobEffects.SLOWNESS, cfg(IAmZombieConfig.SWEET_SLOWNESS_DURATION_TICKS)"),
                "the sweet branch should add a config-driven Slowness debuff");
    }

    @Test
    void nonSweetCookedFoodsMapToPlainHumanCookedWithNoExtraDebuff() throws IOException {
        String rules = Files.readString(RULES_SOURCE);
        for (String id : new String[] {
                "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken",
                "minecraft:cooked_mutton", "minecraft:cooked_cod", "minecraft:baked_potato",
                "minecraft:golden_carrot", "minecraft:mushroom_stew", "minecraft:rabbit_stew",
                "minecraft:beetroot_soup", "minecraft:suspicious_stew"}) {
            assertTrue(rules.contains("Map.entry(\"" + id + "\", () -> humanCooked(false))"),
                    id + " should be HUMAN_COOKED with sweet=false (punishes but no Slowness)");
        }
    }

    @Test
    void everyDefaultZombieFoodMapsToCarrionInTheExplicitTable() throws IOException {
        String rules = Files.readString(RULES_SOURCE);
        // Every member of DEFAULT_ZOMBIE_FOODS resolves to a CARRION rule: the raw flesh/meats/fish via explicit carrion(
        // entries, and super rotten flesh via carrionBabyRestore(. None of them carry the human-food punishment.
        for (String id : new String[] {
                "minecraft:rotten_flesh", "minecraft:spider_eye", "minecraft:beef", "minecraft:porkchop",
                "minecraft:mutton", "minecraft:chicken", "minecraft:rabbit", "minecraft:cod",
                "minecraft:salmon", "minecraft:tropical_fish"}) {
            assertTrue(rules.contains("Map.entry(\"" + id + "\", () -> carrion("),
                    id + " should resolve to a CARRION rule");
        }
        assertTrue(rules.contains("Map.entry(SUPER_ROTTEN_FLESH_ID, () -> carrionBabyRestore("),
                "super rotten flesh should be CARRION with the baby->adult restore");
        // poisonous_potato and pufferfish are default zombie foods but the EXPLICIT table escalates them to SPECIAL.
        assertTrue(rules.contains("Map.entry(\"minecraft:pufferfish\", () -> special("),
                "pufferfish is a default zombie food escalated to SPECIAL in the explicit table");
        assertTrue(rules.contains("Map.entry(\"minecraft:poisonous_potato\", () -> special("),
                "poisonous potato is a default zombie food escalated to SPECIAL in the explicit table");
    }

    @Test
    void defaultRuleForUsesTheBuiltInZombieFoodSet() throws IOException {
        // defaultRuleFor delegates to ruleFor(id, DEFAULT_ZOMBIE_FOODS); confirm the wiring in source (calling it for a
        // §3 id would touch MobEffects), and exercise the Minecraft-free unknown-food branch through it.
        String rules = Files.readString(RULES_SOURCE);
        assertTrue(rules.contains("return ruleFor(itemId, DEFAULT_ZOMBIE_FOODS);"),
                "defaultRuleFor should resolve against the built-in default zombie food set");
        assertEquals(FoodTier.HUMAN_COOKED, ZombieFoodRules.defaultRuleFor("modid:unknown_to_defaults").tier(),
                "an id outside both the explicit table and the default set defaults to HUMAN_COOKED");
    }
}
