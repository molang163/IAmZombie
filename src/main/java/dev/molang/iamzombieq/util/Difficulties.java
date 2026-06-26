package dev.molang.iamzombieq.util;

import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;
import net.minecraft.world.Difficulty;

/**
 * Maps the vanilla {@link Difficulty} enum to the mod's {@link GameDifficulty}. Centralizes the
 * conversion that was previously duplicated as an identical private {@code switch} in several event
 * handlers; the per-handler {@code gameDifficulty(...)} methods now delegate here.
 */
public final class Difficulties {
    private Difficulties() {
    }

    public static GameDifficulty toGameDifficulty(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> GameDifficulty.PEACEFUL;
            case EASY -> GameDifficulty.EASY;
            case NORMAL -> GameDifficulty.NORMAL;
            case HARD -> GameDifficulty.HARD;
        };
    }
}
