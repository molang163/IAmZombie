package dev.molang.iamzombieq.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class SpiderPassengerEarlyPacketGateTest {
    private static final String MIXIN_PATH =
            "dev/molang/iamzombieq/mixin/ServerPlayerSpiderPassengerPacketMixin.java";

    @Test
    void exactRestorationShapeIsRequired() throws IOException {
        String gate = compactGate();
        String executableBody = gate.substring(gate.indexOf('{'));

        assertEquals(
                "{returnrestorationContext"
                        + "&&nodeNativeRestorationShape"
                        + "&&sameConnection"
                        + "&&serverZombie"
                        + "&&sameLevelAndDimension"
                        + "&&owned"
                        + "&&controlledByPlayer"
                        + "&&currentVehicle"
                        + "&&targetVehicleId==packetVehicleId"
                        + "&&passengerEntityIds!=null"
                        + "&&passengerEntityIds.length==1"
                        + "&&passengerEntityIds[0]==playerEntityId;}",
                executableBody,
                "only the exact restored owned-spider self association may be deferred");
    }

    @Test
    void connectionAndServerAdmissionMustMatch() throws IOException {
        String gate = compactGate();

        assertTrue(gate.contains("&&sameConnection&&serverZombie"));
    }

    @Test
    void levelOwnershipControllerAndCurrentVehicleMustAllMatch() throws IOException {
        String gate = compactGate();

        assertTrue(
                gate.contains(
                        "&&sameLevelAndDimension&&owned&&controlledByPlayer&&currentVehicle"));
    }

    @Test
    void packetMustNameTheExactVehicleAndSoleController() throws IOException {
        String gate = compactGate();

        assertTrue(gate.contains("&&targetVehicleId==packetVehicleId"));
        assertTrue(gate.contains("&&passengerEntityIds!=null"));
        assertTrue(gate.contains("&&passengerEntityIds.length==1"));
        assertTrue(gate.contains("&&passengerEntityIds[0]==playerEntityId"));
    }

    @Test
    void decisionHasNoRetryOrManualPublication() throws IOException {
        String source = SourceScan.stripComments(SourceScan.mainJava(MIXIN_PATH));
        String gate =
                SourceScan.stripComments(
                        SourceScan.methodBody(
                                source,
                                "private static boolean iamzombieq$shouldDefer"));

        assertFalse(source.contains("AtomicBoolean"));
        assertFalse(gate.contains("new "));
        assertFalse(gate.contains("while ("));
        assertFalse(gate.contains("for ("));
        assertFalse(gate.contains("Thread.sleep"));
        assertFalse(gate.contains("send("));
    }

    private static String compactGate() throws IOException {
        return SourceScan.compact(
                SourceScan.stripComments(
                        SourceScan.methodBody(
                                SourceScan.mainJava(MIXIN_PATH),
                                "private static boolean iamzombieq$shouldDefer")));
    }
}
