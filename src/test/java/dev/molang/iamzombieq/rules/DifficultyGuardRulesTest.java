package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;
import dev.molang.iamzombieq.rules.difficulty.DifficultyGuardRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DifficultyGuardRulesTest {

    @Test
    void onlyPeacefulDisablesGameplay() {
        assertFalse(DifficultyGuardRules.isGameplayEnabled(GameDifficulty.PEACEFUL), "peaceful disables gameplay");
        assertTrue(DifficultyGuardRules.isGameplayEnabled(GameDifficulty.EASY));
        assertTrue(DifficultyGuardRules.isGameplayEnabled(GameDifficulty.NORMAL));
        assertTrue(DifficultyGuardRules.isGameplayEnabled(GameDifficulty.HARD));
    }

    @Test
    void toPlayableReplacesOnlyPeaceful() {
        assertEquals(GameDifficulty.EASY, DifficultyGuardRules.toPlayable(GameDifficulty.PEACEFUL),
                "peaceful is coerced to the fallback");
        assertEquals(GameDifficulty.EASY, DifficultyGuardRules.toPlayable(GameDifficulty.EASY));
        assertEquals(GameDifficulty.NORMAL, DifficultyGuardRules.toPlayable(GameDifficulty.NORMAL));
        assertEquals(GameDifficulty.HARD, DifficultyGuardRules.toPlayable(GameDifficulty.HARD));
    }

    @Test
    void theFallbackIsItselfPlayable() {
        // The substitute Peaceful is replaced with must never itself be a disabled difficulty, or the guard would loop.
        assertTrue(DifficultyGuardRules.isGameplayEnabled(DifficultyGuardRules.PEACEFUL_FALLBACK));
        assertEquals(GameDifficulty.EASY, DifficultyGuardRules.PEACEFUL_FALLBACK);
    }
}
