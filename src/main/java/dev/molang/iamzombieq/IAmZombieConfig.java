package dev.molang.iamzombieq;

import dev.molang.iamzombieq.rules.ZombieBalanceRules;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;
import dev.molang.iamzombieq.rules.ZombieInfectionRules;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Binary-compatible 1.0.3 facade over the canonical configuration authority holders.
 *
 * <p>The public owner, field descriptors, generic signatures, SPEC field and
 * helper methods are retained for 1.0.3 binary compatibility. No COMMON shadow values
 * are built here: every field is the exact canonical ConfigValue object.</p>
 */
public final class IAmZombieConfig {
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING =
            IAmZombieServerConfig.DEBUG_LOGGING;
    public static final ModConfigSpec.IntValue STARTING_ROTTEN_FLESH =
            IAmZombieServerConfig.STARTING_ROTTEN_FLESH;
    public static final ModConfigSpec.BooleanValue
            UNLOCK_COFFIN_RECIPES_ON_FIRST_JOIN =
                    IAmZombieServerConfig.UNLOCK_COFFIN_RECIPES_ON_FIRST_JOIN;
    public static final ModConfigSpec.BooleanValue UNDEAD_IGNORE_ZOMBIE_PLAYER =
            IAmZombieServerConfig.UNDEAD_IGNORE_ZOMBIE_PLAYER;
    public static final ModConfigSpec.IntValue NORMAL_ZOMBIE_INNATE_ARMOR =
            IAmZombieServerConfig.NORMAL_ZOMBIE_INNATE_ARMOR;
    public static final ModConfigSpec.IntValue DROWNED_INNATE_ARMOR =
            IAmZombieServerConfig.DROWNED_INNATE_ARMOR;
    public static final ModConfigSpec.IntValue HUSK_INNATE_ARMOR =
            IAmZombieServerConfig.HUSK_INNATE_ARMOR;
    public static final ModConfigSpec.IntValue ZOMBIFIED_PIGLIN_INNATE_ARMOR =
            IAmZombieServerConfig.ZOMBIFIED_PIGLIN_INNATE_ARMOR;
    public static final ModConfigSpec.IntValue SUN_PROTECTION_HEADGEAR_DAMAGE =
            IAmZombieServerConfig.SUN_PROTECTION_HEADGEAR_DAMAGE;
    public static final ModConfigSpec.DoubleValue EASY_INFECTION_CHANCE =
            IAmZombieServerConfig.EASY_INFECTION_CHANCE;
    public static final ModConfigSpec.DoubleValue NORMAL_INFECTION_CHANCE =
            IAmZombieServerConfig.NORMAL_INFECTION_CHANCE;
    public static final ModConfigSpec.DoubleValue HARD_INFECTION_CHANCE =
            IAmZombieServerConfig.HARD_INFECTION_CHANCE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>>
            ZOMBIE_FOODS = IAmZombieServerConfig.ZOMBIE_FOODS;
    public static final ModConfigSpec.IntValue
            HUMAN_FOOD_NAUSEA_DURATION_TICKS =
                    IAmZombieServerConfig.HUMAN_FOOD_NAUSEA_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            HUMAN_FOOD_HUNGER_DURATION_TICKS =
                    IAmZombieServerConfig.HUMAN_FOOD_HUNGER_DURATION_TICKS;
    public static final ModConfigSpec.IntValue HUMAN_FOOD_HUNGER_AMPLIFIER =
            IAmZombieServerConfig.HUMAN_FOOD_HUNGER_AMPLIFIER;
    public static final ModConfigSpec.IntValue
            SPIDER_EYE_NIGHT_VISION_DURATION_TICKS =
                    IAmZombieServerConfig.SPIDER_EYE_NIGHT_VISION_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            PUFFERFISH_ABSORPTION_DURATION_TICKS =
                    IAmZombieServerConfig.PUFFERFISH_ABSORPTION_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            PUFFERFISH_REGENERATION_DURATION_TICKS =
                    IAmZombieServerConfig.PUFFERFISH_REGENERATION_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            PUFFERFISH_REGENERATION_AMPLIFIER =
                    IAmZombieServerConfig.PUFFERFISH_REGENERATION_AMPLIFIER;
    public static final ModConfigSpec.IntValue
            POISONOUS_POTATO_POSITIVE_DURATION_TICKS =
                    IAmZombieServerConfig
                            .POISONOUS_POTATO_POSITIVE_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS =
                    IAmZombieServerConfig
                            .SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER =
                    IAmZombieServerConfig
                            .SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER;
    public static final ModConfigSpec.IntValue
            SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS =
                    IAmZombieServerConfig
                            .SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue BED_EXPLOSION_POWER =
            IAmZombieServerConfig.BED_EXPLOSION_POWER;
    public static final ModConfigSpec.BooleanValue BED_EXPLOSION_CAUSES_FIRE =
            IAmZombieServerConfig.BED_EXPLOSION_CAUSES_FIRE;
    public static final ModConfigSpec.IntValue
            HEROBRINE_CAVE_CHECK_INTERVAL_TICKS =
                    IAmZombieServerConfig.HEROBRINE_CAVE_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue
            HEROBRINE_CAVE_SPAWN_CHANCE =
                    IAmZombieServerConfig.HEROBRINE_CAVE_SPAWN_CHANCE;
    public static final ModConfigSpec.IntValue
            HEROBRINE_ESCALATION_SIGHTINGS =
                    IAmZombieServerConfig.HEROBRINE_ESCALATION_SIGHTINGS;
    public static final ModConfigSpec.IntValue HEROBRINE_LETHAL_SIGHTINGS =
            IAmZombieServerConfig.HEROBRINE_LETHAL_SIGHTINGS;
    public static final ModConfigSpec.IntValue
            HEROBRINE_MEMORY_WINDOW_TICKS =
                    IAmZombieServerConfig.HEROBRINE_MEMORY_WINDOW_TICKS;
    public static final ModConfigSpec.IntValue
            HEROBRINE_LETHAL_COOLDOWN_TICKS =
                    IAmZombieServerConfig.HEROBRINE_LETHAL_COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue HEROBRINE_OMEN_ENABLED =
            IAmZombieServerConfig.HEROBRINE_OMEN_ENABLED;
    public static final ModConfigSpec.IntValue HEROBRINE_OMEN_DURATION_TICKS =
            IAmZombieServerConfig.HEROBRINE_OMEN_DURATION_TICKS;
    public static final ModConfigSpec.BooleanValue
            HEROBRINE_HEARTBEAT_ENABLED =
                    IAmZombiePreferencesConfig.HEROBRINE_HEARTBEAT_ENABLED;
    public static final ModConfigSpec.IntValue
            HEROBRINE_HEARTBEAT_NEAR_DISTANCE =
                    IAmZombiePreferencesConfig
                            .HEROBRINE_HEARTBEAT_NEAR_DISTANCE;
    public static final ModConfigSpec.IntValue
            HEROBRINE_HEARTBEAT_FAR_DISTANCE =
                    IAmZombiePreferencesConfig
                            .HEROBRINE_HEARTBEAT_FAR_DISTANCE;
    public static final ModConfigSpec.BooleanValue HEROBRINE_JOLT_ENABLED =
            IAmZombieServerConfig.HEROBRINE_JOLT_ENABLED;
    public static final ModConfigSpec.DoubleValue SPIDER_MOUNT_SPEED =
            IAmZombieServerConfig.SPIDER_MOUNT_SPEED;
    public static final ModConfigSpec.BooleanValue REINFORCEMENTS_ENABLED =
            IAmZombieServerConfig.REINFORCEMENTS_ENABLED;
    public static final ModConfigSpec.IntValue REINFORCEMENT_SPAWN_ATTEMPTS =
            IAmZombieServerConfig.REINFORCEMENT_SPAWN_ATTEMPTS;
    public static final ModConfigSpec.IntValue
            T1_CARRION_STRENGTH_DURATION_TICKS =
                    IAmZombieServerConfig
                            .T1_CARRION_STRENGTH_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            T1_CARRION_SPEED_DURATION_TICKS =
                    IAmZombieServerConfig.T1_CARRION_SPEED_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            T1_CARRION_SATURATION_DURATION_TICKS =
                    IAmZombieServerConfig
                            .T1_CARRION_SATURATION_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            T1_CARRION_WATER_BREATHING_DURATION_TICKS =
                    IAmZombieServerConfig
                            .T1_CARRION_WATER_BREATHING_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            T2_FORAGE_SATURATION_DURATION_TICKS =
                    IAmZombieServerConfig.T2_FORAGE_SATURATION_DURATION_TICKS;
    public static final ModConfigSpec.IntValue SWEET_SLOWNESS_DURATION_TICKS =
            IAmZombieServerConfig.SWEET_SLOWNESS_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            GOLDEN_APPLE_ABSORPTION_DURATION_TICKS =
                    IAmZombieServerConfig
                            .GOLDEN_APPLE_ABSORPTION_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            GOLDEN_APPLE_HUNGER_DURATION_TICKS =
                    IAmZombieServerConfig.GOLDEN_APPLE_HUNGER_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            ENCHANTED_GOLDEN_APPLE_ABSORPTION_DURATION_TICKS =
                    IAmZombieServerConfig
                            .ENCHANTED_GOLDEN_APPLE_ABSORPTION_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            ENCHANTED_GOLDEN_APPLE_RESISTANCE_DURATION_TICKS =
                    IAmZombieServerConfig
                            .ENCHANTED_GOLDEN_APPLE_RESISTANCE_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            ENCHANTED_GOLDEN_APPLE_HUNGER_DURATION_TICKS =
                    IAmZombieServerConfig
                            .ENCHANTED_GOLDEN_APPLE_HUNGER_DURATION_TICKS;
    public static final ModConfigSpec.IntValue
            CHORUS_SLOW_FALLING_DURATION_TICKS =
                    IAmZombieServerConfig
                            .CHORUS_SLOW_FALLING_DURATION_TICKS;
    public static final ModConfigSpec.IntValue CHORUS_NAUSEA_DURATION_TICKS =
            IAmZombieServerConfig.CHORUS_NAUSEA_DURATION_TICKS;
    public static final ModConfigSpec.IntValue HONEY_NAUSEA_DURATION_TICKS =
            IAmZombieServerConfig.HONEY_NAUSEA_DURATION_TICKS;

    public static final ModConfigSpec SPEC = IAmZombieServerConfig.SPEC;

    private IAmZombieConfig() {}

    public static Set<String> configuredZombieFoods() {
        return ZOMBIE_FOODS.get().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static int configuredInnateArmor(ZombieForm form) {
        return switch (form) {
            case NORMAL -> NORMAL_ZOMBIE_INNATE_ARMOR.get();
            case DROWNED -> DROWNED_INNATE_ARMOR.get();
            case HUSK -> HUSK_INNATE_ARMOR.get();
            case ZOMBIFIED_PIGLIN -> ZOMBIFIED_PIGLIN_INNATE_ARMOR.get();
            case GIANT -> ZombieBalanceRules.innateArmor(ZombieForm.GIANT);
        };
    }

    public static double configuredInfectionChance(GameDifficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL ->
                    ZombieInfectionRules.infectionChance(
                            GameDifficulty.PEACEFUL);
            case EASY -> EASY_INFECTION_CHANCE.get();
            case NORMAL -> NORMAL_INFECTION_CHANCE.get();
            case HARD -> HARD_INFECTION_CHANCE.get();
        };
    }
}
