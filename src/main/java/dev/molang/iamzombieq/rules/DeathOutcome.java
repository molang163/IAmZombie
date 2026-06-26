package dev.molang.iamzombieq.rules;

/**
 * The resolved outcome of a zombie player's death. Stable public API (1.x): exposed via {@code api/*}
 * (e.g. {@code IZombiePlayer.evolveFromDeath} and the evolve event DTOs); backward-compatible additions only
 * within 1.x.
 *
 * @since 1.0
 */
public enum DeathOutcome {
    EVOLVE_TO_DROWNED,
    EVOLVE_TO_HUSK,
    EVOLVE_TO_BABY,
    EVOLVE_TO_ZOMBIFIED_PIGLIN,
    ORDINARY_DEATH_RESET
}
