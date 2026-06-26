package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZombieInfectionRulesTest {
    @Test
    void infectionChanceUsesCapturedDifficultyValues() {
        assertTrue(ZombieInfectionRules.shouldInfect(GameDifficulty.EASY, 0.24));
        assertFalse(ZombieInfectionRules.shouldInfect(GameDifficulty.EASY, 0.25));
        assertTrue(ZombieInfectionRules.shouldInfect(GameDifficulty.NORMAL, 0.49));
        assertFalse(ZombieInfectionRules.shouldInfect(GameDifficulty.NORMAL, 0.50));
        assertTrue(ZombieInfectionRules.shouldInfect(GameDifficulty.HARD, 0.99));
        assertFalse(ZombieInfectionRules.shouldInfect(GameDifficulty.PEACEFUL, 0.0));
    }
}
