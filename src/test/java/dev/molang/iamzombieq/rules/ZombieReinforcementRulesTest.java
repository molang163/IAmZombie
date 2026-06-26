package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;
import dev.molang.iamzombieq.rules.core.ZombieForm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZombieReinforcementRulesTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void formMapsToMatchingVanillaUndeadAndGiantHasNone() {
        assertEquals("minecraft:zombie", ZombieReinforcementRules.reinforcementEntityId(ZombieForm.NORMAL));
        assertEquals("minecraft:drowned", ZombieReinforcementRules.reinforcementEntityId(ZombieForm.DROWNED));
        assertEquals("minecraft:husk", ZombieReinforcementRules.reinforcementEntityId(ZombieForm.HUSK));
        assertEquals("minecraft:zombified_piglin", ZombieReinforcementRules.reinforcementEntityId(ZombieForm.ZOMBIFIED_PIGLIN));
        assertNull(ZombieReinforcementRules.reinforcementEntityId(ZombieForm.GIANT), "the giant has no vanilla counterpart");

        assertTrue(ZombieReinforcementRules.hasReinforcementForm(ZombieForm.NORMAL));
        assertTrue(ZombieReinforcementRules.hasReinforcementForm(ZombieForm.ZOMBIFIED_PIGLIN));
        assertFalse(ZombieReinforcementRules.hasReinforcementForm(ZombieForm.GIANT), "the giant never alerts or reinforces");
    }

    @Test
    void alertBoxDimensionsMatchTheVanillaRetargetBox() {
        assertEquals(55.5, ZombieReinforcementRules.ALERT_BOX_INFLATE_XZ, EPSILON);
        assertEquals(10.5, ZombieReinforcementRules.ALERT_BOX_INFLATE_Y, EPSILON);
        // The box is much wider than tall (zombies cluster horizontally), and within the ~67-111 block range.
        assertTrue(ZombieReinforcementRules.ALERT_BOX_INFLATE_XZ > ZombieReinforcementRules.ALERT_BOX_INFLATE_Y);
    }

    @Test
    void reinforcementsRequireHardAndMobSpawning() {
        assertTrue(ZombieReinforcementRules.canSpawnReinforcements(GameDifficulty.HARD, true));
        assertFalse(ZombieReinforcementRules.canSpawnReinforcements(GameDifficulty.HARD, false), "doMobSpawning off blocks reinforcements");
        assertFalse(ZombieReinforcementRules.canSpawnReinforcements(GameDifficulty.NORMAL, true), "only HARD spawns reinforcements");
        assertFalse(ZombieReinforcementRules.canSpawnReinforcements(GameDifficulty.EASY, true));
        assertFalse(ZombieReinforcementRules.canSpawnReinforcements(GameDifficulty.PEACEFUL, true));
    }

    @Test
    void reinforcementRollUsesStrictLessThan() {
        assertTrue(ZombieReinforcementRules.reinforcementRollSucceeds(0.04, 0.05));
        assertFalse(ZombieReinforcementRules.reinforcementRollSucceeds(0.05, 0.05), "roll == chance fails (vanilla strict <)");
        assertFalse(ZombieReinforcementRules.reinforcementRollSucceeds(0.5, 0.0), "a non-positive chance never spawns");
    }

    @Test
    void spawnOffsetMatchesVanillaMagnitudeTimesSign() {
        // magnitude in [7,40] times sign in {-1,0,1} -> 0 or +-7..40
        assertEquals(0, ZombieReinforcementRules.spawnOffset(20, 0));
        assertEquals(20, ZombieReinforcementRules.spawnOffset(20, 1));
        assertEquals(-40, ZombieReinforcementRules.spawnOffset(40, -1));
        assertEquals(7, ZombieReinforcementRules.spawnOffset(7, 1));
        assertEquals(7, ZombieReinforcementRules.REINFORCEMENT_RANGE_MIN);
        assertEquals(40, ZombieReinforcementRules.REINFORCEMENT_RANGE_MAX);
        assertEquals(50, ZombieReinforcementRules.REINFORCEMENT_ATTEMPTS);
    }

    @Test
    void spawnPositionViabilityRequiresAllConditions() {
        // (solidTop, light, playerWithin7, collisionFree)
        assertTrue(ZombieReinforcementRules.isSpawnPositionViable(true, 0, false, true), "dark solid spot clear of players spawns");
        assertTrue(ZombieReinforcementRules.isSpawnPositionViable(true, 9, false, true), "light at the cap (9) is allowed");
        assertFalse(ZombieReinforcementRules.isSpawnPositionViable(true, 10, false, true), "too bright (>9) is rejected");
        assertFalse(ZombieReinforcementRules.isSpawnPositionViable(false, 0, false, true), "no solid surface is rejected");
        assertFalse(ZombieReinforcementRules.isSpawnPositionViable(true, 0, true, true), "a nearby player blocks the spawn");
        assertFalse(ZombieReinforcementRules.isSpawnPositionViable(true, 0, false, false), "a collision blocks the spawn");
        assertEquals(9, ZombieReinforcementRules.MAX_SPAWN_LIGHT);
        assertEquals(7.0, ZombieReinforcementRules.MIN_PLAYER_DISTANCE, EPSILON);
    }

    @Test
    void baseReinforcementChanceIsBoundedZeroToTenth() {
        assertEquals(0.0, ZombieReinforcementRules.baseReinforcementChance(0.0), EPSILON);
        assertEquals(0.05, ZombieReinforcementRules.baseReinforcementChance(0.5), EPSILON);
        // Clamped at the open upper bound: a 1.0 roll stays just under 0.1.
        double max = ZombieReinforcementRules.baseReinforcementChance(1.0);
        assertTrue(max > 0.0999 && max <= 0.1, "base chance is bounded to (0, 0.1]");
        assertEquals(0.0, ZombieReinforcementRules.baseReinforcementChance(-1.0), EPSILON, "negative rolls clamp to 0");
    }

    @Test
    void penaltyDecaysChanceByFiveHundredths() {
        assertEquals(-0.05, ZombieReinforcementRules.REINFORCEMENT_PENALTY, EPSILON);
        assertEquals(0.05, ZombieReinforcementRules.applyReinforcementPenalty(0.1), EPSILON);
        assertEquals(0.0, ZombieReinforcementRules.applyReinforcementPenalty(0.05), EPSILON);
        assertEquals(-0.05, ZombieReinforcementRules.applyReinforcementPenalty(0.0), EPSILON, "penalty can push chance below 0 (no more spawns)");
    }

    @Test
    void leaderChanceIsCappedByRegionalDifficulty() {
        // At regional difficulty 1.0 the leader chance is exactly 5%.
        assertTrue(ZombieReinforcementRules.isLeader(1.0, 0.049));
        assertFalse(ZombieReinforcementRules.isLeader(1.0, 0.05), "roll == cap fails (strict <)");
        // At regional difficulty 0 nothing is ever a leader.
        assertFalse(ZombieReinforcementRules.isLeader(0.0, 0.0));
        // Lower regional difficulty -> lower leader chance.
        assertTrue(ZombieReinforcementRules.isLeader(0.5, 0.024));
        assertFalse(ZombieReinforcementRules.isLeader(0.5, 0.025));
        assertEquals(0.05, ZombieReinforcementRules.LEADER_CHANCE_PER_DIFFICULTY, EPSILON);
    }

    @Test
    void leaderBonusesMatchVanillaRanges() {
        // Reinforcement-chance bonus in [0.5, 0.75].
        assertEquals(0.5, ZombieReinforcementRules.leaderReinforcementBonus(0.0), EPSILON);
        assertEquals(0.625, ZombieReinforcementRules.leaderReinforcementBonus(0.5), EPSILON);
        double maxBonus = ZombieReinforcementRules.leaderReinforcementBonus(1.0);
        assertTrue(maxBonus > 0.7499 && maxBonus <= 0.75);

        // Max-health multiplier (ADD_MULTIPLIED_TOTAL) in [1.0, 4.0] -> 40..100 HP from a 20 base.
        assertEquals(1.0, ZombieReinforcementRules.leaderMaxHealthMultiplier(0.0), EPSILON);
        double maxMult = ZombieReinforcementRules.leaderMaxHealthMultiplier(1.0);
        assertTrue(maxMult > 3.999 && maxMult <= 4.0);
        assertEquals(40.0, ZombieReinforcementRules.leaderMaxHealth(20.0, 1.0), EPSILON, "min leader HP is 40");
        assertEquals(100.0, ZombieReinforcementRules.leaderMaxHealth(20.0, 4.0), EPSILON, "max leader HP is 100");
    }
}
