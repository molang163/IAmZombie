package dev.molang.iamzombieq.config;

import java.util.Objects;
import java.util.function.LongFunction;

/**
 * One connection's configuration authority state. All transitions are
 * fail-closed and synchronized because close listeners run on the channel
 * event loop while payload handlers run on the game thread.
 */
final class ConfigAuthoritySession {
    private final Side side;
    private final long minimumExclusiveEpoch;
    private Phase phase;
    private ConfigAuthoritySnapshot snapshot;

    private ConfigAuthoritySession(
            Side side,
            Phase phase,
            long minimumExclusiveEpoch,
            ConfigAuthoritySnapshot snapshot) {
        this.side = Objects.requireNonNull(side, "side");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.minimumExclusiveEpoch = minimumExclusiveEpoch;
        this.snapshot = snapshot;
    }

    static ConfigAuthoritySession absent() {
        return new ConfigAuthoritySession(
                Side.NONE, Phase.ABSENT, 0L, null);
    }

    static ConfigAuthoritySession clientPending() {
        return clientPending(0L);
    }

    static ConfigAuthoritySession clientPending(
            long minimumExclusiveEpoch) {
        if (minimumExclusiveEpoch < 0L) {
            throw new IllegalArgumentException(
                    "minimumExclusiveEpoch must not be negative");
        }
        return new ConfigAuthoritySession(
                Side.CLIENT,
                Phase.PENDING,
                minimumExclusiveEpoch,
                null);
    }

    static ConfigAuthoritySession serverPending(
            ConfigAuthoritySnapshot snapshot) {
        ConfigAuthorityProtocol.validateSnapshot(snapshot);
        return new ConfigAuthoritySession(
                Side.SERVER, Phase.PENDING, 0L, snapshot);
    }

    synchronized ConfigAuthorityAck acceptClientSnapshot(
            ConfigAuthoritySnapshot incoming) {
        requirePending(Side.CLIENT, "client snapshot");
        ConfigAuthorityProtocol.validateSnapshot(incoming);
        if (incoming.epoch() <= minimumExclusiveEpoch) {
            throw new ConfigAuthorityProtocolException(
                    "Authority snapshot epoch is stale for this reconfiguration");
        }
        snapshot = incoming;
        phase = Phase.READY;
        return ConfigAuthorityAck.forSnapshot(incoming);
    }

    synchronized void acceptServerAck(ConfigAuthorityAck ack) {
        requirePending(Side.SERVER, "server acknowledgement");
        ConfigAuthorityProtocol.validateAck(snapshot, ack);
        phase = Phase.READY;
    }

    /**
     * Replaces a READY client's complete remote19 snapshot without changing
     * the connection epoch or re-entering the handshake.
     */
    synchronized boolean refreshClientSnapshot(
            LongFunction<ConfigAuthoritySnapshot> replacementFactory) {
        Objects.requireNonNull(
                replacementFactory, "replacementFactory");
        if (side != Side.CLIENT
                || phase != Phase.READY
                || snapshot == null) {
            return false;
        }
        try {
            long currentEpoch = snapshot.epoch();
            ConfigAuthoritySnapshot replacement =
                    Objects.requireNonNull(
                            replacementFactory.apply(currentEpoch),
                            "replacement snapshot");
            ConfigAuthorityProtocol.validateSnapshot(replacement);
            if (replacement.epoch() != currentEpoch) {
                throw new ConfigAuthorityProtocolException(
                        "Authority refresh changed the connection epoch");
            }
            snapshot = replacement;
            return true;
        } catch (RuntimeException | Error failure) {
            phase = Phase.CLOSED;
            snapshot = null;
            throw failure;
        }
    }

    synchronized boolean ready() {
        return phase == Phase.READY;
    }

    synchronized long epoch() {
        return snapshot == null ? 0L : snapshot.epoch();
    }

    synchronized ConfigAuthorityRemoteValues values() {
        if (phase != Phase.READY || snapshot == null) {
            throw new ConfigAuthorityUnavailableException(
                    "SERVER authority is not READY for the current connection epoch");
        }
        return snapshot.values();
    }

    synchronized void close() {
        phase = Phase.CLOSED;
        snapshot = null;
    }

    private void requirePending(Side expectedSide, String operation) {
        if (side != expectedSide || phase != Phase.PENDING) {
            throw new ConfigAuthorityProtocolException(
                    "Cannot process " + operation + " from "
                            + side + "/" + phase);
        }
    }

    private enum Side {
        NONE,
        CLIENT,
        SERVER
    }

    private enum Phase {
        ABSENT,
        PENDING,
        READY,
        CLOSED
    }
}
