package dev.molang.iamzombieq.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.molang.iamzombieq.rules.mount.MountKind;
import dev.molang.iamzombieq.rules.mount.ZombieMountRules;
import org.junit.jupiter.api.Test;

class MountCapabilityTest {
    @Test
    void eachCapabilityMapsToItsMountKind() {
        assertEquals(MountKind.SPIDER, MountCapability.SPIDER.mountKind());
        assertEquals(MountKind.CHICKEN, MountCapability.CHICKEN.mountKind());
        assertEquals(MountKind.BIG_ZOMBIE, MountCapability.BIG_ZOMBIE.mountKind());
    }

    @Test
    void riddenSpeedUsesTheResolvedSpiderSpeedForSpiderAndRulesDefaultsForOthers() {
        // Spider takes the (config-resolved) value the caller passes; chicken/big-zombie use the rules table.
        assertEquals(0.55F, MountCapability.SPIDER.riddenSpeed(0.55F));
        assertEquals(ZombieMountRules.riddenSpeedFor(MountKind.CHICKEN), MountCapability.CHICKEN.riddenSpeed(0.55F));
        assertEquals(ZombieMountRules.riddenSpeedFor(MountKind.BIG_ZOMBIE), MountCapability.BIG_ZOMBIE.riddenSpeed(0.55F));
        // The spider arg does not leak into the non-spider capabilities.
        assertEquals(ZombieMountRules.CHICKEN_MOUNT_SPEED, MountCapability.CHICKEN.riddenSpeed(99.0F));
    }
}
