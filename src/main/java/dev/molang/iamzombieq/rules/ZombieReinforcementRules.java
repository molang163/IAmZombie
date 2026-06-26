package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;
import dev.molang.iamzombieq.rules.core.ZombieForm;

/**
 * Pure, Minecraft-free rules backing the official zombie-reinforcement mechanic applied to a zombie PLAYER. Mirrors
 * vanilla {@code Zombie#hurtServer} reinforcement: when a zombie player takes damage from a living attacker, nearby
 * form-matched undead are alerted (retargeted onto the attacker), and on HARD difficulty with mob-spawning enabled the
 * player may spawn matching-form reinforcements that ignore the mob cap.
 *
 * <p>This class holds ONLY plain-type math/predicates so it stays unit-testable: the alert AABB dimensions, the
 * form&rarr;entity-type-id mapping, the spawn offset / spawn-position predicate, the HARD+gamerule gate, and the
 * leader-chance / reinforcement-attribute / per-spawn-penalty numbers. All entity simulation (scans, spawns, attribute
 * application) lives in the event layer.
 */
public final class ZombieReinforcementRules {
    /** Max spawn attempts per damage event (vanilla {@code REINFORCEMENT_ATTEMPTS}). */
    public static final int REINFORCEMENT_ATTEMPTS = 50;
    /** Minimum offset magnitude for a reinforcement spawn position (vanilla {@code REINFORCEMENT_RANGE_MIN}). */
    public static final int REINFORCEMENT_RANGE_MIN = 7;
    /** Maximum offset magnitude for a reinforcement spawn position (vanilla {@code REINFORCEMENT_RANGE_MAX}). */
    public static final int REINFORCEMENT_RANGE_MAX = 40;
    /** Other players must be at least this far away for a reinforcement to spawn (vanilla 7.0). */
    public static final double MIN_PLAYER_DISTANCE = 7.0;
    /** Reinforcements only spawn where the surface light level is at most this (vanilla monster cap). */
    public static final int MAX_SPAWN_LIGHT = 9;

    /** Per-spawn decay applied to the caller's reinforcement chance (vanilla -0.05). */
    public static final double REINFORCEMENT_PENALTY = -0.05;
    /** Base reinforcement chance is a random fraction of this (vanilla {@code randomizeReinforcementsChance}: 0-0.1). */
    public static final double BASE_REINFORCEMENT_CHANCE_MAX = 0.1;
    /** Leader chance cap: a zombie is a leader with at most this probability, scaled by regional difficulty. */
    public static final double LEADER_CHANCE_PER_DIFFICULTY = 0.05;
    /** Leader reinforcement-chance bonus is in [0.5, 0.75] (vanilla {@code nextDouble()*0.25 + 0.5}). */
    public static final double LEADER_REINFORCEMENT_BONUS_MIN = 0.5;
    public static final double LEADER_REINFORCEMENT_BONUS_SPAN = 0.25;
    /** Leader max-health multiplier (ADD_MULTIPLIED_TOTAL) is in [1.0, 4.0] (vanilla {@code nextDouble()*3.0 + 1.0}). */
    public static final double LEADER_HEALTH_MULTIPLIER_MIN = 1.0;
    public static final double LEADER_HEALTH_MULTIPLIER_SPAN = 3.0;

    /** Alert AABB half-extents around the zombie player (vanilla retarget box ~111 wide / ~21 tall). */
    public static final double ALERT_BOX_INFLATE_XZ = 55.5;
    public static final double ALERT_BOX_INFLATE_Y = 10.5;

    private ZombieReinforcementRules() {
    }

    /**
     * The vanilla entity-type id of the undead matching a zombie player's form, used both to alert nearby kin and to
     * spawn reinforcements. GIANT has no spawnable vanilla counterpart and yields {@code null} (no reinforcement).
     */
    public static String reinforcementEntityId(ZombieForm form) {
        return switch (form) {
            case NORMAL -> "minecraft:zombie";
            case DROWNED -> "minecraft:drowned";
            case HUSK -> "minecraft:husk";
            case ZOMBIFIED_PIGLIN -> "minecraft:zombified_piglin";
            case GIANT -> null;
        };
    }

    /** Whether a form has a reinforcement/alert counterpart at all (everything but the giant). */
    public static boolean hasReinforcementForm(ZombieForm form) {
        return reinforcementEntityId(form) != null;
    }

    /** Reinforcements only spawn on HARD difficulty when the {@code doMobSpawning} gamerule is enabled. */
    public static boolean canSpawnReinforcements(GameDifficulty difficulty, boolean doMobSpawning) {
        return difficulty == GameDifficulty.HARD && doMobSpawning;
    }

    /**
     * Whether a reinforcement chance roll succeeds: {@code roll < chance}. Mirrors the vanilla
     * {@code random.nextFloat() < SPAWN_REINFORCEMENTS_CHANCE} gate.
     */
    public static boolean reinforcementRollSucceeds(double roll, double chance) {
        return roll < chance;
    }

    /**
     * A single spawn-position offset component (X/Y/Z), matching vanilla
     * {@code nextInt(7,40) * nextInt(-1,1)}: a magnitude in [7,40] times a sign in {-1,0,1}, yielding 0 or +-7..40.
     */
    public static int spawnOffset(int magnitude, int sign) {
        return magnitude * sign;
    }

    /**
     * Whether a candidate reinforcement spawn position is viable. Mirrors the vanilla checks: a solid top surface to
     * stand on, a dark-enough spot (light at most {@value #MAX_SPAWN_LIGHT}), no player within
     * {@value #MIN_PLAYER_DISTANCE}, and no collision/obstruction at the spawn box.
     */
    public static boolean isSpawnPositionViable(boolean solidTopSurface, int lightLevel, boolean playerWithin7, boolean collisionFree) {
        return solidTopSurface
                && lightLevel <= MAX_SPAWN_LIGHT
                && !playerWithin7
                && collisionFree;
    }

    /** The base reinforcement chance for a freshly-rolled zombie: {@code roll01 * 0.1} in [0, 0.1]. */
    public static double baseReinforcementChance(double roll01) {
        return clamp01Frac(roll01) * BASE_REINFORCEMENT_CHANCE_MAX;
    }

    /** Apply one caller penalty ({@value #REINFORCEMENT_PENALTY}) to the current reinforcement chance. */
    public static double applyReinforcementPenalty(double current) {
        return current + REINFORCEMENT_PENALTY;
    }

    /** Leader chance: {@code roll < regionalDifficulty * 0.05} (at most ~5% on the hardest regional difficulty). */
    public static boolean isLeader(double regionalDifficulty, double roll) {
        return roll < regionalDifficulty * LEADER_CHANCE_PER_DIFFICULTY;
    }

    /** Leader reinforcement-chance bonus in [0.5, 0.75] from a [0,1) roll. */
    public static double leaderReinforcementBonus(double roll01) {
        return LEADER_REINFORCEMENT_BONUS_MIN + clamp01Frac(roll01) * LEADER_REINFORCEMENT_BONUS_SPAN;
    }

    /** Leader max-health ADD_MULTIPLIED_TOTAL multiplier in [1.0, 4.0] from a [0,1) roll. */
    public static double leaderMaxHealthMultiplier(double roll01) {
        return LEADER_HEALTH_MULTIPLIER_MIN + clamp01Frac(roll01) * LEADER_HEALTH_MULTIPLIER_SPAN;
    }

    /**
     * Resulting leader max health for a {@code baseMaxHealth} (default 20) given the ADD_MULTIPLIED_TOTAL multiplier:
     * {@code base * (1 + multiplier)}. For base 20 this spans [40, 100], matching vanilla leader zombies.
     */
    public static double leaderMaxHealth(double baseMaxHealth, double multiplier) {
        return baseMaxHealth * (1.0 + multiplier);
    }

    private static double clamp01Frac(double roll01) {
        if (roll01 < 0.0) {
            return 0.0;
        }
        return Math.min(roll01, 0.9999999);
    }
}
