package dev.molang.iamzombieq.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import dev.molang.iamzombieq.rules.ZombieMobTargetingRules.MobKind;
import org.junit.jupiter.api.Test;

/**
 * Behaviour-level, registry-free coverage for {@link ZombieMobTargetingRules#classifyByEntityTypeId(String)}. This is
 * the pure entry the live {@code gameplay.ZombieMobTargetingAdapter#classify} delegates to; it must map every EXACT
 * vanilla entity-type id in the ① attacker table to the same {@link MobKind} the {@code instanceof} chain would, and
 * map everything else (unknown / near-miss / other-namespace / no-namespace / null) to {@link MobKind#IGNORED}.
 */
class ZombieMobTargetingRulesByIdTest {

    /** The committed exact id → MobKind mapping, independent of the production switch. */
    private static final Map<String, MobKind> EXPECTED = Map.ofEntries(
            Map.entry("minecraft:iron_golem", MobKind.IRON_GOLEM),
            Map.entry("minecraft:snow_golem", MobKind.SNOW_GOLEM),
            Map.entry("minecraft:zoglin", MobKind.ZOGLIN),
            Map.entry("minecraft:goat", MobKind.GOAT),
            Map.entry("minecraft:creeper", MobKind.CREEPER),
            Map.entry("minecraft:endermite", MobKind.CREEPER),
            Map.entry("minecraft:trader_llama", MobKind.TRADER_LLAMA),
            Map.entry("minecraft:axolotl", MobKind.AXOLOTL),
            Map.entry("minecraft:warden", MobKind.BOSS),
            Map.entry("minecraft:wither", MobKind.BOSS),
            Map.entry("minecraft:enderman", MobKind.PROVOKED_SELF_TARGETING),
            Map.entry("minecraft:polar_bear", MobKind.PROVOKED_SELF_TARGETING)
    );

    @Test
    void everyExactVanillaAttackerIdMapsToItsMobKind() {
        EXPECTED.forEach((id, kind) ->
                assertEquals(kind, ZombieMobTargetingRules.classifyByEntityTypeId(id),
                        "classifyByEntityTypeId(" + id + ")"));
    }

    @Test
    void creeperAndEndermiteShareTheAllFormsCreeperRow() {
        assertEquals(MobKind.CREEPER, ZombieMobTargetingRules.classifyByEntityTypeId("minecraft:creeper"));
        assertEquals(MobKind.CREEPER, ZombieMobTargetingRules.classifyByEntityTypeId("minecraft:endermite"));
    }

    @Test
    void wardenAndWitherAreBothBoss() {
        assertEquals(MobKind.BOSS, ZombieMobTargetingRules.classifyByEntityTypeId("minecraft:warden"));
        assertEquals(MobKind.BOSS, ZombieMobTargetingRules.classifyByEntityTypeId("minecraft:wither"));
    }

    @Test
    void endermanAndPolarBearAreBothProvokedSelfTargeting() {
        assertEquals(MobKind.PROVOKED_SELF_TARGETING,
                ZombieMobTargetingRules.classifyByEntityTypeId("minecraft:enderman"));
        assertEquals(MobKind.PROVOKED_SELF_TARGETING,
                ZombieMobTargetingRules.classifyByEntityTypeId("minecraft:polar_bear"));
    }

    @Test
    void plainLlamaIsIgnoredEvenThoughTraderLlamaAttacks() {
        // TraderLlama is a subclass of Llama; a plain llama must NOT inherit the trader-llama attacker row.
        assertEquals(MobKind.IGNORED, ZombieMobTargetingRules.classifyByEntityTypeId("minecraft:llama"));
        assertEquals(MobKind.TRADER_LLAMA, ZombieMobTargetingRules.classifyByEntityTypeId("minecraft:trader_llama"));
    }

    @Test
    void fellowMonstersAndPassiveAnimalsAreIgnored() {
        for (String id : List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider",
                "minecraft:cow", "minecraft:villager", "minecraft:pig", "minecraft:wither_skeleton")) {
            assertEquals(MobKind.IGNORED, ZombieMobTargetingRules.classifyByEntityTypeId(id),
                    id + " does not attack the zombie player");
        }
    }

    @Test
    void nearMissAndOtherNamespaceAndBadIdsAreIgnored() {
        for (String id : List.of(
                "minecraft:iron_golf",          // typo / near-miss
                "minecraft:iron_golem_boss",     // superstring
                "iron_golem",                    // no namespace
                "othermod:iron_golem",           // another namespace reusing the path
                "MINECRAFT:CREEPER",             // wrong case
                "minecraft:snow_golem ",         // trailing space
                "")) {                           // empty
            assertEquals(MobKind.IGNORED, ZombieMobTargetingRules.classifyByEntityTypeId(id),
                    "non-exact id is IGNORED: [" + id + "]");
        }
    }

    @Test
    void nullIdIsIgnored() {
        assertEquals(MobKind.IGNORED, ZombieMobTargetingRules.classifyByEntityTypeId(null));
    }

    @Test
    void everyMobKindExceptIgnoredIsProducedByAtLeastOneKnownId() {
        // Coverage guard complementary to everyExactVanillaAttackerIdMapsToItsMobKind: it keeps this test's EXPECTED
        // map in lockstep with the production MobKind enum, so a NEW non-IGNORED attacker kind added to the enum
        // without a mapped id here fails (the exact-id test alone would silently skip an unmapped new kind).
        for (MobKind kind : MobKind.values()) {
            if (kind == MobKind.IGNORED) {
                continue;
            }
            boolean produced = EXPECTED.values().stream().anyMatch(k -> k == kind);
            assertEquals(true, produced, kind + " must be reachable from at least one exact vanilla id");
        }
    }
}
