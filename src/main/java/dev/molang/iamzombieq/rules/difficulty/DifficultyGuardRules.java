package dev.molang.iamzombieq.rules.difficulty;

/**
 * The authoritative, registry-free answer to "is the zombie gameplay enabled at this difficulty?" — and the policy
 * that Peaceful is NOT a supported gameplay difficulty.
 *
 * <p>Only {@link GameDifficulty#PEACEFUL} disables gameplay; Easy/Normal/Hard all run it. The live server makes this
 * deterministic: every runtime difficulty change is routed through the single server-side guard
 * ({@code PeacefulGuard} + the {@code MinecraftServer.setDifficulty} mixin), and a world saved on Peaceful is
 * corrected to {@link #PEACEFUL_FALLBACK} on startup. As a result the running difficulty is never Peaceful, so
 * {@link #isGameplayEnabled} evaluated against the live difficulty is always {@code true} — the PEACEFUL branch is
 * kept only as defensive, never-reached fallback.
 */
public final class DifficultyGuardRules {
    /** Peaceful is unsupported; it is always replaced with this (the lowest supported gameplay tier). */
    public static final GameDifficulty PEACEFUL_FALLBACK = GameDifficulty.EASY;

    private DifficultyGuardRules() {
    }

    /** Whether the zombie gameplay runs at {@code difficulty}. Only PEACEFUL disables it. */
    public static boolean isGameplayEnabled(GameDifficulty difficulty) {
        return difficulty != GameDifficulty.PEACEFUL;
    }

    /** Coerce a difficulty to a playable one: PEACEFUL becomes {@link #PEACEFUL_FALLBACK}, everything else is kept. */
    public static GameDifficulty toPlayable(GameDifficulty difficulty) {
        return isGameplayEnabled(difficulty) ? difficulty : PEACEFUL_FALLBACK;
    }
}
