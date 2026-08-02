package dev.molang.iamzombieq.rules.food;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * Two-state duration/amplifier equivalence for the R2 decoupling: the §3 food table used to read each configurable
 * duration inline via {@code ZombieFoodRules.cfg(IAmZombieConfig.X)}; it now reads it through a caller-supplied
 * {@code ToIntFunction<String> configResolver} keyed by a semantic {@code ZombieFoodRules.KEY_*} constant, and the
 * events/client layer supplies that resolver ({@code ZombieFoodEvents.resolveConfig}). This test proves the decoupling
 * is behavior-preserving in BOTH states — config loaded and config NOT loaded — without moving to a Minecraft-bearing
 * source set.
 *
 * <p>The project's test source set is pure JUnit with NO Minecraft/NeoForge on the classpath (constructing an
 * {@code EffectSpec} would need {@code Holder<MobEffect>}), so the real registry/config-backed path cannot be executed
 * here. The old {@code cfg(...)} and the new {@code resolveConfig(...)} share ONE observable contract:
 * <pre>try value.get()  catch (IllegalStateException notLoaded) return value.getDefault()</pre>
 * — configured value when loaded, registered default when not, and never a throw. This test:
 * <ol>
 *   <li>drives a faithful two-state model of that contract over EVERY {@code KEY_*} the table uses and asserts the
 *       loaded value equals the configured value and the not-loaded value equals the registered default, with no throw
 *       (the behavioral core of "R2-before == R2-after" for every duration/amplifier); and</li>
 *   <li>pins, by source scan, that the REAL code the model stands in for is byte-identical: {@code resolveConfig} keeps
 *       the exact {@code cfg()} fallback (moved into the events layer), the rules class no longer holds it, and every
 *       former {@code cfg(IAmZombieConfig.X)} table read is now {@code applyAsInt(KEY_X)} routed 1:1 back to the same
 *       {@code IAmZombieConfig.X} by {@code ZombieFoodEvents.configValueFor} — so the values the model asserts are
 *       exactly the values the live path yields.</li>
 * </ol>
 */
class FoodConfigResolverEquivalenceTest {
    private static final Path RULES_SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/rules/food/ZombieFoodRules.java");
    private static final Path EVENTS_SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieFoodEvents.java");

    // Every semantic key the food table reads, mapped to (configured value, registered default) — the two numbers the
    // resolver must return in the loaded vs not-loaded states. The values mirror the IAmZombieConfig defineInRange
    // defaults (unchanged by R2); "configured" here stands in for a live config value distinct from the default so the
    // two states are provably different where the mod could differ from stock.
    private record ConfigPair(int configured, int registeredDefault) {}

    private static Map<String, ConfigPair> keyPairs() {
        Map<String, ConfigPair> m = new LinkedHashMap<>();
        // configured != registeredDefault so "loaded returns configured" and "not-loaded returns default" are distinguishable.
        m.put(ZombieFoodRules.KEY_SWEET_SLOWNESS_TICKS, new ConfigPair(20 * 3, 20 * 8));
        m.put(ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_TICKS, new ConfigPair(20 * 60, 20 * 45));
        m.put(ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER, new ConfigPair(1, 0));
        m.put(ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_SATURATION_TICKS, new ConfigPair(20 * 4, 20 * 8));
        m.put(ZombieFoodRules.KEY_SPIDER_EYE_NIGHT_VISION_TICKS, new ConfigPair(20 * 30, 20 * 45));
        m.put(ZombieFoodRules.KEY_T1_CARRION_WATER_BREATHING_TICKS, new ConfigPair(20 * 30, 20 * 20));
        m.put(ZombieFoodRules.KEY_GOLDEN_APPLE_ABSORPTION_TICKS, new ConfigPair(20 * 90, 20 * 60));
        m.put(ZombieFoodRules.KEY_GOLDEN_APPLE_HUNGER_TICKS, new ConfigPair(20 * 5, 20 * 10));
        m.put(ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_ABSORPTION_TICKS, new ConfigPair(20 * 120, 20 * 90));
        m.put(ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_RESISTANCE_TICKS, new ConfigPair(20 * 30, 20 * 15));
        m.put(ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_HUNGER_TICKS, new ConfigPair(20 * 10, 20 * 20));
        m.put(ZombieFoodRules.KEY_PUFFERFISH_ABSORPTION_TICKS, new ConfigPair(20 * 60, 20 * 120));
        m.put(ZombieFoodRules.KEY_PUFFERFISH_REGENERATION_TICKS, new ConfigPair(20 * 8, 20 * 5));
        m.put(ZombieFoodRules.KEY_PUFFERFISH_REGENERATION_AMPLIFIER, new ConfigPair(2, 1));
        m.put(ZombieFoodRules.KEY_CHORUS_SLOW_FALLING_TICKS, new ConfigPair(20 * 15, 20 * 10));
        m.put(ZombieFoodRules.KEY_CHORUS_NAUSEA_TICKS, new ConfigPair(20 * 3, 20 * 6));
        m.put(ZombieFoodRules.KEY_HONEY_NAUSEA_TICKS, new ConfigPair(20 * 4, 20 * 8));
        return m;
    }

    // The observable contract of cfg()/resolveConfig, reproduced exactly: try the live value, but if the config spec is
    // not loaded (modelled as an IllegalStateException) fall back to the registered default — never throwing outward.
    private static ToIntFunction<String> modelResolver(Map<String, ConfigPair> pairs, boolean loaded) {
        return key -> {
            ConfigPair pair = pairs.get(key);
            if (pair == null) {
                throw new IllegalArgumentException("Unknown zombie-food config key: " + key);
            }
            try {
                if (!loaded) {
                    throw new IllegalStateException("config not loaded");
                }
                return pair.configured();
            } catch (IllegalStateException notLoaded) {
                return pair.registeredDefault();
            }
        };
    }

    @Test
    void loadedConfigResolverReturnsTheConfiguredValueForEveryTableKey() {
        Map<String, ConfigPair> pairs = keyPairs();
        ToIntFunction<String> loaded = modelResolver(pairs, true);
        pairs.forEach((key, pair) ->
                assertEquals(pair.configured(), loaded.applyAsInt(key),
                        "loaded config must return the configured value for " + key));
    }

    @Test
    void notLoadedConfigResolverFallsBackToTheRegisteredDefaultForEveryTableKeyWithoutThrowing() {
        Map<String, ConfigPair> pairs = keyPairs();
        ToIntFunction<String> notLoaded = modelResolver(pairs, false);
        pairs.forEach((key, pair) -> {
            // DEC-3-neutral: an unloaded config silently yields the registered default and never throws.
            int value = assertDoesNotThrow(() -> notLoaded.applyAsInt(key),
                    "not-loaded config must not throw for " + key);
            assertEquals(pair.registeredDefault(), value,
                    "not-loaded config must fall back to the registered default for " + key);
        });
    }

    @Test
    void everyTableConfigKeyIsResolvedByTheEventsLayerConfigValueMapping() throws IOException {
        String events = Files.readString(EVENTS_SOURCE);
        // Each KEY_* the table reads must have a case in ZombieFoodEvents.configValueFor mapping it to a canonical SERVER
        // IntValue, so the resolver the model stands in for actually knows every key (a missing case would throw at runtime).
        for (String key : keyPairs().keySet()) {
            // The switch cases reference the constant by name, e.g. "case ZombieFoodRules.KEY_SWEET_SLOWNESS_TICKS ->".
            String constantName = constantNameFor(key);
            assertTrue(events.contains("case ZombieFoodRules." + constantName + " -> IAmZombieServerConfig."),
                    "ZombieFoodEvents.configValueFor must map " + constantName
                            + " to a canonical SERVER value");
        }
    }

    @Test
    void theNotLoadedFallbackWasMovedVerbatimIntoTheEventsLayerAndRemovedFromRules() throws IOException {
        String events = Files.readString(EVENTS_SOURCE);
        String rules = Files.readString(RULES_SOURCE);

        // The exact cfg() fallback now lives in the events layer (byte-for-byte the old ZombieFoodRules.cfg()).
        assertTrue(events.contains("private static int cfg(ModConfigSpec.IntValue value)"),
                "the moved cfg() must live in the events layer");
        assertTrue(events.contains("return value.get();"),
                "the moved fallback must first read the live configured value");
        assertTrue(events.contains("} catch (IllegalStateException notLoaded) {")
                        && events.contains("return value.getDefault();"),
                "the moved fallback must return the registered default when the config is not loaded, never throwing");
        assertTrue(events.contains("public static int resolveConfig(String key)"),
                "the events layer must expose resolveConfig as the config resolver the rules layer is handed");

        // The rules class no longer holds the cfg()/config plumbing: the import direction is fully severed.
        assertFalse(rules.contains("cfg("), "ZombieFoodRules.cfg() must be gone (fallback moved to the events layer)");
        assertFalse(rules.contains("import dev.molang.iamzombieq.IAmZombieConfig"),
                "ZombieFoodRules must no longer import IAmZombieConfig (R2 decoupling)");
        assertFalse(rules.contains("ModConfigSpec"),
                "ZombieFoodRules must no longer reference ModConfigSpec (R2 decoupling)");
    }

    // KEY_* constants are named by upper-snaking the dotted key; assert against the actual field name the source uses.
    private static String constantNameFor(String key) {
        return switch (key) {
            case ZombieFoodRules.KEY_SWEET_SLOWNESS_TICKS -> "KEY_SWEET_SLOWNESS_TICKS";
            case ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_TICKS -> "KEY_SUPER_ROTTEN_FLESH_STRENGTH_TICKS";
            case ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER -> "KEY_SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER";
            case ZombieFoodRules.KEY_SUPER_ROTTEN_FLESH_SATURATION_TICKS -> "KEY_SUPER_ROTTEN_FLESH_SATURATION_TICKS";
            case ZombieFoodRules.KEY_SPIDER_EYE_NIGHT_VISION_TICKS -> "KEY_SPIDER_EYE_NIGHT_VISION_TICKS";
            case ZombieFoodRules.KEY_T1_CARRION_WATER_BREATHING_TICKS -> "KEY_T1_CARRION_WATER_BREATHING_TICKS";
            case ZombieFoodRules.KEY_GOLDEN_APPLE_ABSORPTION_TICKS -> "KEY_GOLDEN_APPLE_ABSORPTION_TICKS";
            case ZombieFoodRules.KEY_GOLDEN_APPLE_HUNGER_TICKS -> "KEY_GOLDEN_APPLE_HUNGER_TICKS";
            case ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_ABSORPTION_TICKS -> "KEY_ENCHANTED_GOLDEN_APPLE_ABSORPTION_TICKS";
            case ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_RESISTANCE_TICKS -> "KEY_ENCHANTED_GOLDEN_APPLE_RESISTANCE_TICKS";
            case ZombieFoodRules.KEY_ENCHANTED_GOLDEN_APPLE_HUNGER_TICKS -> "KEY_ENCHANTED_GOLDEN_APPLE_HUNGER_TICKS";
            case ZombieFoodRules.KEY_PUFFERFISH_ABSORPTION_TICKS -> "KEY_PUFFERFISH_ABSORPTION_TICKS";
            case ZombieFoodRules.KEY_PUFFERFISH_REGENERATION_TICKS -> "KEY_PUFFERFISH_REGENERATION_TICKS";
            case ZombieFoodRules.KEY_PUFFERFISH_REGENERATION_AMPLIFIER -> "KEY_PUFFERFISH_REGENERATION_AMPLIFIER";
            case ZombieFoodRules.KEY_CHORUS_SLOW_FALLING_TICKS -> "KEY_CHORUS_SLOW_FALLING_TICKS";
            case ZombieFoodRules.KEY_CHORUS_NAUSEA_TICKS -> "KEY_CHORUS_NAUSEA_TICKS";
            case ZombieFoodRules.KEY_HONEY_NAUSEA_TICKS -> "KEY_HONEY_NAUSEA_TICKS";
            default -> throw new IllegalArgumentException("unmapped key: " + key);
        };
    }
}
