package dev.molang.iamzombieq.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VillagerFearRulesTest {
    @Test
    void undisguisedZombiePlayerTriggersFlee() {
        assertTrue(VillagerFearRules.shouldFleeFromZombiePlayer(true, false));
    }

    @Test
    void disguisedZombiePlayerDoesNotTriggerFlee() {
        assertFalse(VillagerFearRules.shouldFleeFromZombiePlayer(true, true));
    }

    @Test
    void nonZombiePlayerDoesNotTriggerFlee() {
        assertFalse(VillagerFearRules.shouldFleeFromZombiePlayer(false, false));
        assertFalse(VillagerFearRules.shouldFleeFromZombiePlayer(false, true));
    }

    @Test
    void fleeDistanceMatchesVanillaZombie() {
        org.junit.jupiter.api.Assertions.assertEquals(8.0, VillagerFearRules.FLEE_DISTANCE);
    }
}
