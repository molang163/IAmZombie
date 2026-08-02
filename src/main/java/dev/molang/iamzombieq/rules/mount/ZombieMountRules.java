package dev.molang.iamzombieq.rules.mount;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;

import java.util.Map;

public final class ZombieMountRules {
    // Ridden movement-speed attribute values for the mod-driven mounts. These feed LivingEntity#getRiddenSpeed
    // (setSpeed -> getFrictionInfluencedSpeed -> travel), so they are in MOVEMENT_SPEED-attribute units, the
    // SAME units AbstractHorse#getRiddenSpeed returns (it just returns the horse's MOVEMENT_SPEED attribute).
    // Ridden speed = each mount's VANILLA base MOVEMENT_SPEED, so a mount moves at its natural pace (no inflation).
    // Per the official table: spider 0.3, chicken 0.25, zombie 0.23. (Earlier 0.40/0.45/0.40 felt too fast --
    // the chicken especially. Config can still override the spider via spiderMountSpeed.)
    public static final float DEFAULT_SPIDER_MOUNT_SPEED = 0.30F;
    public static final float CHICKEN_MOUNT_SPEED = 0.25F;
    public static final float BIG_ZOMBIE_MOUNT_SPEED = 0.23F;
    public static final double BIG_ZOMBIE_AUTO_ATTACK_RANGE = 4.0;

    private ZombieMountRules() {
    }

    /**
     * Ridden movement-speed (movement-speed-attribute units) for a mod-driven mount kind, used by the
     * controlling-passenger riding flow (LivingEntity#getRiddenSpeed). Spider/chicken/big-zombie are unified
     * here (B3); other kinds return 0 so they keep their vanilla ridden speed. Pure logic so it stays
     * unit-testable. The spider value can be overridden by config; see {@link #spiderRiddenSpeed(float)}.
     */
    public static float riddenSpeedFor(MountKind mountKind) {
        return switch (mountKind) {
            case SPIDER -> DEFAULT_SPIDER_MOUNT_SPEED;
            case CHICKEN -> CHICKEN_MOUNT_SPEED;
            case BIG_ZOMBIE -> BIG_ZOMBIE_MOUNT_SPEED;
            default -> 0.0F;
        };
    }

    /**
     * Resolved spider ridden speed: the configured override when set (the config default equals
     * {@link #DEFAULT_SPIDER_MOUNT_SPEED}), else the rules default. Centralizes the spider's speed in the
     * same place as chicken/big-zombie (B3) while preserving the existing config override. Non-positive
     * config values fall back to the rules default so a misconfiguration can never freeze the mount.
     */
    public static float spiderRiddenSpeed(float configuredSpeed) {
        return configuredSpeed > 0.0F ? configuredSpeed : DEFAULT_SPIDER_MOUNT_SPEED;
    }

    public static boolean canMount(boolean zombiePlayer, MountKind mountKind, boolean spiderTamedByPlayer) {
        return canMount(zombiePlayer, ZombieSize.ADULT, mountKind, spiderTamedByPlayer);
    }

    public static boolean canMount(boolean zombiePlayer, ZombieSize riderSize, MountKind mountKind, boolean spiderTamedByPlayer) {
        if (!zombiePlayer) {
            return mountKind != MountKind.SPIDER
                    && mountKind != MountKind.BIG_ZOMBIE
                    && mountKind != MountKind.CHICKEN
                    && mountKind != MountKind.ZOMBIE_NAUTILUS;
        }
        return switch (mountKind) {
            case NORMAL_HORSE -> false;
            case ZOMBIE_HORSE, SKELETON_HORSE, OTHER -> true;
            case BIG_ZOMBIE, CHICKEN -> riderSize == ZombieSize.BABY;
            case ZOMBIE_NAUTILUS -> true;
            // Striders are steered by vanilla ItemSteerable (saddle + warped-fungus-on-a-stick); the mod
            // does not override driveMount for them. The zombie player is allowed to mount a saddled strider.
            case STRIDER -> true;
            case SPIDER -> spiderTamedByPlayer;
        };
    }

    public static boolean bigZombieShouldAutoAttack(double targetDistanceBlocks) {
        return targetDistanceBlocks <= BIG_ZOMBIE_AUTO_ATTACK_RANGE;
    }

    public static MountKind mountKindForZombieEntityId(String entityTypeId, boolean baby) {
        if (baby) {
            return MountKind.OTHER;
        }
        return switch (entityTypeId) {
            case "minecraft:zombie", "minecraft:husk", "minecraft:drowned" -> MountKind.BIG_ZOMBIE;
            default -> MountKind.OTHER;
        };
    }

    public static float zombieHorseHealAmount(String itemId) {
        return switch (itemId) {
            case ZombieFoodRules.SUPER_ROTTEN_FLESH_ID -> 10.0F;
            case "minecraft:rotten_flesh" -> 4.0F;
            default -> 0.0F;
        };
    }

    // --- Spider taming progress (B1) ---
    // Taming is no longer instant: each feed adds progress points and the spider is tamed once progress
    // reaches the threshold. Pure logic so it is unit-testable; the event layer accumulates it on
    // SpiderMountData (progress field, default 0 = untamed). A pre-existing owned spider is already tamed.
    public static final int SPIDER_TAME_PROGRESS_THRESHOLD = 100;
    public static final int SPIDER_TAME_PROGRESS_ROTTEN_FLESH = 20;
    public static final int SPIDER_TAME_PROGRESS_SPIDER_EYE = 35;
    public static final int SPIDER_TAME_PROGRESS_SUPER_ROTTEN_FLESH = 60;

    /** One spider-food row: the heal it grants when fed to a spider and the taming progress it awards (R6). */
    public record SpiderFood(float healAmount, int tameProgress) {
    }

    // The single source of truth for the three spider-food lookups (taming eligibility / heal / progress), so the
    // per-item numbers live in one place instead of three parallel switches (R6). Same ids/values as before.
    private static final Map<String, SpiderFood> SPIDER_FOODS = Map.of(
            "minecraft:rotten_flesh", new SpiderFood(4.0F, SPIDER_TAME_PROGRESS_ROTTEN_FLESH),
            "minecraft:spider_eye", new SpiderFood(6.0F, SPIDER_TAME_PROGRESS_SPIDER_EYE),
            ZombieFoodRules.SUPER_ROTTEN_FLESH_ID, new SpiderFood(10.0F, SPIDER_TAME_PROGRESS_SUPER_ROTTEN_FLESH)
    );

    public static boolean isSpiderTamingFood(String itemId) {
        return SPIDER_FOODS.containsKey(itemId);
    }

    public static float spiderHealAmount(String itemId) {
        SpiderFood food = SPIDER_FOODS.get(itemId);
        return food == null ? 0.0F : food.healAmount();
    }

    /** Taming-progress points awarded by one feed of the given food id (0 for non-taming foods). */
    public static int spiderTameProgressFor(String itemId) {
        SpiderFood food = SPIDER_FOODS.get(itemId);
        return food == null ? 0 : food.tameProgress();
    }

    /** Accumulated progress after one feed, clamped to the threshold so it never overshoots/underflows. */
    public static int spiderTameProgressAfterFeed(int currentProgress, String itemId) {
        int next = Math.max(0, currentProgress) + spiderTameProgressFor(itemId);
        return Math.min(next, SPIDER_TAME_PROGRESS_THRESHOLD);
    }

    /** Whether the accumulated progress is enough to bind ownership (tame succeeds). */
    public static boolean spiderIsTamed(int progress) {
        return progress >= SPIDER_TAME_PROGRESS_THRESHOLD;
    }
}
