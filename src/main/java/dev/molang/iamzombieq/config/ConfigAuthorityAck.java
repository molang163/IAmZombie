package dev.molang.iamzombieq.config;

import dev.molang.iamzombieq.util.ModIds;
import java.util.Objects;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

record ConfigAuthorityAck(
        long epoch,
        String protocol,
        String schemaFingerprint,
        String payloadSha256)
        implements CustomPacketPayload {
    static final Type<ConfigAuthorityAck> TYPE =
            new Type<>(ModIds.id("config_authority_ack"));

    ConfigAuthorityAck {
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(schemaFingerprint, "schemaFingerprint");
        Objects.requireNonNull(payloadSha256, "payloadSha256");
    }

    static ConfigAuthorityAck forSnapshot(
            ConfigAuthoritySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new ConfigAuthorityAck(
                snapshot.epoch(),
                snapshot.protocol(),
                snapshot.schemaFingerprint(),
                snapshot.payloadSha256());
    }

    @Override
    public Type<ConfigAuthorityAck> type() {
        return TYPE;
    }
}
