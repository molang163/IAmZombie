package dev.molang.iamzombieq.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import dev.molang.iamzombieq.rules.mount.ZombieMountRules;
import org.junit.jupiter.api.Test;

class SpiderMountDataTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void defaultIsUntamedWithNoProgress() {
        assertFalse(SpiderMountData.DEFAULT.hasOwner());
        assertEquals(0, SpiderMountData.DEFAULT.tameProgress());
        assertFalse(ZombieMountRules.spiderIsTamed(SpiderMountData.DEFAULT.tameProgress()));
        assertFalse(SpiderMountData.DEFAULT.isOwnedBy(OWNER), "unowned spider is not owned by anyone");
    }

    @Test
    void ownedBySetsOwnerAndFullProgress() {
        SpiderMountData owned = SpiderMountData.ownedBy(OWNER);
        assertTrue(owned.hasOwner());
        assertTrue(owned.isOwnedBy(OWNER));
        assertFalse(owned.isOwnedBy(OTHER));
        assertEquals(ZombieMountRules.SPIDER_TAME_PROGRESS_THRESHOLD, owned.tameProgress());
        assertTrue(ZombieMountRules.spiderIsTamed(owned.tameProgress()));
    }

    @Test
    void legacySingleArgConstructorTreatsAnExistingOwnerAsFullyTamed() {
        // B1 backward compatibility: an old save had only the "owner" key. Loading it via the single-arg
        // constructor must treat a present owner as already (instantly) tamed -> full progress.
        SpiderMountData legacyOwned = new SpiderMountData(OWNER.toString());
        assertTrue(legacyOwned.hasOwner());
        assertEquals(ZombieMountRules.SPIDER_TAME_PROGRESS_THRESHOLD, legacyOwned.tameProgress());

        SpiderMountData legacyUnowned = new SpiderMountData("");
        assertFalse(legacyUnowned.hasOwner());
        assertEquals(0, legacyUnowned.tameProgress());
    }

    @Test
    void withProgressKeepsOwnerBlankAndStoresProgress() {
        SpiderMountData partial = SpiderMountData.DEFAULT.withProgress(40);
        assertFalse(partial.hasOwner());
        assertEquals(40, partial.tameProgress());
        assertFalse(ZombieMountRules.spiderIsTamed(partial.tameProgress()));
        assertFalse(partial.isOwnedBy(OWNER), "a partially-tamed, unowned spider is not owned by anyone");
    }
}
