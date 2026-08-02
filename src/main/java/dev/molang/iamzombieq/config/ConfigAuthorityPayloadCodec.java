package dev.molang.iamzombieq.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

final class ConfigAuthorityPayloadCodec {
    private static final int WIRE_ID_MAX_CHARS = 64;
    private static final int FOOD_ID_MAX_CHARS = 32_767;

    static final StreamCodec<FriendlyByteBuf, ConfigAuthoritySnapshot> SNAPSHOT =
            StreamCodec.of(
                    ConfigAuthorityPayloadCodec::encodeSnapshot,
                    ConfigAuthorityPayloadCodec::decodeSnapshot);
    static final StreamCodec<FriendlyByteBuf, ConfigAuthorityAck> ACK =
            StreamCodec.of(
                    ConfigAuthorityPayloadCodec::encodeAck,
                    ConfigAuthorityPayloadCodec::decodeAck);

    private ConfigAuthorityPayloadCodec() {
    }

    private static void encodeSnapshot(
            FriendlyByteBuf output, ConfigAuthoritySnapshot snapshot) {
        ConfigAuthorityProtocol.validateSnapshot(snapshot);
        output.writeLong(snapshot.epoch());
        output.writeUtf(snapshot.protocol(), WIRE_ID_MAX_CHARS);
        output.writeUtf(
                snapshot.schemaFingerprint(), WIRE_ID_MAX_CHARS);
        output.writeUtf(snapshot.payloadSha256(), WIRE_ID_MAX_CHARS);
        output.writeVarInt(snapshot.values().zombieFoods().size());
        for (String food : snapshot.values().zombieFoods()) {
            output.writeUtf(food, FOOD_ID_MAX_CHARS);
        }
        for (String field : ConfigAuthorityRemoteValues.integerFields()) {
            output.writeVarInt(snapshot.values().integer(field));
        }
        output.writeDouble(snapshot.values().spiderMountSpeed());
    }

    private static ConfigAuthoritySnapshot decodeSnapshot(
            FriendlyByteBuf input) {
        long epoch = input.readLong();
        String protocol = input.readUtf(WIRE_ID_MAX_CHARS);
        String schema = input.readUtf(WIRE_ID_MAX_CHARS);
        String payloadHash = input.readUtf(WIRE_ID_MAX_CHARS);

        int foodCount = input.readVarInt();
        if (foodCount < 0 || foodCount > input.readableBytes()) {
            throw new IllegalArgumentException(
                    "Invalid zombieFoods element count: " + foodCount);
        }
        List<String> foods = new ArrayList<>(foodCount);
        for (int index = 0; index < foodCount; index++) {
            foods.add(input.readUtf(FOOD_ID_MAX_CHARS));
        }
        LinkedHashMap<String, Integer> integers = new LinkedHashMap<>();
        for (String field : ConfigAuthorityRemoteValues.integerFields()) {
            integers.put(field, input.readVarInt());
        }
        double spiderSpeed = input.readDouble();
        ConfigAuthoritySnapshot snapshot = new ConfigAuthoritySnapshot(
                epoch,
                protocol,
                schema,
                payloadHash,
                new ConfigAuthorityRemoteValues(
                        foods, integers, spiderSpeed));
        ConfigAuthorityProtocol.validateSnapshot(snapshot);
        rejectTrailingBytes(input, "authority snapshot");
        return snapshot;
    }

    private static void encodeAck(
            FriendlyByteBuf output, ConfigAuthorityAck ack) {
        validateAckShape(ack);
        output.writeLong(ack.epoch());
        output.writeUtf(ack.protocol(), WIRE_ID_MAX_CHARS);
        output.writeUtf(
                ack.schemaFingerprint(), WIRE_ID_MAX_CHARS);
        output.writeUtf(ack.payloadSha256(), WIRE_ID_MAX_CHARS);
    }

    private static ConfigAuthorityAck decodeAck(FriendlyByteBuf input) {
        ConfigAuthorityAck ack = new ConfigAuthorityAck(
                input.readLong(),
                input.readUtf(WIRE_ID_MAX_CHARS),
                input.readUtf(WIRE_ID_MAX_CHARS),
                input.readUtf(WIRE_ID_MAX_CHARS));
        validateAckShape(ack);
        rejectTrailingBytes(input, "authority acknowledgement");
        return ack;
    }

    private static void validateAckShape(ConfigAuthorityAck ack) {
        if (ack.epoch() <= 0L
                || ack.protocol().isEmpty()
                || !ack.schemaFingerprint().matches("[0-9a-f]{64}")
                || !ack.payloadSha256().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Malformed authority acknowledgement");
        }
    }

    private static void rejectTrailingBytes(
            FriendlyByteBuf input, String payloadName) {
        if (input.isReadable()) {
            throw new IllegalArgumentException(
                    "Trailing bytes after " + payloadName);
        }
    }
}
