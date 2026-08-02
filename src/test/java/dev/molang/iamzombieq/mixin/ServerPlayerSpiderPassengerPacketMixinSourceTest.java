package dev.molang.iamzombieq.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ServerPlayerSpiderPassengerPacketMixinSourceTest {
    private static final String MIXIN_PATH =
            "dev/molang/iamzombieq/mixin/ServerPlayerSpiderPassengerPacketMixin.java";
    private static final Path MIXIN_FILE =
            Path.of("src/main/java").resolve(MIXIN_PATH);

    @Test
    void restoredSpiderAssociationWrapsTheExactRequiredVanillaSend() throws IOException {
        assertTrue(
                Files.isRegularFile(MIXIN_FILE),
                "Candidate B must provide the narrow ServerPlayer passenger-send mixin");
        String source = SourceScan.mainJava(MIXIN_PATH);
        String executable = SourceScan.stripComments(source);
        String compact = SourceScan.compact(executable);

        assertTrue(executable.contains("@Mixin(ServerPlayer.class)"));
        assertTrue(executable.contains("@WrapOperation("));
        assertTrue(
                compact.contains(
                        "method=\"startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z\""));
        assertTrue(
                compact.contains(
                                "target=\"Lnet/minecraft/server/network/ServerGamePacketListenerImpl;\""
                                        + "+\"send(Lnet/minecraft/network/protocol/Packet;)V\"")
                        || compact.contains(
                                "target=\"Lnet/minecraft/server/network/ServerGamePacketListenerImpl;"
                                        + "send(Lnet/minecraft/network/protocol/Packet;)V\""));
        assertTrue(compact.contains("require=1"));
    }

    @Test
    void suppressionIsBoundToTheExactServerOwnedSpiderRestoration() throws IOException {
        assertTrue(Files.isRegularFile(MIXIN_FILE));
        String source = SourceScan.mainJava(MIXIN_PATH);
        String method =
                SourceScan.methodBody(
                        source, "private void iamzombieq$deferRestoredSpiderPassengers");
        String compact = SourceScan.compact(SourceScan.stripComments(method));

        assertTrue(compact.contains("listener==player.connection"));
        assertTrue(compact.contains("packetinstanceofClientboundSetPassengersPacket"));
        assertTrue(compact.contains("entityToRideinstanceofSpiderspider"));
        assertTrue(compact.contains("player.level()==spider.level()"));
        assertTrue(
                compact.contains(
                        "player.level().dimension()==spider.level().dimension()"));
        assertTrue(compact.contains("ZombiePlayerGates.isServerZombiePlayer(player)"));
        assertTrue(
                compact.contains(
                        "MountCapability.isOwnedSpider(spider,player.getUUID())"));
        assertTrue(compact.contains("spider.getControllingPassenger()==player"));
        assertTrue(compact.contains("spider.getFirstPassenger()==player"));
        assertTrue(compact.contains("player.getVehicle()==spider"));
        assertTrue(compact.contains("iamzombieq$shouldDefer("));
        String exactDecisionWiring =
                "booleanshouldDefer=iamzombieq$shouldDefer("
                        + "force,"
                        + "sendEventAndTriggers,"
                        + "listener==player.connection,"
                        + "ZombiePlayerGates.isServerZombiePlayer(player),"
                        + "player.level()==spider.level()"
                        + "&&player.level().dimension()==spider.level().dimension(),"
                        + "MountCapability.isOwnedSpider(spider,player.getUUID()),"
                        + "spider.getFirstPassenger()==player"
                        + "&&spider.getControllingPassenger()==player,"
                        + "player.getVehicle()==spider,"
                        + "spider.getId(),"
                        + "passengers.getVehicle(),"
                        + "player.getId(),"
                        + "passengers.getPassengers());";
        assertEquals(
                1,
                occurrences(compact, exactDecisionWiring),
                "the inline decision seam must receive the exact live wrapper values in order");
    }

    @Test
    void everyNonMatchingSendUsesTheOriginalOperation() throws IOException {
        assertTrue(Files.isRegularFile(MIXIN_FILE));
        String source = SourceScan.mainJava(MIXIN_PATH);
        String method =
                SourceScan.stripComments(
                        SourceScan.methodBody(
                                source,
                                "private void iamzombieq$deferRestoredSpiderPassengers"));

        assertEquals(
                2,
                occurrences(method, "original.call(listener, packet)"),
                "both structural nonmatches and failed decision gates must forward");
        assertTrue(
                SourceScan.compact(method)
                        .contains(
                                "if(!shouldDefer){original.call(listener,packet);}"),
                "the exact matching early association is the only send that may be deferred");
        assertFalse(method.contains("new ClientboundSetPassengersPacket"));
        assertFalse(method.contains("ClientboundAddEntityPacket"));
        assertFalse(method.contains("Thread.sleep"));
        assertFalse(method.contains("while ("));
        assertFalse(method.contains("for ("));
    }

    @Test
    void mixinIsRegisteredExactlyOnceOnTheCommonSide() throws IOException {
        String mixins = SourceScan.resource("iamzombieq.mixins.json");

        assertEquals(
                1,
                occurrences(mixins, "\"ServerPlayerSpiderPassengerPacketMixin\""));
        assertFalse(
                SourceScan.compact(mixins)
                        .contains(
                                "\"client\":[\"ServerPlayerSpiderPassengerPacketMixin\""));
    }

    @Test
    void decisionCodeIsMergedIntoTheMixinAndNeverLoadedAsAMixinPackageHelper()
            throws IOException {
        assertTrue(Files.isRegularFile(MIXIN_FILE));
        String source = SourceScan.mainJava(MIXIN_PATH);
        String executable = SourceScan.stripComments(source);
        String compact = SourceScan.compact(executable);

        Matcher topLevelTypes =
                Pattern.compile(
                                "(?m)^(?:(?:public|protected|private|abstract|final|static|"
                                        + "strictfp|sealed|non-sealed)\\s+)*"
                                        + "(?:class|interface|enum|record)\\s+[A-Za-z_$][\\w$]*\\b")
                        .matcher(executable);
        int topLevelTypeCount = 0;
        while (topLevelTypes.find()) {
            topLevelTypeCount++;
        }
        assertEquals(
                1,
                topLevelTypeCount,
                "a directly referenced helper type in a declared mixin package is rejected at runtime");
        assertEquals(
                1,
                occurrences(
                        compact,
                        "@Uniqueprivatestaticbooleaniamzombieq$shouldDefer("),
                "the decision seam must remain a uniquely named method merged into ServerPlayer");
        assertEquals(
                0,
                topLevelMemberSemicolons(executable),
                "the deferral mixin must remain stateless: it needs no class field or abstract member");
        assertFalse(
                executable.contains("net.minecraft.client"),
                "the common ServerPlayer mixin must remain safe to load on a dedicated server");
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static int topLevelMemberSemicolons(String source) {
        int declaration = source.indexOf("abstract class ServerPlayerSpiderPassengerPacketMixin");
        if (declaration < 0) {
            return -1;
        }
        int openingBrace = source.indexOf('{', declaration);
        if (openingBrace < 0) {
            return -1;
        }

        int depth = 1;
        int semicolons = 0;
        boolean inString = false;
        boolean inChar = false;
        for (int i = openingBrace + 1; i < source.length() && depth > 0; i++) {
            char current = source.charAt(i);
            if (inString) {
                if (current == '\\') {
                    i++;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (current == '\\') {
                    i++;
                } else if (current == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '\'') {
                inChar = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
            } else if (current == ';' && depth == 1) {
                semicolons++;
            }
        }
        return semicolons;
    }
}
