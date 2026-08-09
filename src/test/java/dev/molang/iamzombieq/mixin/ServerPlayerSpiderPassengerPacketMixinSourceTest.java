package dev.molang.iamzombieq.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import dev.molang.iamzombieq.util.StonecutterCapabilityMatrix;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ServerPlayerSpiderPassengerPacketMixinSourceTest {
    private static final String MIXIN_PATH =
            "dev/molang/iamzombieq/mixin/ServerPlayerSpiderPassengerPacketMixin.java";
    private static final Path MIXIN_FILE = Path.of("src/main/java").resolve(MIXIN_PATH);

    @Test
    void activePassengerSendWrapperUsesTheExactRequiredNodeDescriptor() throws Exception {
        String source = activeSource();
        String compact = SourceScan.compact(SourceScan.stripComments(source));
        boolean legacy = StonecutterCapabilityMatrix.nodeId().equals("1.21.8");
        String descriptor = legacy
                ? "method=\"startRiding(Lnet/minecraft/world/entity/Entity;Z)Z\""
                : "method=\"startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z\"";

        assertTrue(compact.contains(descriptor));
        assertTrue(compact.contains(
                "target=\"Lnet/minecraft/server/network/ServerGamePacketListenerImpl;\""
                        + "+\"send(Lnet/minecraft/network/protocol/Packet;)V\""));
        String executable = SourceScan.stripComments(source);
        String wrapper = SourceScan.methodBody(
                executable, "private void iamzombieq$deferRestoredSpiderPassengers");
        String compactWrapper = SourceScan.compact(wrapper);
        assertTrue(source.contains("require = 1"));
        assertTrue(legacy
                ? compactWrapper.contains(
                        "Operation<Void>original,EntityentityToRide,booleanforce)")
                : compactWrapper.contains(
                        "Operation<Void>original,EntityentityToRide,booleanforce,"
                                + "booleansendEventAndTriggers)"));
        assertTrue(legacy
                ? compactWrapper.contains(
                        "listener,packet,original,entityToRide,force);")
                : compactWrapper.contains(
                        "listener,packet,original,entityToRide,"
                                + "force&&!sendEventAndTriggers);"));
    }

    @Test
    void loadRestorationContextWrapsBothVanillaAttachmentBranches() throws Exception {
        String source = activeSource();
        String executable = SourceScan.stripComments(source);
        String active = SourceScan.compact(executable);
        String wrapper = SourceScan.methodBody(
                executable, "private boolean iamzombieq$restoreLoadedSpiderPassenger");
        String compact = SourceScan.compact(wrapper);

        assertTrue(active.contains(
                "method=\"loadAndSpawnParentVehicle("
                        + "Lnet/minecraft/world/level/storage/ValueInput;)V\""));
        assertTrue(source.contains("require = 2"));
        assertTrue(compact.contains(
                "if(!(entityToRideinstanceofSpider)){returnoriginal.call("));
        assertTrue(compact.contains("iamzombieq$beginSpiderPassengerRestoration(entityToRide)"));
        assertTrue(compact.contains("try{"));
        assertTrue(compact.contains("finally{"));
        assertTrue(compact.contains("iamzombieq$endSpiderPassengerRestoration(entityToRide)"));
        assertTrue(SourceScan.containsInOrder(
                compact,
                "if(!(entityToRideinstanceofSpider)){returnoriginal.call(",
                "iamzombieq$beginSpiderPassengerRestoration(entityToRide)",
                "try{",
                "original.call(",
                "finally{",
                "iamzombieq$endSpiderPassengerRestoration(entityToRide)"));
    }

    @Test
    void restorationStateIsPerPlayerNestedAndVehicleIdentityBound() throws Exception {
        String source = Files.readString(MIXIN_FILE);
        String executable = SourceScan.stripComments(source);
        String compact = SourceScan.compact(executable);

        assertTrue(compact.contains(
                "abstractclassServerPlayerSpiderPassengerPacketMixin"
                        + "implementsSpiderPassengerRestorationAccess"));
        assertEquals(1, SourceScan.countOccurrences(
                compact,
                "@UniqueprivateArrayDeque<Entity>iamzombieq$restorationVehicles;"));
        assertEquals(0, SourceScan.countOccurrences(compact, "staticArrayDeque<Entity>"));
        assertFalse(executable.contains("ThreadLocal"));
        assertFalse(executable.contains("Map<"));
        assertFalse(executable.contains("currentPlayer"));
        assertFalse(executable.contains("earlyPacket"));
        assertFalse(executable.contains("restorationEarly"));
        assertFalse(compact.contains("staticServerPlayeriamzombieq$current"));
        assertFalse(compact.contains("staticEntityiamzombieq$current"));

        String begin = SourceScan.compact(SourceScan.methodBody(
                executable, "public void iamzombieq$beginSpiderPassengerRestoration"));
        String matches = SourceScan.compact(SourceScan.methodBody(
                executable, "private boolean iamzombieq$matchesSpiderPassengerRestoration"));
        String end = SourceScan.compact(SourceScan.methodBody(
                executable, "public void iamzombieq$endSpiderPassengerRestoration"));
        assertTrue(source.contains(
                "@Override\n"
                        + "    public void iamzombieq$beginSpiderPassengerRestoration"));
        assertTrue(source.contains(
                "@Override\n"
                        + "    public void iamzombieq$endSpiderPassengerRestoration"));
        assertTrue(begin.contains("if(iamzombieq$restorationVehicles==null){"
                + "iamzombieq$restorationVehicles=newArrayDeque<>();}"));
        assertTrue(begin.contains("iamzombieq$restorationVehicles.push(entityToRide);"));
        assertTrue(matches.contains("iamzombieq$restorationVehicles!=null"
                + "&&iamzombieq$restorationVehicles.peek()==entityToRide"));
        assertTrue(end.contains("iamzombieq$restorationVehicles==null"
                + "||iamzombieq$restorationVehicles.peek()!=entityToRide"));
        assertTrue(end.contains("iamzombieq$restorationVehicles.pop();"));
        assertTrue(end.contains("if(iamzombieq$restorationVehicles.isEmpty()){"
                + "iamzombieq$restorationVehicles=null;}"));
    }

    @Test
    void suppressionRequiresCallerContextAndEveryExistingIdentityGate() throws Exception {
        String source = activeSource();
        String executable = SourceScan.stripComments(source);
        String handler = SourceScan.compact(SourceScan.methodBody(
                executable, "private void iamzombieq$handleRestoredSpiderPassengers"));
        String decision = SourceScan.compact(SourceScan.methodBody(
                executable, "private static boolean iamzombieq$shouldDefer"));

        assertTrue(handler.contains(
                "iamzombieq$matchesSpiderPassengerRestoration(entityToRide)"));
        assertTrue(handler.contains("listener==player.connection"));
        assertTrue(handler.contains("ZombiePlayerGates.isServerZombiePlayer(player)"));
        assertTrue(handler.contains("player.level()==spider.level()"));
        assertTrue(handler.contains("MountCapability.isOwnedSpider(spider,player.getUUID())"));
        assertTrue(handler.contains("spider.getFirstPassenger()==player"
                + "&&spider.getControllingPassenger()==player"));
        assertTrue(handler.contains("player.getVehicle()==spider"));
        String decisionBody = decision.substring(decision.indexOf('{'));
        assertTrue(decisionBody.startsWith("{returnrestorationContext"
                + "&&nodeNativeRestorationShape"));
        assertTrue(decision.contains("&&sameConnection&&serverZombie"
                + "&&sameLevelAndDimension&&owned&&controlledByPlayer&&currentVehicle"));
        assertTrue(decision.contains("&&targetVehicleId==packetVehicleId"));
        assertTrue(decision.contains("&&passengerEntityIds.length==1"
                + "&&passengerEntityIds[0]==playerEntityId"));
    }

    @Test
    void normalAndNonMatchingPassengerSendsAlwaysUseTheOriginalOperation() throws Exception {
        String source = activeSource();
        String handler = SourceScan.methodBody(
                SourceScan.stripComments(source),
                "private void iamzombieq$handleRestoredSpiderPassengers");
        String compact = SourceScan.compact(handler);

        assertEquals(2, SourceScan.countOccurrences(
                handler, "original.call(listener, packet)"));
        assertTrue(compact.contains("if(!shouldDefer){original.call(listener,packet);}"));
        assertFalse(handler.contains("new ClientboundSetPassengersPacket"));
        assertFalse(handler.contains("ClientboundAddEntityPacket"));
        assertFalse(handler.contains("Thread.sleep"));
        assertFalse(handler.contains("schedule("));
        assertFalse(handler.contains("while ("));
        assertFalse(handler.contains("for ("));
    }

    @Test
    void contextsAreTransientAndBothMixinsAreCommonRequiredRegistrations() throws Exception {
        String source = Files.readString(MIXIN_FILE);
        String mixins = SourceScan.resource("iamzombieq.mixins.json");

        assertFalse(source.contains("getPersistentData"));
        assertFalse(source.contains("setData("));
        assertFalse(source.contains("Attachment"));
        assertFalse(source.contains("required = 0"));
        assertFalse(source.contains("require = 0"));
        assertEquals(1, occurrences(mixins, "\"ServerPlayerSpiderPassengerPacketMixin\""));
        assertEquals(1, occurrences(mixins, "\"EntitySpiderPassengerRestorationMixin\""));
        assertFalse(SourceScan.compact(mixins).contains(
                "\"client\":[\"ServerPlayerSpiderPassengerPacketMixin\""));
        assertFalse(SourceScan.compact(mixins).contains(
                "\"client\":[\"EntitySpiderPassengerRestorationMixin\""));
    }

    private static String activeSource() throws Exception {
        Path generated = Path.of(
                "versions",
                StonecutterCapabilityMatrix.nodeId(),
                "build/generated/stonecutter/main/java")
                .resolve(MIXIN_PATH);
        return Files.readString(generated);
    }

    private static int occurrences(String source, String needle) {
        return SourceScan.countOccurrences(source, needle);
    }
}
