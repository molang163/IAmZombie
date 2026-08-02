package dev.molang.iamzombieq.rules.food;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * T2: pure, Minecraft-free data form of the §3 explicit food table. Each row's buffs/debuffs are a list of
 * PER-EFFECT builders ({@code ToIntFunction<String> -> EffectId}, never a live {@code EffectSpec}/{@code Holder}),
 * so the whole 42-row table is directly callable -- and therefore directly JUnit-behavior-testable -- without any
 * Minecraft/NeoForge on the classpath. {@link ZombieFoodRules} looks a row up by item id and converts it into the
 * existing {@link FoodRule} via {@link Row#resolveBuffs}/{@link Row#resolveDebuffs}.
 *
 * <p>T2-REV1: resolution is per-effect, not per-row-in-bulk: for each effect, its own config key(s) are read and it
 * is IMMEDIATELY handed to the effect resolver before the next effect's config is touched -- reproducing the exact
 * evaluation order the old inline {@code r.apply(EffectId.of(..., c.applyAsInt(KEY), ...))} table had. All buffs
 * resolve (in order) before any debuff is touched. A row never caches a config read: every
 * {@code resolveBuffs}/{@code resolveDebuffs} call re-invokes the supplied resolvers fresh. An empty effect list
 * never touches either resolver at all.</p>
 */
final class ZombieFoodTable {
    private ZombieFoodTable() {
    }

    /** One row: tier + flags + a per-effect builder list for buffs and for debuffs. */
    record Row(
            FoodTier tier,
            List<Function<ToIntFunction<String>, EffectId>> buffs,
            List<Function<ToIntFunction<String>, EffectId>> debuffs,
            boolean restoresBabyState,
            boolean suppressesVanillaPositiveEffects
    ) {
        /** Resolves {@link #buffs()} in order, one effect at a time: read that effect's config, then resolve it. */
        <T> List<T> resolveBuffs(Function<EffectId, T> effectResolver, ToIntFunction<String> configResolver) {
            return resolve(buffs, effectResolver, configResolver);
        }

        /** Resolves {@link #debuffs()} the same way. Callers must resolve buffs first (see {@link #resolveBuffs}). */
        <T> List<T> resolveDebuffs(Function<EffectId, T> effectResolver, ToIntFunction<String> configResolver) {
            return resolve(debuffs, effectResolver, configResolver);
        }

        private static <T> List<T> resolve(
                List<Function<ToIntFunction<String>, EffectId>> builders,
                Function<EffectId, T> effectResolver, ToIntFunction<String> configResolver) {
            List<T> resolved = new ArrayList<>(builders.size());
            for (Function<ToIntFunction<String>, EffectId> builder : builders) {
                EffectId effectId = builder.apply(configResolver); // this effect's config read(s) happen here
                resolved.add(effectResolver.apply(effectId));       // ...then it is immediately resolved
            }
            // T2-REV2: freeze this group the moment it's done -- List.copyOf both makes it immutable and throws NPE
            // immediately if the effect resolver returned null for any effect, instead of letting a null silently
            // ride into the next group (e.g. debuffs) or into FoodRule.
            return List.copyOf(resolved);
        }
    }

    static Row lookup(String itemId) {
        return TABLE.get(itemId);
    }

    /** The exact set of item ids the table covers. Backed by an immutable map, so this view cannot be mutated. */
    static Set<String> itemIds() {
        return TABLE.keySet();
    }

    // ---------- Row factories (mirror the FoodRule tier factories in ZombieFoodRules) ----------

    private static Row carrion(List<Function<ToIntFunction<String>, EffectId>> buffs) {
        return new Row(FoodTier.CARRION, buffs, List.of(), false, false);
    }

    private static Row carrionBabyRestore(List<Function<ToIntFunction<String>, EffectId>> buffs) {
        return new Row(FoodTier.CARRION, buffs, List.of(), true, false);
    }

    private static Row forage(List<Function<ToIntFunction<String>, EffectId>> buffs) {
        return new Row(FoodTier.FORAGE, buffs, List.of(), false, false);
    }

    /** T3: the std Hunger II + Nausea debuff is applied by the events layer; only the SWEET extra Slowness lives here. */
    private static Row humanCooked(boolean sweet) {
        List<Function<ToIntFunction<String>, EffectId>> debuffs = sweet
                ? List.of((ToIntFunction<String> c) -> EffectId.of(
                        "minecraft:slowness", c.applyAsInt(ZombieFoodRules.KEY_SWEET_SLOWNESS_TICKS), 0))
                : List.of();
        return new Row(FoodTier.HUMAN_COOKED, List.of(), debuffs, false, false);
    }

    private static Row special(
            List<Function<ToIntFunction<String>, EffectId>> buffs,
            List<Function<ToIntFunction<String>, EffectId>> debuffs,
            boolean suppressVanilla) {
        return new Row(FoodTier.SPECIAL, buffs, debuffs, false, suppressVanilla);
    }

    // ---------- The 42-row explicit table (identical ids/order/values to the pre-T2 EXPLICIT map) ----------

    private static final Map<String, Row> TABLE = Map.ofEntries(
            // —— T1 CARRION —— (buffs applied directly; vanilla self-debuffs are stripped by the events layer)
            // Rotten flesh is the most basic, infinitely-farmable zombie food, so it grants NO buff (per balance pass):
            // no Strength, and crucially no Saturation (that free-hunger-refill on a farmable item was overpowered).
            Map.entry("minecraft:rotten_flesh", carrion(List.of())),
            Map.entry(ZombieFoodRules.SUPER_ROTTEN_FLESH_ID, carrionBabyRestore(List.of(
                    c -> EffectId.of("minecraft:strength",
                            c.applyAsInt(ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_TICKS),
                            c.applyAsInt(ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER)),
                    c -> EffectId.of("minecraft:saturation",
                            c.applyAsInt(ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_SATURATION_TICKS), 0)))),
            Map.entry("minecraft:spider_eye", carrion(List.of(
                    c -> EffectId.of("minecraft:night_vision",
                            c.applyAsInt(ZombieFoodRules.KEY_SPIDER_EYE_NIGHT_VISION_TICKS), 0)))),
            Map.entry("minecraft:beef", carrion(List.of())),
            Map.entry("minecraft:porkchop", carrion(List.of())),
            Map.entry("minecraft:mutton", carrion(List.of())),
            Map.entry("minecraft:chicken", carrion(List.of())),
            Map.entry("minecraft:rabbit", carrion(List.of(
                    c -> EffectId.of("minecraft:speed", 20 * 8, 0),
                    c -> EffectId.of("minecraft:saturation", 20 * 4, 0)))),
            Map.entry("minecraft:cod", carrion(List.of(
                    c -> EffectId.of("minecraft:water_breathing", c.applyAsInt(ZombieFoodRules.KEY_T1_CARRION_WATER_BREATHING_TICKS), 0)))),
            Map.entry("minecraft:salmon", carrion(List.of(
                    c -> EffectId.of("minecraft:water_breathing", c.applyAsInt(ZombieFoodRules.KEY_T1_CARRION_WATER_BREATHING_TICKS), 0)))),
            Map.entry("minecraft:tropical_fish", carrion(List.of(
                    c -> EffectId.of("minecraft:water_breathing", 20 * 15, 0)))),

            // —— T2 FORAGE ——
            Map.entry("minecraft:apple", forage(List.of())),
            Map.entry("minecraft:melon_slice", forage(List.of())),
            Map.entry("minecraft:carrot", forage(List.of())),
            Map.entry("minecraft:potato", forage(List.of())),
            Map.entry("minecraft:beetroot", forage(List.of())),
            Map.entry("minecraft:sweet_berries", forage(List.of())),
            Map.entry("minecraft:glow_berries", forage(List.of(
                    c -> EffectId.of("minecraft:night_vision", 20 * 6, 0)))),
            Map.entry("minecraft:bread", forage(List.of())),
            Map.entry("minecraft:dried_kelp", forage(List.of())),

            // —— T3 HUMAN_COOKED —— (std via events layer; sweet adds Slowness)
            Map.entry("minecraft:cooked_beef", humanCooked(false)),
            Map.entry("minecraft:cooked_porkchop", humanCooked(false)),
            Map.entry("minecraft:cooked_chicken", humanCooked(false)),
            Map.entry("minecraft:cooked_mutton", humanCooked(false)),
            Map.entry("minecraft:cooked_rabbit", humanCooked(false)),
            Map.entry("minecraft:cooked_cod", humanCooked(false)),
            Map.entry("minecraft:cooked_salmon", humanCooked(false)),
            Map.entry("minecraft:baked_potato", humanCooked(false)),
            Map.entry("minecraft:golden_carrot", humanCooked(false)),
            Map.entry("minecraft:mushroom_stew", humanCooked(false)),
            Map.entry("minecraft:rabbit_stew", humanCooked(false)),
            Map.entry("minecraft:beetroot_soup", humanCooked(false)),
            Map.entry("minecraft:suspicious_stew", humanCooked(false)),
            Map.entry("minecraft:cookie", humanCooked(true)),
            Map.entry("minecraft:cake", humanCooked(true)),
            Map.entry("minecraft:pumpkin_pie", humanCooked(true)),

            // —— T4 SPECIAL ——
            Map.entry("minecraft:golden_apple", special(
                    List.of(c -> EffectId.of("minecraft:absorption", c.applyAsInt(ZombieFoodRules.KEY_GOLDEN_APPLE_ABSORPTION_TICKS), 0)),
                    List.of(c -> EffectId.of("minecraft:hunger", c.applyAsInt(ZombieFoodRules.KEY_GOLDEN_APPLE_HUNGER_TICKS), 0)),
                    true)),
            Map.entry("minecraft:enchanted_golden_apple", special(
                    List.of(
                            c -> EffectId.of("minecraft:absorption", c.applyAsInt(ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_ABSORPTION_TICKS), 1),
                            c -> EffectId.of("minecraft:resistance", c.applyAsInt(ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_RESISTANCE_TICKS), 0)),
                    List.of(c -> EffectId.of("minecraft:hunger", c.applyAsInt(ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_HUNGER_TICKS), 0)),
                    true)),
            Map.entry("minecraft:pufferfish", special(
                    List.of(
                            c -> EffectId.of("minecraft:absorption", c.applyAsInt(ZombieFoodRules.KEY_PUFFERFISH_ABSORPTION_TICKS), 0),
                            c -> EffectId.of("minecraft:regeneration",
                                    c.applyAsInt(ZombieFoodRules.KEY_PUFFERFISH_REGENERATION_TICKS),
                                    c.applyAsInt(ZombieFoodRules.KEY_PUFFERFISH_REGENERATION_AMPLIFIER))),
                    List.of(), false)),
            Map.entry("minecraft:poisonous_potato", special(
                    // Random positive handled at runtime in the events layer; no static EffectId.
                    List.of(), List.of(), false)),
            Map.entry("minecraft:chorus_fruit", special(
                    List.of(c -> EffectId.of("minecraft:slow_falling", c.applyAsInt(ZombieFoodRules.KEY_CHORUS_SLOW_FALLING_TICKS), 0)),
                    List.of(c -> EffectId.of("minecraft:nausea", c.applyAsInt(ZombieFoodRules.KEY_CHORUS_NAUSEA_TICKS), 0)),
                    false)),
            Map.entry("minecraft:honey_bottle", special(
                    List.of(),
                    List.of(c -> EffectId.of("minecraft:nausea", c.applyAsInt(ZombieFoodRules.KEY_HONEY_NAUSEA_TICKS), 0)),
                    false))
    );
}
