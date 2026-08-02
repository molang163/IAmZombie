package dev.molang.iamzombieq.config;

import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import java.util.Objects;
import java.util.function.LongFunction;
import net.minecraft.network.Connection;

/**
 * Stores authority state on the actual Netty channel, so two connections can
 * never observe each other's epoch or values.
 */
final class ConfigAuthorityConnections {
    private static final AttributeKey<ConfigAuthoritySession> SESSION_KEY =
            AttributeKey.valueOf(
                    "iamzombieq:config_authority_session");

    private ConfigAuthorityConnections() {
    }

    static void beginClient(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        Channel channel = requireOpenChannel(connection);
        Attribute<ConfigAuthoritySession> attribute =
                channel.attr(SESSION_KEY);
        while (true) {
            if (!channel.isOpen()) {
                throw new ConfigAuthorityUnavailableException(
                        "Cannot begin authority configuration on a closed connection");
            }
            ConfigAuthoritySession previous = attribute.get();
            long minimumExclusiveEpoch =
                    previous == null ? 0L : previous.epoch();
            ConfigAuthoritySession replacement =
                    ConfigAuthoritySession.clientPending(
                            minimumExclusiveEpoch);
            if (!attribute.compareAndSet(previous, replacement)) {
                replacement.close();
                continue;
            }
            if (previous != null) {
                previous.close();
            }
            armClose(channel, attribute, replacement);
            return;
        }
    }

    static void beginServer(
            Connection connection, ConfigAuthoritySnapshot snapshot) {
        install(
                connection,
                ConfigAuthoritySession.serverPending(snapshot));
    }

    static ConfigAuthorityAck acceptClientSnapshot(
            Connection connection, ConfigAuthoritySnapshot snapshot) {
        return require(connection).acceptClientSnapshot(snapshot);
    }

    static void acceptServerAck(
            Connection connection, ConfigAuthorityAck ack) {
        require(connection).acceptServerAck(ack);
    }

    static boolean refreshClientIfReady(
            Connection connection,
            LongFunction<ConfigAuthoritySnapshot> replacementFactory) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(
                replacementFactory, "replacementFactory");
        Channel channel = connection.channel();
        if (channel == null || !channel.isOpen()) {
            return false;
        }
        ConfigAuthoritySession session =
                channel.attr(SESSION_KEY).get();
        return session != null
                && session.refreshClientSnapshot(replacementFactory);
    }

    static boolean ready(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        Channel channel = connection.channel();
        if (channel == null || !channel.isOpen()) {
            return false;
        }
        ConfigAuthoritySession session =
                channel.attr(SESSION_KEY).get();
        return session != null && session.ready();
    }

    static ConfigAuthoritySession require(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        Channel channel = connection.channel();
        ConfigAuthoritySession session = channel == null
                ? null
                : channel.attr(SESSION_KEY).get();
        if (session == null || !channel.isOpen()) {
            throw new ConfigAuthorityUnavailableException(
                    "No active authority session is bound to this connection");
        }
        return session;
    }

    static void clear(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        Channel channel = connection.channel();
        if (channel == null) {
            return;
        }
        ConfigAuthoritySession session =
                channel.attr(SESSION_KEY).getAndSet(null);
        if (session != null) {
            session.close();
        }
    }

    private static void install(
            Connection connection, ConfigAuthoritySession replacement) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(replacement, "replacement");
        Channel channel = requireOpenChannel(connection);

        Attribute<ConfigAuthoritySession> attribute =
                channel.attr(SESSION_KEY);
        ConfigAuthoritySession previous =
                attribute.getAndSet(replacement);
        if (previous != null) {
            previous.close();
        }
        armClose(channel, attribute, replacement);
    }

    private static Channel requireOpenChannel(Connection connection) {
        Channel channel = connection.channel();
        if (channel == null || !channel.isOpen()) {
            throw new ConfigAuthorityUnavailableException(
                    "Cannot begin authority configuration before the connection channel is active");
        }
        return channel;
    }

    private static void armClose(
            Channel channel,
            Attribute<ConfigAuthoritySession> attribute,
            ConfigAuthoritySession replacement) {
        channel.closeFuture().addListener(ignored -> {
            if (attribute.compareAndSet(replacement, null)) {
                replacement.close();
            }
        });
    }
}
