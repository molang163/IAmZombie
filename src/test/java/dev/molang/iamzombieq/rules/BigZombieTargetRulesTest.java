package dev.molang.iamzombieq.rules;

import dev.molang.iamzombieq.rules.mount.BigZombieTargetRules;
import dev.molang.iamzombieq.rules.mount.BigZombieTargetRules.Candidate;
import dev.molang.iamzombieq.rules.mount.BigZombieTargetRules.RiderCombatTarget;
import dev.molang.iamzombieq.rules.mount.BigZombieTargetRules.TargetTier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class BigZombieTargetRulesTest {
    @Test
    void riderCombatMemoryIncludesTickOneHundredButNotOneHundredOne() {
        assertTrue(BigZombieTargetRules.withinRiderCombatMemory(99));
        assertTrue(BigZombieTargetRules.withinRiderCombatMemory(100));
        assertFalse(BigZombieTargetRules.withinRiderCombatMemory(101));
        assertTrue(BigZombieTargetRules.withinRiderCombatMemory(-1),
                "the original timestamp comparison did not reject a future combat tick");
    }

    @Test
    void riderAttackTargetWinsBeforeAttackerAndNearbyScan() {
        String selected = BigZombieTargetRules.pickTarget(
                () -> combat("rider-target", 100),
                () -> {
                    throw new AssertionError("the lower-priority rider attacker must stay lazy");
                },
                () -> {
                    throw new AssertionError("the nearby scan must stay lazy");
                });

        assertEquals("rider-target", selected);
    }

    @Test
    void riderAttackerWinsBeforeNearbyWhenAttackTargetIsExpired() {
        String selected = BigZombieTargetRules.pickTarget(
                () -> combat("expired-rider-target", 101),
                () -> combat("rider-attacker", 99),
                () -> {
                    throw new AssertionError("the nearby scan must stay lazy");
                });

        assertEquals("rider-attacker", selected);
    }

    @Test
    void nearbyScanRunsOnlyWhenBothRiderCombatTargetsAreUnavailable() {
        AtomicBoolean nearbyScanned = new AtomicBoolean();
        String selected = BigZombieTargetRules.pickTarget(
                () -> combat(null, 0),
                () -> new RiderCombatTarget<>("unattackable", false, 0),
                () -> {
                    nearbyScanned.set(true);
                    return List.of(candidate("nearby-monster", TargetTier.OTHER_MONSTER, 2.0));
                });

        assertEquals("nearby-monster", selected);
        assertTrue(nearbyScanned.get());
    }

    @Test
    void riderDirectedTargetCanBeAFellowZombie() {
        String selected = BigZombieTargetRules.pickTarget(
                () -> combat("fellow-zombie", 0),
                () -> combat("rider-attacker", 0),
                List::of);

        assertEquals("fellow-zombie", selected,
                "direct rider combat targets must not be filtered by the nearby zombie exclusion");
    }

    @Test
    void nearbyClassificationKeepsTheThreeTiersAndExclusions() {
        assertEquals(TargetTier.VILLAGER,
                BigZombieTargetRules.classify(true, false, false, false, false));
        assertEquals(TargetTier.IRON_GOLEM,
                BigZombieTargetRules.classify(false, true, false, false, false));
        assertEquals(TargetTier.OTHER_MONSTER,
                BigZombieTargetRules.classify(false, false, true, false, false));
        assertEquals(TargetTier.EXCLUDED,
                BigZombieTargetRules.classify(false, false, false, false, false));
        assertEquals(TargetTier.EXCLUDED,
                BigZombieTargetRules.classify(false, false, true, true, false));
        assertEquals(TargetTier.EXCLUDED,
                BigZombieTargetRules.classify(false, false, true, false, true));
    }

    @Test
    void higherNearbyTierWinsEvenWhenItIsFartherAway() {
        assertEquals("far-villager", BigZombieTargetRules.pickBest(List.of(
                candidate("near-monster", TargetTier.OTHER_MONSTER, 1.0),
                candidate("mid-golem", TargetTier.IRON_GOLEM, 16.0),
                candidate("far-villager", TargetTier.VILLAGER, 64.0))));

        assertEquals("far-golem", BigZombieTargetRules.pickBest(List.of(
                candidate("near-monster", TargetTier.OTHER_MONSTER, 1.0),
                candidate("far-golem", TargetTier.IRON_GOLEM, 64.0))));
    }

    @Test
    void sameTierUsesNearestAndKeepsFirstOnEqualDistance() {
        assertEquals("nearest", BigZombieTargetRules.pickBest(List.of(
                candidate("farther", TargetTier.OTHER_MONSTER, 9.0),
                candidate("nearest", TargetTier.OTHER_MONSTER, 4.0),
                candidate("middle", TargetTier.OTHER_MONSTER, 6.0))));

        assertEquals("first", BigZombieTargetRules.pickBest(List.of(
                candidate("first", TargetTier.VILLAGER, 4.0),
                candidate("second", TargetTier.VILLAGER, 4.0))));
    }

    @Test
    void emptyOrEntirelyExcludedNearbyCandidatesSelectNothing() {
        assertNull(BigZombieTargetRules.pickBest(List.of()));
        assertNull(BigZombieTargetRules.pickBest(List.of(
                candidate("passive", TargetTier.EXCLUDED, 1.0),
                candidate("fellow-zombie", TargetTier.EXCLUDED, 0.0),
                candidate("owned-spider", TargetTier.EXCLUDED, 0.0))));
    }

    private static RiderCombatTarget<String> combat(String target, int ageTicks) {
        return new RiderCombatTarget<>(target, true, ageTicks);
    }

    private static Candidate<String> candidate(String target, TargetTier tier, double distanceSquared) {
        return new Candidate<>(target, tier, distanceSquared);
    }
}
