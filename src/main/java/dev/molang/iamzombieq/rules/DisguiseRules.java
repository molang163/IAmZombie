package dev.molang.iamzombieq.rules;

import dev.molang.iamzombieq.IAmZombieItems;
import net.minecraft.world.item.ItemStack;

/**
 * Pure-logic decision for whether a zombie player is "passing as human" by wearing the crude disguise mask
 * (G12). A disguised zombie may open villager / wandering-trader trades (G19); the crude mask does NOT fool
 * any mob (iron golems and others still attack every form) — it ONLY gates trading. Each successful trade
 * spends one point of mask durability.
 *
 * <p>The {@link #DISGUISE_MASK_ID} constant mirrors the registered item id ({@code iamzombieq:disguise_mask})
 * so the rule can be unit-tested without bootstrapping the Minecraft item registry; the live
 * {@link #isDisguisedAsHuman(ItemStack)} adapter compares against the registered {@code IAmZombieItems.DISGUISE_MASK}
 * item directly so the two can never drift.
 */
public final class DisguiseRules {
    /** Registered id of the disguise mask head item (mirrors {@code IAmZombieItems.DISGUISE_MASK}). */
    public static final String DISGUISE_MASK_ID = "iamzombieq:disguise_mask";

    private DisguiseRules() {
    }

    /**
     * Testable, registry-free core: is the given item id the disguise mask?
     */
    public static boolean isDisguiseMaskId(String itemId) {
        return DISGUISE_MASK_ID.equals(itemId);
    }

    /**
     * True when the stack worn on the head is the crude disguise mask. An empty stack (no headgear) is never a
     * disguise.
     */
    public static boolean isDisguisedAsHuman(ItemStack head) {
        return head != null && !head.isEmpty() && head.is(IAmZombieItems.DISGUISE_MASK.get());
    }
}
