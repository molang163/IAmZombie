package dev.molang.iamzombieq.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable migration authority. The schema is embedded so the dormant core has no
 * runtime resource or config-holder dependency.
 */
final class ConfigSchemaCatalog {
    private static final String VERSION = "1.1.0";
    private static final String AUTHORITY_SHA256 =
            "99677081884d39731d0345e92543631478a5e54c28436c8c05bba31d49a655b4";

    private static final String EMBEDDED_SCHEMA = """
            SERVER|DEBUG~LOGGING|debugLogging|B|false|-|-|Enable extra debug logging for I Am Zombie? development diagnostics.
            SERVER|STARTING_ROTTEN_FLESH|startingRottenFlesh|I|8|0|64|Rotten flesh given to a survival/adventure player when they first become a zombie.\\n Default: 8\\n Range: 0 ~ 64
            SERVER|UNLOCK_COFFIN_RECIPES_ON_FIRST_JOIN|unlockCoffinRecipesOnFirstJoin|B|true|-|-|Unlock all coffin recipes and show a short hint when a player first becomes a zombie.
            SERVER|UNDEAD_IGNORE_ZOMBIE_PLAYER|undeadIgnoreZombiePlayer|B|true|-|-|Vanilla undead/zombie-family mobs (zombie, husk, drowned, zombified piglin, zombie villager, giant) do not hunt zombie players, the way zombies ignore other zombies. Mobs the player attacks still retaliate.
            SERVER|NORMAL_ZOMBIE_INNATE_ARMOR|normalZombieInnateArmor|I|2|0|30|Innate armor points for normal zombie players.\\n Default: 2\\n Range: 0 ~ 30
            SERVER|DROWNED_INNATE_ARMOR|drownedInnateArmor|I|2|0|30|Innate armor points for drowned zombie players.\\n Default: 2\\n Range: 0 ~ 30
            SERVER|HUSK_INNATE_ARMOR|huskInnateArmor|I|4|0|30|Innate armor points for husk zombie players.\\n Default: 4\\n Range: 0 ~ 30
            SERVER|ZOMBIFIED_PIGLIN_INNATE_ARMOR|zombifiedPiglinInnateArmor|I|2|0|30|Innate armor points for zombified piglin zombie players.\\n Default: 2\\n Range: 0 ~ 30
            SERVER|SUN_PROTECTION_HEADGEAR_DAMAGE|sunProtectionHeadgearDamage|I|1|0|64|Durability damage applied on each sunlight tick to damageable headgear that protects sun-vulnerable zombie forms.\\n Default: 1\\n Range: 0 ~ 64
            SERVER|EASY_INFECTION_CHANCE|easyInfectionChance|D|0.25|0.0|1.0|Chance that eligible zombie-player infection succeeds on Easy difficulty.\\n Default: 0.25\\n Range: 0.0 ~ 1.0
            SERVER|NORMAL_INFECTION_CHANCE|normalInfectionChance|D|0.5|0.0|1.0|Chance that eligible zombie-player infection succeeds on Normal difficulty.\\n Default: 0.5\\n Range: 0.0 ~ 1.0
            SERVER|HARD_INFECTION_CHANCE|hardInfectionChance|D|1.0|0.0|1.0|Chance that eligible zombie-player infection succeeds on Hard difficulty.\\n Default: 1.0\\n Range: 0.0 ~ 1.0
            SERVER|ZOMBIE_FOODS|zombieFoods|L|[minecraft:rotten_flesh, minecraft:spider_eye, minecraft:poisonous_potato, minecraft:pufferfish, minecraft:beef, minecraft:porkchop, minecraft:mutton, minecraft:chicken, minecraft:rabbit, minecraft:cod, minecraft:salmon, minecraft:tropical_fish, iamzombieq:super_rotten_flesh]|-|-|Items treated as zombie food before special-case effects are applied.
            SERVER|HUMAN_FOOD_NAUSEA_DURATION_TICKS|humanFoodNauseaDurationTicks|I|240|0|6000|Nausea duration in ticks applied when a zombie player eats human food.\\n Default: 240\\n Range: 0 ~ 6000
            SERVER|HUMAN_FOOD_HUNGER_DURATION_TICKS|humanFoodHungerDurationTicks|I|360|0|6000|Hunger duration in ticks applied when a zombie player eats human food.\\n Default: 360\\n Range: 0 ~ 6000
            SERVER|HUMAN_FOOD_HUNGER_AMPLIFIER|humanFoodHungerAmplifier|I|2|0|4|Hunger amplifier applied when a zombie player eats human food. 1 means Hunger II.\\n Default: 2\\n Range: 0 ~ 4
            SERVER|SPIDER_EYE_NIGHT_VISION_DURATION_TICKS|spiderEyeNightVisionDurationTicks|I|900|0|12000|Night Vision duration in ticks granted by Spider Eye as zombie food.\\n Default: 900\\n Range: 0 ~ 12000
            SERVER|PUFFERFISH_ABSORPTION_DURATION_TICKS|pufferfishAbsorptionDurationTicks|I|2400|0|12000|Absorption duration in ticks granted by Pufferfish as zombie food.\\n Default: 2400\\n Range: 0 ~ 12000
            SERVER|PUFFERFISH_REGENERATION_DURATION_TICKS|pufferfishRegenerationDurationTicks|I|100|0|6000|Regeneration duration in ticks granted by Pufferfish as zombie food.\\n Default: 100\\n Range: 0 ~ 6000
            SERVER|PUFFERFISH_REGENERATION_AMPLIFIER|pufferfishRegenerationAmplifier|I|1|0|4|Regeneration amplifier granted by Pufferfish as zombie food. 1 means Regeneration II.\\n Default: 1\\n Range: 0 ~ 4
            SERVER|POISONOUS_POTATO_POSITIVE_DURATION_TICKS|poisonousPotatoPositiveDurationTicks|I|500|0|6000|Duration in ticks for the random positive effect granted by Poisonous Potato as zombie food.\\n Default: 500\\n Range: 0 ~ 6000
            SERVER|SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS|superRottenFleshStrengthDurationTicks|I|900|0|12000|Strength duration in ticks granted by Super Rotten Flesh.\\n Default: 900\\n Range: 0 ~ 12000
            SERVER|SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER|superRottenFleshStrengthAmplifier|I|0|0|4|Strength amplifier granted by Super Rotten Flesh. 0 means Strength I.\\n Default: 0\\n Range: 0 ~ 4
            SERVER|SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS|superRottenFleshSaturationDurationTicks|I|160|0|6000|Saturation duration in ticks granted by Super Rotten Flesh.\\n Default: 160\\n Range: 0 ~ 6000
            SERVER|BED_EXPLOSION_POWER|bedExplosionPower|D|5.0|0.0|20.0|Explosion power when a zombie player tries to sleep in a bed. Default mirrors Nether bed explosions.\\n Default: 5.0\\n Range: 0.0 ~ 20.0
            SERVER|BED_EXPLOSION_CAUSES_FIRE|bedExplosionCausesFire|B|true|-|-|Whether zombie bed explosions create fire. Default mirrors Nether bed explosions.
            SERVER|HEROBRINE_CAVE_CHECK_INTERVAL_TICKS|herobrineCaveCheckIntervalTicks|I|600|100|12000|Low-frequency check interval for rare Herobrine cave appearances.\\n Default: 600\\n Range: 100 ~ 12000
            SERVER|HEROBRINE_CAVE_SPAWN_CHANCE|herobrineCaveSpawnChance|D|5.0E-4|0.0|1.0|Chance per eligible cave check that Herobrine appears near a player.\\n Default: 5.0E-4\\n Range: 0.0 ~ 1.0
            SERVER|HEROBRINE_ESCALATION_SIGHTINGS|herobrineEscalationSightings|I|0|0|64|Non-lethal Herobrine sightings before the encounter escalates (heartbeat onset). 0 collapses straight to the legacy instant-kill behavior.\\n Default: 0\\n Range: 0 ~ 64
            SERVER|HEROBRINE_LETHAL_SIGHTINGS|herobrineLethalSightings|I|0|0|64|Additional Herobrine sightings beyond escalation before gazing/attacking becomes lethal. Guarantees at least one non-lethal sighting first when escalation > 0.\\n Default: 0\\n Range: 0 ~ 64
            SERVER|HEROBRINE_MEMORY_WINDOW_TICKS|herobrineMemoryWindowTicks|I|24000|0|72000|How long (ticks) a Herobrine sighting is remembered before the accumulated dread decays. 0 = never forget.\\n Default: 24000\\n Range: 0 ~ 72000
            SERVER|HEROBRINE_LETHAL_COOLDOWN_TICKS|herobrineLethalCooldownTicks|I|0|0|72000|Cooldown (ticks) after a lethal Herobrine encounter during which it will not be lethal again, to prevent farming. 0 = no cooldown.\\n Default: 0\\n Range: 0 ~ 72000
            SERVER|HEROBRINE_OMEN_ENABLED|herobrineOmenEnabled|B|true|-|-|Whether Herobrine spawns trigger a reversible environmental omen (briefly extinguishing nearby lit blocks + phantom footsteps), scaled by encounter phase.
            SERVER|HEROBRINE_OMEN_DURATION_TICKS|herobrineOmenDurationTicks|I|240|20|1200|Maximum duration (ticks) that omen-extinguished lit blocks stay dark before being restored.\\n Default: 240\\n Range: 20 ~ 1200
            SERVER|HEROBRINE_JOLT_ENABLED|herobrineJoltEnabled|B|true|-|-|Whether a vanilla stinger sound + brief client red vignette plays just before a lethal Herobrine encounter.
            SERVER|SPIDER_MOUNT_SPEED|spiderMountSpeed|D|0.30000001192092896|0.05|1.0|Ridden movement-speed (MOVEMENT_SPEED-attribute units) for zombie players riding tamed spiders. Overrides the mod default; non-positive values fall back to the default.\\n Default: 0.30000001192092896\\n Range: 0.05 ~ 1.0
            SERVER|REINFORCEMENTS_ENABLED|reinforcementsEnabled|B|true|-|-|Whether a hurt zombie player can spawn official zombie reinforcements (HARD + doMobSpawning) and alert nearby form-matched undead onto the attacker, like a vanilla zombie.
            SERVER|REINFORCEMENT_SPAWN_ATTEMPTS|reinforcementSpawnAttempts|I|50|0|200|Maximum spawn-position attempts per damage event when a zombie player spawns reinforcements (vanilla = 50).\\n Default: 50\\n Range: 0 ~ 200
            SERVER|T1_CARRION_STRENGTH_DURATION_TICKS|t1CarrionStrengthDurationTicks|I|160|0|12000|Strength duration (ticks) granted by T1 CARRION raw meats.\\n Default: 160\\n Range: 0 ~ 12000
            SERVER|T1_CARRION_SPEED_DURATION_TICKS|t1CarrionSpeedDurationTicks|I|200|0|12000|Speed duration (ticks) granted by T1 CARRION raw poultry (chicken/rabbit).\\n Default: 200\\n Range: 0 ~ 12000
            SERVER|T1_CARRION_SATURATION_DURATION_TICKS|t1CarrionSaturationDurationTicks|I|120|0|6000|Saturation duration (ticks) granted by T1 CARRION rotten flesh.\\n Default: 120\\n Range: 0 ~ 6000
            SERVER|T1_CARRION_WATER_BREATHING_DURATION_TICKS|t1CarrionWaterBreathingDurationTicks|I|400|0|12000|Water Breathing duration (ticks) granted by T1 CARRION raw fish.\\n Default: 400\\n Range: 0 ~ 12000
            SERVER|T2_FORAGE_SATURATION_DURATION_TICKS|t2ForageSaturationDurationTicks|I|40|0|6000|Saturation duration (ticks) granted by T2 FORAGE neutral foods.\\n Default: 40\\n Range: 0 ~ 6000
            SERVER|SWEET_SLOWNESS_DURATION_TICKS|sweetSlownessDurationTicks|I|160|0|6000|Slowness I duration (ticks) added to SWEET human-cooked foods (cookie/cake/pumpkin pie).\\n Default: 160\\n Range: 0 ~ 6000
            SERVER|GOLDEN_APPLE_ABSORPTION_DURATION_TICKS|goldenAppleAbsorptionDurationTicks|I|1200|0|12000|Absorption I duration (ticks) for a zombie eating a Golden Apple.\\n Default: 1200\\n Range: 0 ~ 12000
            SERVER|GOLDEN_APPLE_HUNGER_DURATION_TICKS|goldenAppleHungerDurationTicks|I|200|0|6000|Hunger I cost duration (ticks) for a zombie eating a Golden Apple.\\n Default: 200\\n Range: 0 ~ 6000
            SERVER|ENCHANTED_GOLDEN_APPLE_ABSORPTION_DURATION_TICKS|enchantedGoldenAppleAbsorptionDurationTicks|I|1800|0|12000|Absorption II duration (ticks) for a zombie eating an Enchanted Golden Apple.\\n Default: 1800\\n Range: 0 ~ 12000
            SERVER|ENCHANTED_GOLDEN_APPLE_RESISTANCE_DURATION_TICKS|enchantedGoldenAppleResistanceDurationTicks|I|300|0|12000|Resistance I duration (ticks) for a zombie eating an Enchanted Golden Apple.\\n Default: 300\\n Range: 0 ~ 12000
            SERVER|ENCHANTED_GOLDEN_APPLE_HUNGER_DURATION_TICKS|enchantedGoldenAppleHungerDurationTicks|I|400|0|6000|Hunger I cost duration (ticks) for a zombie eating an Enchanted Golden Apple.\\n Default: 400\\n Range: 0 ~ 6000
            SERVER|CHORUS_SLOW_FALLING_DURATION_TICKS|chorusSlowFallingDurationTicks|I|200|0|6000|Slow Falling duration (ticks) granted by a zombie eating a Chorus Fruit.\\n Default: 200\\n Range: 0 ~ 6000
            SERVER|CHORUS_NAUSEA_DURATION_TICKS|chorusNauseaDurationTicks|I|120|0|6000|Nausea cost duration (ticks) for a zombie eating a Chorus Fruit.\\n Default: 120\\n Range: 0 ~ 6000
            SERVER|HONEY_NAUSEA_DURATION_TICKS|honeyNauseaDurationTicks|I|160|0|6000|Nausea cost duration (ticks) for a zombie drinking a Honey Bottle.\\n Default: 160\\n Range: 0 ~ 6000
            PREFERENCES|HEROBRINE_HEARTBEAT_ENABLED|herobrineHeartbeatEnabled|B|true|-|-|Whether a vanilla heartbeat is layered under the Herobrine silence once the encounter escalates (client-side).
            PREFERENCES|HEROBRINE_HEARTBEAT_NEAR_DISTANCE|herobrineHeartbeatNearDistance|I|12|1|28|Inner distance (blocks) of the Herobrine heartbeat band; at/under this the heartbeat is fastest and loudest.\\n Default: 12\\n Range: 1 ~ 28
            PREFERENCES|HEROBRINE_HEARTBEAT_FAR_DISTANCE|herobrineHeartbeatFarDistance|I|28|1|64|Outer distance (blocks) of the Herobrine heartbeat band; beyond this no heartbeat plays.\\n Default: 28\\n Range: 1 ~ 64
            PREFERENCES|HEROBRINE_JOLT_VIGNETTE_ENABLED|herobrineJoltVignetteEnabled|B|true|-|-|Whether the brief client red vignette is shown for the Herobrine jolt.
            """;

    private static final List<Entry> ENTRIES = loadEmbedded();
    private static final Map<MigrationTarget, Map<String, Entry>> BY_TARGET =
            indexByTarget(ENTRIES);

    private ConfigSchemaCatalog() {
    }

    static ConfigSchemaCatalog load() {
        return new ConfigSchemaCatalog();
    }

    String version() {
        return VERSION;
    }

    String authoritySha256() {
        return AUTHORITY_SHA256;
    }

    List<Entry> entries() {
        return ENTRIES;
    }

    List<Entry> entries(MigrationTarget target) {
        Objects.requireNonNull(target, "target");
        List<Entry> matching = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (entry.target() == target) {
                matching.add(entry);
            }
        }
        return List.copyOf(matching);
    }

    Entry require(MigrationTarget target, String key) {
        Entry entry = BY_TARGET.get(Objects.requireNonNull(target, "target")).get(key);
        if (entry == null) {
            throw new IllegalArgumentException(join("unknown ", target, " key ", key));
        }
        return entry;
    }

    private static List<Entry> loadEmbedded() {
        List<RawEntry> rawEntries = new ArrayList<>();
        for (String line : EMBEDDED_SCHEMA.split("\\R", -1)) {
            if (!line.isBlank()) {
                rawEntries.add(parseRawEntry(line));
            }
        }
        List<SourceTarget> expected = expectedCatalogTargets();
        if (rawEntries.size() != expected.size()) {
            throw new IllegalStateException(
                    join(
                            "embedded schema has ",
                            rawEntries.size(),
                            " rows; catalog requires ",
                            expected.size()));
        }

        List<Entry> joined = new ArrayList<>(expected.size());
        for (int index = 0; index < expected.size(); index++) {
            SourceTarget source = expected.get(index);
            RawEntry raw = rawEntries.get(index);
            if (raw.target() != source.target()
                    || !raw.field().equals(source.field())
                    || !raw.key().equals(source.key())) {
                throw new IllegalStateException(
                        join(
                                "embedded schema/catalog drift at row ",
                                index + 1,
                                ": schema=",
                                raw.identity(),
                                ", catalog=",
                                source.identity()));
            }
            joined.add(new Entry(
                    raw.target(),
                    source.sourceKey(),
                    raw.field(),
                    raw.key(),
                    raw.type(),
                    raw.defaultValue(),
                    raw.minimum(),
                    raw.maximum(),
                    raw.comment()));
        }
        return List.copyOf(joined);
    }

    private static RawEntry parseRawEntry(String line) {
        String[] fields = line.split("\\|", -1);
        if (fields.length != 8) {
            throw new IllegalStateException(join("invalid embedded schema row: ", line));
        }
        ValueType type = ValueType.fromCode(fields[3]);
        return new RawEntry(
                MigrationTarget.valueOf(fields[0]),
                fields[1].replace('~', '_'),
                fields[2],
                type,
                type.parse(fields[4]),
                number(fields[5]),
                number(fields[6]),
                fields[7].replace("\\n", "\n"));
    }

    private static Number number(String value) {
        return value.equals("-") ? null : Double.valueOf(value);
    }

    private static List<SourceTarget> expectedCatalogTargets() {
        List<SourceTarget> expected = new ArrayList<>(56);
        for (MigrationTarget target : MigrationTarget.values()) {
            for (ConfigKeyCatalog.Entry entry : ConfigKeyCatalog.entries()) {
                switch (entry.authority()) {
                    case SERVER, INERT -> {
                        if (target == MigrationTarget.SERVER) {
                            expected.add(sourceTarget(entry, target, 0));
                        }
                    }
                    case CLIENT -> {
                        if (target == MigrationTarget.PREFERENCES) {
                            expected.add(sourceTarget(entry, target, 0));
                        }
                    }
                    case SPLIT -> expected.add(sourceTarget(
                            entry,
                            target,
                            target == MigrationTarget.SERVER ? 0 : 1));
                }
            }
        }
        return expected;
    }

    private static SourceTarget sourceTarget(
            ConfigKeyCatalog.Entry entry, MigrationTarget target, int targetIndex) {
        if (targetIndex >= entry.targets().size()) {
            throw new IllegalStateException(
                    join("catalog target is absent for ", entry.legacyTomlKey()));
        }
        ConfigKeyCatalog.Target mapped = entry.targets().get(targetIndex);
        return new SourceTarget(
                entry.legacyTomlKey(), target, mapped.field(), mapped.tomlKey());
    }

    private static Map<MigrationTarget, Map<String, Entry>> indexByTarget(
            List<Entry> entries) {
        Map<MigrationTarget, Map<String, Entry>> result =
                new LinkedHashMap<>();
        for (MigrationTarget target : MigrationTarget.values()) {
            LinkedHashMap<String, Entry> targetEntries = new LinkedHashMap<>();
            for (Entry entry : entries) {
                if (entry.target() == target
                        && targetEntries.put(entry.key(), entry) != null) {
                    throw new IllegalStateException(
                            join("duplicate embedded target key ", target, ":", entry.key()));
                }
            }
            result.put(target, Collections.unmodifiableMap(targetEntries));
        }
        return Collections.unmodifiableMap(result);
    }

    enum ValueType {
        BOOLEAN("B"),
        INTEGER("I"),
        DOUBLE("D"),
        STRING("S"),
        LIST("L");

        private final String code;

        ValueType(String code) {
            this.code = code;
        }

        private static ValueType fromCode(String code) {
            for (ValueType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            throw new IllegalStateException(join("unknown embedded value type ", code));
        }

        private Object parse(String value) {
            return switch (this) {
                case BOOLEAN -> Boolean.valueOf(value);
                case INTEGER -> Long.valueOf(value);
                case DOUBLE -> Double.valueOf(value);
                case STRING -> value;
                case LIST -> {
                    if (!value.startsWith("[") || !value.endsWith("]")) {
                        throw new IllegalStateException(
                                join("invalid embedded list default ", value));
                    }
                    String elements = value.substring(1, value.length() - 1);
                    yield elements.isEmpty()
                            ? List.of()
                            : List.of(elements.split(", "));
                }
            };
        }
    }

    record Entry(
            MigrationTarget target,
            String sourceKey,
            String field,
            String key,
            ValueType type,
            Object defaultValue,
            Number minimum,
            Number maximum,
            String comment) {
        Entry {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(sourceKey, "sourceKey");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(defaultValue, "defaultValue");
            Objects.requireNonNull(comment, "comment");
            if (defaultValue instanceof List<?> list) {
                defaultValue = List.copyOf(list);
            }
            if (!accepts(type, defaultValue)
                    || !inRange(minimum, maximum, defaultValue)) {
                throw new IllegalArgumentException(
                        join("invalid embedded default for ", target, ":", key));
            }
        }

        boolean accepts(Object value) {
            return accepts(type, value);
        }

        boolean inRange(Object value) {
            return inRange(minimum, maximum, value);
        }

        Object canonicalValue(Object value) {
            if (!accepts(value)) {
                throw new IllegalArgumentException(
                        join("invalid value for ", target, ":", key));
            }
            if (type == ValueType.BOOLEAN && value instanceof String text) {
                return Boolean.parseBoolean(text);
            }
            return value instanceof List<?> list ? List.copyOf(list) : value;
        }

        private static boolean accepts(ValueType type, Object value) {
            return switch (type) {
                case BOOLEAN -> value instanceof Boolean
                        || value instanceof String text
                                && ("true".equalsIgnoreCase(text)
                                        || "false".equalsIgnoreCase(text));
                case INTEGER -> value instanceof Byte
                        || value instanceof Short
                        || value instanceof Integer
                        || value instanceof Long;
                case DOUBLE -> value instanceof Number;
                case STRING -> value instanceof String;
                case LIST -> validResourceIdList(value);
            };
        }

        private static boolean validResourceIdList(Object value) {
            if (!(value instanceof List<?> list)) {
                return false;
            }
            for (Object element : list) {
                if (!validResourceId(element)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean inRange(
                Number minimum, Number maximum, Object value) {
            if (minimum == null || maximum == null) {
                return true;
            }
            if (!(value instanceof Number number)) {
                return false;
            }
            double numeric = number.doubleValue();
            return Double.isFinite(numeric)
                    && numeric >= minimum.doubleValue()
                    && numeric <= maximum.doubleValue();
        }

        private static boolean validResourceId(Object value) {
            return value instanceof String text && text.contains(":");
        }
    }

    private record RawEntry(
            MigrationTarget target,
            String field,
            String key,
            ValueType type,
            Object defaultValue,
            Number minimum,
            Number maximum,
            String comment) {
        private String identity() {
            return join(target, ":", field, ":", key);
        }
    }

    private record SourceTarget(
            String sourceKey, MigrationTarget target, String field, String key) {
        private String identity() {
            return join(target, ":", field, ":", key);
        }
    }

    private static String join(Object... parts) {
        StringBuilder result = new StringBuilder();
        for (Object part : parts) {
            result.append(part);
        }
        return result.toString();
    }
}
