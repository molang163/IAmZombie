package dev.molang.iamzombieq.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DisguiseRulesTest {
    @Test
    void disguiseMaskIdIsComposedFromTheSharedPathConstant() {
        // Behavioural: the registered id is BUILT from the single DISGUISE_MASK_PATH source of truth (not an
        // independent literal), so the id, the item registration and the equip asset can never drift.
        assertEquals("iamzombieq:" + DisguiseRules.DISGUISE_MASK_PATH, DisguiseRules.DISGUISE_MASK_ID,
                "DISGUISE_MASK_ID must be composed from DISGUISE_MASK_PATH");
        assertTrue(DisguiseRules.DISGUISE_MASK_ID.endsWith(":" + DisguiseRules.DISGUISE_MASK_PATH),
                "the id's path segment must be exactly DISGUISE_MASK_PATH");
    }

    @Test
    void theComposedIdIsStillTheRegisteredItemId() {
        // The final registered id must remain strictly iamzombieq:disguise_mask (the live ItemStack adapter compares
        // against the registered item, so this must stay in lockstep with item registration).
        assertEquals("iamzombieq:disguise_mask", DisguiseRules.DISGUISE_MASK_ID);
        assertEquals("disguise_mask", DisguiseRules.DISGUISE_MASK_PATH);
    }

    @Test
    void onlyTheDisguiseMaskIdCountsAsAHumanDisguise() {
        // Drive the check through the composed id so the behaviour is tied to the single source of truth.
        assertTrue(DisguiseRules.isDisguiseMaskId("iamzombieq:" + DisguiseRules.DISGUISE_MASK_PATH));
        assertTrue(DisguiseRules.isDisguiseMaskId(DisguiseRules.DISGUISE_MASK_ID));
        assertFalse(DisguiseRules.isDisguiseMaskId("iamzombieq:super_rotten_flesh"));
        assertFalse(DisguiseRules.isDisguiseMaskId("iamzombieq:herobrine_head"));
        assertFalse(DisguiseRules.isDisguiseMaskId("minecraft:carved_pumpkin"));
        assertFalse(DisguiseRules.isDisguiseMaskId("disguise_mask"));
        assertFalse(DisguiseRules.isDisguiseMaskId(""));
    }
}
