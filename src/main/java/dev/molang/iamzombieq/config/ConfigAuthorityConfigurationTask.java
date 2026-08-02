package dev.molang.iamzombieq.config;

import dev.molang.iamzombieq.util.ModIds;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.util.Util;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;

final class ConfigAuthorityConfigurationTask
        implements ICustomConfigurationTask {
    static final long ACK_DEADLINE_MILLIS =
            ServerCommonPacketListenerImpl.LATENCY_CHECK_INTERVAL;
    static final ConfigurationTask.Type TYPE =
            new ConfigurationTask.Type(
                    ModIds.id("config_authority"));

    private final ConfigAuthoritySnapshot snapshot;
    private final LongSupplier clock;
    private boolean started;
    private long startedAtMillis;

    ConfigAuthorityConfigurationTask(
            ConfigAuthoritySnapshot snapshot) {
        this(snapshot, Util::getMillis);
    }

    ConfigAuthorityConfigurationTask(
            ConfigAuthoritySnapshot snapshot,
            LongSupplier clock) {
        this.snapshot = Objects.requireNonNull(
                snapshot, "snapshot");
        this.clock = Objects.requireNonNull(clock, "clock");
        ConfigAuthorityProtocol.validateSnapshot(snapshot);
    }

    @Override
    public void run(Consumer<CustomPacketPayload> sender) {
        if (started) {
            throw new ConfigAuthorityProtocolException(
                    "Configuration authority task started more than once");
        }
        startedAtMillis = clock.getAsLong();
        started = true;
        sender.accept(snapshot);
    }

    @Override
    public boolean tick() {
        if (!started) {
            return false;
        }

        long nowMillis = clock.getAsLong();
        if (nowMillis < startedAtMillis) {
            throw new ConfigAuthorityProtocolException(
                    "Configuration authority acknowledgement clock "
                            + "moved backwards");
        }
        if (nowMillis - startedAtMillis >= ACK_DEADLINE_MILLIS) {
            throw new ConfigAuthorityProtocolException(
                    "Configuration authority snapshot acknowledgement "
                            + "was not received before the deadline");
        }
        return false;
    }

    @Override
    public ConfigurationTask.Type type() {
        return TYPE;
    }
}
