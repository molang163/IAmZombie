package dev.molang.iamzombieq.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import dev.molang.iamzombieq.util.StonecutterCapabilityMatrix;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EntitySpiderPassengerRestorationMixinSourceTest {
    private static final String MIXIN_PATH =
            "dev/molang/iamzombieq/mixin/EntitySpiderPassengerRestorationMixin.java";
    private static final Path MIXIN_FILE = Path.of("src/main/java").resolve(MIXIN_PATH);
    private static final Path BRIDGE_FILE = Path.of(
            "src/main/java/dev/molang/iamzombieq/internal/mount/SpiderPassengerRestorationAccess.java");

    @Test
    void portalWrapperMatchesTheOneNodeNativeVanillaRestorationCall() throws Exception {
        String source = activeSource();
        String compact = SourceScan.compact(SourceScan.stripComments(source));
        boolean legacy = StonecutterCapabilityMatrix.nodeId().equals("1.21.8");
        String descriptor = legacy
                ? "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z"
                : "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z";

        assertTrue(compact.contains("@Mixin(Entity.class)"));
        assertTrue(compact.contains(
                "method=\"teleportCrossDimension("
                        + "Lnet/minecraft/server/level/ServerLevel;"
                        + "Lnet/minecraft/server/level/ServerLevel;"
                        + "Lnet/minecraft/world/level/portal/TeleportTransition;)"
                        + "Lnet/minecraft/world/entity/Entity;\""));
        assertTrue(compact.contains(
                "target=\"Lnet/minecraft/world/entity/Entity;" + descriptor + "\""));
        assertTrue(source.contains("require = 1"));
    }

    @Test
    void onlyServerPlayerSpiderRestorationEntersTheContextAndItAlwaysExits() throws Exception {
        String source = activeSource();
        String wrapper = SourceScan.compact(SourceScan.methodBody(
                SourceScan.stripComments(source),
                "private boolean iamzombieq$restoreTeleportedSpiderPassenger"));

        assertTrue(wrapper.contains(
                "if(!(passengerinstanceofServerPlayerplayer)"
                        + "||!(entityToRideinstanceofSpider)){returnoriginal.call("));
        assertTrue(wrapper.contains(
                "SpiderPassengerRestorationAccessrestoration="
                        + "(SpiderPassengerRestorationAccess)player"));
        assertTrue(wrapper.contains(
                "restoration.iamzombieq$beginSpiderPassengerRestoration(entityToRide)"));
        assertTrue(wrapper.contains("try{"));
        assertTrue(wrapper.contains("finally{"));
        assertTrue(wrapper.contains(
                "restoration.iamzombieq$endSpiderPassengerRestoration(entityToRide)"));
        assertTrue(SourceScan.containsInOrder(
                wrapper,
                "if(!(passengerinstanceofServerPlayerplayer)"
                        + "||!(entityToRideinstanceofSpider)){returnoriginal.call(",
                "SpiderPassengerRestorationAccessrestoration="
                        + "(SpiderPassengerRestorationAccess)player",
                "restoration.iamzombieq$beginSpiderPassengerRestoration(entityToRide)",
                "try{",
                "original.call(",
                "finally{",
                "restoration.iamzombieq$endSpiderPassengerRestoration(entityToRide)"));
    }

    @Test
    void bridgeIsStaticallyLinkedTypedAndFailClosed() throws Exception {
        String source = Files.readString(MIXIN_FILE);
        String executable = SourceScan.stripComments(source);
        String compact = SourceScan.compact(executable);

        assertFalse(executable.contains("MethodHandle"));
        assertFalse(executable.contains("MethodHandles"));
        assertFalse(executable.contains("MethodType"));
        assertFalse(executable.contains("privateLookupIn"));
        assertFalse(executable.contains("invokeExact"));
        assertFalse(executable.contains("catch ("));
        assertFalse(executable.contains("catch("));
        assertFalse(executable.contains("StackWalker"));
        assertFalse(executable.contains("getStackTrace"));
        assertFalse(executable.contains("Class.forName"));
        assertFalse(executable.contains("Thread.sleep"));
        assertFalse(executable.contains("schedule("));
        assertFalse(executable.contains("ClientboundSetPassengersPacket"));
        assertFalse(executable.contains("required = 0"));
        assertFalse(executable.contains("require = 0"));

        assertTrue(compact.contains(
                "importdev.molang.iamzombieq.internal.mount."
                        + "SpiderPassengerRestorationAccess;"));
        String bridge = SourceScan.compact(SourceScan.stripComments(
                Files.readString(BRIDGE_FILE)));
        assertTrue(bridge.contains("@ApiStatus.Internal"));
        assertTrue(bridge.contains("publicinterfaceSpiderPassengerRestorationAccess"));
        assertEquals(1, SourceScan.countOccurrences(
                bridge,
                "voidiamzombieq$beginSpiderPassengerRestoration(EntityentityToRide);"));
        assertEquals(1, SourceScan.countOccurrences(
                bridge,
                "voidiamzombieq$endSpiderPassengerRestoration(EntityentityToRide);"));
        assertFalse(bridge.contains("MethodHandle"));
        assertFalse(bridge.contains("Object"));
    }

    @Test
    void entityMixinContainsNoReflectiveBridgeCacheOrRetryState() throws Exception {
        String executable = SourceScan.stripComments(Files.readString(MIXIN_FILE));
        String compact = SourceScan.compact(executable);

        assertFalse(compact.contains("MethodHandle"));
        assertFalse(compact.contains("volatile"));
        assertFalse(compact.contains("synchronized("));
        assertFalse(compact.contains("ThreadLocal"));
        assertFalse(compact.contains("ArrayDeque"));
        assertFalse(compact.contains("Map<"));
        assertFalse(compact.contains("ClientboundSetPassengersPacket"));
        assertFalse(compact.contains("Thread.sleep"));
        assertFalse(compact.contains("schedule("));
    }

    private static String activeSource() throws Exception {
        Path generated = Path.of(
                "versions",
                StonecutterCapabilityMatrix.nodeId(),
                "build/generated/stonecutter/main/java")
                .resolve(MIXIN_PATH);
        return Files.readString(generated);
    }
}
