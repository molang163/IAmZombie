package dev.molang.iamzombieq.gameplay;

import dev.molang.iamzombieq.IAmZombieMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;

/**
 * The single server-side entry that forbids Peaceful difficulty, so the mod's "is gameplay enabled?" question
 * ({@link dev.molang.iamzombieq.rules.difficulty.DifficultyGuardRules#isGameplayEnabled}) has deterministic semantics.
 *
 * <p>Every runtime difficulty change funnels through {@code MinecraftServer.setDifficulty} — the {@code /difficulty}
 * command, the client change-difficulty packet / open-to-LAN settings, and a dedicated server applying
 * {@code server.properties}. {@code MinecraftServerMixin} routes that one method's argument through {@link #sanitize},
 * which turns Peaceful into {@link #FALLBACK}. {@link #enforce} additionally corrects a world that was already saved on
 * Peaceful when the server starts (that stored value bypasses {@code setDifficulty}). Together these guarantee the live
 * difficulty is never Peaceful.
 */
public final class PeacefulGuard {
    /**
     * Peaceful is replaced with this everywhere. Mirrors
     * {@link dev.molang.iamzombieq.rules.difficulty.DifficultyGuardRules#PEACEFUL_FALLBACK} (EASY) on the vanilla enum.
     */
    public static final Difficulty FALLBACK = Difficulty.EASY;

    private PeacefulGuard() {
    }

    /** The shared "is this the forbidden difficulty?" predicate (used by the command rejection and the chokepoint). */
    public static boolean isForbidden(Difficulty difficulty) {
        return difficulty == Difficulty.PEACEFUL;
    }

    /** The single coercion: Peaceful becomes {@link #FALLBACK}; any other difficulty is returned unchanged. */
    public static Difficulty sanitize(Difficulty difficulty) {
        return isForbidden(difficulty) ? FALLBACK : difficulty;
    }

    /**
     * Startup correction: if the loaded world is on Peaceful, move it to {@link #FALLBACK}. Routed through
     * {@code MinecraftServer.setDifficulty} (so mob-spawning flags update); called from {@code ServerStartedEvent}
     * where the player list exists and no players are connected yet.
     */
    public static void enforce(MinecraftServer server) {
        if (isForbidden(server.getWorldData().getDifficulty())) {
            IAmZombieMod.LOGGER.info(
                    "World loaded on Peaceful; switching to {} ({} requires a hostile difficulty).",
                    FALLBACK.getSerializedName(), IAmZombieMod.ENGLISH_NAME);
            server.setDifficulty(FALLBACK, true);
        }
    }
}
