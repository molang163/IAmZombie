package dev.molang.iamzombieq.rules;

/**
 * Pure-logic decision for whether a zombie player is "passing as human" by wearing the crude disguise mask
 * with the crude disguise mask. A disguised zombie may open villager or wandering-trader trades; the mask does not fool
 * any mob (iron golems and others still attack every form) — it ONLY gates trading. Each successful trade
 * spends one point of mask durability.
 *
 * <p>The {@link #DISGUISE_MASK_ID} constant mirrors the registered item id ({@code iamzombieq:disguise_mask})
 * so the rule can be unit-tested without bootstrapping the Minecraft item registry; the live
 * {@code ItemStack} adapter ({@code gameplay.ZombieMobTargetingAdapter.isDisguisedAsHuman}) compares against the
 * registered {@code IAmZombieItems.DISGUISE_MASK} item directly so the two can never drift.
 */
public final class DisguiseRules {
    /**
     * Single source of truth for the disguise-mask registry PATH (no namespace, no Minecraft types). The item
     * registration, its equipment-asset id, and {@link #DISGUISE_MASK_ID} are all composed from this one constant so
     * the three can never drift apart.
     */
    public static final String DISGUISE_MASK_PATH = "disguise_mask";

    /**
     * Registered id of the disguise mask head item, composed from {@link #DISGUISE_MASK_PATH}; equals
     * {@code iamzombieq:disguise_mask} and mirrors the registered {@code IAmZombieItems.DISGUISE_MASK}.
     */
    public static final String DISGUISE_MASK_ID = "iamzombieq:" + DISGUISE_MASK_PATH;

    private DisguiseRules() {
    }

    /**
     * Testable, registry-free core: is the given item id the disguise mask?
     */
    public static boolean isDisguiseMaskId(String itemId) {
        return DISGUISE_MASK_ID.equals(itemId);
    }
}
