package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.config.ConfigKeyCatalog.Authority;
import dev.molang.iamzombieq.config.ConfigKeyCatalog.Target;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ConfigSchemaCatalogTest {
    private static final String SERVER_OWNER =
            "dev.molang.iamzombieq.IAmZombieServerConfig";
    private static final String PREFERENCES_OWNER =
            "dev.molang.iamzombieq.IAmZombiePreferencesConfig";

    @Test
    void schemaJoinsTheCanonicalLegacyMapWithoutResolvingAHolder() {
        ConfigSchemaCatalog catalog = ConfigSchemaCatalog.load();

        assertEquals("1.1.0", catalog.version());
        assertEquals(
                "99677081884d39731d0345e92543631478a5e54c28436c8c05bba31d49a655b4",
                catalog.authoritySha256());
        assertEquals(56, catalog.entries().size(), "55 sources plus the jolt fan-out");
        assertEquals(52, catalog.entries(MigrationTarget.SERVER).size());
        assertEquals(4, catalog.entries(MigrationTarget.PREFERENCES).size());

        assertEquals(
                targetKeys(SERVER_OWNER),
                catalog.entries(MigrationTarget.SERVER).stream()
                        .map(ConfigSchemaCatalog.Entry::key)
                        .toList());
        assertEquals(
                targetKeys(PREFERENCES_OWNER),
                catalog.entries(MigrationTarget.PREFERENCES).stream()
                        .map(ConfigSchemaCatalog.Entry::key)
                        .toList());

        Set<String> targetKeys = new HashSet<>();
        for (ConfigSchemaCatalog.Entry entry : catalog.entries()) {
            assertTrue(targetKeys.add(entry.target() + ":" + entry.key()));
            assertNotNull(entry.type());
            assertNotNull(entry.defaultValue());
            assertFalse(entry.comment().isBlank());
            assertEquals(entry, catalog.require(entry.target(), entry.key()));
        }
        assertFalse(targetKeys.contains("SERVER:playerSkinMode"));
        assertFalse(targetKeys.contains("PREFERENCES:firstPersonArmSkinMode"));
    }

    @Test
    void legacyDispositionAndPhysicalTargetArithmeticAreExact() {
        Map<Authority, Long> counts = ConfigKeyCatalog.entries().stream()
                .collect(Collectors.groupingBy(
                        ConfigKeyCatalog.Entry::authority, Collectors.counting()));
        assertEquals(
                Map.of(
                        Authority.SERVER, 47L,
                        Authority.CLIENT, 3L,
                        Authority.SPLIT, 1L,
                        Authority.INERT, 4L),
                counts);
        assertEquals(
                Set.of(Authority.SERVER, Authority.CLIENT, Authority.SPLIT, Authority.INERT),
                Set.copyOf(Arrays.asList(Authority.values())));
        assertEquals(
                Set.of(
                        "t1CarrionStrengthDurationTicks",
                        "t1CarrionSpeedDurationTicks",
                        "t1CarrionSaturationDurationTicks",
                        "t2ForageSaturationDurationTicks"),
                ConfigKeyCatalog.entries().stream()
                        .filter(ConfigKeyCatalog.Entry::inert)
                        .map(ConfigKeyCatalog.Entry::legacyTomlKey)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    @Test
    void exactTypesRangesListsAndMultilineCommentsComeFromTheIndependentSchema() {
        ConfigSchemaCatalog catalog = ConfigSchemaCatalog.load();

        ConfigSchemaCatalog.Entry starting =
                catalog.require(MigrationTarget.SERVER, "startingRottenFlesh");
        assertEquals(ConfigSchemaCatalog.ValueType.INTEGER, starting.type());
        assertEquals(8L, starting.defaultValue());
        assertEquals(0D, starting.minimum());
        assertEquals(64D, starting.maximum());
        assertEquals(
                "Rotten flesh given to a survival/adventure player when they first become a zombie.\n"
                        + " Default: 8\n Range: 0 ~ 64",
                starting.comment());

        ConfigSchemaCatalog.Entry foods =
                catalog.require(MigrationTarget.SERVER, "zombieFoods");
        assertEquals(ConfigSchemaCatalog.ValueType.LIST, foods.type());
        assertTrue(foods.accepts(List.of()));
        assertTrue(foods.accepts(List.of(":path", "modid:", ":")));
        assertFalse(foods.accepts(List.of("missing_namespace_separator")));
        assertFalse(foods.accepts(List.of(1)));

        ConfigSchemaCatalog.Entry joltServer =
                catalog.require(MigrationTarget.SERVER, "herobrineJoltEnabled");
        ConfigSchemaCatalog.Entry joltClient = catalog.require(
                MigrationTarget.PREFERENCES, "herobrineJoltVignetteEnabled");
        assertEquals(ConfigSchemaCatalog.ValueType.BOOLEAN, joltServer.type());
        assertEquals(ConfigSchemaCatalog.ValueType.BOOLEAN, joltClient.type());
        assertTrue(joltServer.accepts("true"));
        assertTrue(joltServer.accepts("FALSE"));
        assertFalse(joltServer.accepts("not-a-boolean"));
    }

    private static List<String> targetKeys(String owner) {
        return ConfigKeyCatalog.entries().stream()
                .flatMap(entry -> entry.targets().stream())
                .filter(target -> target.owner().equals(owner))
                .map(Target::tomlKey)
                .toList();
    }
}
