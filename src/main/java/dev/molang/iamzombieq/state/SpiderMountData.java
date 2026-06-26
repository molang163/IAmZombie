package dev.molang.iamzombieq.state;
import dev.molang.iamzombieq.rules.mount.ZombieMountRules;

import java.util.UUID;

/**
 * Per-spider mount state: the owner UUID (blank = none) and the accumulated taming progress (B1).
 *
 * <p>Backward compatibility: the original single-arg form {@code new SpiderMountData(ownerUuid)} is kept as a
 * compatibility constructor. An owned spider loaded from old saves (which had no progress field) is treated as
 * fully tamed -- the single-arg constructor sets progress to {@link ZombieMountRules#SPIDER_TAME_PROGRESS_THRESHOLD}
 * when an owner is present, and 0 when it is not. The attachment serializer/sync defaults the missing field to 0.
 */
public record SpiderMountData(String ownerUuid, int tameProgress) {
    public static final SpiderMountData DEFAULT = new SpiderMountData("", 0);

    /**
     * Compatibility constructor: keeps the original {@code new SpiderMountData(ownerUuid)} signature. A
     * pre-existing owner means the spider was tamed under the old (instant) mechanic, so its progress is
     * treated as full; no owner means untamed (progress 0).
     */
    public SpiderMountData(String ownerUuid) {
        this(ownerUuid, ownerUuid != null && !ownerUuid.isBlank()
                ? dev.molang.iamzombieq.rules.mount.ZombieMountRules.SPIDER_TAME_PROGRESS_THRESHOLD
                : 0);
    }

    public static SpiderMountData ownedBy(UUID ownerUuid) {
        return new SpiderMountData(ownerUuid.toString(),
                dev.molang.iamzombieq.rules.mount.ZombieMountRules.SPIDER_TAME_PROGRESS_THRESHOLD);
    }

    /** Returns a copy with the given accumulated taming progress (still un-owned). */
    public SpiderMountData withProgress(int progress) {
        return new SpiderMountData(ownerUuid, progress);
    }

    public boolean hasOwner() {
        return !ownerUuid.isBlank();
    }

    public boolean isOwnedBy(UUID playerUuid) {
        return ownerUuid.equals(playerUuid.toString());
    }
}
