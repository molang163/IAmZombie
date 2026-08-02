package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.IAmZombieClientConfig;
import dev.molang.iamzombieq.IAmZombieConfig;
import dev.molang.iamzombieq.IAmZombiePreferencesConfig;
import dev.molang.iamzombieq.IAmZombieServerConfig;
import dev.molang.iamzombieq.config.ConfigKeyCatalog.Authority;
import dev.molang.iamzombieq.config.ConfigKeyCatalog.Entry;
import dev.molang.iamzombieq.rules.ZombiePlayerSkinMode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

class ConfigAuthoritySchemaTest {
    private static final Path LEGACY_SOURCE =
            Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieConfig.java");
    private static final Path SERVER_SOURCE =
            Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieServerConfig.java");
    private static final Path PREFERENCES_SOURCE =
            Path.of("src/main/java/dev/molang/iamzombieq/IAmZombiePreferencesConfig.java");
    private static final Path APPEARANCE_SOURCE =
            Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieClientConfig.java");
    private static final String SCHEMA_FIXTURE =
            "/dev/molang/iamzombieq/config/config-authority-schema-1.1.0.tsv";

    private static final List<String> SERVER_FIELDS = List.of(
            "DEBUG_LOGGING",
            "STARTING_ROTTEN_FLESH",
            "UNLOCK_COFFIN_RECIPES_ON_FIRST_JOIN",
            "UNDEAD_IGNORE_ZOMBIE_PLAYER",
            "NORMAL_ZOMBIE_INNATE_ARMOR",
            "DROWNED_INNATE_ARMOR",
            "HUSK_INNATE_ARMOR",
            "ZOMBIFIED_PIGLIN_INNATE_ARMOR",
            "SUN_PROTECTION_HEADGEAR_DAMAGE",
            "EASY_INFECTION_CHANCE",
            "NORMAL_INFECTION_CHANCE",
            "HARD_INFECTION_CHANCE",
            "ZOMBIE_FOODS",
            "HUMAN_FOOD_NAUSEA_DURATION_TICKS",
            "HUMAN_FOOD_HUNGER_DURATION_TICKS",
            "HUMAN_FOOD_HUNGER_AMPLIFIER",
            "SPIDER_EYE_NIGHT_VISION_DURATION_TICKS",
            "PUFFERFISH_ABSORPTION_DURATION_TICKS",
            "PUFFERFISH_REGENERATION_DURATION_TICKS",
            "PUFFERFISH_REGENERATION_AMPLIFIER",
            "POISONOUS_POTATO_POSITIVE_DURATION_TICKS",
            "SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS",
            "SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER",
            "SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS",
            "BED_EXPLOSION_POWER",
            "BED_EXPLOSION_CAUSES_FIRE",
            "HEROBRINE_CAVE_CHECK_INTERVAL_TICKS",
            "HEROBRINE_CAVE_SPAWN_CHANCE",
            "HEROBRINE_ESCALATION_SIGHTINGS",
            "HEROBRINE_LETHAL_SIGHTINGS",
            "HEROBRINE_MEMORY_WINDOW_TICKS",
            "HEROBRINE_LETHAL_COOLDOWN_TICKS",
            "HEROBRINE_OMEN_ENABLED",
            "HEROBRINE_OMEN_DURATION_TICKS",
            "HEROBRINE_JOLT_ENABLED",
            "SPIDER_MOUNT_SPEED",
            "REINFORCEMENTS_ENABLED",
            "REINFORCEMENT_SPAWN_ATTEMPTS",
            "T1_CARRION_STRENGTH_DURATION_TICKS",
            "T1_CARRION_SPEED_DURATION_TICKS",
            "T1_CARRION_SATURATION_DURATION_TICKS",
            "T1_CARRION_WATER_BREATHING_DURATION_TICKS",
            "T2_FORAGE_SATURATION_DURATION_TICKS",
            "SWEET_SLOWNESS_DURATION_TICKS",
            "GOLDEN_APPLE_ABSORPTION_DURATION_TICKS",
            "GOLDEN_APPLE_HUNGER_DURATION_TICKS",
            "ENCHANTED_GOLDEN_APPLE_ABSORPTION_DURATION_TICKS",
            "ENCHANTED_GOLDEN_APPLE_RESISTANCE_DURATION_TICKS",
            "ENCHANTED_GOLDEN_APPLE_HUNGER_DURATION_TICKS",
            "CHORUS_SLOW_FALLING_DURATION_TICKS",
            "CHORUS_NAUSEA_DURATION_TICKS",
            "HONEY_NAUSEA_DURATION_TICKS");

    private static final List<String> HEARTBEAT_FIELDS = List.of(
            "HEROBRINE_HEARTBEAT_ENABLED",
            "HEROBRINE_HEARTBEAT_NEAR_DISTANCE",
            "HEROBRINE_HEARTBEAT_FAR_DISTANCE");

    private static final List<String> PREFERENCES_FIELDS = List.of(
            "HEROBRINE_HEARTBEAT_ENABLED",
            "HEROBRINE_HEARTBEAT_NEAR_DISTANCE",
            "HEROBRINE_HEARTBEAT_FAR_DISTANCE",
            "HEROBRINE_JOLT_VIGNETTE_ENABLED");

    private static final List<String> APPEARANCE_FIELDS = List.of(
            "PLAYER_SKIN_MODE",
            "FIRST_PERSON_ARM_SKIN_MODE");

    private static final List<Object> VALIDATOR_PROBES = Arrays.asList(
            null,
            true,
            false,
            -1,
            0,
            1,
            4,
            28,
            64,
            200,
            6_000,
            12_000,
            72_000,
            -1.0,
            0.0,
            0.05,
            0.3,
            1.0,
            1.000_001,
            "minecraft:rotten_flesh",
            "not-an-id",
            List.of(),
            List.of("minecraft:rotten_flesh"),
            List.of("not-an-id"),
            List.of(1));

    @Test
    void physicalSchemasHaveExactPublicFieldsAndExactKeyCounts() throws ReflectiveOperationException {
        assertHolderShape(IAmZombieServerConfig.class, IAmZombieServerConfig.SPEC, SERVER_FIELDS);
        assertHolderShape(IAmZombiePreferencesConfig.class, IAmZombiePreferencesConfig.SPEC, PREFERENCES_FIELDS);
        assertHolderShape(IAmZombieClientConfig.class, IAmZombieClientConfig.SPEC, APPEARANCE_FIELDS);

        assertEquals(52, SERVER_FIELDS.size());
        assertEquals(4, PREFERENCES_FIELDS.size());
        assertEquals(2, APPEARANCE_FIELDS.size());
    }

    @Test
    void absoluteSchemaFixturePinsAllFiftyEightPhysicalDefinitions()
            throws ReflectiveOperationException, IOException {
        List<String> actual = new ArrayList<>();
        actual.addAll(schemaRows("SERVER", IAmZombieServerConfig.class, SERVER_SOURCE, SERVER_FIELDS));
        actual.addAll(schemaRows(
                "PREFERENCES", IAmZombiePreferencesConfig.class, PREFERENCES_SOURCE, PREFERENCES_FIELDS));
        actual.addAll(schemaRows("APPEARANCE", IAmZombieClientConfig.class, APPEARANCE_SOURCE, APPEARANCE_FIELDS));
        assertEquals(58, actual.size(), "SERVER52 + preferences4 + appearance2");

        InputStream stream = ConfigAuthoritySchemaTest.class.getResourceAsStream(SCHEMA_FIXTURE);
        if (stream == null) {
            throw new AssertionError("missing reviewed schema fixture " + SCHEMA_FIXTURE
                    + "; expected rows:\n" + String.join("\n", actual));
        }
        List<String> expected = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    expected.add(line);
                }
            }
        }
        assertEquals(actual.size(), expected.size(), "absolute schema fixture row count");
        assertEquals(expected, actual,
                "evaluated default/range/comment/validator kind or absolute declaration changed");
    }

    @Test
    void serverAndHeartbeatDefinitionsPreserveEveryLegacyMetadataProperty()
            throws ReflectiveOperationException {
        for (String field : SERVER_FIELDS) {
            assertDefinitionEquivalent(IAmZombieConfig.class, IAmZombieServerConfig.class, field);
        }
        for (String field : HEARTBEAT_FIELDS) {
            assertDefinitionEquivalent(IAmZombieConfig.class, IAmZombiePreferencesConfig.class, field);
        }
    }

    @Test
    void compatibilityFacadeBuildsNoShadowDefinitions() throws IOException {
        String legacySource = requireSource(LEGACY_SOURCE);
        assertFalse(
                legacySource.contains("new ModConfigSpec.Builder")
                        || legacySource.contains("BUILDER."),
                "the K1 compatibility facade must not build a COMMON shadow");
        assertTrue(
                legacySource.contains(
                        "public static final ModConfigSpec SPEC = IAmZombieServerConfig.SPEC;"),
                "the legacy SPEC field must forward to the canonical SERVER spec");
    }

    @Test
    void zombieFoodsPreservesItsExactListValidatorContract() throws ReflectiveOperationException {
        ModConfigSpec.ConfigValue<?> foods = configValue(IAmZombieServerConfig.class, "ZOMBIE_FOODS");
        ModConfigSpec.ValueSpec spec = foods.getSpec();

        assertTrue(spec.test(List.of()), "zombieFoods remains explicitly allowed to be empty");
        assertTrue(spec.test(List.of("minecraft:rotten_flesh")));
        assertTrue(spec.test(List.of("modid:path", "another:value")));
        assertTrue(spec.test(List.of(":path", "modid:", ":")),
                "the signed holder contract requires only String.contains(\":\")");
        assertFalse(spec.test(List.of("missing_namespace_separator")));
        assertFalse(spec.test(List.of(1)));
        assertFalse(spec.test("minecraft:rotten_flesh"));
        assertFalse(spec.test(null));
    }

    @Test
    void newVignettePreferenceHasOneExactBooleanDefinition() throws ReflectiveOperationException, IOException {
        Field field = IAmZombiePreferencesConfig.class.getField("HEROBRINE_JOLT_VIGNETTE_ENABLED");
        assertEquals(ModConfigSpec.BooleanValue.class, field.getType());

        ModConfigSpec.ConfigValue<?> value = configValue(
                IAmZombiePreferencesConfig.class, "HEROBRINE_JOLT_VIGNETTE_ENABLED");
        ModConfigSpec.ValueSpec spec = value.getSpec();
        assertEquals(List.of("herobrineJoltVignetteEnabled"), value.getPath());
        assertEquals(true, value.getDefault());
        assertEquals("Whether the brief client red vignette is shown for the Herobrine jolt.",
                spec.getComment());
        assertEquals(Boolean.class, spec.getClazz());
        assertNull(spec.getRange());
        assertNull(spec.getTranslationKey());
        assertEquals(ModConfigSpec.RestartType.NONE, spec.restartType());
        assertTrue(spec.test(true));
        assertTrue(spec.test(false));
        assertFalse(spec.test(null));
        assertTrue(spec.test("true"));
        assertTrue(spec.test("FALSE"));
        assertFalse(spec.test("not-a-boolean"));

        String expectedDeclaration = """
                public static final ModConfigSpec.BooleanValue HEROBRINE_JOLT_VIGNETTE_ENABLED = BUILDER
                        .comment("Whether the brief client red vignette is shown for the Herobrine jolt.")
                        .define("herobrineJoltVignetteEnabled", true);
                """;
        assertEquals(normalizeDeclaration(expectedDeclaration),
                normalizeDeclaration(declarationBlock(requireSource(PREFERENCES_SOURCE),
                        "HEROBRINE_JOLT_VIGNETTE_ENABLED")));
    }

    @Test
    void appearanceSchemaRemainsTheExactGeneratedTwoEnumDefinitions()
            throws ReflectiveOperationException {
        assertAppearanceDefinition(
                "PLAYER_SKIN_MODE",
                "playerSkinMode",
                "How zombie players are skinned in third person. MONSTER_TEXTURE uses vanilla zombie/drowned/husk "
                        + "textures on the player model; PLAYER_SKIN keeps the player's own skin.\n"
                        + "Allowed Values: MONSTER_TEXTURE, PLAYER_SKIN");
        assertAppearanceDefinition(
                "FIRST_PERSON_ARM_SKIN_MODE",
                "firstPersonArmSkinMode",
                "How first-person arms are skinned. MONSTER_TEXTURE uses the current zombie form texture; "
                        + "PLAYER_SKIN keeps vanilla player arms.\n"
                        + "Allowed Values: MONSTER_TEXTURE, PLAYER_SKIN");
    }

    @Test
    void signedDispositionAndRemoteArithmeticMatchesThePhysicalSchemas() {
        List<Entry> entries = ConfigKeyCatalog.entries();
        Map<Authority, Long> counts = entries.stream().collect(Collectors.groupingBy(
                Entry::authority, Collectors.counting()));

        assertEquals(47L, counts.get(Authority.SERVER).longValue());
        assertEquals(3L, counts.get(Authority.CLIENT).longValue());
        assertEquals(1L, counts.get(Authority.SPLIT).longValue());
        assertEquals(4L, counts.get(Authority.INERT).longValue());
        assertEquals(52, counts.get(Authority.SERVER) + counts.get(Authority.SPLIT)
                + counts.get(Authority.INERT),
                "SERVER52 = 47 server dispositions + jolt server half + four inert storage keys");
        assertEquals(4, counts.get(Authority.CLIENT) + counts.get(Authority.SPLIT),
                "preferences4 = heartbeat3 + jolt client half");
        assertEquals(19, entries.stream().filter(Entry::remoteRequired).count());
    }

    private static void assertHolderShape(
            Class<?> holder, ModConfigSpec spec, List<String> expectedConfigFields)
            throws ReflectiveOperationException {
        Set<String> expectedPublicFields = new HashSet<>(expectedConfigFields);
        expectedPublicFields.add("SPEC");
        Set<String> actualPublicFields = Arrays.stream(holder.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toUnmodifiableSet());
        assertEquals(expectedPublicFields, actualPublicFields,
                holder.getSimpleName() + " public field set must be exact; extra fields are not ignored");

        Set<String> expectedTomlKeys = new HashSet<>();
        for (String field : expectedConfigFields) {
            Field declared = holder.getField(field);
            assertTrue(Modifier.isStatic(declared.getModifiers()), field + " must be static");
            assertTrue(Modifier.isFinal(declared.getModifiers()), field + " must be final");
            assertTrue(ModConfigSpec.ConfigValue.class.isAssignableFrom(declared.getType()),
                    field + " must be a ConfigValue field");
            ModConfigSpec.ConfigValue<?> value = configValue(holder, field);
            assertEquals(1, value.getPath().size(), field + " must remain a flat TOML key");
            assertTrue(expectedTomlKeys.add(value.getPath().getFirst()), "duplicate TOML path for " + field);
        }
        assertEquals(expectedConfigFields.size(), spec.getValues().valueMap().size());
        assertEquals(expectedTomlKeys, spec.getValues().valueMap().keySet(),
                holder.getSimpleName() + " spec key set must be exact; extra values are not filtered out");
    }

    private static void assertDefinitionEquivalent(Class<?> legacyOwner, Class<?> canonicalOwner, String fieldName)
            throws ReflectiveOperationException {
        Field legacyField = legacyOwner.getField(fieldName);
        Field canonicalField = canonicalOwner.getField(fieldName);
        assertEquals(legacyField.getType(), canonicalField.getType(), fieldName + " ConfigValue subtype changed");
        assertEquals(legacyField.getGenericType().getTypeName(), canonicalField.getGenericType().getTypeName(),
                fieldName + " generic declaration changed");

        ModConfigSpec.ConfigValue<?> legacyValue = configValue(legacyOwner, fieldName);
        ModConfigSpec.ConfigValue<?> canonicalValue = configValue(canonicalOwner, fieldName);
        assertEquals(legacyValue.getPath(), canonicalValue.getPath(), fieldName + " TOML path changed");
        assertEquals(legacyValue.getDefault(), canonicalValue.getDefault(), fieldName + " default changed");

        ModConfigSpec.ValueSpec legacySpec = legacyValue.getSpec();
        ModConfigSpec.ValueSpec canonicalSpec = canonicalValue.getSpec();
        assertEquals(legacySpec.getComment(), canonicalSpec.getComment(),
                fieldName + " complete generated comment changed");
        assertEquals(legacySpec.getClazz(), canonicalSpec.getClazz(), fieldName + " validator class changed");
        assertEquals(legacySpec.getTranslationKey(), canonicalSpec.getTranslationKey(),
                fieldName + " translation key changed");
        assertEquals(legacySpec.restartType(), canonicalSpec.restartType(), fieldName + " restart contract changed");
        assertRangeEquivalent(legacySpec.getRange(), canonicalSpec.getRange(), fieldName);

        for (Object probe : VALIDATOR_PROBES) {
            assertEquals(legacySpec.test(probe), canonicalSpec.test(probe),
                    () -> fieldName + " validator differs for probe " + String.valueOf(probe));
        }
        assertRepresentativeBoundaryBehavior(canonicalSpec, fieldName);
    }

    private static void assertRangeEquivalent(
            ModConfigSpec.Range<?> legacyRange, ModConfigSpec.Range<?> canonicalRange, String fieldName) {
        if (legacyRange == null) {
            assertNull(canonicalRange, fieldName + " unexpectedly acquired a range");
            return;
        }
        assertNotNull(canonicalRange, fieldName + " lost its range");
        assertEquals(legacyRange.getClazz(), canonicalRange.getClazz(), fieldName + " range class changed");
        assertEquals(legacyRange.getMin(), canonicalRange.getMin(), fieldName + " minimum changed");
        assertEquals(legacyRange.getMax(), canonicalRange.getMax(), fieldName + " maximum changed");
    }

    private static void assertRepresentativeBoundaryBehavior(ModConfigSpec.ValueSpec spec, String fieldName) {
        ModConfigSpec.Range<?> range = spec.getRange();
        if (range != null) {
            Object minimum = range.getMin();
            Object maximum = range.getMax();
            assertTrue(spec.test(minimum), fieldName + " must accept its minimum");
            assertTrue(spec.test(maximum), fieldName + " must accept its maximum");
            if (minimum instanceof Integer min && maximum instanceof Integer max) {
                assertFalse(spec.test(min - 1), fieldName + " must reject below-minimum integers");
                assertFalse(spec.test(max + 1), fieldName + " must reject above-maximum integers");
            } else if (minimum instanceof Double min && maximum instanceof Double max) {
                assertFalse(spec.test(Math.nextDown(min)), fieldName + " must reject below-minimum doubles");
                assertFalse(spec.test(Math.nextUp(max)), fieldName + " must reject above-maximum doubles");
            }
        } else if (spec.getClazz() == Boolean.class) {
            assertTrue(spec.test(true));
            assertTrue(spec.test(false));
            assertFalse(spec.test(null));
            assertTrue(spec.test("true"));
            assertTrue(spec.test("FALSE"));
            assertFalse(spec.test("not-a-boolean"));
        }
    }

    private static void assertAppearanceDefinition(String fieldName, String tomlKey, String generatedComment)
            throws ReflectiveOperationException {
        Field field = IAmZombieClientConfig.class.getField(fieldName);
        assertEquals(ModConfigSpec.EnumValue.class, field.getType());
        ModConfigSpec.ConfigValue<?> value = configValue(IAmZombieClientConfig.class, fieldName);
        ModConfigSpec.ValueSpec spec = value.getSpec();

        assertEquals(List.of(tomlKey), value.getPath());
        assertSame(ZombiePlayerSkinMode.MONSTER_TEXTURE, value.getDefault());
        assertEquals(generatedComment, spec.getComment());
        assertEquals(ZombiePlayerSkinMode.class, spec.getClazz());
        assertNull(spec.getRange());
        assertNull(spec.getTranslationKey());
        assertEquals(ModConfigSpec.RestartType.NONE, spec.restartType());
        assertTrue(spec.test(ZombiePlayerSkinMode.MONSTER_TEXTURE));
        assertTrue(spec.test(ZombiePlayerSkinMode.PLAYER_SKIN));
        assertTrue(spec.test("monster_texture"));
        assertFalse(spec.test("not_a_skin_mode"));
        assertFalse(spec.test(null));
    }

    private static ModConfigSpec.ConfigValue<?> configValue(Class<?> owner, String fieldName)
            throws ReflectiveOperationException {
        Object value = owner.getField(fieldName).get(null);
        assertTrue(value instanceof ModConfigSpec.ConfigValue<?>, fieldName + " is not a ConfigValue");
        return (ModConfigSpec.ConfigValue<?>) value;
    }

    private static List<String> schemaRows(
            String surface, Class<?> owner, Path sourcePath, List<String> fieldNames)
            throws ReflectiveOperationException, IOException {
        String source = requireSource(sourcePath);
        List<String> rows = new ArrayList<>();
        for (String fieldName : fieldNames) {
            Field field = owner.getField(fieldName);
            ModConfigSpec.ConfigValue<?> value = configValue(owner, fieldName);
            ModConfigSpec.ValueSpec spec = value.getSpec();
            ModConfigSpec.Range<?> range = spec.getRange();
            rows.add(String.join("\t",
                    surface,
                    fieldName,
                    field.getType().getSimpleName(),
                    value.getPath().getFirst(),
                    escaped(String.valueOf(value.getDefault())),
                    validatorKind(field, fieldName),
                    range == null ? "-" : range.getClazz().getSimpleName(),
                    range == null ? "-" : String.valueOf(range.getMin()),
                    range == null ? "-" : String.valueOf(range.getMax()),
                    escaped(String.valueOf(spec.getComment())),
                    sha256(normalizeDeclaration(declarationBlock(source, fieldName)))));
        }
        return rows;
    }

    private static String validatorKind(Field field, String fieldName) {
        if (fieldName.equals("ZOMBIE_FOODS")) {
            return "LIST_ALLOW_EMPTY_RESOURCE_ID";
        }
        if (field.getType() == ModConfigSpec.BooleanValue.class) {
            return "BOOLEAN_OR_STRING";
        }
        if (field.getType() == ModConfigSpec.IntValue.class) {
            return "NUMBER_RANGE_INTEGER";
        }
        if (field.getType() == ModConfigSpec.DoubleValue.class) {
            return "NUMBER_RANGE_DOUBLE";
        }
        if (field.getType() == ModConfigSpec.EnumValue.class) {
            return "ENUM_NAME_IGNORE_CASE";
        }
        throw new AssertionError("unreviewed validator kind for " + fieldName + ": " + field.getType());
    }

    private static String escaped(String value) {
        return value.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("Java must provide SHA-256", impossible);
        }
    }

    private static String requireSource(Path source) throws IOException {
        assertTrue(Files.isRegularFile(source), () -> "missing schema source: " + source.toAbsolutePath());
        return Files.readString(source);
    }

    private static String declarationBlock(String source, String fieldName) {
        int fieldAnchor = source.indexOf(" " + fieldName + " =");
        assertTrue(fieldAnchor >= 0, "missing declaration for " + fieldName);
        int start = source.lastIndexOf("public static final", fieldAnchor);
        assertTrue(start >= 0, "missing public static final anchor for " + fieldName);

        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int index = fieldAnchor; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (inLineComment) {
                if (current == '\n') {
                    inLineComment = false;
                }
            } else if (inBlockComment) {
                if (current == '*' && next == '/') {
                    index++;
                    inBlockComment = false;
                }
            } else if (inString) {
                if (current == '\\') {
                    index++;
                } else if (current == '"') {
                    inString = false;
                }
            } else if (inChar) {
                if (current == '\\') {
                    index++;
                } else if (current == '\'') {
                    inChar = false;
                }
            } else if (current == '/' && next == '/') {
                index++;
                inLineComment = true;
            } else if (current == '/' && next == '*') {
                index++;
                inBlockComment = true;
            } else if (current == '"') {
                inString = true;
            } else if (current == '\'') {
                inChar = true;
            } else if (current == ';') {
                return source.substring(start, index + 1);
            }
        }
        throw new AssertionError("unterminated declaration for " + fieldName);
    }

    private static String normalizeDeclaration(String declaration) {
        return declaration.replaceAll("\\s+", "");
    }
}
