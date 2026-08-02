package dev.molang.iamzombieq.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class ZombiePlayerGatesSourceTest {
    private static final String GATES = "dev/molang/iamzombieq/util/ZombiePlayerGates.java";
    private static final String RIDE_HELPER = "dev/molang/iamzombieq/util/RideHelper.java";

    @Test
    void canonicalGatesPreserveCreativeSpectatorAndServerShortCircuitSemantics() throws IOException {
        String source = SourceScan.mainJava(GATES);
        String zombieGate = compactMethod(source, "public static boolean isZombiePlayer");
        assertTrue(zombieGate.contains("return!player.isSpectator();"),
                "the canonical player gate should exclude spectators");
        assertFalse(zombieGate.contains("isCreative"),
                "the canonical player gate must continue admitting creative players");

        String serverGate = compactMethod(source, "public static boolean isServerZombiePlayer");
        assertTrue(serverGate.contains(
                        "return!player.level().isClientSide()&&isZombiePlayer(player);"),
                "the server gate should check logical side before delegating to the player gate");
        int serverSideCheck = serverGate.indexOf("!player.level().isClientSide()");
        int playerGateCheck = serverGate.indexOf("isZombiePlayer(player)");
        assertTrue(serverSideCheck >= 0 && serverSideCheck < playerGateCheck,
                "the server-side check must retain its short-circuit position before player admission");
    }

    @Test
    void babyZombieRiderUsesTheSharedExclusionBeforeReadingAttachmentState() throws IOException {
        String source = SourceScan.mainJava(RIDE_HELPER);
        String method = compactMethod(source, "public static boolean isBabyZombieRider");

        assertTrue(method.contains(
                        "if(!ZombiePlayerGates.isZombiePlayer(rider)){returnfalse;}"),
                "baby riders should negate the shared admission gate to exclude spectators");
        assertFalse(method.contains(".isSpectator("),
                "the rider helper should not duplicate the canonical spectator check");
        assertFalse(method.contains("isCreative"),
                "creative baby zombie players must continue to be eligible riders");
        assertTrue(method.contains(
                        "rider.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state().size()==ZombieSize.BABY"),
                "the rider helper should retain its attachment-backed BABY check");

        int admissionGate = method.indexOf("ZombiePlayerGates.isZombiePlayer(rider)");
        int attachmentRead = method.indexOf("rider.getData(IAmZombieAttachments.PLAYER_ZOMBIE)");
        int babyCheck = method.indexOf("ZombieSize.BABY");
        assertTrue(admissionGate >= 0 && admissionGate < attachmentRead && attachmentRead < babyCheck,
                "spectator exclusion must remain before the attachment read and BABY comparison");
    }

    private static String compactMethod(String source, String signature) {
        return SourceScan.compact(SourceScan.stripComments(SourceScan.methodBody(source, signature)));
    }
}
