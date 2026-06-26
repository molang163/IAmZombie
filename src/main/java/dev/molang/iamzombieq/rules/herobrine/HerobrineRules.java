package dev.molang.iamzombieq.rules.herobrine;

public final class HerobrineRules {
    public static final double CAVE_SPAWN_CHANCE = 0.0005;
    public static final double GAZE_DOT_THRESHOLD = 0.985;
    public static final double GAZE_DISTANCE = 24.0;
    public static final double SILENCE_DISTANCE = 28.0;

    private HerobrineRules() {
    }

    public static boolean shouldAttemptCaveSpawn(double roll, boolean playerInCave, boolean noHerobrineNearby) {
        return shouldAttemptCaveSpawn(roll, CAVE_SPAWN_CHANCE, playerInCave, noHerobrineNearby);
    }

    public static boolean shouldAttemptCaveSpawn(double roll, double chance, boolean playerInCave, boolean noHerobrineNearby) {
        return playerInCave && noHerobrineNearby && roll < Math.max(0.0, chance);
    }

    public static boolean isGazingAtHerobrine(double lookDot, boolean hasLineOfSight, double distance) {
        return hasLineOfSight && distance <= GAZE_DISTANCE && lookDot >= GAZE_DOT_THRESHOLD;
    }

    public static boolean hasCollisionBox() {
        return false;
    }

    public static boolean canInteract() {
        return false;
    }

    public static boolean canRideMinecarts() {
        return false;
    }

    public static boolean canSurvivalObtainHead() {
        return false;
    }

    public static boolean canCreativeObtainHead() {
        return true;
    }
}
