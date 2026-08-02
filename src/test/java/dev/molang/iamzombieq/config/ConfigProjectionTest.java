package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigProjectionTest {
    @Test
    void legacyJoltFansOutToBothNamedTargets() throws IOException {
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        LegacyConfigParser.Parsed legacy =
                LegacyConfigParser.parse(LegacyConfigParserTest.fixtureBytes());

        Map<String, Object> server =
                project(MigrationTarget.SERVER, legacy, schema);
        Map<String, Object> preferences =
                project(MigrationTarget.PREFERENCES, legacy, schema);

        assertEquals(Boolean.FALSE, server.get("herobrineJoltEnabled"));
        assertEquals(
                Boolean.FALSE,
                preferences.get("herobrineJoltVignetteEnabled"));
    }

    @Test
    void targetLazyProjectionUsesParsedValuesForExactServerAndPreferencesTargets()
            throws IOException {
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        LegacyConfigParser.Parsed legacy =
                LegacyConfigParser.parse(LegacyConfigParserTest.fixtureBytes());
        Map<String, Object> server = project(MigrationTarget.SERVER, legacy, schema);
        Map<String, Object> preferences =
                project(MigrationTarget.PREFERENCES, legacy, schema);

        assertEquals(52, server.size());
        assertEquals(4, preferences.size());
        assertEquals(
                schema.entries(MigrationTarget.SERVER).stream()
                        .map(ConfigSchemaCatalog.Entry::key)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                server.keySet());
        assertEquals(
                Set.of(
                        "herobrineHeartbeatEnabled",
                        "herobrineHeartbeatNearDistance",
                        "herobrineHeartbeatFarDistance",
                        "herobrineJoltVignetteEnabled"),
                preferences.keySet());

        assertEquals(9L, server.get("startingRottenFlesh"));
        assertEquals(0.45D, server.get("spiderMountSpeed"));
        assertEquals(161L, server.get("t1CarrionStrengthDurationTicks"));
        assertEquals(201L, server.get("t1CarrionSpeedDurationTicks"));
        assertEquals(121L, server.get("t1CarrionSaturationDurationTicks"));
        assertEquals(41L, server.get("t2ForageSaturationDurationTicks"));
        assertEquals(Boolean.FALSE, server.get("herobrineJoltEnabled"));
        assertEquals(Boolean.FALSE, preferences.get("herobrineHeartbeatEnabled"));
        assertEquals(11L, preferences.get("herobrineHeartbeatNearDistance"));
        assertEquals(29L, preferences.get("herobrineHeartbeatFarDistance"));
        assertEquals(Boolean.FALSE, preferences.get("herobrineJoltVignetteEnabled"));
    }

    @Test
    void applicableMissingTypeRangeAndListFailuresAreTargetLazy() throws IOException {
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        String complete = LegacyConfigParserTest.fixtureText();

        assertThrows(IllegalArgumentException.class, () -> project(
                MigrationTarget.SERVER,
                parse(complete.replace("startingRottenFlesh = 9\n", "")),
                schema));
        assertThrows(IllegalArgumentException.class, () -> project(
                MigrationTarget.SERVER,
                parse(complete.replace("startingRottenFlesh = 9", "startingRottenFlesh = \"nine\"")),
                schema));
        assertThrows(IllegalArgumentException.class, () -> project(
                MigrationTarget.SERVER,
                parse(complete.replace("startingRottenFlesh = 9", "startingRottenFlesh = 65")),
                schema));

        String invalidServerList = complete.replace(
                "[\"minecraft:rotten_flesh\", \"iamzombieq:super_rotten_flesh\"]",
                "[\"missing_namespace_separator\"]");
        assertThrows(
                IllegalArgumentException.class,
                () -> project(MigrationTarget.SERVER, parse(invalidServerList), schema));
        assertEquals(
                4,
                project(MigrationTarget.PREFERENCES, parse(invalidServerList), schema).size(),
                "a non-applicable invalid SERVER value must not initialize or block preferences");

        String invalidPreference = complete.replace(
                "herobrineHeartbeatNearDistance = 11",
                "herobrineHeartbeatNearDistance = \"near\"");
        assertEquals(
                52,
                project(MigrationTarget.SERVER, parse(invalidPreference), schema).size());
        assertThrows(
                IllegalArgumentException.class,
                () -> project(MigrationTarget.PREFERENCES, parse(invalidPreference), schema));
    }

    @Test
    void quotedBooleansAndColonBoundaryFoodsFollowTheSignedHolderValidators()
            throws IOException {
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        String legacy = LegacyConfigParserTest.fixtureText()
                .replace(
                        "herobrineJoltEnabled = false",
                        "herobrineJoltEnabled = \"FALSE\"")
                .replace(
                        "[\"minecraft:rotten_flesh\", \"iamzombieq:super_rotten_flesh\"]",
                        "[\":path\", \"modid:\", \":\"]");

        Map<String, Object> server =
                project(MigrationTarget.SERVER, parse(legacy), schema);
        Map<String, Object> preferences =
                project(MigrationTarget.PREFERENCES, parse(legacy), schema);

        assertEquals(Boolean.FALSE, server.get("herobrineJoltEnabled"));
        assertEquals(
                Boolean.FALSE,
                preferences.get("herobrineJoltVignetteEnabled"));
        assertEquals(List.of(":path", "modid:", ":"), server.get("zombieFoods"));

        String canonical =
                ConfigProjectionCodec.encode(MigrationTarget.SERVER, server, schema);
        assertTrue(canonical.contains("herobrineJoltEnabled = false"));
        assertFalse(canonical.contains("herobrineJoltEnabled = \"FALSE\""));
        assertTrue(new TargetConfigValidator(schema)
                .validateEncoded(MigrationTarget.SERVER, canonical)
                .valid());
    }

    @Test
    void typedHashesAreTargetSpecificAndCanonicalOutputDropsLegacyRawExtras()
            throws Exception {
        ConfigSchemaCatalog schema = ConfigSchemaCatalog.load();
        String complete = LegacyConfigParserTest.fixtureText();
        Map<String, Object> server = project(MigrationTarget.SERVER, parse(complete), schema);
        Map<String, Object> preferences =
                project(MigrationTarget.PREFERENCES, parse(complete), schema);
        String serverSha =
                ConfigProjectionCodec.typedSha256(MigrationTarget.SERVER, server, schema);
        String preferencesSha = ConfigProjectionCodec.typedSha256(
                MigrationTarget.PREFERENCES, preferences, schema);

        assertTrue(serverSha.matches("1\\.1\\.0:server:[0-9a-f]{64}"));
        assertTrue(preferencesSha.matches("1\\.1\\.0:preferences:[0-9a-f]{64}"));
        assertNotEquals(serverSha, preferencesSha);

        String canonical =
                ConfigProjectionCodec.encode(MigrationTarget.SERVER, server, schema);
        String wholeFileSha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        assertFalse(serverSha.endsWith(wholeFileSha),
                "the typed projection digest must not be a canonical-file digest");
        assertFalse(canonical.contains("operator note"));
        assertFalse(canonical.contains("thirdPartyExtension"));
        assertFalse(canonical.contains("playerSkinMode"));
        assertFalse(canonical.contains("\r"));

        String withRawExtras = complete
                + "\n# operator note retained only in raw backup\n"
                + "thirdPartyExtension = \"raw-only\"\n";
        Map<String, Object> extraServer =
                project(MigrationTarget.SERVER, parse(withRawExtras), schema);
        assertEquals(server, extraServer);
        assertEquals(
                serverSha,
                ConfigProjectionCodec.typedSha256(
                        MigrationTarget.SERVER, extraServer, schema));

        assertHashIsolation(
                schema,
                complete.replace("startingRottenFlesh = 9", "startingRottenFlesh = 10"),
                false,
                true,
                serverSha,
                preferencesSha);
        assertHashIsolation(
                schema,
                complete.replace(
                        "herobrineHeartbeatEnabled = false",
                        "herobrineHeartbeatEnabled = true"),
                true,
                false,
                serverSha,
                preferencesSha);
        assertHashIsolation(
                schema,
                complete.replace("herobrineJoltEnabled = false", "herobrineJoltEnabled = true"),
                false,
                false,
                serverSha,
                preferencesSha);
    }

    private static void assertHashIsolation(
            ConfigSchemaCatalog schema,
            String changedLegacy,
            boolean serverSame,
            boolean preferencesSame,
            String serverSha,
            String preferencesSha) {
        LegacyConfigParser.Parsed parsed = parse(changedLegacy);
        String changedServer = ConfigProjectionCodec.typedSha256(
                MigrationTarget.SERVER, project(MigrationTarget.SERVER, parsed, schema), schema);
        String changedPreferences = ConfigProjectionCodec.typedSha256(
                MigrationTarget.PREFERENCES,
                project(MigrationTarget.PREFERENCES, parsed, schema),
                schema);
        assertEquals(serverSame, serverSha.equals(changedServer));
        assertEquals(preferencesSame, preferencesSha.equals(changedPreferences));
    }

    private static LegacyConfigParser.Parsed parse(String text) {
        return LegacyConfigParser.parse(text.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> project(
            MigrationTarget target,
            LegacyConfigParser.Parsed legacy,
            ConfigSchemaCatalog schema) {
        try {
            Method method = ConfigProjection.class.getDeclaredMethod(
                    "project",
                    MigrationTarget.class,
                    LegacyConfigParser.Parsed.class,
                    ConfigSchemaCatalog.class);
            return (Map<String, Object>) method.invoke(null, target, legacy, schema);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(
                    "missing target-lazy ConfigProjection.project(target, parsed, schema)", e);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AssertionError(e.getCause());
        }
    }
}
