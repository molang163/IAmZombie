package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;

public final class ZombieInfectionRules {
    private ZombieInfectionRules() {
    }

    /** The per-difficulty infection chance table, moved in from {@code ZombieBalanceRules}. */
    public static double infectionChance(GameDifficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> 0.0;
            case EASY -> 0.25;
            case NORMAL -> 0.50;
            case HARD -> 1.0;
        };
    }

    public static boolean shouldInfect(GameDifficulty difficulty, double roll) {
        double chance = infectionChance(difficulty);
        return shouldInfect(chance, roll);
    }

    public static boolean shouldInfect(double chance, double roll) {
        return roll >= 0.0 && roll < chance;
    }

    /**
     * Whether a slain victim is eligible to be infected into a zombified piglin. Both a Pig and any
     * Piglin/AbstractPiglin convert to a zombified piglin (matching vanilla's pig+lightning zombification), so the
     * predicate is simply "is this a pig or a piglin". Kept in the rules layer so the eligibility is unit-testable
     * independently of the Minecraft entity classes.
     */
    public static boolean canInfectIntoZombifiedPiglin(boolean isPig, boolean isPiglin) {
        return isPig || isPiglin;
    }
}
