package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;
import org.junit.jupiter.api.Test;

class ConfigAuthorityRemoteValuesTest {
    private static final List<String> EXPECTED_INTEGER_FIELDS = List.of(
            "SPIDER_EYE_NIGHT_VISION_DURATION_TICKS",
            "PUFFERFISH_ABSORPTION_DURATION_TICKS",
            "PUFFERFISH_REGENERATION_DURATION_TICKS",
            "PUFFERFISH_REGENERATION_AMPLIFIER",
            "SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS",
            "SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER",
            "SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS",
            "T1_CARRION_WATER_BREATHING_DURATION_TICKS",
            "SWEET_SLOWNESS_DURATION_TICKS",
            "GOLDEN_APPLE_ABSORPTION_DURATION_TICKS",
            "GOLDEN_APPLE_HUNGER_DURATION_TICKS",
            "ENCHANTED_GOLDEN_APPLE_ABSORPTION_DURATION_TICKS",
            "ENCHANTED_GOLDEN_APPLE_RESISTANCE_DURATION_TICKS",
            "ENCHANTED_GOLDEN_APPLE_HUNGER_DURATION_TICKS",
            "CHORUS_SLOW_FALLING_DURATION_TICKS",
            "CHORUS_NAUSEA_DURATION_TICKS",
            "HONEY_NAUSEA_DURATION_TICKS");

    @Test
    void remoteValuesAreTheExactTypedNineteenAndHaveAStableContentHash() {
        ConfigAuthorityRemoteValues values = defaultValues();

        assertEquals(EXPECTED_INTEGER_FIELDS, ConfigAuthorityRemoteValues.integerFields());
        assertEquals(19, values.semanticValueCount());
        assertEquals(List.of("minecraft:rotten_flesh"), values.zombieFoods());
        assertEquals(900, values.integer("SPIDER_EYE_NIGHT_VISION_DURATION_TICKS"));
        assertEquals(0.3D, values.spiderMountSpeed());
        assertEquals(64, values.payloadSha256().length());

        Map<String, Integer> changed = new LinkedHashMap<>(values.integerValues());
        changed.put("SPIDER_EYE_NIGHT_VISION_DURATION_TICKS", 901);
        ConfigAuthorityRemoteValues edited = new ConfigAuthorityRemoteValues(
                values.zombieFoods(), changed, values.spiderMountSpeed());
        assertNotEquals(values.payloadSha256(), edited.payloadSha256(),
                "the payload hash must cover every typed remote value");
    }

    @Test
    void valuesRejectMissingExtraWrongTypeAndOutOfRangeSemantics() {
        ConfigAuthorityRemoteValues values = defaultValues();

        Map<String, Integer> missing = new LinkedHashMap<>(values.integerValues());
        missing.remove("HONEY_NAUSEA_DURATION_TICKS");
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigAuthorityRemoteValues(values.zombieFoods(), missing, values.spiderMountSpeed()));

        Map<String, Integer> extra = new LinkedHashMap<>(values.integerValues());
        extra.put("NOT_AUTHORITY", 1);
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigAuthorityRemoteValues(values.zombieFoods(), extra, values.spiderMountSpeed()));

        Map<String, Integer> outOfRange = new LinkedHashMap<>(values.integerValues());
        outOfRange.put("PUFFERFISH_REGENERATION_AMPLIFIER", 5);
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigAuthorityRemoteValues(values.zombieFoods(), outOfRange, values.spiderMountSpeed()));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigAuthorityRemoteValues(values.zombieFoods(), values.integerValues(), 1.01D));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigAuthorityRemoteValues(List.of("missing_namespace"),
                        values.integerValues(), values.spiderMountSpeed()));
    }

    @Test
    void valuesDefensivelyCopyFoodAndIntegerCollections() {
        List<String> foods = new java.util.ArrayList<>(List.of("minecraft:rotten_flesh"));
        Map<String, Integer> integers = defaultIntegers();
        ConfigAuthorityRemoteValues values = new ConfigAuthorityRemoteValues(foods, integers, 0.3D);

        foods.add("minecraft:spider_eye");
        integers.put("SPIDER_EYE_NIGHT_VISION_DURATION_TICKS", 901);
        assertEquals(List.of("minecraft:rotten_flesh"), values.zombieFoods());
        assertEquals(900, values.integer("SPIDER_EYE_NIGHT_VISION_DURATION_TICKS"));
        assertThrows(UnsupportedOperationException.class,
                () -> values.zombieFoods().add("minecraft:spider_eye"));
        assertThrows(UnsupportedOperationException.class,
                () -> values.integerValues().put("SPIDER_EYE_NIGHT_VISION_DURATION_TICKS", 901));
    }

    @Test
    void foodIdsUseTheSameRootLowercaseSetSemanticsAsServerGameplay() {
        ConfigAuthorityRemoteValues canonical = new ConfigAuthorityRemoteValues(
                List.of(
                        "minecraft:rotten_flesh",
                        "minecraft:spider_eye"),
                defaultIntegers(),
                0.3D);
        ConfigAuthorityRemoteValues equivalent = new ConfigAuthorityRemoteValues(
                List.of(
                        "MINECRAFT:ROTTEN_FLESH",
                        "minecraft:rotten_flesh",
                        "MINECRAFT:SPIDER_EYE"),
                defaultIntegers(),
                0.3D);

        assertEquals(
                List.of(
                        "minecraft:rotten_flesh",
                        "minecraft:spider_eye"),
                equivalent.zombieFoods());
        assertEquals(canonical, equivalent);
        assertEquals(canonical.payloadSha256(), equivalent.payloadSha256());
    }

    @Test
    void foodResolverUsesTheExactSeventeenExistingSemanticKeys() {
        ConfigAuthorityRemoteValues values = defaultValues();
        Set<String> expected = Set.of(
                ZombieFoodRules.KEY_SWEET_SLOWNESS_TICKS,
                ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_TICKS,
                ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER,
                ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_SATURATION_TICKS,
                ZombieFoodRules.KEY_SPIDER_EYE_NIGHT_VISION_TICKS,
                ZombieFoodRules.KEY_T1_CARRION_WATER_BREATHING_TICKS,
                ZombieFoodRules.KEY_GOLDEN_APPLE_ABSORPTION_TICKS,
                ZombieFoodRules.KEY_GOLDEN_APPLE_HUNGER_TICKS,
                ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_ABSORPTION_TICKS,
                ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_RESISTANCE_TICKS,
                ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_HUNGER_TICKS,
                ZombieFoodRules.KEY_PUFFERFISH_ABSORPTION_TICKS,
                ZombieFoodRules.KEY_PUFFERFISH_REGENERATION_TICKS,
                ZombieFoodRules.KEY_PUFFERFISH_REGENERATION_AMPLIFIER,
                ZombieFoodRules.KEY_CHORUS_SLOW_FALLING_TICKS,
                ZombieFoodRules.KEY_CHORUS_NAUSEA_TICKS,
                ZombieFoodRules.KEY_HONEY_NAUSEA_TICKS);

        assertEquals(expected, Set.copyOf(
                ConfigAuthorityRemoteValues.foodSemanticKeys()));
        assertEquals(900, values.foodConfig(
                ZombieFoodRules.KEY_SPIDER_EYE_NIGHT_VISION_TICKS));
        assertThrows(IllegalArgumentException.class,
                () -> values.foodConfig("SPIDER_EYE_NIGHT_VISION_DURATION_TICKS"));
        assertThrows(IllegalArgumentException.class,
                () -> values.foodConfig("unknown.food.key"));
    }

    static ConfigAuthorityRemoteValues defaultValues() {
        return new ConfigAuthorityRemoteValues(
                List.of("minecraft:rotten_flesh"), defaultIntegers(), 0.3D);
    }

    static ConfigAuthorityRemoteValues editedValues(int generation) {
        if (generation < 1 || generation > 2) {
            throw new IllegalArgumentException("generation must be 1 or 2");
        }
        ConfigAuthorityRemoteValues defaults = defaultValues();
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        LinkedHashMap<String, Integer> edited =
                new LinkedHashMap<>(defaults.integerValues());
        for (String field : EXPECTED_INTEGER_FIELDS) {
            ConfigKeyCatalog.Entry catalogEntry =
                    ConfigKeyCatalog.entries().stream()
                            .filter(entry -> entry.legacyField().equals(field))
                            .findFirst()
                            .orElseThrow();
            ConfigSchemaCatalog.Entry schemaEntry = schema.require(
                    MigrationTarget.SERVER,
                    catalogEntry.targets().getFirst().tomlKey());
            int current = edited.get(field);
            int maximum = schemaEntry.maximum().intValue();
            int minimum = schemaEntry.minimum().intValue();
            int candidate = current + generation;
            if (candidate > maximum) {
                candidate = current - generation;
            }
            if (candidate < minimum || candidate == current) {
                throw new IllegalStateException(
                        "No edited test value for " + field);
            }
            edited.put(field, candidate);
        }
        return new ConfigAuthorityRemoteValues(
                generation == 1
                        ? List.of(
                                "minecraft:rotten_flesh",
                                "minecraft:apple")
                        : List.of(
                                "minecraft:rotten_flesh",
                                "minecraft:carrot"),
                edited,
                generation == 1 ? 0.45D : 0.75D);
    }

    static Map<String, Integer> defaultIntegers() {
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        for (String field : EXPECTED_INTEGER_FIELDS) {
            ConfigKeyCatalog.Entry catalogEntry = ConfigKeyCatalog.entries().stream()
                    .filter(entry -> entry.legacyField().equals(field))
                    .findFirst()
                    .orElseThrow();
            ConfigSchemaCatalog.Entry schemaEntry =
                    schema.require(MigrationTarget.SERVER, catalogEntry.targets().getFirst().tomlKey());
            values.put(field, ((Number) schemaEntry.defaultValue()).intValue());
        }
        return values;
    }
}
