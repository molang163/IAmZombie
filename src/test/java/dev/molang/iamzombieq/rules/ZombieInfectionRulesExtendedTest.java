package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Boundary coverage for {@link ZombieInfectionRules}, complementing {@link ZombieInfectionRulesTest}.
 *
 * <p>The decision is {@code roll >= 0.0 && roll < chance} (the threshold itself does NOT infect — strict less-than), with
 * per-difficulty chances EASY=0.25, NORMAL=0.50, HARD=1.0, PEACEFUL=0.0. These are pure-double/enum calls, fully runnable
 * on the JUnit-only test classpath.
 */
class ZombieInfectionRulesExtendedTest {

    @Test
    void easyDifficultyInfectsStrictlyBelowItsQuarterThreshold() {
        assertTrue(ZombieInfectionRules.shouldInfect(GameDifficulty.EASY, 0.0), "a zero roll is below 0.25");
        assertTrue(ZombieInfectionRules.shouldInfect(GameDifficulty.EASY, 0.2499), "just below 0.25 infects");
        assertFalse(ZombieInfectionRules.shouldInfect(GameDifficulty.EASY, 0.25), "exactly 0.25 does NOT infect (strict <)");
        assertFalse(ZombieInfectionRules.shouldInfect(GameDifficulty.EASY, 0.2501), "just above 0.25 does not infect");
    }

    @Test
    void normalDifficultyInfectsStrictlyBelowItsHalfThreshold() {
        assertTrue(ZombieInfectionRules.shouldInfect(GameDifficulty.NORMAL, 0.4999), "just below 0.50 infects");
        assertFalse(ZombieInfectionRules.shouldInfect(GameDifficulty.NORMAL, 0.50), "exactly 0.50 does NOT infect");
        assertFalse(ZombieInfectionRules.shouldInfect(GameDifficulty.NORMAL, 0.5001), "just above 0.50 does not infect");
    }

    @Test
    void hardDifficultyInfectsEveryRollBelowOneButNotOneItself() {
        assertTrue(ZombieInfectionRules.shouldInfect(GameDifficulty.HARD, 0.0));
        assertTrue(ZombieInfectionRules.shouldInfect(GameDifficulty.HARD, 0.5));
        assertTrue(ZombieInfectionRules.shouldInfect(GameDifficulty.HARD, 0.9999), "just below 1.0 still infects on HARD");
        assertFalse(ZombieInfectionRules.shouldInfect(GameDifficulty.HARD, 1.0), "a maxed roll of 1.0 does NOT infect (strict <)");
    }

    @Test
    void peacefulNeverInfectsForAnyRollIncludingZero() {
        // chance is 0.0, and roll < 0.0 is impossible for a valid [0,1) roll, so PEACEFUL can never infect.
        for (double roll : new double[] {0.0, 0.0001, 0.25, 0.5, 0.9999}) {
            assertFalse(ZombieInfectionRules.shouldInfect(GameDifficulty.PEACEFUL, roll),
                    "PEACEFUL never infects (roll=" + roll + ")");
        }
    }

    @Test
    void negativeRollsAreRejectedEvenWhenChanceIsPositive() {
        // The roll >= 0.0 guard rejects a malformed negative roll regardless of difficulty.
        assertFalse(ZombieInfectionRules.shouldInfect(GameDifficulty.HARD, -0.0001));
        assertFalse(ZombieInfectionRules.shouldInfect(1.0, -1.0));
    }

    @Test
    void rawChanceOverloadHonoursTheSameStrictLessThanBoundary() {
        assertTrue(ZombieInfectionRules.shouldInfect(0.30, 0.29));
        assertFalse(ZombieInfectionRules.shouldInfect(0.30, 0.30), "roll == chance does not infect");
        assertFalse(ZombieInfectionRules.shouldInfect(0.30, 0.31));
        assertFalse(ZombieInfectionRules.shouldInfect(0.0, 0.0), "a zero chance never infects");
    }

    @Test
    void pigOrPiglinAreEligibleToInfectIntoAZombifiedPiglin() {
        assertTrue(ZombieInfectionRules.canInfectIntoZombifiedPiglin(true, false), "a pig converts to a zombified piglin");
        assertTrue(ZombieInfectionRules.canInfectIntoZombifiedPiglin(false, true), "a piglin converts to a zombified piglin");
        assertTrue(ZombieInfectionRules.canInfectIntoZombifiedPiglin(true, true), "either flag alone is sufficient");
    }

    @Test
    void aVictimThatIsNeitherPigNorPiglinIsNotZombifiedPiglinEligible() {
        assertFalse(ZombieInfectionRules.canInfectIntoZombifiedPiglin(false, false),
                "a non-pig, non-piglin victim is not eligible for zombified-piglin conversion");
    }
}
