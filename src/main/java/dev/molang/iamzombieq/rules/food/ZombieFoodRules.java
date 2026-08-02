package dev.molang.iamzombieq.rules.food;
import dev.molang.iamzombieq.rules.EffectSpec;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ZombieFoodRules {
    public static final String SUPER_ROTTEN_FLESH_ID = "iamzombieq:super_rotten_flesh";

    // Kept: the human-food (T3 std) punishment defaults reused by the canonical SERVER holder + events layer.
    public static final int DEFAULT_HUMAN_FOOD_NAUSEA_DURATION_TICKS = 20 * 12;
    public static final int DEFAULT_HUMAN_FOOD_HUNGER_DURATION_TICKS = 20 * 18;
    public static final int DEFAULT_HUMAN_FOOD_HUNGER_AMPLIFIER = 2;
    // Kept: existing special-food defaults still referenced by the canonical SERVER holder.
    public static final int DEFAULT_SPIDER_EYE_NIGHT_VISION_DURATION_TICKS = 20 * 45;
    public static final int DEFAULT_PUFFERFISH_ABSORPTION_DURATION_TICKS = 20 * 120;
    public static final int DEFAULT_PUFFERFISH_REGENERATION_DURATION_TICKS = 20 * 5;
    public static final int DEFAULT_PUFFERFISH_REGENERATION_AMPLIFIER = 1;
    public static final int DEFAULT_POISONOUS_POTATO_POSITIVE_DURATION_TICKS = 20 * 25;
    public static final int DEFAULT_SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS = 20 * 45;
    public static final int DEFAULT_SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER = 0;
    public static final int DEFAULT_SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS = 20 * 8;

    private static final Set<String> DEFAULT_ZOMBIE_FOODS = Set.of(
            "minecraft:rotten_flesh",
            "minecraft:spider_eye",
            "minecraft:poisonous_potato",
            "minecraft:pufferfish",
            "minecraft:beef",
            "minecraft:porkchop",
            "minecraft:mutton",
            "minecraft:chicken",
            "minecraft:rabbit",
            "minecraft:cod",
            "minecraft:salmon",
            "minecraft:tropical_fish",
            SUPER_ROTTEN_FLESH_ID
    );

    private ZombieFoodRules() {
    }

    // ---------- Public API (FoodRule signatures preserved; ruleFor/ruleForStack take an effect resolver + a
    // config resolver). The config resolver maps a semantic duration/amplifier key (e.g. "spider_eye.night_vision.ticks")
    // to its configured int value; the events/client layer supplies it so this class needs no config-holder import
    // (R2 decoupling; DEC-3-neutral: the not-loaded fallback lives in that resolver, unchanged, see ZombieFoodEvents). ----------

    public static FoodRule defaultRuleFor(
            String itemId, Function<EffectId, EffectSpec> effectResolver, ToIntFunction<String> configResolver) {
        return ruleFor(itemId, DEFAULT_ZOMBIE_FOODS, effectResolver, configResolver);
    }

    /**
     * Id-only resolution (no ItemStack, so no tag layer) for the tooltip caller. Resolves via the explicit §3 map,
     * then config {@code ZOMBIE_FOODS} (=> T1 CARRION), then DEFAULT T3 HUMAN_COOKED. The {@code effectResolver}
     * converts the explicit table's Minecraft-free {@link EffectId}s into live {@link EffectSpec}s (a
     * {@code Holder<MobEffect>} + duration + amplifier), and the {@code configResolver} supplies the configured
     * duration/amplifier for a semantic key; both are supplied by the events layer so this class stays Minecraft-free
     * and free of any config-holder import.
     */
    public static FoodRule ruleFor(
            String itemId, Set<String> zombieFoods,
            Function<EffectId, EffectSpec> effectResolver, ToIntFunction<String> configResolver) {
        String id = itemId.toLowerCase(Locale.ROOT);
        ZombieFoodTable.Row row = ZombieFoodTable.lookup(id);
        if (row != null) {
            return toFoodRule(row, effectResolver, configResolver);
        }
        if (zombieFoods.contains(id)) {
            return carrion(List.of());
        }
        return humanCookedDefault();
    }

    /**
     * ItemStack-aware resolution for the events layer, running the full §4 catch-all (the mod tier tags and
     * {@link ItemTags#WOLF_FOOD}). {@code stack.is(TagKey)} is confirmed usable in the repo's ItemStackMixin.
     * Precedence: explicit map -> config ZOMBIE_FOODS -> tier tags -> WOLF_FOOD -> DEFAULT T3. The
     * {@code effectResolver} converts explicit-table {@link EffectId}s into live {@link EffectSpec}s and the
     * {@code configResolver} supplies configured durations/amplifiers (see {@link #ruleFor}).
     */
    public static FoodRule ruleForStack(
            ItemStack stack, String itemId, Set<String> zombieFoods,
            Function<EffectId, EffectSpec> effectResolver, ToIntFunction<String> configResolver) {
        String id = itemId.toLowerCase(Locale.ROOT);
        ZombieFoodTable.Row row = ZombieFoodTable.lookup(id);
        if (row != null) {
            return toFoodRule(row, effectResolver, configResolver);
        }
        if (zombieFoods.contains(id)) {
            return carrion(List.of());
        }
        // Mod tier tags (§4 catch-all step 2). Built on demand so this class stays loadable without Minecraft on the
        // classpath (e.g. in plain unit tests); ruleForStack is only ever reached at runtime where Minecraft is present.
        if (stack.is(itemTag("carrion"))) {
            return carrion(List.of());
        }
        if (stack.is(itemTag("forage"))) {
            return forage(List.of());
        }
        if (stack.is(itemTag("human_cooked"))) {
            return humanCookedDefault();
        }
        if (stack.is(itemTag("special"))) {
            return special(List.of(), List.of(), false);
        }
        if (stack.is(ItemTags.WOLF_FOOD)) {
            return carrion(List.of());
        }
        return humanCookedDefault();
    }

    /** Converts a pure {@link ZombieFoodTable.Row} into a {@link FoodRule} by resolving its buffs/debuffs through the
     * caller-supplied effect + config resolvers (see {@link #ruleFor}). */
    private static FoodRule toFoodRule(
            ZombieFoodTable.Row row, Function<EffectId, EffectSpec> effectResolver, ToIntFunction<String> configResolver) {
        List<EffectSpec> buffs = row.resolveBuffs(effectResolver, configResolver);
        List<EffectSpec> debuffs = row.resolveDebuffs(effectResolver, configResolver);
        return new FoodRule(row.tier(), buffs, debuffs, row.restoresBabyState(), row.suppressesVanillaPositiveEffects());
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("iamzombieq", path));
    }

    // ---------- Config resolver keys ----------
    // Semantic (Minecraft/config-free) names for the configurable durations/amplifiers the explicit table and the sweet
    // human-food debuff read. The events/client layer's ToIntFunction<String> config resolver maps each key to the
    // matching configured int value, applying the SAME not-loaded fallback the rules layer used to do inline (try the
    // live value, else the registered default). The R2 decoupling only moved WHERE the value is read; the per-key
    // mapping is 1:1 with the former inline reads, so the resolved durations are byte-for-byte unchanged.
    public static final String KEY_SWEET_SLOWNESS_TICKS = "sweet.slowness.ticks";
    public static final String KEY_SUPER_ROTTEN_FLESH_STRENGTH_TICKS = "super_rotten_flesh.strength.ticks";
    public static final String KEY_SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER = "super_rotten_flesh.strength.amplifier";
    public static final String KEY_SUPER_ROTTEN_FLESH_SATURATION_TICKS = "super_rotten_flesh.saturation.ticks";
    public static final String KEY_SPIDER_EYE_NIGHT_VISION_TICKS = "spider_eye.night_vision.ticks";
    public static final String KEY_T1_CARRION_WATER_BREATHING_TICKS = "t1_carrion.water_breathing.ticks";
    public static final String KEY_GOLDEN_APPLE_ABSORPTION_TICKS = "golden_apple.absorption.ticks";
    public static final String KEY_GOLDEN_APPLE_HUNGER_TICKS = "golden_apple.hunger.ticks";
    public static final String KEY_ENCHANTED_GOLDEN_APPLE_ABSORPTION_TICKS = "enchanted_golden_apple.absorption.ticks";
    public static final String KEY_ENCHANTED_GOLDEN_APPLE_RESISTANCE_TICKS = "enchanted_golden_apple.resistance.ticks";
    public static final String KEY_ENCHANTED_GOLDEN_APPLE_HUNGER_TICKS = "enchanted_golden_apple.hunger.ticks";
    public static final String KEY_PUFFERFISH_ABSORPTION_TICKS = "pufferfish.absorption.ticks";
    public static final String KEY_PUFFERFISH_REGENERATION_TICKS = "pufferfish.regeneration.ticks";
    public static final String KEY_PUFFERFISH_REGENERATION_AMPLIFIER = "pufferfish.regeneration.amplifier";
    public static final String KEY_CHORUS_SLOW_FALLING_TICKS = "chorus.slow_falling.ticks";
    public static final String KEY_CHORUS_NAUSEA_TICKS = "chorus.nausea.ticks";
    public static final String KEY_HONEY_NAUSEA_TICKS = "honey.nausea.ticks";

    public static boolean isFoodRuleTarget(String itemId) {
        return !"minecraft:milk_bucket".equals(itemId.toLowerCase(Locale.ROOT));
    }

    /**
     * Vanilla foods that a zombie player should be able to eat even at a full hunger bar, because their value to a
     * zombie is the substituted buff rather than the nutrition. The mod's own {@link #SUPER_ROTTEN_FLESH_ID} item is
     * made always-edible via its registration (FoodProperties.alwaysEdible), so it is also reported here so callers
     * reasoning purely about ids agree with that registration. This set is unchanged.
     */
    public static boolean isAlwaysEdibleForZombies(String itemId) {
        if (itemId == null) {
            return false;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        return "minecraft:pufferfish".equals(id)
                || "minecraft:spider_eye".equals(id)
                || "minecraft:poisonous_potato".equals(id)
                || SUPER_ROTTEN_FLESH_ID.equals(id);
    }

    /** Explicit tooltip mapping by tier (replaces the old effects().isEmpty() implicit logic). */
    public static String tooltipKey(FoodRule rule) {
        return rule.tier().tooltipKey();
    }

    public static HumanFoodPunishmentSettings humanFoodPunishmentSettings(
            int nauseaDurationTicks,
            int hungerDurationTicks,
            int hungerAmplifier
    ) {
        return new HumanFoodPunishmentSettings(
                Math.max(0, nauseaDurationTicks),
                Math.max(0, hungerDurationTicks),
                Math.max(0, hungerAmplifier)
        );
    }

    public static PreservedFoodPunishments preserveExistingFoodPunishments(
            PreservedEffect hunger,
            PreservedEffect nausea,
            PreservedEffect poison
    ) {
        return new PreservedFoodPunishments(hunger, nausea, poison);
    }

    public static PreservedGoldenAppleEffects preserveExistingGoldenAppleEffects(
            PreservedEffect regeneration,
            PreservedEffect absorption,
            PreservedEffect resistance,
            PreservedEffect fireResistance
    ) {
        return new PreservedGoldenAppleEffects(regeneration, absorption, resistance, fireResistance);
    }

    // ---------- Tier factories ----------

    private static FoodRule carrion(List<EffectSpec> buffs) {
        return new FoodRule(FoodTier.CARRION, buffs, List.of(), false, false);
    }

    private static FoodRule carrionBabyRestore(List<EffectSpec> buffs) {
        return new FoodRule(FoodTier.CARRION, buffs, List.of(), true, false);
    }

    private static FoodRule forage(List<EffectSpec> buffs) {
        return new FoodRule(FoodTier.FORAGE, buffs, List.of(), false, false);
    }

    /** T3 fallback shape used by the catch-all/tag paths (never sweet -- the explicit table's own sweet cookie/cake/
     * pumpkin_pie rows are resolved via {@link ZombieFoodTable} instead): no inline buff or debuff; the std Hunger II
     * + Nausea punishment is applied by the events layer via humanFood* config (see appliesHumanFoodPunishment()). */
    private static FoodRule humanCookedDefault() {
        return new FoodRule(FoodTier.HUMAN_COOKED, List.of(), List.of(), false, false);
    }

    private static FoodRule special(List<EffectSpec> buffs, List<EffectSpec> debuffs, boolean suppressVanilla) {
        return new FoodRule(FoodTier.SPECIAL, buffs, debuffs, false, suppressVanilla);
    }

    public record HumanFoodPunishmentSettings(
            int nauseaDurationTicks,
            int hungerDurationTicks,
            int hungerAmplifier
    ) {
    }

    public record PreservedEffect(boolean present, int durationTicks, int amplifier) {
        public static PreservedEffect absent() {
            return new PreservedEffect(false, 0, 0);
        }
    }

    public record PreservedFoodPunishments(
            PreservedEffect hunger,
            PreservedEffect nausea,
            PreservedEffect poison
    ) {
    }

    public record PreservedGoldenAppleEffects(
            PreservedEffect regeneration,
            PreservedEffect absorption,
            PreservedEffect resistance,
            PreservedEffect fireResistance
    ) {
    }
}
