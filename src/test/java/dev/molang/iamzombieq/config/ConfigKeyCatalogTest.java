package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.IAmZombiePreferencesConfig;
import dev.molang.iamzombieq.IAmZombieServerConfig;
import dev.molang.iamzombieq.config.ConfigKeyCatalog.Authority;
import dev.molang.iamzombieq.config.ConfigKeyCatalog.Entry;
import dev.molang.iamzombieq.config.ConfigKeyCatalog.Target;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ConfigKeyCatalogTest {
    private static final String SERVER_OWNER = IAmZombieServerConfig.class.getName();
    private static final String PREFERENCES_OWNER = IAmZombiePreferencesConfig.class.getName();

    private static final List<LegacyKey> LEGACY_KEYS = List.of(
            key("DEBUG_LOGGING", "debugLogging"),
            key("STARTING_ROTTEN_FLESH", "startingRottenFlesh"),
            key("UNLOCK_COFFIN_RECIPES_ON_FIRST_JOIN", "unlockCoffinRecipesOnFirstJoin"),
            key("UNDEAD_IGNORE_ZOMBIE_PLAYER", "undeadIgnoreZombiePlayer"),
            key("NORMAL_ZOMBIE_INNATE_ARMOR", "normalZombieInnateArmor"),
            key("DROWNED_INNATE_ARMOR", "drownedInnateArmor"),
            key("HUSK_INNATE_ARMOR", "huskInnateArmor"),
            key("ZOMBIFIED_PIGLIN_INNATE_ARMOR", "zombifiedPiglinInnateArmor"),
            key("SUN_PROTECTION_HEADGEAR_DAMAGE", "sunProtectionHeadgearDamage"),
            key("EASY_INFECTION_CHANCE", "easyInfectionChance"),
            key("NORMAL_INFECTION_CHANCE", "normalInfectionChance"),
            key("HARD_INFECTION_CHANCE", "hardInfectionChance"),
            key("ZOMBIE_FOODS", "zombieFoods"),
            key("HUMAN_FOOD_NAUSEA_DURATION_TICKS", "humanFoodNauseaDurationTicks"),
            key("HUMAN_FOOD_HUNGER_DURATION_TICKS", "humanFoodHungerDurationTicks"),
            key("HUMAN_FOOD_HUNGER_AMPLIFIER", "humanFoodHungerAmplifier"),
            key("SPIDER_EYE_NIGHT_VISION_DURATION_TICKS", "spiderEyeNightVisionDurationTicks"),
            key("PUFFERFISH_ABSORPTION_DURATION_TICKS", "pufferfishAbsorptionDurationTicks"),
            key("PUFFERFISH_REGENERATION_DURATION_TICKS", "pufferfishRegenerationDurationTicks"),
            key("PUFFERFISH_REGENERATION_AMPLIFIER", "pufferfishRegenerationAmplifier"),
            key("POISONOUS_POTATO_POSITIVE_DURATION_TICKS", "poisonousPotatoPositiveDurationTicks"),
            key("SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS", "superRottenFleshStrengthDurationTicks"),
            key("SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER", "superRottenFleshStrengthAmplifier"),
            key("SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS", "superRottenFleshSaturationDurationTicks"),
            key("BED_EXPLOSION_POWER", "bedExplosionPower"),
            key("BED_EXPLOSION_CAUSES_FIRE", "bedExplosionCausesFire"),
            key("HEROBRINE_CAVE_CHECK_INTERVAL_TICKS", "herobrineCaveCheckIntervalTicks"),
            key("HEROBRINE_CAVE_SPAWN_CHANCE", "herobrineCaveSpawnChance"),
            key("HEROBRINE_ESCALATION_SIGHTINGS", "herobrineEscalationSightings"),
            key("HEROBRINE_LETHAL_SIGHTINGS", "herobrineLethalSightings"),
            key("HEROBRINE_MEMORY_WINDOW_TICKS", "herobrineMemoryWindowTicks"),
            key("HEROBRINE_LETHAL_COOLDOWN_TICKS", "herobrineLethalCooldownTicks"),
            key("HEROBRINE_OMEN_ENABLED", "herobrineOmenEnabled"),
            key("HEROBRINE_OMEN_DURATION_TICKS", "herobrineOmenDurationTicks"),
            key("HEROBRINE_HEARTBEAT_ENABLED", "herobrineHeartbeatEnabled"),
            key("HEROBRINE_HEARTBEAT_NEAR_DISTANCE", "herobrineHeartbeatNearDistance"),
            key("HEROBRINE_HEARTBEAT_FAR_DISTANCE", "herobrineHeartbeatFarDistance"),
            key("HEROBRINE_JOLT_ENABLED", "herobrineJoltEnabled"),
            key("SPIDER_MOUNT_SPEED", "spiderMountSpeed"),
            key("REINFORCEMENTS_ENABLED", "reinforcementsEnabled"),
            key("REINFORCEMENT_SPAWN_ATTEMPTS", "reinforcementSpawnAttempts"),
            key("T1_CARRION_STRENGTH_DURATION_TICKS", "t1CarrionStrengthDurationTicks"),
            key("T1_CARRION_SPEED_DURATION_TICKS", "t1CarrionSpeedDurationTicks"),
            key("T1_CARRION_SATURATION_DURATION_TICKS", "t1CarrionSaturationDurationTicks"),
            key("T1_CARRION_WATER_BREATHING_DURATION_TICKS", "t1CarrionWaterBreathingDurationTicks"),
            key("T2_FORAGE_SATURATION_DURATION_TICKS", "t2ForageSaturationDurationTicks"),
            key("SWEET_SLOWNESS_DURATION_TICKS", "sweetSlownessDurationTicks"),
            key("GOLDEN_APPLE_ABSORPTION_DURATION_TICKS", "goldenAppleAbsorptionDurationTicks"),
            key("GOLDEN_APPLE_HUNGER_DURATION_TICKS", "goldenAppleHungerDurationTicks"),
            key("ENCHANTED_GOLDEN_APPLE_ABSORPTION_DURATION_TICKS",
                    "enchantedGoldenAppleAbsorptionDurationTicks"),
            key("ENCHANTED_GOLDEN_APPLE_RESISTANCE_DURATION_TICKS",
                    "enchantedGoldenAppleResistanceDurationTicks"),
            key("ENCHANTED_GOLDEN_APPLE_HUNGER_DURATION_TICKS",
                    "enchantedGoldenAppleHungerDurationTicks"),
            key("CHORUS_SLOW_FALLING_DURATION_TICKS", "chorusSlowFallingDurationTicks"),
            key("CHORUS_NAUSEA_DURATION_TICKS", "chorusNauseaDurationTicks"),
            key("HONEY_NAUSEA_DURATION_TICKS", "honeyNauseaDurationTicks"));

    private static final Set<String> INERT_FIELDS = Set.of(
            "T1_CARRION_STRENGTH_DURATION_TICKS",
            "T1_CARRION_SPEED_DURATION_TICKS",
            "T1_CARRION_SATURATION_DURATION_TICKS",
            "T2_FORAGE_SATURATION_DURATION_TICKS");

    private static final Set<String> CLIENT_FIELDS = Set.of(
            "HEROBRINE_HEARTBEAT_ENABLED",
            "HEROBRINE_HEARTBEAT_NEAR_DISTANCE",
            "HEROBRINE_HEARTBEAT_FAR_DISTANCE");

    private static final Set<String> REMOTE_REQUIRED_FIELDS = Set.of(
            "ZOMBIE_FOODS",
            "SPIDER_EYE_NIGHT_VISION_DURATION_TICKS",
            "PUFFERFISH_ABSORPTION_DURATION_TICKS",
            "PUFFERFISH_REGENERATION_DURATION_TICKS",
            "PUFFERFISH_REGENERATION_AMPLIFIER",
            "SUPER_ROTTEN_FLESH_STRENGTH_DURATION_TICKS",
            "SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER",
            "SUPER_ROTTEN_FLESH_SATURATION_DURATION_TICKS",
            "SPIDER_MOUNT_SPEED",
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
    void catalogIsTheOrderedExactSetOfAllFiftyFiveLegacyKeys() {
        List<Entry> entries = ConfigKeyCatalog.entries();

        assertEquals(55, entries.size(), "the legacy catalog must have exactly 55 rows");
        assertEquals(LEGACY_KEYS.stream().map(LegacyKey::field).toList(),
                entries.stream().map(Entry::legacyField).toList(),
                "legacy fields must match the baseline in declaration order; no filtering of extras is allowed");
        assertEquals(LEGACY_KEYS.stream().map(LegacyKey::tomlKey).toList(),
                entries.stream().map(Entry::legacyTomlKey).toList(),
                "legacy TOML keys must match the baseline in declaration order");

        assertEquals(55, new HashSet<>(entries.stream().map(Entry::legacyField).toList()).size(),
                "legacy fields must be unique");
        assertEquals(55, new HashSet<>(entries.stream().map(Entry::legacyTomlKey).toList()).size(),
                "legacy TOML keys must be unique");
    }

    @Test
    void dispositionArithmeticIsExactlyFortySevenThreeOneFour() {
        List<Entry> entries = ConfigKeyCatalog.entries();
        Map<Authority, Long> counts = entries.stream().collect(Collectors.groupingBy(
                Entry::authority, Collectors.counting()));

        assertEquals(Map.of(
                        Authority.SERVER, 47L,
                        Authority.CLIENT, 3L,
                        Authority.SPLIT, 1L,
                        Authority.INERT, 4L),
                counts);
        assertEquals(Set.of(Authority.SERVER, Authority.CLIENT, Authority.SPLIT, Authority.INERT),
                Set.copyOf(Arrays.asList(Authority.values())),
                "Authority must not acquire an unreviewed fifth disposition");

        for (Entry entry : entries) {
            assertEquals(entry.authority() == Authority.SPLIT, entry.split(),
                    entry.legacyField() + " split flag must derive from the signed disposition");
            assertEquals(entry.authority() == Authority.INERT, entry.inert(),
                    entry.legacyField() + " inert flag must derive from the signed disposition");
        }
    }

    @Test
    void eachDispositionMapsToItsCanonicalOwnerFieldKeyAndLegacySource() {
        Map<String, LegacyKey> expectedByField = LEGACY_KEYS.stream()
                .collect(Collectors.toUnmodifiableMap(LegacyKey::field, Function.identity()));
        Set<String> canonicalTargets = new HashSet<>();

        for (Entry entry : ConfigKeyCatalog.entries()) {
            LegacyKey legacy = expectedByField.get(entry.legacyField());
            assertNotNull(legacy, "unexpected legacy field: " + entry.legacyField());
            assertEquals(legacy.tomlKey(), entry.legacyTomlKey());

            switch (entry.authority()) {
                case SERVER -> assertSingleTarget(entry, SERVER_OWNER, legacy.field(), legacy.tomlKey());
                case CLIENT -> assertSingleTarget(entry, PREFERENCES_OWNER, legacy.field(), legacy.tomlKey());
                case INERT -> {
                    assertTrue(INERT_FIELDS.contains(entry.legacyField()));
                    assertSingleTarget(entry, SERVER_OWNER, legacy.field(), legacy.tomlKey());
                }
                case SPLIT -> assertJoltTargets(entry);
            }

            for (Target target : entry.targets()) {
                assertEquals(entry.legacyField(), target.legacyValueSource(),
                        "every canonical target must name the exact 1.0.3 field that seeds it");
                assertTrue(canonicalTargets.add(target.owner() + "#" + target.field() + "#" + target.tomlKey()),
                        "canonical target duplicated: " + target);
            }
        }
        assertEquals(56, canonicalTargets.size(), "55 legacy rows plus the jolt fan-out must yield 56 targets");

        assertEquals(CLIENT_FIELDS, ConfigKeyCatalog.entries().stream()
                .filter(entry -> entry.authority() == Authority.CLIENT)
                .map(Entry::legacyField)
                .collect(Collectors.toUnmodifiableSet()));
        assertEquals(INERT_FIELDS, ConfigKeyCatalog.entries().stream()
                .filter(entry -> entry.authority() == Authority.INERT)
                .map(Entry::legacyField)
                .collect(Collectors.toUnmodifiableSet()));
    }

    @Test
    void remoteRequiredIsTheExactNineteenItemSemanticSet() {
        Set<String> actual = ConfigKeyCatalog.entries().stream()
                .filter(Entry::remoteRequired)
                .map(Entry::legacyField)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(19, actual.size());
        assertEquals(REMOTE_REQUIRED_FIELDS, actual);
        assertTrue(actual.contains("ZOMBIE_FOODS"));
        assertTrue(actual.contains("SPIDER_MOUNT_SPEED"));
        assertEquals(17, actual.stream()
                .filter(field -> !field.equals("ZOMBIE_FOODS") && !field.equals("SPIDER_MOUNT_SPEED"))
                .count(), "remote19 must be zombieFoods + 17 food-effect values + spiderMountSpeed");
        assertTrue(ConfigKeyCatalog.entries().stream()
                .filter(Entry::remoteRequired)
                .allMatch(entry -> entry.authority() == Authority.SERVER),
                "all remotely required values are server-authoritative");
    }

    @Test
    void joltFanOutNamesBothTargetsAndTheSameLegacyValueSource() {
        Entry jolt = ConfigKeyCatalog.entries().stream()
                .filter(entry -> entry.legacyField().equals("HEROBRINE_JOLT_ENABLED"))
                .findFirst()
                .orElseThrow();

        assertEquals(Authority.SPLIT, jolt.authority());
        assertTrue(jolt.split());
        assertFalse(jolt.inert());
        assertFalse(jolt.remoteRequired());
        assertJoltTargets(jolt);
        assertTrue(jolt.targets().stream()
                .allMatch(target -> target.legacyValueSource().equals("HEROBRINE_JOLT_ENABLED")),
                "both jolt halves must be seeded from the one legacy boolean");
    }

    private static void assertSingleTarget(Entry entry, String owner, String field, String tomlKey) {
        assertEquals(1, entry.targets().size(), entry.legacyField() + " must have exactly one canonical target");
        assertTarget(entry.targets().getFirst(), owner, field, tomlKey, entry.legacyField());
    }

    private static void assertJoltTargets(Entry entry) {
        assertEquals("HEROBRINE_JOLT_ENABLED", entry.legacyField());
        assertEquals("herobrineJoltEnabled", entry.legacyTomlKey());
        assertEquals(2, entry.targets().size());
        assertTarget(entry.targets().get(0), SERVER_OWNER, "HEROBRINE_JOLT_ENABLED",
                "herobrineJoltEnabled", "HEROBRINE_JOLT_ENABLED");
        assertTarget(entry.targets().get(1), PREFERENCES_OWNER, "HEROBRINE_JOLT_VIGNETTE_ENABLED",
                "herobrineJoltVignetteEnabled", "HEROBRINE_JOLT_ENABLED");
    }

    private static void assertTarget(
            Target target, String owner, String field, String tomlKey, String legacyValueSource) {
        assertEquals(owner, target.owner());
        assertEquals(field, target.field());
        assertEquals(tomlKey, target.tomlKey());
        assertEquals(legacyValueSource, target.legacyValueSource());
    }

    private static LegacyKey key(String field, String tomlKey) {
        return new LegacyKey(field, tomlKey);
    }

    private record LegacyKey(String field, String tomlKey) {
    }
}
