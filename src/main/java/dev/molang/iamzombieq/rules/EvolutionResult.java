package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieState;

public record EvolutionResult(
        ZombieState nextState,
        boolean inPlaceRespawn,
        boolean keepInventoryAndExperience,
        DeathOutcome outcome
) {
    public static EvolutionResult evolved(ZombieState nextState, DeathOutcome outcome) {
        return new EvolutionResult(nextState, true, true, outcome);
    }

    public static EvolutionResult ordinaryReset() {
        return new EvolutionResult(ZombieState.DEFAULT, false, false, DeathOutcome.ORDINARY_DEATH_RESET);
    }
}
