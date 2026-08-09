package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import dev.molang.iamzombieq.util.StonecutterCapabilityMatrix;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Locks the node-native forced-ride and restoration descriptors. */
class RideStartRidingApiBoundarySourceTest {
    private static final String MOUNT_EVENTS =
            "dev/molang/iamzombieq/gameplay/ZombieMountEvents.java";
    private static final String SERVER_PLAYER_MIXIN =
            "dev/molang/iamzombieq/mixin/ServerPlayerSpiderPassengerPacketMixin.java";
    private static final String ENTITY_MIXIN =
            "dev/molang/iamzombieq/mixin/EntitySpiderPassengerRestorationMixin.java";

    @Test
    void canonicalGameplayKeepsBothTypedForcedRideOverloads() throws Exception {
        String source = Files.readString(Path.of("src/main/java").resolve(MOUNT_EVENTS));
        String simple = SourceScan.methodBody(
                source, "private static void completeSimpleMountInteraction");
        String spider = SourceScan.methodBody(
                source, "private static void handleSpiderInteract");

        assertEquals(1, SourceScan.countOccurrences(simple, "//? if >=1.21.10 {"));
        assertEquals(1, SourceScan.countOccurrences(spider, "//? if >=1.21.10 {"));
        assertEquals(1, SourceScan.countOccurrences(
                simple, "player.startRiding(mount, true, true);"));
        assertEquals(1, SourceScan.countOccurrences(
                simple, "player.startRiding(mount, true);"));
        assertEquals(1, SourceScan.countOccurrences(
                spider, "player.startRiding(spider, true, true);"));
        assertEquals(1, SourceScan.countOccurrences(
                spider, "player.startRiding(spider, true);"));
        assertFalse(source.contains("player.startRiding(mount);"));
        assertFalse(source.contains("player.startRiding(spider);"));
    }

    @Test
    void activeGameplayUsesOnlyItsNodeNativeForcedOverload() throws Exception {
        String active = compactGenerated(MOUNT_EVENTS);
        boolean legacy = StonecutterCapabilityMatrix.nodeId().equals("1.21.8");

        assertEquals(legacy ? 1 : 0, SourceScan.countOccurrences(
                active, "player.startRiding(mount,true);"));
        assertEquals(legacy ? 1 : 0, SourceScan.countOccurrences(
                active, "player.startRiding(spider,true);"));
        assertEquals(legacy ? 0 : 1, SourceScan.countOccurrences(
                active, "player.startRiding(mount,true,true);"));
        assertEquals(legacy ? 0 : 1, SourceScan.countOccurrences(
                active, "player.startRiding(spider,true,true);"));
        assertFalse(active.contains("player.startRiding(mount);"));
        assertFalse(active.contains("player.startRiding(spider);"));
    }

    @Test
    void canonicalMixinsRetainBothRestorationDescriptorFamilies() throws Exception {
        String serverPlayer = Files.readString(
                Path.of("src/main/java").resolve(SERVER_PLAYER_MIXIN));
        String entity = Files.readString(Path.of("src/main/java").resolve(ENTITY_MIXIN));

        assertTrue(serverPlayer.contains(
                "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z"));
        assertTrue(serverPlayer.contains(
                "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z"));
        assertTrue(serverPlayer.contains(
                "loadAndSpawnParentVehicle(Lnet/minecraft/world/level/storage/ValueInput;)V"));
        assertTrue(entity.contains(
                "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z"));
        assertTrue(entity.contains(
                "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z"));
        assertTrue(entity.contains(
                "teleportCrossDimension(Lnet/minecraft/server/level/ServerLevel;"
                        + "Lnet/minecraft/server/level/ServerLevel;"
                        + "Lnet/minecraft/world/level/portal/TeleportTransition;)"
                        + "Lnet/minecraft/world/entity/Entity;"));
    }

    @Test
    void activeMixinsUseOnlyTheirNodeNativeDescriptors() throws Exception {
        String serverPlayer = compactGenerated(SERVER_PLAYER_MIXIN);
        String entity = compactGenerated(ENTITY_MIXIN);
        boolean legacy = StonecutterCapabilityMatrix.nodeId().equals("1.21.8");
        String selected = legacy
                ? "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z"
                : "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z";
        String rejected = legacy
                ? "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z"
                : "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z";

        assertEquals(2, SourceScan.countOccurrences(serverPlayer, selected));
        assertEquals(0, SourceScan.countOccurrences(serverPlayer, rejected));
        assertEquals(1, SourceScan.countOccurrences(entity, selected));
        assertEquals(0, SourceScan.countOccurrences(entity, rejected));
    }

    private static String compactGenerated(String relative) throws Exception {
        Path generated = Path.of(
                "versions",
                StonecutterCapabilityMatrix.nodeId(),
                "build/generated/stonecutter/main/java")
                .resolve(relative);
        return SourceScan.compact(SourceScan.stripComments(Files.readString(generated)));
    }
}
