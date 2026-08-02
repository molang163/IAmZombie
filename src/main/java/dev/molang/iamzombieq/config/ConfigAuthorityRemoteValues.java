package dev.molang.iamzombieq.config;

import dev.molang.iamzombieq.IAmZombieServerConfig;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The exact typed remote authority projection: one list, seventeen integers,
 * and the spider speed.
 */
final class ConfigAuthorityRemoteValues {
    private static final ConfigSchemaCatalog SCHEMA = ConfigSchemaCatalog.load();
    private static final List<ConfigKeyCatalog.Entry> REMOTE_ENTRIES =
            ConfigKeyCatalog.entries().stream()
                    .filter(ConfigKeyCatalog.Entry::remoteRequired)
                    .toList();
    private static final List<String> INTEGER_FIELDS = REMOTE_ENTRIES.stream()
            .filter(entry -> !entry.legacyField().equals("ZOMBIE_FOODS"))
            .filter(entry -> !entry.legacyField().equals("SPIDER_MOUNT_SPEED"))
            .map(ConfigKeyCatalog.Entry::legacyField)
            .toList();
    private static final Map<String, String> FOOD_SEMANTIC_TO_FIELD =
            foodSemanticToField();

    private final List<String> zombieFoods;
    private final Map<String, Integer> integerValues;
    private final double spiderMountSpeed;

    ConfigAuthorityRemoteValues(
            List<String> zombieFoods,
            Map<String, Integer> integerValues,
            double spiderMountSpeed) {
        this.zombieFoods = Objects.requireNonNull(
                        zombieFoods, "zombieFoods")
                .stream()
                .map(value -> Objects.requireNonNull(
                                value, "zombieFoods element")
                        .toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        this.integerValues = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(integerValues, "integerValues")));
        this.spiderMountSpeed = spiderMountSpeed;
        validate();
    }

    static ConfigAuthorityRemoteValues captureServerConfig() {
        List<String> foods = IAmZombieServerConfig.ZOMBIE_FOODS.get().stream()
                .map(String::valueOf)
                .toList();
        LinkedHashMap<String, Integer> integers = new LinkedHashMap<>();
        integers.put("SPIDER_EYE_NIGHT_VISION_DURATION_TICKS",
                IAmZombieServerConfig.SPIDER_EYE_NIGHT_VISION_DURATION_TICKS.get());
        integers.put("PUFFERFISH_ABSORPTION_DURATION_TICKS",
                IAmZombieServerConfig.PUFFERFISH_ABSORPTION_DURATION_TICKS.get());
        integers.put("PUFFERFISH_REGENERATION_DURATION_TICKS",
                IAmZombieServerConfig.PUFFERFISH_REGENERATION_DURATION_TICKS.get());
        integers.put("PUFFERFISH_REGENERATION_AMPLIFIER",
                IAmZombieServerConfig.PUFFERFISH_REGENERATION_AMPLIFIER.get());
        integers.put("SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS",
                IAmZombieServerConfig.SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS.get());
        integers.put("SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER",
                IAmZombieServerConfig.SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER.get());
        integers.put("SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS",
                IAmZombieServerConfig.SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS.get());
        integers.put("T1_CARRION_WATER_BREATHING_DURATION_TICKS",
                IAmZombieServerConfig.T1_CARRION_WATER_BREATHING_DURATION_TICKS.get());
        integers.put("SWEET_SLOWNESS_DURATION_TICKS",
                IAmZombieServerConfig.SWEET_SLOWNESS_DURATION_TICKS.get());
        integers.put("GOLDEN_APPLE_ABSORPTION_DURATION_TICKS",
                IAmZombieServerConfig.GOLDEN_APPLE_ABSORPTION_DURATION_TICKS.get());
        integers.put("GOLDEN_APPLE_HUNGER_DURATION_TICKS",
                IAmZombieServerConfig.GOLDEN_APPLE_HUNGER_DURATION_TICKS.get());
        integers.put("ENCHANTED_GOLDEN_APPLE_ABSORPTION_DURATION_TICKS",
                IAmZombieServerConfig.ENCHANTED_GOLDEN_APPLE_ABSORPTION_DURATION_TICKS.get());
        integers.put("ENCHANTED_GOLDEN_APPLE_RESISTANCE_DURATION_TICKS",
                IAmZombieServerConfig.ENCHANTED_GOLDEN_APPLE_RESISTANCE_DURATION_TICKS.get());
        integers.put("ENCHANTED_GOLDEN_APPLE_HUNGER_DURATION_TICKS",
                IAmZombieServerConfig.ENCHANTED_GOLDEN_APPLE_HUNGER_DURATION_TICKS.get());
        integers.put("CHORUS_SLOW_FALLING_DURATION_TICKS",
                IAmZombieServerConfig.CHORUS_SLOW_FALLING_DURATION_TICKS.get());
        integers.put("CHORUS_NAUSEA_DURATION_TICKS",
                IAmZombieServerConfig.CHORUS_NAUSEA_DURATION_TICKS.get());
        integers.put("HONEY_NAUSEA_DURATION_TICKS",
                IAmZombieServerConfig.HONEY_NAUSEA_DURATION_TICKS.get());
        return new ConfigAuthorityRemoteValues(
                foods, integers, IAmZombieServerConfig.SPIDER_MOUNT_SPEED.get());
    }

    static List<String> integerFields() {
        return INTEGER_FIELDS;
    }

    static List<String> foodSemanticKeys() {
        return List.copyOf(FOOD_SEMANTIC_TO_FIELD.keySet());
    }

    List<String> zombieFoods() {
        return zombieFoods;
    }

    Map<String, Integer> integerValues() {
        return integerValues;
    }

    int integer(String field) {
        Integer value = integerValues.get(Objects.requireNonNull(field, "field"));
        if (value == null) {
            throw new IllegalArgumentException(
                    "Not a remote integer authority field: " + field);
        }
        return value;
    }

    int foodConfig(String semanticKey) {
        String field = FOOD_SEMANTIC_TO_FIELD.get(
                Objects.requireNonNull(semanticKey, "semanticKey"));
        if (field == null) {
            throw new IllegalArgumentException(
                    "Unknown zombie-food config key: " + semanticKey);
        }
        return integer(field);
    }

    double spiderMountSpeed() {
        return spiderMountSpeed;
    }

    int semanticValueCount() {
        return 2 + integerValues.size();
    }

    String payloadSha256() {
        return ConfigAuthorityProtocol.payloadSha256(this);
    }

    private void validate() {
        if (REMOTE_ENTRIES.size() != 19 || INTEGER_FIELDS.size() != 17) {
            throw new IllegalStateException(
                    "Authority catalog must be zombieFoods + 17 integers + spiderMountSpeed");
        }
        if (!integerValues.keySet().equals(
                new java.util.LinkedHashSet<>(INTEGER_FIELDS))) {
            throw new IllegalArgumentException(
                    "Remote integer keys must be the exact ordered authority set");
        }
        if (FOOD_SEMANTIC_TO_FIELD.size() != 17
                || FOOD_SEMANTIC_TO_FIELD.values().stream().distinct().count()
                        != 17
                || !new java.util.LinkedHashSet<>(
                                FOOD_SEMANTIC_TO_FIELD.values())
                        .equals(new java.util.LinkedHashSet<>(
                                INTEGER_FIELDS))) {
            throw new IllegalStateException(
                    "Zombie-food semantic key mapping must cover each remote integer exactly once");
        }

        ConfigSchemaCatalog.Entry foodsSchema = schemaForField("ZOMBIE_FOODS");
        if (!foodsSchema.accepts(zombieFoods)) {
            throw new IllegalArgumentException(
                    "zombieFoods does not satisfy the authority schema");
        }
        for (String field : INTEGER_FIELDS) {
            Integer value = integerValues.get(field);
            ConfigSchemaCatalog.Entry entry = schemaForField(field);
            if (!entry.accepts(value) || !entry.inRange(value)) {
                throw new IllegalArgumentException(
                        "Remote integer is invalid for " + field + ": " + value);
            }
        }
        ConfigSchemaCatalog.Entry speedSchema =
                schemaForField("SPIDER_MOUNT_SPEED");
        if (!speedSchema.accepts(spiderMountSpeed)
                || !speedSchema.inRange(spiderMountSpeed)) {
            throw new IllegalArgumentException(
                    "spiderMountSpeed does not satisfy the authority schema");
        }
    }

    private static ConfigSchemaCatalog.Entry schemaForField(String field) {
        ConfigKeyCatalog.Entry catalog = REMOTE_ENTRIES.stream()
                .filter(entry -> entry.legacyField().equals(field))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Remote field is absent from authority catalog: " + field));
        return SCHEMA.require(
                MigrationTarget.SERVER, catalog.targets().getFirst().tomlKey());
    }

    private static Map<String, String> foodSemanticToField() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        result.put(ZombieFoodRules.KEY_SWEET_SLOWNESS_TICKS,
                "SWEET_SLOWNESS_DURATION_TICKS");
        result.put(ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_TICKS,
                "SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS");
        result.put(ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER,
                "SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER");
        result.put(ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_SATURATION_TICKS,
                "SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS");
        result.put(ZombieFoodRules.KEY_SPIDER_EYE_NIGHT_VISION_TICKS,
                "SPIDER_EYE_NIGHT_VISION_DURATION_TICKS");
        result.put(ZombieFoodRules.KEY_T1_CARRION_WATER_BREATHING_TICKS,
                "T1_CARRION_WATER_BREATHING_DURATION_TICKS");
        result.put(ZombieFoodRules.KEY_GOLDEN_APPLE_ABSORPTION_TICKS,
                "GOLDEN_APPLE_ABSORPTION_DURATION_TICKS");
        result.put(ZombieFoodRules.KEY_GOLDEN_APPLE_HUNGER_TICKS,
                "GOLDEN_APPLE_HUNGER_DURATION_TICKS");
        result.put(ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_ABSORPTION_TICKS,
                "ENCHANTED_GOLDEN_APPLE_ABSORPTION_DURATION_TICKS");
        result.put(ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_RESISTANCE_TICKS,
                "ENCHANTED_GOLDEN_APPLE_RESISTANCE_DURATION_TICKS");
        result.put(ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_HUNGER_TICKS,
                "ENCHANTED_GOLDEN_APPLE_HUNGER_DURATION_TICKS");
        result.put(ZombieFoodRules.KEY_PUFFERFISH_ABSORPTION_TICKS,
                "PUFFERFISH_ABSORPTION_DURATION_TICKS");
        result.put(ZombieFoodRules.KEY_PUFFERFISH_REGENERATION_TICKS,
                "PUFFERFISH_REGENERATION_DURATION_TICKS");
        result.put(ZombieFoodRules.KEY_PUFFERFISH_REGENERATION_AMPLIFIER,
                "PUFFERFISH_REGENERATION_AMPLIFIER");
        result.put(ZombieFoodRules.KEY_CHORUS_SLOW_FALLING_TICKS,
                "CHORUS_SLOW_FALLING_DURATION_TICKS");
        result.put(ZombieFoodRules.KEY_CHORUS_NAUSEA_TICKS,
                "CHORUS_NAUSEA_DURATION_TICKS");
        result.put(ZombieFoodRules.KEY_HONEY_NAUSEA_TICKS,
                "HONEY_NAUSEA_DURATION_TICKS");
        return Collections.unmodifiableMap(result);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ConfigAuthorityRemoteValues that
                && zombieFoods.equals(that.zombieFoods)
                && integerValues.equals(that.integerValues)
                && Double.doubleToLongBits(spiderMountSpeed)
                        == Double.doubleToLongBits(that.spiderMountSpeed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                zombieFoods, integerValues,
                Double.doubleToLongBits(spiderMountSpeed));
    }

    @Override
    public String toString() {
        return "ConfigAuthorityRemoteValues[foods=" + zombieFoods.size()
                + ", integers=" + integerValues.size()
                + ", spiderMountSpeed=" + spiderMountSpeed + "]";
    }
}
