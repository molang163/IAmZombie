package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Gameplay-layer guard for the effect ids the §3 food table hands to the events-layer effect resolver
 * ({@code ZombieFoodEvents.resolveEffect}, which parses each {@code EffectId} into a live {@code Holder<MobEffect>}
 * via {@code BuiltInRegistries.MOB_EFFECT}). At runtime an unknown id throws (the resolver has no silent fallback),
 * which would surface only when a player eats that food; this test promotes such a typo to a build-time failure.
 *
 * <p>The project's test source set is pure JUnit with NO Minecraft/NeoForge on the classpath (no test in the repo
 * imports {@code net.minecraft}), so the real registry-backed resolver cannot be executed here without a
 * {@code NoClassDefFoundError}. Instead every effect id the table stores is extracted from {@code ZombieFoodTable.java}
 * (the pure T2 table data moved out of {@code ZombieFoodRules.java}) by a source scan and cross-checked against
 * the closed set of vanilla mob-effect ids — the exact set the runtime resolver must resolve against. A mistyped id
 * (e.g. {@code "minecraft:strenght"}) fails this test immediately. The live end-to-end resolution + buff application
 * is additionally covered by the runtime food GameTests.
 */
class ZombieFoodEffectResolverTest {
    private static final Path TABLE_SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/rules/food/ZombieFoodTable.java");
    // Captures the id-string first argument of every EffectId.of("...", ...) literal (crosses newlines via \s).
    private static final Pattern EFFECT_ID_LITERAL = Pattern.compile("EffectId\\.of\\(\\s*\"([^\"]+)\"");

    // The closed set of vanilla mob-effect registry ids (net.minecraft.world.effect.MobEffects). The events-layer
    // resolver resolves the table's ids against BuiltInRegistries.MOB_EFFECT at runtime; any table id outside this set
    // would fail to resolve (throwing) in game, so it must fail the build here instead.
    private static final Set<String> VANILLA_MOB_EFFECT_IDS = Set.of(
            "minecraft:speed", "minecraft:slowness", "minecraft:haste", "minecraft:mining_fatigue",
            "minecraft:strength", "minecraft:instant_health", "minecraft:instant_damage", "minecraft:jump_boost",
            "minecraft:nausea", "minecraft:regeneration", "minecraft:resistance", "minecraft:fire_resistance",
            "minecraft:water_breathing", "minecraft:invisibility", "minecraft:blindness", "minecraft:night_vision",
            "minecraft:hunger", "minecraft:weakness", "minecraft:poison", "minecraft:wither",
            "minecraft:health_boost", "minecraft:absorption", "minecraft:saturation", "minecraft:glowing",
            "minecraft:levitation", "minecraft:luck", "minecraft:unluck", "minecraft:slow_falling",
            "minecraft:conduit_power", "minecraft:dolphins_grace", "minecraft:bad_omen", "minecraft:hero_of_the_village",
            "minecraft:darkness", "minecraft:trial_omen", "minecraft:raid_omen", "minecraft:wind_charged",
            "minecraft:weaving", "minecraft:oozing", "minecraft:infested");

    @Test
    void everyTableEffectIdIsAResolvableVanillaMobEffect() throws IOException {
        String table = Files.readString(TABLE_SOURCE);
        Matcher matcher = EFFECT_ID_LITERAL.matcher(table);
        Set<String> ids = new LinkedHashSet<>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        assertTrue(ids.size() >= 10,
                "expected the food table to name a healthy spread of effects, saw " + ids.size() + ": " + ids);
        for (String id : ids) {
            assertTrue(VANILLA_MOB_EFFECT_IDS.contains(id),
                    "food-table effect id '" + id + "' is not a known vanilla mob effect; the events-layer resolver "
                            + "(ZombieFoodEvents.resolveEffect via BuiltInRegistries.MOB_EFFECT) would throw at runtime");
        }
    }
}
