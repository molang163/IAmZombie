package dev.molang.iamzombieq.config;

import dev.molang.iamzombieq.util.ModIds;
import java.util.Objects;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

record ConfigAuthoritySnapshot(
        long epoch,
        String protocol,
        String schemaFingerprint,
        String payloadSha256,
        ConfigAuthorityRemoteValues values)
        implements CustomPacketPayload {
    static final Type<ConfigAuthoritySnapshot> TYPE =
            new Type<>(ModIds.id("config_authority_snapshot"));

    ConfigAuthoritySnapshot {
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(schemaFingerprint, "schemaFingerprint");
        Objects.requireNonNull(payloadSha256, "payloadSha256");
        Objects.requireNonNull(values, "values");
    }

    @Override
    public Type<ConfigAuthoritySnapshot> type() {
        return TYPE;
    }
}
