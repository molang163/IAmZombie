package dev.molang.iamzombieq.rules.sleep;

public final class ZombieSleepRules {
    public static final float DEFAULT_BED_EXPLOSION_POWER = 5.0F;
    public static final boolean DEFAULT_BED_EXPLOSION_CAUSES_FIRE = true;

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

    public static BedExplosionSettings bedExplosionSettings(float power, boolean causesFire) {
        return new BedExplosionSettings(Math.max(0.0F, power), causesFire);
    }

    public record BedExplosionSettings(float power, boolean causesFire) {
    }
}
