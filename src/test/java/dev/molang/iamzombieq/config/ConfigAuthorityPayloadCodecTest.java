package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class ConfigAuthorityPayloadCodecTest {
    @Test
    void snapshotAndAckRoundTripExactlyThroughConfigurationCodecs() {
        ConfigAuthoritySnapshot snapshot = ConfigAuthorityProtocol.snapshot(
                1234L, ConfigAuthorityRemoteValuesTest.defaultValues());
        FriendlyByteBuf snapshotBuffer = new FriendlyByteBuf(Unpooled.buffer());
        ConfigAuthorityPayloadCodec.SNAPSHOT.encode(snapshotBuffer, snapshot);
        assertEquals(snapshot, ConfigAuthorityPayloadCodec.SNAPSHOT.decode(snapshotBuffer));
        assertEquals(0, snapshotBuffer.readableBytes());

        ConfigAuthorityAck ack = ConfigAuthorityAck.forSnapshot(snapshot);
        FriendlyByteBuf ackBuffer = new FriendlyByteBuf(Unpooled.buffer());
        ConfigAuthorityPayloadCodec.ACK.encode(ackBuffer, ack);
        assertEquals(ack, ConfigAuthorityPayloadCodec.ACK.decode(ackBuffer));
        assertEquals(0, ackBuffer.readableBytes());
    }

    @Test
    void snapshotCodecRejectsADeclaredHashThatDoesNotMatchTypedValues() {
        ConfigAuthoritySnapshot valid = ConfigAuthorityProtocol.snapshot(
                1L, ConfigAuthorityRemoteValuesTest.defaultValues());
        ConfigAuthoritySnapshot tampered = new ConfigAuthoritySnapshot(
                valid.epoch(), valid.protocol(), valid.schemaFingerprint(),
                "0".repeat(64), valid.values());

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        assertThrows(IllegalArgumentException.class,
                () -> ConfigAuthorityPayloadCodec.SNAPSHOT.encode(buffer, tampered));
    }

    @Test
    void snapshotCodecRejectsInvalidFoodCountBeforeAllocating() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeLong(1L);
        buffer.writeUtf(ConfigAuthorityProtocol.PROTOCOL, 64);
        buffer.writeUtf(ConfigAuthorityProtocol.schemaFingerprint(), 64);
        buffer.writeUtf("0".repeat(64), 64);
        buffer.writeVarInt(Integer.MAX_VALUE);

        assertThrows(IllegalArgumentException.class,
                () -> ConfigAuthorityPayloadCodec.SNAPSHOT.decode(buffer));
    }

    @Test
    void bothCodecsRejectTrailingPayloadBytes() {
        ConfigAuthoritySnapshot snapshot = ConfigAuthorityProtocol.snapshot(
                2L, ConfigAuthorityRemoteValuesTest.defaultValues());
        FriendlyByteBuf snapshotBuffer =
                new FriendlyByteBuf(Unpooled.buffer());
        ConfigAuthorityPayloadCodec.SNAPSHOT.encode(
                snapshotBuffer, snapshot);
        snapshotBuffer.writeByte(1);
        assertThrows(IllegalArgumentException.class,
                () -> ConfigAuthorityPayloadCodec.SNAPSHOT.decode(
                        snapshotBuffer));

        FriendlyByteBuf ackBuffer =
                new FriendlyByteBuf(Unpooled.buffer());
        ConfigAuthorityPayloadCodec.ACK.encode(
                ackBuffer, ConfigAuthorityAck.forSnapshot(snapshot));
        ackBuffer.writeByte(1);
        assertThrows(IllegalArgumentException.class,
                () -> ConfigAuthorityPayloadCodec.ACK.decode(ackBuffer));
    }
}
