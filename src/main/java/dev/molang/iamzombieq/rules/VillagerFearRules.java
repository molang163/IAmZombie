package dev.molang.iamzombieq.rules;

/**
 * Bug #3: an undisguised zombie player frightens villagers (panic/flee) and wandering traders (avoid). Pure,
 * registry-free decision logic so it is L0-unit-testable. "Zombie player" mirrors the codebase convention used by
 * {@code ZombieSleepEvents}/{@code CoffinBlock} (every non-spectator player is a zombie player); "disguised" means the
 * player wears the disguise mask on the head ({@code DisguiseRules.isDisguisedAsHuman}).
 */
public final class VillagerFearRules {
    /** Blocks within which a villager / wandering trader reacts to an undisguised zombie player (matches vanilla Zombie). */
    public static final double FLEE_DISTANCE = 8.0;

    private VillagerFearRules() {
    }

    /** A villager / wandering trader flees only an undisguised zombie player. */
    public static boolean shouldFleeFromZombiePlayer(boolean zombiePlayer, boolean disguised) {
        return zombiePlayer && !disguised;
    }
}
