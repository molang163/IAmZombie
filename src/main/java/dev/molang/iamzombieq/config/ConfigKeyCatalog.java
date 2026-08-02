package dev.molang.iamzombieq.config;

import java.util.List;
import java.util.Objects;

/**
 * Dormant 1.0.3-to-1.1 authority directory. It contains metadata only and does not load either target spec.
 */
final class ConfigKeyCatalog {
    private static final String SERVER_OWNER = "dev.molang.iamzombieq.IAmZombieServerConfig";
    private static final String PREFERENCES_OWNER = "dev.molang.iamzombieq.IAmZombiePreferencesConfig";

    private static final List<Entry> ENTRIES = List.of(
            server("DEBUG_LOGGING", "debugLogging", false),
            server("STARTING_ROTTEN_FLESH", "startingRottenFlesh", false),
            server("UNLOCK_COFFIN_RECIPES_ON_FIRST_JOIN", "unlockCoffinRecipesOnFirstJoin", false),
            server("UNDEAD_IGNORE_ZOMBIE_PLAYER", "undeadIgnoreZombiePlayer", false),
            server("NORMAL_ZOMBIE_INNATE_ARMOR", "normalZombieInnateArmor", false),
            server("DROWNED_INNATE_ARMOR", "drownedInnateArmor", false),
            server("HUSK_INNATE_ARMOR", "huskInnateArmor", false),
            server("ZOMBIFIED_PIGLIN_INNATE_ARMOR", "zombifiedPiglinInnateArmor", false),
            server("SUN_PROTECTION_HEADGEAR_DAMAGE", "sunProtectionHeadgearDamage", false),
            server("EASY_INFECTION_CHANCE", "easyInfectionChance", false),
            server("NORMAL_INFECTION_CHANCE", "normalInfectionChance", false),
            server("HARD_INFECTION_CHANCE", "hardInfectionChance", false),
            server("ZOMBIE_FOODS", "zombieFoods", true),
            server("HUMAN_FOOD_NAUSEA_DURATION_TICKS", "humanFoodNauseaDurationTicks", false),
            server("HUMAN_FOOD_HUNGER_DURATION_TICKS", "humanFoodHungerDurationTicks", false),
            server("HUMAN_FOOD_HUNGER_AMPLIFIER", "humanFoodHungerAmplifier", false),
            server("SPIDER_EYE_NIGHT_VISION_DURATION_TICKS", "spiderEyeNightVisionDurationTicks", true),
            server("PUFFERFISH_ABSORPTION_DURATION_TICKS", "pufferfishAbsorptionDurationTicks", true),
            server("PUFFERFISH_REGENERATION_DURATION_TICKS", "pufferfishRegenerationDurationTicks", true),
            server("PUFFERFISH_REGENERATION_AMPLIFIER", "pufferfishRegenerationAmplifier", true),
            server("POISONOUS_POTATO_POSITIVE_DURATION_TICKS", "poisonousPotatoPositiveDurationTicks", false),
            server("SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS", "superRottenFleshStrengthDurationTicks", true),
            server("SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER", "superRottenFleshStrengthAmplifier", true),
            server("SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS", "superRottenFleshSaturationDurationTicks", true),
            server("BED_EXPLOSION_POWER", "bedExplosionPower", false),
            server("BED_EXPLOSION_CAUSES_FIRE", "bedExplosionCausesFire", false),
            server("HEROBRINE_CAVE_CHECK_INTERVAL_TICKS", "herobrineCaveCheckIntervalTicks", false),
            server("HEROBRINE_CAVE_SPAWN_CHANCE", "herobrineCaveSpawnChance", false),
            server("HEROBRINE_ESCALATION_SIGHTINGS", "herobrineEscalationSightings", false),
            server("HEROBRINE_LETHAL_SIGHTINGS", "herobrineLethalSightings", false),
            server("HEROBRINE_MEMORY_WINDOW_TICKS", "herobrineMemoryWindowTicks", false),
            server("HEROBRINE_LETHAL_COOLDOWN_TICKS", "herobrineLethalCooldownTicks", false),
            server("HEROBRINE_OMEN_ENABLED", "herobrineOmenEnabled", false),
            server("HEROBRINE_OMEN_DURATION_TICKS", "herobrineOmenDurationTicks", false),
            client("HEROBRINE_HEARTBEAT_ENABLED", "herobrineHeartbeatEnabled"),
            client("HEROBRINE_HEARTBEAT_NEAR_DISTANCE", "herobrineHeartbeatNearDistance"),
            client("HEROBRINE_HEARTBEAT_FAR_DISTANCE", "herobrineHeartbeatFarDistance"),
            splitJolt(),
            server("SPIDER_MOUNT_SPEED", "spiderMountSpeed", true),
            server("REINFORCEMENTS_ENABLED", "reinforcementsEnabled", false),
            server("REINFORCEMENT_SPAWN_ATTEMPTS", "reinforcementSpawnAttempts", false),
            inert("T1_CARRION_STRENGTH_DURATION_TICKS", "t1CarrionStrengthDurationTicks"),
            inert("T1_CARRION_SPEED_DURATION_TICKS", "t1CarrionSpeedDurationTicks"),
            inert("T1_CARRION_SATURATION_DURATION_TICKS", "t1CarrionSaturationDurationTicks"),
            server("T1_CARRION_WATER_BREATHING_DURATION_TICKS", "t1CarrionWaterBreathingDurationTicks", true),
            inert("T2_FORAGE_SATURATION_DURATION_TICKS", "t2ForageSaturationDurationTicks"),
            server("SWEET_SLOWNESS_DURATION_TICKS", "sweetSlownessDurationTicks", true),
            server("GOLDEN_APPLE_ABSORPTION_DURATION_TICKS", "goldenAppleAbsorptionDurationTicks", true),
            server("GOLDEN_APPLE_HUNGER_DURATION_TICKS", "goldenAppleHungerDurationTicks", true),
            server("ENCHANTED_GOLDEN_APPLE_ABSORPTION_DURATION_TICKS",
                    "enchantedGoldenAppleAbsorptionDurationTicks", true),
            server("ENCHANTED_GOLDEN_APPLE_RESISTANCE_DURATION_TICKS",
                    "enchantedGoldenAppleResistanceDurationTicks", true),
            server("ENCHANTED_GOLDEN_APPLE_HUNGER_DURATION_TICKS",
                    "enchantedGoldenAppleHungerDurationTicks", true),
            server("CHORUS_SLOW_FALLING_DURATION_TICKS", "chorusSlowFallingDurationTicks", true),
            server("CHORUS_NAUSEA_DURATION_TICKS", "chorusNauseaDurationTicks", true),
            server("HONEY_NAUSEA_DURATION_TICKS", "honeyNauseaDurationTicks", true));

    private ConfigKeyCatalog() {
    }

    static List<Entry> entries() {
        return ENTRIES;
    }

    enum Authority {
        SERVER,
        CLIENT,
        SPLIT,
        INERT
    }

    record Target(
            String owner,
            String field,
            String tomlKey,
            String legacyValueSource) {
        Target {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(tomlKey, "tomlKey");
            Objects.requireNonNull(legacyValueSource, "legacyValueSource");
        }
    }

    record Entry(
            String legacyField,
            String legacyTomlKey,
            Authority authority,
            List<Target> targets,
            boolean remoteRequired) {
        Entry {
            Objects.requireNonNull(legacyField, "legacyField");
            Objects.requireNonNull(legacyTomlKey, "legacyTomlKey");
            Objects.requireNonNull(authority, "authority");
            targets = List.copyOf(targets);
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("canonical targets must not be empty");
            }
            if ((authority == Authority.SPLIT) != (targets.size() == 2)) {
                throw new IllegalArgumentException("only SPLIT rows have two canonical targets");
            }
        }

        boolean split() {
            return authority == Authority.SPLIT;
        }

        boolean inert() {
            return authority == Authority.INERT;
        }
    }

    private static Entry server(String field, String tomlKey, boolean remoteRequired) {
        return direct(field, tomlKey, Authority.SERVER, SERVER_OWNER, remoteRequired);
    }

    private static Entry client(String field, String tomlKey) {
        return direct(field, tomlKey, Authority.CLIENT, PREFERENCES_OWNER, false);
    }

    private static Entry inert(String field, String tomlKey) {
        return direct(field, tomlKey, Authority.INERT, SERVER_OWNER, false);
    }

    private static Entry direct(
            String field, String tomlKey, Authority authority, String owner, boolean remoteRequired) {
        return new Entry(field, tomlKey, authority,
                List.of(new Target(owner, field, tomlKey, field)), remoteRequired);
    }

    private static Entry splitJolt() {
        String legacyField = "HEROBRINE_JOLT_ENABLED";
        return new Entry(
                legacyField,
                "herobrineJoltEnabled",
                Authority.SPLIT,
                List.of(
                        new Target(SERVER_OWNER, "HEROBRINE_JOLT_ENABLED", "herobrineJoltEnabled", legacyField),
                        new Target(PREFERENCES_OWNER, "HEROBRINE_JOLT_VIGNETTE_ENABLED",
                                "herobrineJoltVignetteEnabled", legacyField)),
                false);
    }
}
