package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

class PeacefulGuardTest {
    private static final Difficulty[] EXPECTED_SELECTABLE = {
            Difficulty.EASY,
            Difficulty.NORMAL,
            Difficulty.HARD
    };

    @Test
    void selectableDifficultiesContainExactlyTheAllowedValuesInEnumOrder() {
        assertArrayEquals(EXPECTED_SELECTABLE, PeacefulGuard.selectableDifficulties());
    }

    @Test
    void selectableDifficultiesReturnsANewArrayEveryTime() {
        assertNotSame(PeacefulGuard.selectableDifficulties(), PeacefulGuard.selectableDifficulties());
    }

    @Test
    void mutatingOneResultCannotAffectLaterCalls() {
        Difficulty[] first = PeacefulGuard.selectableDifficulties();
        first[0] = Difficulty.PEACEFUL;
        first[1] = Difficulty.HARD;

        assertArrayEquals(EXPECTED_SELECTABLE, PeacefulGuard.selectableDifficulties());
    }

    @Test
    void everySelectableDifficultyUsesTheSharedForbiddenPredicate() {
        for (Difficulty difficulty : PeacefulGuard.selectableDifficulties()) {
            assertFalse(PeacefulGuard.isForbidden(difficulty), difficulty + " must be selectable");
        }
    }
}
