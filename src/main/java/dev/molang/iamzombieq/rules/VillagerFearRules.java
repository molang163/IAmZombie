package dev.molang.iamzombieq.rules;

/**
 * An undisguised zombie player frightens villagers (panic/flee) and wandering traders (avoid). Pure,
 * registry-free decision logic so it is L0-unit-testable. "Zombie player" mirrors the codebase convention used by
 * {@code ZombieSleepEvents}/{@code CoffinBlock} (every non-spectator player is a zombie player); "disguised" means the
 * player wears the disguise mask on the head ({@code gameplay.ZombieMobTargetingAdapter.isDisguisedAsHuman}).
 */
public final class VillagerFearRules {
    /** Blocks within which a villager / wandering trader reacts to an undisguised zombie player (matches vanilla Zombie). */
    public static final double FLEE_DISTANCE = 8.0;

    /**
     * Goal-selector priority of the wandering trader's avoid-zombie-player goal. Migrated verbatim from the
     * deleted WanderingTrader avoid-goal mixin ({@code addGoal(1, ...)}), matching the priority of the vanilla trader's own
     * anti-Zombie {@code AvoidEntityGoal}.
     */
    public static final int AVOID_ZOMBIE_PLAYER_GOAL_PRIORITY = 1;

    /** Walk-speed modifier while fleeing. Migrated verbatim from the deleted WanderingTrader mixin ({@code 0.5}). */
    public static final double FLEE_WALK_SPEED = 0.5;

    /** Sprint-speed modifier while fleeing. Migrated verbatim from the deleted WanderingTrader mixin ({@code 0.5}). */
    public static final double FLEE_SPRINT_SPEED = 0.5;

    private VillagerFearRules() {
    }

    /** A villager / wandering trader flees only an undisguised zombie player. */
    public static boolean shouldFleeFromZombiePlayer(boolean zombiePlayer, boolean disguised) {
        return zombiePlayer && !disguised;
    }
}
