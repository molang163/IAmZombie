package dev.molang.iamzombieq.rules.sleep;

public final class ZombieSleepRules {
    public static final float DEFAULT_BED_EXPLOSION_POWER = 5.0F;
    public static final boolean DEFAULT_BED_EXPLOSION_CAUSES_FIRE = true;

    // Single literal fact-source for respawn_set_only: shared by coffinMessageKey (entry, REST_UNTIL_NIGHT
    // fallback) and napWakeMessageKey (C4, VOTE_PASSED_NO_SKIP), so the key exists exactly once in this file.
    private static final String RESPAWN_SET_ONLY_KEY = "iamzombieq.message.coffin.respawn_set_only";

    private ZombieSleepRules() {
    }

    public static SleepAction useBed(boolean zombiePlayer) {
        return zombiePlayer ? SleepAction.BED_EXPLODES : SleepAction.PASS_THROUGH;
    }

    public static SleepAction useCoffin(boolean zombiePlayer, boolean hostileNearby, boolean daytime) {
        if (!zombiePlayer) {
            return SleepAction.DENY_NOT_ZOMBIE;
        }
        if (hostileNearby) {
            return SleepAction.DENY_HOSTILE_NEARBY;
        }
        return daytime ? SleepAction.REST_UNTIL_NIGHT : SleepAction.SET_RESPAWN;
    }

    /**
     * The overlay-message translation key {@code CoffinBlock} shows for a resolved {@link SleepAction}, extracted so
     * the routing is unit-testable without a live world. {@code napBegan} is the {@code CoffinNapManager.beginNap}
     * result and is only consulted for {@link SleepAction#REST_UNTIL_NIGHT} (lying-down on success, respawn-set-only
     * on the mount/already-sleeping fallback). Returns {@code null} for the message-less actions
     * ({@link SleepAction#PASS_THROUGH} / {@link SleepAction#BED_EXPLODES}), which send no overlay. Pure, no Minecraft
     * types -> JUnit-testable. Notably {@code SET_RESPAWN} (night / clockless dimension) maps to {@code respawn_set},
     * NOT the daytime {@code respawn_set_only} whose "but night never came" line is false there.
     */
    public static String coffinMessageKey(SleepAction action, boolean napBegan) {
        return switch (action) {
            case DENY_NOT_ZOMBIE -> "iamzombieq.message.coffin.zombie_only";
            case DENY_HOSTILE_NEARBY -> "iamzombieq.message.coffin.not_safe";
            case REST_UNTIL_NIGHT ->
                    napBegan ? "iamzombieq.message.coffin.lying_down" : RESPAWN_SET_ONLY_KEY;
            case SET_RESPAWN -> "iamzombieq.message.coffin.respawn_set";
            case PASS_THROUGH, BED_EXPLODES -> null;
        };
    }

    // Coffin "skip the day" vote math, mirroring vanilla SleepStatus.sleepersNeeded (server/players/SleepStatus.java):
    // need = max(1, ceil(eligible * pct / 100)). The percentage is clamped to [0, 100] so a misconfigured
    // players_sleeping_percentage gamerule can never divide-by-zero or demand an impossible count, and the max(1, ...)
    // floor guarantees a single resting zombie can always finish (single-player friendly). Pure Java, unit-testable.
    public static int coffinSleepersNeeded(int eligibleZombies, int percentage) {
        int pct = Math.max(0, Math.min(100, percentage));
        int eligible = Math.max(0, eligibleZombies);
        return Math.max(1, (int) Math.ceil(eligible * pct / 100.0));
    }

    public static boolean enoughCoffinSleepers(int deepSleepers, int eligibleZombies, int percentage) {
        return deepSleepers >= coffinSleepersNeeded(eligibleZombies, percentage);
    }

    // Coffin vote progress overlay throttle: the anti-deadlock timeout and the vote check itself must stay per-tick
    // (otherwise a completed vote or a timed-out wait would gain up to a second of extra latency), but the
    // "players_sleeping" progress message is purely informational and was previously resent unconditionally every
    // tick (~20/s per napper). Allow it immediately when the nap starts, then throttle to once per second (20 ticks)
    // so the action bar still stays continuously visible without repeating identical packets every tick.
    public static boolean shouldSendCoffinVoteProgress(long gameTime, long napStartTick) {
        return (gameTime - napStartTick) % 20L == 0L;
    }

    /**
     * C4: the reason {@code CoffinNapManager} is ending a nap, so the overlay-message choice for the whole
     * sleep/wake lifecycle (not just the entry, see {@link #coffinMessageKey}) lives in one JUnit-testable place.
     */
    public enum NapWakeReason {
        DISTURBED,
        NOT_ENOUGH_TIMEOUT,
        VOTE_PASSED,
        VOTE_PASSED_NO_SKIP
    }

    /** The overlay-message translation key for a nap ending for {@code reason}. Pure, no Minecraft types. */
    public static String napWakeMessageKey(NapWakeReason reason) {
        return switch (reason) {
            case DISTURBED -> "iamzombieq.message.coffin.disturbed";
            case NOT_ENOUGH_TIMEOUT -> "iamzombieq.message.coffin.not_enough";
            case VOTE_PASSED -> "iamzombieq.message.coffin.rested";
            case VOTE_PASSED_NO_SKIP -> RESPAWN_SET_ONLY_KEY;
        };
    }

    /** The overlay-message translation key for the coffin vote's periodic progress update (throttled, see C3). */
    public static String coffinVoteProgressMessageKey() {
        return "iamzombieq.message.coffin.players_sleeping";
    }

    public static BedExplosionSettings bedExplosionSettings(float power, boolean causesFire) {
        return new BedExplosionSettings(Math.max(0.0F, power), causesFire);
    }

    public record BedExplosionSettings(float power, boolean causesFire) {
    }
}
