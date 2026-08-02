package dev.molang.iamzombieq.rules.food;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Minecraft-free coverage of the effect-id layer of the §3 explicit food table (T2). The table itself
 * ({@link ZombieFoodTable}) stores every effect as a Minecraft-free {@link EffectId}, so this class calls it
 * directly (no Minecraft/NeoForge on the test classpath is needed) instead of scanning {@code ZombieFoodRules.java}
 * source text.
 *
 * <ul>
 *   <li>{@link EffectId#of} clamping is exercised directly (pure record, no Minecraft).</li>
 *   <li>Every {@link EffectId} the whole table can produce is resolved via a dummy config resolver and asserted to be
 *       a good-form {@code namespace:path} id, and a set of representative rows (effect id string + tier) is pinned
 *       so a typo or a wrong tier is caught at test time.</li>
 * </ul>
 *
 * A companion gameplay-layer test ({@code ZombieFoodEffectResolverTest}) resolves every one of these effect ids
 * through the real events-layer resolver, promoting any runtime {@code Holder<MobEffect>} typo to a test failure.
 */
class EffectIdTableTest {
    // A good-form vanilla-style resource id: lower-case namespace:path (path may contain / for completeness).
    private static final Pattern WELL_FORMED_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final ToIntFunction<String> ANY_CONFIG_VALUE = key -> 1;

    // ---------- EffectId record (pure, Minecraft-free) ----------

    @Test
    void effectIdOfClampsNegativeDurationAndAmplifierToZero() {
        EffectId clamped = EffectId.of("minecraft:strength", -5, -3);
        assertEquals("minecraft:strength", clamped.effectId());
        assertEquals(0, clamped.durationTicks(), "negative duration must clamp to 0");
        assertEquals(0, clamped.amplifier(), "negative amplifier must clamp to 0");
    }

    @Test
    void effectIdOfKeepsNonNegativeValues() {
        EffectId spec = EffectId.of("minecraft:night_vision", 20 * 45, 2);
        assertEquals("minecraft:night_vision", spec.effectId());
        assertEquals(20 * 45, spec.durationTicks());
        assertEquals(2, spec.amplifier());
    }

    // ---------- Explicit-table effect-id data (behavioral: calls ZombieFoodTable directly) ----------

    @Test
    void everyEffectIdInTheExplicitTableIsAWellFormedNamespacedId() {
        List<String> found = new ArrayList<>();
        for (String itemId : ZombieFoodTable.itemIds()) {
            ZombieFoodTable.Row row = ZombieFoodTable.lookup(itemId);
            for (EffectId effect : row.resolveBuffs(Function.identity(), ANY_CONFIG_VALUE)) {
                found.add(effect.effectId());
            }
            for (EffectId effect : row.resolveDebuffs(Function.identity(), ANY_CONFIG_VALUE)) {
                found.add(effect.effectId());
            }
        }
        for (String id : found) {
            assertTrue(WELL_FORMED_ID.matcher(id).matches(),
                    "explicit-table effect id must be a good-form namespace:path: '" + id + "'");
            assertFalse(id.contains(" "), "effect id must not contain spaces: '" + id + "'");
        }
        // The table names a good spread of vanilla effects; guard against the scan silently matching nothing.
        assertTrue(found.size() >= 12, "expected the explicit table to name at least a dozen effects, saw " + found.size());
    }

    @Test
    void representativeTableEntriesPinTheirEffectIdAndTier() {
        // T1 CARRION super rotten flesh: Strength + Saturation, and it restores the baby state.
        ZombieFoodTable.Row superRottenFlesh = ZombieFoodTable.lookup(ZombieFoodRules.SUPER_ROTTEN_FLESH_ID);
        assertEquals(FoodTier.CARRION, superRottenFlesh.tier(), "super rotten flesh is a CARRION rule");
        assertTrue(superRottenFlesh.restoresBabyState(), "super rotten flesh is a CARRION baby-restore rule");
        List<String> superRottenFleshIds = superRottenFlesh.resolveBuffs(Function.identity(), ANY_CONFIG_VALUE).stream().map(EffectId::effectId).toList();
        assertTrue(superRottenFleshIds.contains("minecraft:strength"), "super rotten flesh grants Strength");
        assertTrue(superRottenFleshIds.contains("minecraft:saturation"), "super rotten flesh grants Saturation");

        // Spider eye: CARRION Night Vision.
        ZombieFoodTable.Row spiderEye = ZombieFoodTable.lookup("minecraft:spider_eye");
        assertEquals(FoodTier.CARRION, spiderEye.tier(), "spider eye is CARRION");
        assertTrue(spiderEye.resolveBuffs(Function.identity(), ANY_CONFIG_VALUE).stream().anyMatch(e -> e.effectId().equals("minecraft:night_vision")),
                "spider eye grants Night Vision");

        // Cod/salmon: CARRION Water Breathing.
        for (String fish : List.of("minecraft:cod", "minecraft:salmon")) {
            assertTrue(ZombieFoodTable.lookup(fish).resolveBuffs(Function.identity(), ANY_CONFIG_VALUE).stream()
                            .anyMatch(e -> e.effectId().equals("minecraft:water_breathing")),
                    fish + " grants Water Breathing");
        }

        // Sweet T3: cake maps to the sweet HUMAN_COOKED branch which adds Slowness.
        ZombieFoodTable.Row cake = ZombieFoodTable.lookup("minecraft:cake");
        assertEquals(FoodTier.HUMAN_COOKED, cake.tier(), "cake is HUMAN_COOKED");
        assertTrue(cake.resolveDebuffs(Function.identity(), ANY_CONFIG_VALUE).stream().anyMatch(e -> e.effectId().equals("minecraft:slowness")),
                "cake's sweet branch adds Slowness");

        // T4 SPECIAL golden apple: Absorption buff + Hunger debuff, suppresses vanilla positives.
        ZombieFoodTable.Row goldenApple = ZombieFoodTable.lookup("minecraft:golden_apple");
        assertEquals(FoodTier.SPECIAL, goldenApple.tier(), "golden apple is SPECIAL");
        assertTrue(goldenApple.suppressesVanillaPositiveEffects(), "golden apple suppresses vanilla positives");
        assertTrue(goldenApple.resolveBuffs(Function.identity(), ANY_CONFIG_VALUE).stream().anyMatch(e -> e.effectId().equals("minecraft:absorption")),
                "golden apple grants Absorption");
        assertTrue(goldenApple.resolveDebuffs(Function.identity(), ANY_CONFIG_VALUE).stream().anyMatch(e -> e.effectId().equals("minecraft:hunger")),
                "golden apple carries a Hunger debuff");

        // Pufferfish SPECIAL: Absorption + Regeneration.
        ZombieFoodTable.Row pufferfish = ZombieFoodTable.lookup("minecraft:pufferfish");
        assertEquals(FoodTier.SPECIAL, pufferfish.tier(), "pufferfish is SPECIAL");
        assertTrue(pufferfish.resolveBuffs(Function.identity(), ANY_CONFIG_VALUE).stream().anyMatch(e -> e.effectId().equals("minecraft:regeneration")),
                "pufferfish grants Regeneration");

        // Chorus fruit SPECIAL: Slow Falling + Nausea.
        ZombieFoodTable.Row chorusFruit = ZombieFoodTable.lookup("minecraft:chorus_fruit");
        assertTrue(chorusFruit.resolveBuffs(Function.identity(), ANY_CONFIG_VALUE).stream().anyMatch(e -> e.effectId().equals("minecraft:slow_falling")),
                "chorus fruit grants Slow Falling");
        assertTrue(chorusFruit.resolveDebuffs(Function.identity(), ANY_CONFIG_VALUE).stream().anyMatch(e -> e.effectId().equals("minecraft:nausea")),
                "chorus fruit carries a Nausea debuff");

        // Enchanted golden apple SPECIAL: Absorption + Resistance.
        ZombieFoodTable.Row enchantedGoldenApple = ZombieFoodTable.lookup("minecraft:enchanted_golden_apple");
        assertEquals(FoodTier.SPECIAL, enchantedGoldenApple.tier(), "enchanted golden apple is SPECIAL");
        assertTrue(enchantedGoldenApple.resolveBuffs(Function.identity(), ANY_CONFIG_VALUE).stream().anyMatch(e -> e.effectId().equals("minecraft:resistance")),
                "enchanted golden apple grants Resistance");
    }

    @Test
    void plainCarrionAndForageFoodsCarryNoStaticEffect() {
        // The buff-less staples resolve to an empty buff list, never an EffectId.
        for (String id : new String[] {
                "minecraft:rotten_flesh", "minecraft:beef", "minecraft:porkchop", "minecraft:mutton",
                "minecraft:chicken"}) {
            ZombieFoodTable.Row row = ZombieFoodTable.lookup(id);
            assertEquals(FoodTier.CARRION, row.tier(), id + " is a plain CARRION rule");
            assertTrue(row.resolveBuffs(Function.identity(), ANY_CONFIG_VALUE).isEmpty(), id + " should carry no effect");
        }
        for (String id : new String[] {
                "minecraft:apple", "minecraft:carrot", "minecraft:potato", "minecraft:bread"}) {
            ZombieFoodTable.Row row = ZombieFoodTable.lookup(id);
            assertEquals(FoodTier.FORAGE, row.tier(), id + " is a plain FORAGE rule");
            assertTrue(row.resolveBuffs(Function.identity(), ANY_CONFIG_VALUE).isEmpty(), id + " should carry no effect");
        }
    }
}
