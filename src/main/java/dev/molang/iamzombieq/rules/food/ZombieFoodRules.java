package dev.molang.iamzombieq.rules.food;
import dev.molang.iamzombieq.rules.EffectSpec;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import dev.molang.iamzombieq.IAmZombieConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ZombieFoodRules {
    public static final String SUPER_ROTTEN_FLESH_ID = "iamzombieq:super_rotten_flesh";

    // Kept: the human-food (T3 std) punishment defaults reused by IAmZombieConfig + the events layer.
    public static final int DEFAULT_HUMAN_FOOD_NAUSEA_DURATION_TICKS = 20 * 12;
    public static final int DEFAULT_HUMAN_FOOD_HUNGER_DURATION_TICKS = 20 * 18;
    public static final int DEFAULT_HUMAN_FOOD_HUNGER_AMPLIFIER = 2;
    // Kept: existing special-food defaults still referenced by IAmZombieConfig (values now read inline below).
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

    // ---------- Public API (signatures preserved) ----------

    public static FoodRule defaultRuleFor(String itemId) {
        return ruleFor(itemId, DEFAULT_ZOMBIE_FOODS);
    }

    /**
     * Id-only resolution (no ItemStack, so no tag layer) for the tooltip caller. Resolves via the explicit §3 map,
     * then config {@code ZOMBIE_FOODS} (=> T1 CARRION), then DEFAULT T3 HUMAN_COOKED.
     */
    public static FoodRule ruleFor(String itemId, Set<String> zombieFoods) {
        String id = itemId.toLowerCase(Locale.ROOT);
        Supplier<FoodRule> explicit = EXPLICIT.get(id);
        if (explicit != null) {
            return explicit.get();
        }
        if (zombieFoods.contains(id)) {
            return carrion(List.of());
        }
        return humanCooked(false);
    }

    /**
     * ItemStack-aware resolution for the events layer, running the full §4 catch-all (the mod tier tags and
     * {@link ItemTags#WOLF_FOOD}). {@code stack.is(TagKey)} is confirmed usable in the repo's ItemStackMixin.
     * Precedence: explicit map -> config ZOMBIE_FOODS -> tier tags -> WOLF_FOOD -> DEFAULT T3.
     */
    public static FoodRule ruleForStack(ItemStack stack, String itemId, Set<String> zombieFoods) {
        String id = itemId.toLowerCase(Locale.ROOT);
        Supplier<FoodRule> explicit = EXPLICIT.get(id);
        if (explicit != null) {
            return explicit.get();
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
            return humanCooked(false);
        }
        if (stack.is(itemTag("special"))) {
            return special(List.of(), List.of(), false);
        }
        if (stack.is(ItemTags.WOLF_FOOD)) {
            return carrion(List.of());
        }
        return humanCooked(false);
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("iamzombieq", path));
    }

    /**
     * Read a config duration at call time, falling back to its registered default if the config spec is not yet
     * loaded (e.g. in plain unit tests that never bootstrap the mod). At runtime the config is always loaded, so this
     * returns the configured value.
     */
    private static int cfg(ModConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return value.getDefault();
        }
    }

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

    /** T3: the std Hunger II + Nausea debuff is applied by the events layer via humanFood* config (see
     * appliesHumanFoodPunishment()); only the SWEET extra Slowness rides in debuffs here. */
    private static FoodRule humanCooked(boolean sweet) {
        List<EffectSpec> debuffs = sweet
                ? List.of(EffectSpec.of(MobEffects.SLOWNESS, cfg(IAmZombieConfig.SWEET_SLOWNESS_DURATION_TICKS), 0))
                : List.of();
        return new FoodRule(FoodTier.HUMAN_COOKED, List.of(), debuffs, false, false);
    }

    private static FoodRule special(List<EffectSpec> buffs, List<EffectSpec> debuffs, boolean suppressVanilla) {
        return new FoodRule(FoodTier.SPECIAL, buffs, debuffs, false, suppressVanilla);
    }

    // ---------- §3 explicit table (each entry a supplier; durations read from config at call time) ----------
    private static final Map<String, Supplier<FoodRule>> EXPLICIT = Map.ofEntries(
            // —— T1 CARRION —— (buffs applied directly; vanilla self-debuffs are stripped by the events layer)
            // Rotten flesh is the most basic, infinitely-farmable zombie food, so it grants NO buff (per balance pass):
            // no Strength, and crucially no Saturation (that free-hunger-refill on a farmable item was overpowered).
            Map.entry("minecraft:rotten_flesh", () -> carrion(List.of())),
            Map.entry(SUPER_ROTTEN_FLESH_ID, () -> carrionBabyRestore(List.of(
                    EffectSpec.of(MobEffects.STRENGTH,
                            cfg(IAmZombieConfig.SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS),
                            cfg(IAmZombieConfig.SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER)),
                    EffectSpec.of(MobEffects.SATURATION,
                            cfg(IAmZombieConfig.SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS), 0)))),
            Map.entry("minecraft:spider_eye", () -> carrion(List.of(
                    EffectSpec.of(MobEffects.NIGHT_VISION,
                            cfg(IAmZombieConfig.SPIDER_EYE_NIGHT_VISION_DURATION_TICKS), 0)))),
            Map.entry("minecraft:beef", () -> carrion(List.of())),
            Map.entry("minecraft:porkchop", () -> carrion(List.of())),
            Map.entry("minecraft:mutton", () -> carrion(List.of())),
            Map.entry("minecraft:chicken", () -> carrion(List.of())),
            Map.entry("minecraft:rabbit", () -> carrion(List.of(
                    EffectSpec.of(MobEffects.SPEED, 20 * 8, 0),
                    EffectSpec.of(MobEffects.SATURATION, 20 * 4, 0)))),
            Map.entry("minecraft:cod", () -> carrion(List.of(
                    EffectSpec.of(MobEffects.WATER_BREATHING, cfg(IAmZombieConfig.T1_CARRION_WATER_BREATHING_DURATION_TICKS), 0)))),
            Map.entry("minecraft:salmon", () -> carrion(List.of(
                    EffectSpec.of(MobEffects.WATER_BREATHING, cfg(IAmZombieConfig.T1_CARRION_WATER_BREATHING_DURATION_TICKS), 0)))),
            Map.entry("minecraft:tropical_fish", () -> carrion(List.of(
                    EffectSpec.of(MobEffects.WATER_BREATHING, 20 * 15, 0)))),

            // —— T2 FORAGE ——
            Map.entry("minecraft:apple", () -> forage(List.of())),
            Map.entry("minecraft:melon_slice", () -> forage(List.of())),
            Map.entry("minecraft:carrot", () -> forage(List.of())),
            Map.entry("minecraft:potato", () -> forage(List.of())),
            Map.entry("minecraft:beetroot", () -> forage(List.of())),
            Map.entry("minecraft:sweet_berries", () -> forage(List.of())),
            Map.entry("minecraft:glow_berries", () -> forage(List.of(
                    EffectSpec.of(MobEffects.NIGHT_VISION, 20 * 6, 0)))),
            Map.entry("minecraft:bread", () -> forage(List.of())),
            Map.entry("minecraft:dried_kelp", () -> forage(List.of())),

            // —— T3 HUMAN_COOKED —— (std via events layer; sweet adds Slowness)
            Map.entry("minecraft:cooked_beef", () -> humanCooked(false)),
            Map.entry("minecraft:cooked_porkchop", () -> humanCooked(false)),
            Map.entry("minecraft:cooked_chicken", () -> humanCooked(false)),
            Map.entry("minecraft:cooked_mutton", () -> humanCooked(false)),
            Map.entry("minecraft:cooked_rabbit", () -> humanCooked(false)),
            Map.entry("minecraft:cooked_cod", () -> humanCooked(false)),
            Map.entry("minecraft:cooked_salmon", () -> humanCooked(false)),
            Map.entry("minecraft:baked_potato", () -> humanCooked(false)),
            Map.entry("minecraft:golden_carrot", () -> humanCooked(false)),
            Map.entry("minecraft:mushroom_stew", () -> humanCooked(false)),
            Map.entry("minecraft:rabbit_stew", () -> humanCooked(false)),
            Map.entry("minecraft:beetroot_soup", () -> humanCooked(false)),
            Map.entry("minecraft:suspicious_stew", () -> humanCooked(false)),
            Map.entry("minecraft:cookie", () -> humanCooked(true)),
            Map.entry("minecraft:cake", () -> humanCooked(true)),
            Map.entry("minecraft:pumpkin_pie", () -> humanCooked(true)),

            // —— T4 SPECIAL ——
            Map.entry("minecraft:golden_apple", () -> special(
                    List.of(EffectSpec.of(MobEffects.ABSORPTION, cfg(IAmZombieConfig.GOLDEN_APPLE_ABSORPTION_DURATION_TICKS), 0)),
                    List.of(EffectSpec.of(MobEffects.HUNGER, cfg(IAmZombieConfig.GOLDEN_APPLE_HUNGER_DURATION_TICKS), 0)),
                    true)),
            Map.entry("minecraft:enchanted_golden_apple", () -> special(
                    List.of(EffectSpec.of(MobEffects.ABSORPTION, cfg(IAmZombieConfig.ENCHANTED_GOLDEN_APPLE_ABSORPTION_DURATION_TICKS), 1),
                            EffectSpec.of(MobEffects.RESISTANCE, cfg(IAmZombieConfig.ENCHANTED_GOLDEN_APPLE_RESISTANCE_DURATION_TICKS), 0)),
                    List.of(EffectSpec.of(MobEffects.HUNGER, cfg(IAmZombieConfig.ENCHANTED_GOLDEN_APPLE_HUNGER_DURATION_TICKS), 0)),
                    true)),
            Map.entry("minecraft:pufferfish", () -> special(
                    List.of(EffectSpec.of(MobEffects.ABSORPTION, cfg(IAmZombieConfig.PUFFERFISH_ABSORPTION_DURATION_TICKS), 0),
                            EffectSpec.of(MobEffects.REGENERATION,
                                    cfg(IAmZombieConfig.PUFFERFISH_REGENERATION_DURATION_TICKS),
                                    cfg(IAmZombieConfig.PUFFERFISH_REGENERATION_AMPLIFIER))),
                    List.of(), false)),
            Map.entry("minecraft:poisonous_potato", () -> special(
                    // Random positive handled at runtime in the events layer; no static EffectSpec.
                    List.of(), List.of(), false)),
            Map.entry("minecraft:chorus_fruit", () -> special(
                    List.of(EffectSpec.of(MobEffects.SLOW_FALLING, cfg(IAmZombieConfig.CHORUS_SLOW_FALLING_DURATION_TICKS), 0)),
                    List.of(EffectSpec.of(MobEffects.NAUSEA, cfg(IAmZombieConfig.CHORUS_NAUSEA_DURATION_TICKS), 0)),
                    false)),
            Map.entry("minecraft:honey_bottle", () -> special(
                    List.of(),
                    List.of(EffectSpec.of(MobEffects.NAUSEA, cfg(IAmZombieConfig.HONEY_NAUSEA_DURATION_TICKS), 0)),
                    false))
    );

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
