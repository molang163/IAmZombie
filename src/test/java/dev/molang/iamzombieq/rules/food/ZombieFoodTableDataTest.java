package dev.molang.iamzombieq.rules.food;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * T2/T2-REV1: exhaustive BEHAVIOR coverage of the full 42-row {@link ZombieFoodTable}, calling the pure table API
 * directly instead of parsing {@code ZombieFoodRules.java} source text. {@link ZombieFoodTable.Row#resolveBuffs}/
 * {@link ZombieFoodTable.Row#resolveDebuffs} resolve one effect at a time (its own config read(s), then IMMEDIATELY
 * the effect resolver, before the next effect's config is touched), so the exact resolved {@link EffectId} list (id,
 * duration, amplifier, IN ORDER) and the exact interleaved call sequence are both asserted directly.
 *
 * <p><b>Precise, never permissive.</b> Ordered effect lists are compared with exact list equality; the exact READ
 * COUNT of every config key a row can reference is asserted (a counting map, not just a presence set); two distinct
 * config resolvers prove nothing is cached, for every config-bound row (not just one representative); "actual"
 * events are recorded independently of the hand-written "expected" call sequences.</p>
 */
class ZombieFoodTableDataTest {
    /** A resolvable int: either a fixed literal or read from a config key via the resolver. */
    private sealed interface Ticks {
        int resolve(ToIntFunction<String> configResolver);

        record Fixed(int value) implements Ticks {
            public int resolve(ToIntFunction<String> configResolver) {
                return value;
            }
        }

        record FromConfig(String key) implements Ticks {
            public int resolve(ToIntFunction<String> configResolver) {
                return configResolver.applyAsInt(key);
            }
        }
    }

    private static Ticks fixed(int value) {
        return new Ticks.Fixed(value);
    }

    private static Ticks cfg(String key) {
        return new Ticks.FromConfig(key);
    }

    /** One expected effect: id + how its duration/amplifier resolve. */
    private record ExpectedEffect(String effectId, Ticks duration, Ticks amplifier) {
        EffectId resolve(ToIntFunction<String> configResolver) {
            return EffectId.of(effectId, duration.resolve(configResolver), amplifier.resolve(configResolver));
        }

        /** How many times each config key this effect touches would be read (0, 1, or 2 keys; 1 read each). */
        void addKeyCounts(Map<String, Integer> counts) {
            if (duration instanceof Ticks.FromConfig fc) {
                counts.merge(fc.key(), 1, Integer::sum);
            }
            if (amplifier instanceof Ticks.FromConfig fc) {
                counts.merge(fc.key(), 1, Integer::sum);
            }
        }
    }

    private static ExpectedEffect effect(String id, Ticks duration, Ticks amplifier) {
        return new ExpectedEffect(id, duration, amplifier);
    }

    private static ExpectedEffect effect(String id, Ticks duration) {
        return new ExpectedEffect(id, duration, fixed(0));
    }

    /** One expected row: tier + flags + the exact ordered buff/debuff lists. */
    private record ExpectedRow(
            String itemId,
            FoodTier tier,
            List<ExpectedEffect> buffs,
            List<ExpectedEffect> debuffs,
            boolean restoresBabyState,
            boolean suppressesVanillaPositiveEffects
    ) {
        Map<String, Integer> allConfigKeyCounts() {
            Map<String, Integer> counts = new HashMap<>();
            buffs.forEach(e -> e.addKeyCounts(counts));
            debuffs.forEach(e -> e.addKeyCounts(counts));
            return counts;
        }
    }

    private static ExpectedRow carrion(String itemId, List<ExpectedEffect> buffs) {
        return new ExpectedRow(itemId, FoodTier.CARRION, buffs, List.of(), false, false);
    }

    private static ExpectedRow carrionBabyRestore(String itemId, List<ExpectedEffect> buffs) {
        return new ExpectedRow(itemId, FoodTier.CARRION, buffs, List.of(), true, false);
    }

    private static ExpectedRow forage(String itemId, List<ExpectedEffect> buffs) {
        return new ExpectedRow(itemId, FoodTier.FORAGE, buffs, List.of(), false, false);
    }

    private static ExpectedRow humanCooked(String itemId, boolean sweet) {
        List<ExpectedEffect> debuffs = sweet
                ? List.of(effect("minecraft:slowness", cfg(ZombieFoodRules.KEY_SWEET_SLOWNESS_TICKS)))
                : List.of();
        return new ExpectedRow(itemId, FoodTier.HUMAN_COOKED, List.of(), debuffs, false, false);
    }

    private static ExpectedRow special(
            String itemId, List<ExpectedEffect> buffs, List<ExpectedEffect> debuffs, boolean suppress) {
        return new ExpectedRow(itemId, FoodTier.SPECIAL, buffs, debuffs, false, suppress);
    }

    // The full 42-row expectation table, mirroring ZombieFoodTable's row order exactly.
    private static Stream<ExpectedRow> rows() {
        return Stream.of(
                // —— T1 CARRION ——
                carrion("minecraft:rotten_flesh", List.of()),
                carrionBabyRestore(ZombieFoodRules.SUPER_ROTTEN_FLESH_ID, List.of(
                        effect("minecraft:strength",
                                cfg(ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_TICKS),
                                cfg(ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER)),
                        effect("minecraft:saturation", cfg(ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_SATURATION_TICKS)))),
                carrion("minecraft:spider_eye", List.of(
                        effect("minecraft:night_vision", cfg(ZombieFoodRules.KEY_SPIDER_EYE_NIGHT_VISION_TICKS)))),
                carrion("minecraft:beef", List.of()),
                carrion("minecraft:porkchop", List.of()),
                carrion("minecraft:mutton", List.of()),
                carrion("minecraft:chicken", List.of()),
                carrion("minecraft:rabbit", List.of(
                        effect("minecraft:speed", fixed(20 * 8)),
                        effect("minecraft:saturation", fixed(20 * 4)))),
                carrion("minecraft:cod", List.of(
                        effect("minecraft:water_breathing", cfg(ZombieFoodRules.KEY_T1_CARRION_WATER_BREATHING_TICKS)))),
                carrion("minecraft:salmon", List.of(
                        effect("minecraft:water_breathing", cfg(ZombieFoodRules.KEY_T1_CARRION_WATER_BREATHING_TICKS)))),
                carrion("minecraft:tropical_fish", List.of(
                        effect("minecraft:water_breathing", fixed(20 * 15)))),

                // —— T2 FORAGE ——
                forage("minecraft:apple", List.of()),
                forage("minecraft:melon_slice", List.of()),
                forage("minecraft:carrot", List.of()),
                forage("minecraft:potato", List.of()),
                forage("minecraft:beetroot", List.of()),
                forage("minecraft:sweet_berries", List.of()),
                forage("minecraft:glow_berries", List.of(effect("minecraft:night_vision", fixed(20 * 6)))),
                forage("minecraft:bread", List.of()),
                forage("minecraft:dried_kelp", List.of()),

                // —— T3 HUMAN_COOKED ——
                humanCooked("minecraft:cooked_beef", false),
                humanCooked("minecraft:cooked_porkchop", false),
                humanCooked("minecraft:cooked_chicken", false),
                humanCooked("minecraft:cooked_mutton", false),
                humanCooked("minecraft:cooked_rabbit", false),
                humanCooked("minecraft:cooked_cod", false),
                humanCooked("minecraft:cooked_salmon", false),
                humanCooked("minecraft:baked_potato", false),
                humanCooked("minecraft:golden_carrot", false),
                humanCooked("minecraft:mushroom_stew", false),
                humanCooked("minecraft:rabbit_stew", false),
                humanCooked("minecraft:beetroot_soup", false),
                humanCooked("minecraft:suspicious_stew", false),
                humanCooked("minecraft:cookie", true),
                humanCooked("minecraft:cake", true),
                humanCooked("minecraft:pumpkin_pie", true),

                // —— T4 SPECIAL ——
                special("minecraft:golden_apple",
                        List.of(effect("minecraft:absorption", cfg(ZombieFoodRules.KEY_GOLDEN_APPLE_ABSORPTION_TICKS))),
                        List.of(effect("minecraft:hunger", cfg(ZombieFoodRules.KEY_GOLDEN_APPLE_HUNGER_TICKS))),
                        true),
                special("minecraft:enchanted_golden_apple",
                        List.of(effect("minecraft:absorption",
                                        cfg(ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_ABSORPTION_TICKS), fixed(1)),
                                effect("minecraft:resistance", cfg(ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_RESISTANCE_TICKS))),
                        List.of(effect("minecraft:hunger", cfg(ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_HUNGER_TICKS))),
                        true),
                special("minecraft:pufferfish",
                        List.of(effect("minecraft:absorption", cfg(ZombieFoodRules.KEY_PUFFERFISH_ABSORPTION_TICKS)),
                                effect("minecraft:regeneration",
                                        cfg(ZombieFoodRules.KEY_PUFFERFISH_REGENERATION_TICKS),
                                        cfg(ZombieFoodRules.KEY_PUFFERFISH_REGENERATION_AMPLIFIER))),
                        List.of(), false),
                special("minecraft:poisonous_potato", List.of(), List.of(), false),
                special("minecraft:chorus_fruit",
                        List.of(effect("minecraft:slow_falling", cfg(ZombieFoodRules.KEY_CHORUS_SLOW_FALLING_TICKS))),
                        List.of(effect("minecraft:nausea", cfg(ZombieFoodRules.KEY_CHORUS_NAUSEA_TICKS))),
                        false),
                special("minecraft:honey_bottle",
                        List.of(),
                        List.of(effect("minecraft:nausea", cfg(ZombieFoodRules.KEY_HONEY_NAUSEA_TICKS))),
                        false)
        );
    }

    private static Stream<Arguments> tableRows() {
        return rows().map(row -> Arguments.of(row.itemId(), row));
    }

    // Every KEY_* the table can read maps to a distinct sentinel value, so "the resolved amount equals the value
    // this resolver returned for that exact key" only holds when the row queried the RIGHT key.
    private static int sentinelFor(String key) {
        return 1_000_000 + Math.floorMod(key.hashCode(), 900_000);
    }

    /** Records each config-key read into a counting map (read COUNT, not just presence), keyed by KEY_* name. */
    private static ToIntFunction<String> countingConfigResolver(Map<String, Integer> readCounts) {
        return key -> {
            readCounts.merge(key, 1, Integer::sum);
            return sentinelFor(key);
        };
    }

    @Test
    void theTableExposesExactlyTheFortyTwoExpectedItemIds() {
        Set<String> expectedIds = rows().map(ExpectedRow::itemId).collect(java.util.stream.Collectors.toSet());
        assertEquals(42, expectedIds.size(), "the expectation table must enumerate exactly 42 distinct ids");
        assertEquals(expectedIds, ZombieFoodTable.itemIds(),
                "ZombieFoodTable must expose exactly these 42 ids -- no missing, no extra");
    }

    @Test
    void theItemIdSetCannotBeModifiedByCallers() {
        Set<String> ids = ZombieFoodTable.itemIds();
        assertThrows(UnsupportedOperationException.class, () -> ids.add("modid:not_a_real_food"),
                "add() must be rejected");
        assertThrows(UnsupportedOperationException.class, () -> ids.remove("minecraft:rotten_flesh"),
                "remove() of a genuinely present key must still be rejected, not silently accepted");
        assertThrows(UnsupportedOperationException.class, ids::clear,
                "clear() must be rejected");
        assertThrows(UnsupportedOperationException.class, () -> {
            var it = ids.iterator();
            it.next();
            it.remove();
        }, "iterator().remove() must be rejected");
        // None of the rejected attempts should have silently succeeded in part.
        assertEquals(42, ZombieFoodTable.itemIds().size(), "the table must still expose all 42 ids after the rejected mutations");
    }

    @Test
    void rowBuffAndDebuffBuilderListsCannotBeModified() {
        ZombieFoodTable.Row spiderEye = ZombieFoodTable.lookup("minecraft:spider_eye");
        assertThrows(UnsupportedOperationException.class, () -> spiderEye.buffs().add(c -> EffectId.of("x", 0, 0)),
                "a row's buff builder list must be immutable");
        ZombieFoodTable.Row cake = ZombieFoodTable.lookup("minecraft:cake");
        assertThrows(UnsupportedOperationException.class, () -> cake.debuffs().add(c -> EffectId.of("x", 0, 0)),
                "a row's debuff builder list must be immutable");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tableRows")
    void everyExplicitEntryPinsAllFieldsPrecisely(String itemId, ExpectedRow expected) {
        ZombieFoodTable.Row row = ZombieFoodTable.lookup(itemId);
        assertNotNull(row, itemId + " must be present in the explicit table");
        assertEquals(expected.tier(), row.tier(), itemId + " tier mismatch");
        assertEquals(expected.restoresBabyState(), row.restoresBabyState(), itemId + " restoresBabyState mismatch");
        assertEquals(expected.suppressesVanillaPositiveEffects(), row.suppressesVanillaPositiveEffects(),
                itemId + " suppressesVanillaPositiveEffects mismatch");

        Map<String, Integer> readCounts = new HashMap<>();
        ToIntFunction<String> resolver = countingConfigResolver(readCounts);
        List<EffectId> actualBuffs = row.resolveBuffs(Function.identity(), resolver);
        List<EffectId> actualDebuffs = row.resolveDebuffs(Function.identity(), resolver);

        // A separate, non-counting resolver builds the expected values, so computing the expectation cannot itself
        // inflate the read-count map being asserted against below (independent recorder vs. independent expectation).
        ToIntFunction<String> plainResolver = ZombieFoodTableDataTest::sentinelFor;
        List<EffectId> expectedBuffs = expected.buffs().stream().map(e -> e.resolve(plainResolver)).toList();
        List<EffectId> expectedDebuffs = expected.debuffs().stream().map(e -> e.resolve(plainResolver)).toList();
        assertEquals(expectedBuffs, actualBuffs, itemId + " buffs must match exactly, in order");
        assertEquals(expectedDebuffs, actualDebuffs, itemId + " debuffs must match exactly, in order");

        assertEquals(expected.allConfigKeyCounts(), readCounts,
                itemId + " must read exactly its own required config keys, each exactly the expected number of times");
    }

    @Test
    void everyConfigBoundRowReReadsOnEveryCallAcrossTheWholeTableNotJustOneSample() {
        // T2-REV1: every config-bound row -- not just spider_eye -- must prove no caching, using two DIFFERENT
        // resolvers per row.
        List<ExpectedRow> configBoundRows = rows().filter(r -> !r.allConfigKeyCounts().isEmpty()).toList();
        assertTrue(configBoundRows.size() >= 10, "expected a healthy spread of config-bound rows to check");

        for (ExpectedRow expected : configBoundRows) {
            ZombieFoodTable.Row row = ZombieFoodTable.lookup(expected.itemId());
            List<EffectId> withResolverA = concat(
                    row.resolveBuffs(Function.identity(), key -> 111),
                    row.resolveDebuffs(Function.identity(), key -> 111));
            List<EffectId> withResolverB = concat(
                    row.resolveBuffs(Function.identity(), key -> 222),
                    row.resolveDebuffs(Function.identity(), key -> 222));
            assertNotEquals(withResolverA, withResolverB,
                    expected.itemId() + " must re-resolve its config-bound effect(s) fresh on every call, not cache");
        }
    }

    private static List<EffectId> concat(List<EffectId> a, List<EffectId> b) {
        List<EffectId> combined = new ArrayList<>(a);
        combined.addAll(b);
        return combined;
    }

    @Test
    void hardCodedDurationsIgnoreTheConfigResolverEntirely() {
        ZombieFoodTable.Row rabbit = ZombieFoodTable.lookup("minecraft:rabbit");
        Map<String, Integer> readCounts = new HashMap<>();
        List<EffectId> buffs = rabbit.resolveBuffs(Function.identity(), countingConfigResolver(readCounts));

        assertEquals(List.of(EffectId.of("minecraft:speed", 20 * 8, 0), EffectId.of("minecraft:saturation", 20 * 4, 0)),
                buffs);
        assertTrue(readCounts.isEmpty(), "a hard-coded-duration row must never consult the config resolver");
    }

    @Test
    void superRottenFleshCompleteCallOrder() {
        // Expected sequence hand-written independently of the recorder used to capture the actual one.
        List<String> expected = List.of(
                "config:" + ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_TICKS,
                "config:" + ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER,
                "resolve:minecraft:strength",
                "config:" + ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_SATURATION_TICKS,
                "resolve:minecraft:saturation"
        );

        List<String> actual = new ArrayList<>();
        ZombieFoodTable.Row row = ZombieFoodTable.lookup(ZombieFoodRules.SUPER_ROTTEN_FLESH_ID);
        ToIntFunction<String> configResolver = key -> {
            actual.add("config:" + key);
            return 7;
        };
        Function<EffectId, String> effectResolver = id -> {
            actual.add("resolve:" + id.effectId());
            return id.effectId();
        };

        row.resolveBuffs(effectResolver, configResolver);

        assertEquals(expected, actual,
                "each effect's config read(s) must complete, then its resolver call must happen, before the next effect starts");
    }

    @Test
    void enchantedGoldenAppleTwoBuffsThenOneDebuffCallOrder() {
        List<String> expected = List.of(
                "config:" + ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_ABSORPTION_TICKS,
                "resolve:minecraft:absorption",
                "config:" + ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_RESISTANCE_TICKS,
                "resolve:minecraft:resistance",
                "config:" + ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_HUNGER_TICKS,
                "resolve:minecraft:hunger"
        );

        List<String> actual = new ArrayList<>();
        ZombieFoodTable.Row row = ZombieFoodTable.lookup("minecraft:enchanted_golden_apple");
        ToIntFunction<String> configResolver = key -> {
            actual.add("config:" + key);
            return 3;
        };
        Function<EffectId, String> effectResolver = id -> {
            actual.add("resolve:" + id.effectId());
            return id.effectId();
        };

        row.resolveBuffs(effectResolver, configResolver);
        row.resolveDebuffs(effectResolver, configResolver);

        assertEquals(expected, actual,
                "both buffs must resolve completely (config then resolver, each) before the debuff is touched at all");
    }

    @Test
    void effectResolverThrowingStopsBeforeReadingLaterEffectsConfig() {
        List<String> readKeys = new ArrayList<>();
        ZombieFoodTable.Row row = ZombieFoodTable.lookup(ZombieFoodRules.SUPER_ROTTEN_FLESH_ID);
        ToIntFunction<String> configResolver = key -> {
            readKeys.add(key);
            return 1;
        };
        Function<EffectId, String> throwingResolver = id -> {
            throw new RuntimeException("boom: " + id.effectId());
        };

        assertThrows(RuntimeException.class, () -> row.resolveBuffs(throwingResolver, configResolver));

        assertTrue(readKeys.contains(ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_TICKS));
        assertTrue(readKeys.contains(ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER));
        assertTrue(readKeys.stream().noneMatch(ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_SATURATION_TICKS::equals),
                "once the first effect's resolver throws, the second effect's config must never be read");
    }

    @Test
    void noEffectEntryWithNullEffectResolverStillReturnsNormally() {
        // rotten_flesh has zero buffs and zero debuffs, so a null effectResolver must never actually be invoked.
        var rule = assertDoesNotThrow(() ->
                ZombieFoodRules.ruleFor("minecraft:rotten_flesh", Set.of(), null, key -> 0));
        assertEquals(FoodTier.CARRION, rule.tier());
        assertTrue(rule.buffs().isEmpty());
        assertTrue(rule.debuffs().isEmpty());
    }

    // ---------- T2-REV2: the effect resolver returning null must fail fast, never silently ride into the next
    // group or into FoodRule. ----------

    @Test
    void goldenAppleBuffResolverReturningNullThrowsNpeAndNeverTouchesTheHungerDebuff() {
        List<String> readKeys = new ArrayList<>();
        List<String> resolverCalls = new ArrayList<>();
        ToIntFunction<String> configResolver = key -> {
            readKeys.add(key);
            return 5;
        };
        Function<EffectId, dev.molang.iamzombieq.rules.EffectSpec> nullReturningResolver = id -> {
            resolverCalls.add(id.effectId());
            return null;
        };

        // ruleFor resolves buffs (absorption) before debuffs (hunger); the null the resolver hands back for
        // absorption must blow up immediately, before hunger's config or resolver is ever reached.
        assertThrows(NullPointerException.class, () ->
                ZombieFoodRules.ruleFor("minecraft:golden_apple", Set.of(), nullReturningResolver, configResolver));

        assertEquals(List.of("minecraft:absorption"), resolverCalls,
                "the resolver must have been called for the buff (and only the buff) before the NPE");
        assertTrue(readKeys.contains(ZombieFoodRules.KEY_GOLDEN_APPLE_ABSORPTION_TICKS),
                "the buff's own config must have been read before its resolver returned null");
        assertFalse(readKeys.contains(ZombieFoodRules.KEY_GOLDEN_APPLE_HUNGER_TICKS),
                "the hunger debuff's config must never be read once the buff resolver returned null");
    }

    @Test
    void resolveBuffsResultCannotBeModified() {
        ZombieFoodTable.Row goldenApple = ZombieFoodTable.lookup("minecraft:golden_apple");
        List<EffectId> buffs = goldenApple.resolveBuffs(Function.identity(), key -> 1);

        assertThrows(UnsupportedOperationException.class, buffs::clear, "resolveBuffs()'s result must reject clear()");
        assertThrows(UnsupportedOperationException.class, () -> buffs.remove(0),
                "resolveBuffs()'s result must reject remove()");
    }

    @Test
    void resolveDebuffsResultCannotBeModified() {
        ZombieFoodTable.Row goldenApple = ZombieFoodTable.lookup("minecraft:golden_apple");
        List<EffectId> debuffs = goldenApple.resolveDebuffs(Function.identity(), key -> 1);

        assertThrows(UnsupportedOperationException.class, debuffs::clear, "resolveDebuffs()'s result must reject clear()");
        assertThrows(UnsupportedOperationException.class, () -> debuffs.remove(0),
                "resolveDebuffs()'s result must reject remove()");
    }
}
