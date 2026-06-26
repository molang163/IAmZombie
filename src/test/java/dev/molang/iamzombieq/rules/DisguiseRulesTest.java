package dev.molang.iamzombieq.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DisguiseRulesTest {
    @Test
    void disguiseMaskIdMatchesRegisteredItemId() {
        // Mirrors the registered IAmZombieItems.DISGUISE_MASK id; the live ItemStack adapter compares against
        // the registered item directly, so this constant must stay in lockstep with item registration.
        assertEquals("iamzombieq:disguise_mask", DisguiseRules.DISGUISE_MASK_ID);
    }

    @Test
    void onlyTheDisguiseMaskIdCountsAsAHumanDisguise() {
        assertTrue(DisguiseRules.isDisguiseMaskId("iamzombieq:disguise_mask"));
        assertFalse(DisguiseRules.isDisguiseMaskId("iamzombieq:super_rotten_flesh"));
        assertFalse(DisguiseRules.isDisguiseMaskId("iamzombieq:herobrine_head"));
        assertFalse(DisguiseRules.isDisguiseMaskId("minecraft:carved_pumpkin"));
        assertFalse(DisguiseRules.isDisguiseMaskId(""));
    }
}
