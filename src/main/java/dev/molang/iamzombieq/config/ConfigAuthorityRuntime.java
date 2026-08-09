package dev.molang.iamzombieq.config;

import dev.molang.iamzombieq.IAmZombieMod;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Package-private production runtime for the per-connection SERVER authority
 * handshake. Callers must supply the connection they are actually using;
 * there is deliberately no process-global "current server" state.
 */
final class ConfigAuthorityRuntime {
    private static final AtomicLong NEXT_EPOCH = new AtomicLong();

    private ConfigAuthorityRuntime() {
    }

    /**
     * Registers the two mandatory configuration-phase payloads. The registrar
     * remains non-optional so an old peer is rejected during channel
     * negotiation.
     */
    static void registerPayloads(
            RegisterPayloadHandlersEvent event) {
        Objects.requireNonNull(event, "event");
        PayloadRegistrar registrar = event.registrar(
                ConfigAuthorityProtocol.negotiationVersion());
        registrar.configurationToClient(
                ConfigAuthoritySnapshot.TYPE,
                ConfigAuthorityPayloadCodec.SNAPSHOT,
                ConfigAuthorityRuntime::handleSnapshot);
        registrar.configurationToServer(
                ConfigAuthorityAck.TYPE,
                ConfigAuthorityPayloadCodec.ACK,
                ConfigAuthorityRuntime::handleAck);
    }

    static ConfigurationTask beginServerConfiguration(
            Connection connection) {
        Objects.requireNonNull(connection, "connection");
        ConfigAuthoritySnapshot snapshot =
                ConfigAuthorityProtocol.snapshot(
                        nextEpoch(),
                        ConfigAuthorityRemoteValues.captureServerConfig());
        ConfigAuthorityConnections.beginServer(connection, snapshot);
        return new ConfigAuthorityConfigurationTask(snapshot);
    }

    //? if <1.21.10 {
    /*static void tickLegacyServerConfiguration(
            ConfigurationTask currentTask,
            ServerConfigurationPacketListenerImpl listener) {
        Objects.requireNonNull(listener, "listener");
        if (!(currentTask
                instanceof ConfigAuthorityConfigurationTask authorityTask)) {
            return;
        }

        Connection connection = listener.getConnection();
        try {
            if (authorityTask.tick()) {
                throw new ConfigAuthorityProtocolException(
                        "Configuration authority task attempted to finish "
                                + "without its acknowledgement");
            }
        } catch (RuntimeException rejected) {
            ConfigAuthorityConnections.clear(connection);
            IAmZombieMod.LOGGER.warn(
                    "Configuration authority task failed; disconnecting {}",
                    connection.getRemoteAddress(),
                    rejected);
            listener.disconnect(rejection(
                    "SERVER authority task failed: "
                            + rejected.getMessage()));
        }
    }
    *///?}

    /**
     * Demotes a newly-entered client configuration connection to PENDING.
     * This must be called for every initial and reconfiguration epoch before a
     * snapshot can be accepted.
     */
    static void beginClientConfiguration(
            Connection connection) {
        ConfigAuthorityConnections.beginClient(connection);
    }

    /**
     * Refreshes a READY client's complete remote19 snapshot from the SERVER
     * holder that NeoForge has already replaced for this connection. The
     * session gate runs before the holder capture.
     */
    static void refreshClientFromSyncedServerConfig(
            Connection connection) {
        Objects.requireNonNull(connection, "connection");
        ConfigAuthorityConnections.refreshClientIfReady(
                connection,
                epoch -> ConfigAuthorityProtocol.snapshot(
                        epoch,
                        ConfigAuthorityRemoteValues.captureServerConfig()));
    }

    /**
     * Explicit title/logout/failure cleanup. The channel close listener
     * performs the same cleanup as a backstop.
     */
    static void clear(Connection connection) {
        ConfigAuthorityConnections.clear(connection);
    }

    static boolean isReady(Connection connection) {
        return ConfigAuthorityConnections.ready(connection);
    }

    static void requireReady(Connection connection) {
        ConfigAuthorityConnections.require(connection).values();
    }

    static List<String> configuredZombieFoods(
            Connection connection) {
        return ConfigAuthorityConnections.require(connection)
                .values()
                .zombieFoods();
    }

    /**
     * Resolves one of the exact seventeen {@code ZombieFoodRules.KEY_*}
     * semantic keys from the current READY payload.
     */
    static int resolveFoodConfig(
            Connection connection, String semanticKey) {
        return ConfigAuthorityConnections.require(connection)
                .values()
                .foodConfig(semanticKey);
    }

    static float spiderMountSpeed(Connection connection) {
        return (float) ConfigAuthorityConnections.require(connection)
                .values()
                .spiderMountSpeed();
    }

    private static void handleSnapshot(
            ConfigAuthoritySnapshot snapshot,
            IPayloadContext context) {
        try {
            ConfigAuthorityAck ack =
                    ConfigAuthorityConnections.acceptClientSnapshot(
                            context.connection(), snapshot);
            context.reply(ack);
        } catch (RuntimeException rejected) {
            ConfigAuthorityConnections.clear(context.connection());
            context.disconnect(rejection(
                    "invalid or out-of-epoch SERVER authority snapshot"));
        }
    }

    private static void handleAck(
            ConfigAuthorityAck ack, IPayloadContext context) {
        try {
            ConfigAuthorityConnections.acceptServerAck(
                    context.connection(), ack);
            context.finishCurrentTask(
                    ConfigAuthorityConfigurationTask.TYPE);
        } catch (RuntimeException rejected) {
            ConfigAuthorityConnections.clear(context.connection());
            context.disconnect(rejection(
                    "invalid or out-of-epoch SERVER authority acknowledgement"));
        }
    }

    private static Component rejection(String reason) {
        return Component.literal(
                "I Am Zombie? configuration authority rejected the connection: "
                        + reason);
    }

    private static long nextEpoch() {
        while (true) {
            long current = NEXT_EPOCH.get();
            if (current == Long.MAX_VALUE) {
                throw new IllegalStateException(
                        "Configuration authority epoch exhausted");
            }
            long next = current + 1L;
            if (NEXT_EPOCH.compareAndSet(current, next)) {
                return next;
            }
        }
    }
}
