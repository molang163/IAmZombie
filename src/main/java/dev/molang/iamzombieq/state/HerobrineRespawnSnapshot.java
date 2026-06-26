package dev.molang.iamzombieq.state;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * Durable, server-only mirror of the in-memory Herobrine respawn snapshot (the
 * {@code PendingRespawn} record in {@code HerobrineEvents}). Held in the
 * {@code HEROBRINE_PENDING_RESPAWN} attachment so it serializes with the dead player's NBT and
 * survives a clean server stop at the death screen — read ONLY as a fallback when the in-memory
 * map entry is gone (post-server-stop recovery). Never synced to the client.
 *
 * <p>The inventory is stored as a slot-indexed {@link List} covering the FULL container (main +
 * armor + offhand) by index, exactly mirroring {@code PendingRespawn.inventory()}, so it converts
 * to/from the existing snapshot shape with no slot remapping. The serializer writes only non-empty
 * slots (by index) and restores onto a cleared inventory, preserving the exact slot mapping
 * including empty slots.
 */
public record HerobrineRespawnSnapshot(boolean present, double x, double y, double z, float yRot,
                                       float xRot, List<ItemStack> inventory, int experienceLevel,
                                       float experienceProgress, int totalExperience) {

    /** Absent sentinel: the attachment default. Readers treat this as "no pending snapshot" → no-op. */
    public static final HerobrineRespawnSnapshot EMPTY =
            new HerobrineRespawnSnapshot(false, 0.0, 0.0, 0.0, 0.0F, 0.0F, List.of(), 0, 0.0F, 0);

    /** True when this snapshot carries a real pending respawn (i.e. is not the EMPTY sentinel). */
    public boolean isPresent() {
        return present;
    }
}
