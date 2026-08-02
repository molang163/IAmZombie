package dev.molang.iamzombieq.rules.mount;

import java.util.function.Supplier;

/** Pure target-selection policy for a player-ridden big zombie. */
public final class BigZombieTargetRules {
    public static final int RIDER_COMBAT_MEMORY_TICKS = 100;

    private BigZombieTargetRules() {
    }

    public static boolean withinRiderCombatMemory(int ageTicks) {
        return ageTicks <= RIDER_COMBAT_MEMORY_TICKS;
    }

    /**
     * Chooses the rider's attack target, then the rider's attacker, and only then evaluates nearby candidates.
     * Suppliers keep the lower-priority live-world lookups lazy.
     */
    public static <T> T pickTarget(
            Supplier<? extends RiderCombatTarget<T>> riderAttackTarget,
            Supplier<? extends RiderCombatTarget<T>> riderAttacker,
            Supplier<? extends Iterable<Candidate<T>>> nearbyCandidates) {
        RiderCombatTarget<T> attackTarget = riderAttackTarget.get();
        if (canUse(attackTarget)) {
            return attackTarget.target();
        }

        RiderCombatTarget<T> attacker = riderAttacker.get();
        if (canUse(attacker)) {
            return attacker.target();
        }

        return pickBest(nearbyCandidates.get());
    }

    private static boolean canUse(RiderCombatTarget<?> candidate) {
        return candidate != null
                && candidate.target() != null
                && candidate.attackable()
                && withinRiderCombatMemory(candidate.ageTicks());
    }

    public static TargetTier classify(
            boolean villager,
            boolean ironGolem,
            boolean monster,
            boolean zombie,
            boolean riderOwnedSpider) {
        if (villager) {
            return TargetTier.VILLAGER;
        }
        if (ironGolem) {
            return TargetTier.IRON_GOLEM;
        }
        if (monster && !zombie && !riderOwnedSpider) {
            return TargetTier.OTHER_MONSTER;
        }
        return TargetTier.EXCLUDED;
    }

    /** Picks the highest target tier, then the nearest target in that tier. Equal distance keeps the first. */
    public static <T> T pickBest(Iterable<Candidate<T>> candidates) {
        Candidate<T> nearestVillager = null;
        Candidate<T> nearestGolem = null;
        Candidate<T> nearestMonster = null;

        for (Candidate<T> candidate : candidates) {
            if (candidate == null || candidate.target() == null) {
                continue;
            }
            switch (candidate.tier()) {
                case VILLAGER -> nearestVillager = nearer(nearestVillager, candidate);
                case IRON_GOLEM -> nearestGolem = nearer(nearestGolem, candidate);
                case OTHER_MONSTER -> nearestMonster = nearer(nearestMonster, candidate);
                case EXCLUDED -> {
                }
            }
        }

        Candidate<T> selected = nearestVillager != null
                ? nearestVillager
                : nearestGolem != null ? nearestGolem : nearestMonster;
        return selected == null ? null : selected.target();
    }

    private static <T> Candidate<T> nearer(Candidate<T> current, Candidate<T> candidate) {
        double currentDistance = current == null ? Double.MAX_VALUE : current.distanceSquared();
        return candidate.distanceSquared() < currentDistance ? candidate : current;
    }

    public enum TargetTier {
        VILLAGER,
        IRON_GOLEM,
        OTHER_MONSTER,
        EXCLUDED
    }

    public record RiderCombatTarget<T>(T target, boolean attackable, int ageTicks) {
    }

    public record Candidate<T>(T target, TargetTier tier, double distanceSquared) {
    }
}
