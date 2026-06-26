package dev.molang.iamzombieq;

import java.util.List;

import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;
import dev.molang.iamzombieq.rules.ZombieBalanceRules;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.mount.ZombieMountRules;
import dev.molang.iamzombieq.rules.sleep.ZombieSleepRules;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class IAmZombieConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("Enable extra debug logging for I Am Zombie? development diagnostics.")
            .define("debugLogging", false);

    public static final ModConfigSpec.IntValue STARTING_ROTTEN_FLESH = BUILDER
            .comment("Rotten flesh given to a survival/adventure player when they first become a zombie.")
            .defineInRange("startingRottenFlesh", 8, 0, 64);

    public static final ModConfigSpec.BooleanValue UNLOCK_COFFIN_RECIPES_ON_FIRST_JOIN = BUILDER
            .comment("Unlock all coffin recipes and show a short hint when a player first becomes a zombie.")
            .define("unlockCoffinRecipesOnFirstJoin", true);

    public static final ModConfigSpec.BooleanValue UNDEAD_IGNORE_ZOMBIE_PLAYER = BUILDER
            .comment("Vanilla undead/zombie-family mobs (zombie, husk, drowned, zombified piglin, zombie villager, giant) do not hunt zombie players, the way zombies ignore other zombies. Mobs the player attacks still retaliate.")
            .define("undeadIgnoreZombiePlayer", true);

    public static final ModConfigSpec.IntValue NORMAL_ZOMBIE_INNATE_ARMOR = BUILDER
            .comment("Innate armor points for normal zombie players.")
            .defineInRange("normalZombieInnateArmor", ZombieBalanceRules.innateArmor(ZombieForm.NORMAL), 0, 30);

    public static final ModConfigSpec.IntValue DROWNED_INNATE_ARMOR = BUILDER
            .comment("Innate armor points for drowned zombie players.")
            .defineInRange("drownedInnateArmor", ZombieBalanceRules.innateArmor(ZombieForm.DROWNED), 0, 30);

    public static final ModConfigSpec.IntValue HUSK_INNATE_ARMOR = BUILDER
            .comment("Innate armor points for husk zombie players.")
            .defineInRange("huskInnateArmor", ZombieBalanceRules.innateArmor(ZombieForm.HUSK), 0, 30);

    public static final ModConfigSpec.IntValue ZOMBIFIED_PIGLIN_INNATE_ARMOR = BUILDER
            .comment("Innate armor points for zombified piglin zombie players.")
            .defineInRange("zombifiedPiglinInnateArmor", ZombieBalanceRules.innateArmor(ZombieForm.ZOMBIFIED_PIGLIN), 0, 30);

    public static final ModConfigSpec.IntValue SUN_PROTECTION_HEADGEAR_DAMAGE = BUILDER
            .comment("Durability damage applied on each sunlight tick to damageable headgear that protects sun-vulnerable zombie forms.")
            .defineInRange("sunProtectionHeadgearDamage", 1, 0, 64);

    public static final ModConfigSpec.DoubleValue EASY_INFECTION_CHANCE = BUILDER
            .comment("Chance that eligible zombie-player infection succeeds on Easy difficulty.")
            .defineInRange("easyInfectionChance", ZombieBalanceRules.infectionChance(GameDifficulty.EASY), 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue NORMAL_INFECTION_CHANCE = BUILDER
            .comment("Chance that eligible zombie-player infection succeeds on Normal difficulty.")
            .defineInRange("normalInfectionChance", ZombieBalanceRules.infectionChance(GameDifficulty.NORMAL), 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue HARD_INFECTION_CHANCE = BUILDER
            .comment("Chance that eligible zombie-player infection succeeds on Hard difficulty.")
            .defineInRange("hardInfectionChance", ZombieBalanceRules.infectionChance(GameDifficulty.HARD), 0.0, 1.0);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ZOMBIE_FOODS = BUILDER
            .comment("Items treated as zombie food before special-case effects are applied.")
            .defineListAllowEmpty("zombieFoods", List.of(
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
                    "iamzombieq:super_rotten_flesh"
            ), () -> "", value -> value instanceof String item && item.contains(":"));

    public static final ModConfigSpec.IntValue HUMAN_FOOD_NAUSEA_DURATION_TICKS = BUILDER
            .comment("Nausea duration in ticks applied when a zombie player eats human food.")
            .defineInRange("humanFoodNauseaDurationTicks", ZombieFoodRules.DEFAULT_HUMAN_FOOD_NAUSEA_DURATION_TICKS, 0, 20 * 60 * 5);

    public static final ModConfigSpec.IntValue HUMAN_FOOD_HUNGER_DURATION_TICKS = BUILDER
            .comment("Hunger duration in ticks applied when a zombie player eats human food.")
            .defineInRange("humanFoodHungerDurationTicks", ZombieFoodRules.DEFAULT_HUMAN_FOOD_HUNGER_DURATION_TICKS, 0, 20 * 60 * 5);

    public static final ModConfigSpec.IntValue HUMAN_FOOD_HUNGER_AMPLIFIER = BUILDER
            .comment("Hunger amplifier applied when a zombie player eats human food. 1 means Hunger II.")
            .defineInRange("humanFoodHungerAmplifier", ZombieFoodRules.DEFAULT_HUMAN_FOOD_HUNGER_AMPLIFIER, 0, 4);

    public static final ModConfigSpec.IntValue SPIDER_EYE_NIGHT_VISION_DURATION_TICKS = BUILDER
            .comment("Night Vision duration in ticks granted by Spider Eye as zombie food.")
            .defineInRange("spiderEyeNightVisionDurationTicks", ZombieFoodRules.DEFAULT_SPIDER_EYE_NIGHT_VISION_DURATION_TICKS, 0, 20 * 60 * 10);

    public static final ModConfigSpec.IntValue PUFFERFISH_ABSORPTION_DURATION_TICKS = BUILDER
            .comment("Absorption duration in ticks granted by Pufferfish as zombie food.")
            .defineInRange("pufferfishAbsorptionDurationTicks", ZombieFoodRules.DEFAULT_PUFFERFISH_ABSORPTION_DURATION_TICKS, 0, 20 * 60 * 10);

    public static final ModConfigSpec.IntValue PUFFERFISH_REGENERATION_DURATION_TICKS = BUILDER
            .comment("Regeneration duration in ticks granted by Pufferfish as zombie food.")
            .defineInRange("pufferfishRegenerationDurationTicks", ZombieFoodRules.DEFAULT_PUFFERFISH_REGENERATION_DURATION_TICKS, 0, 20 * 60 * 5);

    public static final ModConfigSpec.IntValue PUFFERFISH_REGENERATION_AMPLIFIER = BUILDER
            .comment("Regeneration amplifier granted by Pufferfish as zombie food. 1 means Regeneration II.")
            .defineInRange("pufferfishRegenerationAmplifier", ZombieFoodRules.DEFAULT_PUFFERFISH_REGENERATION_AMPLIFIER, 0, 4);

    public static final ModConfigSpec.IntValue POISONOUS_POTATO_POSITIVE_DURATION_TICKS = BUILDER
            .comment("Duration in ticks for the random positive effect granted by Poisonous Potato as zombie food.")
            .defineInRange("poisonousPotatoPositiveDurationTicks", ZombieFoodRules.DEFAULT_POISONOUS_POTATO_POSITIVE_DURATION_TICKS, 0, 20 * 60 * 5);

    public static final ModConfigSpec.IntValue SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS = BUILDER
            .comment("Strength duration in ticks granted by Super Rotten Flesh.")
            .defineInRange("superRottenFleshStrengthDurationTicks", ZombieFoodRules.DEFAULT_SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS, 0, 20 * 60 * 10);

    public static final ModConfigSpec.IntValue SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER = BUILDER
            .comment("Strength amplifier granted by Super Rotten Flesh. 0 means Strength I.")
            .defineInRange("superRottenFleshStrengthAmplifier", ZombieFoodRules.DEFAULT_SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER, 0, 4);

    public static final ModConfigSpec.IntValue SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS = BUILDER
            .comment("Saturation duration in ticks granted by Super Rotten Flesh.")
            .defineInRange("superRottenFleshSaturationDurationTicks", ZombieFoodRules.DEFAULT_SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS, 0, 20 * 60 * 5);

    public static final ModConfigSpec.DoubleValue BED_EXPLOSION_POWER = BUILDER
            .comment("Explosion power when a zombie player tries to sleep in a bed. Default mirrors Nether bed explosions.")
            .defineInRange("bedExplosionPower", (double) ZombieSleepRules.DEFAULT_BED_EXPLOSION_POWER, 0.0, 20.0);

    public static final ModConfigSpec.BooleanValue BED_EXPLOSION_CAUSES_FIRE = BUILDER
            .comment("Whether zombie bed explosions create fire. Default mirrors Nether bed explosions.")
            .define("bedExplosionCausesFire", ZombieSleepRules.DEFAULT_BED_EXPLOSION_CAUSES_FIRE);

    public static final ModConfigSpec.IntValue HEROBRINE_CAVE_CHECK_INTERVAL_TICKS = BUILDER
            .comment("Low-frequency check interval for rare Herobrine cave appearances.")
            .defineInRange("herobrineCaveCheckIntervalTicks", 20 * 30, 20 * 5, 20 * 60 * 10);

    public static final ModConfigSpec.DoubleValue HEROBRINE_CAVE_SPAWN_CHANCE = BUILDER
            .comment("Chance per eligible cave check that Herobrine appears near a player.")
            .defineInRange("herobrineCaveSpawnChance", 0.0005, 0.0, 1.0);

    public static final ModConfigSpec.IntValue HEROBRINE_ESCALATION_SIGHTINGS = BUILDER
            .comment("Non-lethal Herobrine sightings before the encounter escalates (heartbeat onset). 0 collapses straight to the legacy instant-kill behavior.")
            .defineInRange("herobrineEscalationSightings", 0, 0, 64);

    public static final ModConfigSpec.IntValue HEROBRINE_LETHAL_SIGHTINGS = BUILDER
            .comment("Additional Herobrine sightings beyond escalation before gazing/attacking becomes lethal. Guarantees at least one non-lethal sighting first when escalation > 0.")
            .defineInRange("herobrineLethalSightings", 0, 0, 64);

    public static final ModConfigSpec.IntValue HEROBRINE_MEMORY_WINDOW_TICKS = BUILDER
            .comment("How long (ticks) a Herobrine sighting is remembered before the accumulated dread decays. 0 = never forget.")
            .defineInRange("herobrineMemoryWindowTicks", 20 * 60 * 20, 0, 20 * 60 * 60);

    public static final ModConfigSpec.IntValue HEROBRINE_LETHAL_COOLDOWN_TICKS = BUILDER
            .comment("Cooldown (ticks) after a lethal Herobrine encounter during which it will not be lethal again, to prevent farming. 0 = no cooldown.")
            .defineInRange("herobrineLethalCooldownTicks", 0, 0, 20 * 60 * 60);

    public static final ModConfigSpec.BooleanValue HEROBRINE_OMEN_ENABLED = BUILDER
            .comment("Whether Herobrine spawns trigger a reversible environmental omen (briefly extinguishing nearby lit blocks + phantom footsteps), scaled by encounter phase.")
            .define("herobrineOmenEnabled", true);

    public static final ModConfigSpec.IntValue HEROBRINE_OMEN_DURATION_TICKS = BUILDER
            .comment("Maximum duration (ticks) that omen-extinguished lit blocks stay dark before being restored.")
            .defineInRange("herobrineOmenDurationTicks", 20 * 12, 20, 20 * 60);

    public static final ModConfigSpec.BooleanValue HEROBRINE_HEARTBEAT_ENABLED = BUILDER
            .comment("Whether a vanilla heartbeat is layered under the Herobrine silence once the encounter escalates (client-side).")
            .define("herobrineHeartbeatEnabled", true);

    public static final ModConfigSpec.IntValue HEROBRINE_HEARTBEAT_NEAR_DISTANCE = BUILDER
            .comment("Inner distance (blocks) of the Herobrine heartbeat band; at/under this the heartbeat is fastest and loudest.")
            .defineInRange("herobrineHeartbeatNearDistance", 12, 1, 28);

    public static final ModConfigSpec.IntValue HEROBRINE_HEARTBEAT_FAR_DISTANCE = BUILDER
            .comment("Outer distance (blocks) of the Herobrine heartbeat band; beyond this no heartbeat plays.")
            .defineInRange("herobrineHeartbeatFarDistance", 28, 1, 64);

    public static final ModConfigSpec.BooleanValue HEROBRINE_JOLT_ENABLED = BUILDER
            .comment("Whether a vanilla stinger sound + brief client red vignette plays just before a lethal Herobrine encounter.")
            .define("herobrineJoltEnabled", true);

    public static final ModConfigSpec.DoubleValue SPIDER_MOUNT_SPEED = BUILDER
            .comment("Ridden movement-speed (MOVEMENT_SPEED-attribute units) for zombie players riding tamed spiders. Overrides the mod default; non-positive values fall back to the default.")
            .defineInRange("spiderMountSpeed", ZombieMountRules.DEFAULT_SPIDER_MOUNT_SPEED, 0.05, 1.0);

    public static final ModConfigSpec.BooleanValue REINFORCEMENTS_ENABLED = BUILDER
            .comment("Whether a hurt zombie player can spawn official zombie reinforcements (HARD + doMobSpawning) and alert nearby form-matched undead onto the attacker, like a vanilla zombie.")
            .define("reinforcementsEnabled", true);

    public static final ModConfigSpec.IntValue REINFORCEMENT_SPAWN_ATTEMPTS = BUILDER
            .comment("Maximum spawn-position attempts per damage event when a zombie player spawns reinforcements (vanilla = 50).")
            .defineInRange("reinforcementSpawnAttempts", dev.molang.iamzombieq.rules.ZombieReinforcementRules.REINFORCEMENT_ATTEMPTS, 0, 200);

    public static final ModConfigSpec.IntValue T1_CARRION_STRENGTH_DURATION_TICKS = BUILDER
            .comment("Strength duration (ticks) granted by T1 CARRION raw meats.")
            .defineInRange("t1CarrionStrengthDurationTicks", 20 * 8, 0, 20 * 60 * 10);

    public static final ModConfigSpec.IntValue T1_CARRION_SPEED_DURATION_TICKS = BUILDER
            .comment("Speed duration (ticks) granted by T1 CARRION raw poultry (chicken/rabbit).")
            .defineInRange("t1CarrionSpeedDurationTicks", 20 * 10, 0, 20 * 60 * 10);

    public static final ModConfigSpec.IntValue T1_CARRION_SATURATION_DURATION_TICKS = BUILDER
            .comment("Saturation duration (ticks) granted by T1 CARRION rotten flesh.")
            .defineInRange("t1CarrionSaturationDurationTicks", 20 * 6, 0, 20 * 60 * 5);

    public static final ModConfigSpec.IntValue T1_CARRION_WATER_BREATHING_DURATION_TICKS = BUILDER
            .comment("Water Breathing duration (ticks) granted by T1 CARRION raw fish.")
            .defineInRange("t1CarrionWaterBreathingDurationTicks", 20 * 20, 0, 20 * 60 * 10);

    public static final ModConfigSpec.IntValue T2_FORAGE_SATURATION_DURATION_TICKS = BUILDER
            .comment("Saturation duration (ticks) granted by T2 FORAGE neutral foods.")
            .defineInRange("t2ForageSaturationDurationTicks", 20 * 2, 0, 20 * 60 * 5);

    public static final ModConfigSpec.IntValue SWEET_SLOWNESS_DURATION_TICKS = BUILDER
            .comment("Slowness I duration (ticks) added to SWEET human-cooked foods (cookie/cake/pumpkin pie).")
            .defineInRange("sweetSlownessDurationTicks", 20 * 8, 0, 20 * 60 * 5);

    public static final ModConfigSpec.IntValue GOLDEN_APPLE_ABSORPTION_DURATION_TICKS = BUILDER
            .comment("Absorption I duration (ticks) for a zombie eating a Golden Apple.")
            .defineInRange("goldenAppleAbsorptionDurationTicks", 20 * 60, 0, 20 * 60 * 10);

    public static final ModConfigSpec.IntValue GOLDEN_APPLE_HUNGER_DURATION_TICKS = BUILDER
            .comment("Hunger I cost duration (ticks) for a zombie eating a Golden Apple.")
            .defineInRange("goldenAppleHungerDurationTicks", 20 * 10, 0, 20 * 60 * 5);

    public static final ModConfigSpec.IntValue ENCHANTED_GOLDEN_APPLE_ABSORPTION_DURATION_TICKS = BUILDER
            .comment("Absorption II duration (ticks) for a zombie eating an Enchanted Golden Apple.")
            .defineInRange("enchantedGoldenAppleAbsorptionDurationTicks", 20 * 90, 0, 20 * 60 * 10);

    public static final ModConfigSpec.IntValue ENCHANTED_GOLDEN_APPLE_RESISTANCE_DURATION_TICKS = BUILDER
            .comment("Resistance I duration (ticks) for a zombie eating an Enchanted Golden Apple.")
            .defineInRange("enchantedGoldenAppleResistanceDurationTicks", 20 * 15, 0, 20 * 60 * 10);

    public static final ModConfigSpec.IntValue ENCHANTED_GOLDEN_APPLE_HUNGER_DURATION_TICKS = BUILDER
            .comment("Hunger I cost duration (ticks) for a zombie eating an Enchanted Golden Apple.")
            .defineInRange("enchantedGoldenAppleHungerDurationTicks", 20 * 20, 0, 20 * 60 * 5);

    public static final ModConfigSpec.IntValue CHORUS_SLOW_FALLING_DURATION_TICKS = BUILDER
            .comment("Slow Falling duration (ticks) granted by a zombie eating a Chorus Fruit.")
            .defineInRange("chorusSlowFallingDurationTicks", 20 * 10, 0, 20 * 60 * 5);

    public static final ModConfigSpec.IntValue CHORUS_NAUSEA_DURATION_TICKS = BUILDER
            .comment("Nausea cost duration (ticks) for a zombie eating a Chorus Fruit.")
            .defineInRange("chorusNauseaDurationTicks", 20 * 6, 0, 20 * 60 * 5);

    public static final ModConfigSpec.IntValue HONEY_NAUSEA_DURATION_TICKS = BUILDER
            .comment("Nausea cost duration (ticks) for a zombie drinking a Honey Bottle.")
            .defineInRange("honeyNauseaDurationTicks", 20 * 8, 0, 20 * 60 * 5);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private IAmZombieConfig() {
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
            case PEACEFUL -> ZombieBalanceRules.infectionChance(GameDifficulty.PEACEFUL);
            case EASY -> EASY_INFECTION_CHANCE.get();
            case NORMAL -> NORMAL_INFECTION_CHANCE.get();
            case HARD -> HARD_INFECTION_CHANCE.get();
        };
    }
}
