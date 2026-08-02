package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConfigAuthoritySessionTest {
    @Test
    void clientCanOnlyBecomeReadyFromCurrentPendingValidatedSnapshot() {
        ConfigAuthorityRemoteValues values = ConfigAuthorityRemoteValuesTest.defaultValues();
        ConfigAuthoritySnapshot snapshot = ConfigAuthorityProtocol.snapshot(41L, values);
        ConfigAuthoritySession session = ConfigAuthoritySession.clientPending();

        assertFalse(session.ready());
        assertThrows(ConfigAuthorityUnavailableException.class, session::values);

        ConfigAuthorityAck ack = session.acceptClientSnapshot(snapshot);
        assertEquals(snapshot.epoch(), ack.epoch());
        assertEquals(snapshot.protocol(), ack.protocol());
        assertEquals(snapshot.schemaFingerprint(), ack.schemaFingerprint());
        assertEquals(snapshot.payloadSha256(), ack.payloadSha256());
        assertTrue(session.ready());
        assertEquals(values, session.values());
        assertThrows(ConfigAuthorityProtocolException.class,
                () -> session.acceptClientSnapshot(snapshot),
                "READY must not accept a second snapshot without a new configuration epoch");
    }

    @Test
    void absentOldReadyAndMismatchedSnapshotsCannotSkipPendingEpoch() {
        ConfigAuthorityRemoteValues values = ConfigAuthorityRemoteValuesTest.defaultValues();
        ConfigAuthoritySnapshot current = ConfigAuthorityProtocol.snapshot(42L, values);

        ConfigAuthoritySession absent = ConfigAuthoritySession.absent();
        assertThrows(ConfigAuthorityProtocolException.class,
                () -> absent.acceptClientSnapshot(current));

        ConfigAuthoritySession old = ConfigAuthoritySession.clientPending();
        old.acceptClientSnapshot(ConfigAuthorityProtocol.snapshot(41L, values));
        assertThrows(ConfigAuthorityProtocolException.class,
                () -> old.acceptClientSnapshot(current));

        ConfigAuthoritySession pending = ConfigAuthoritySession.clientPending();
        ConfigAuthoritySnapshot wrongSchema = new ConfigAuthoritySnapshot(
                current.epoch(), current.protocol(), "0".repeat(64),
                current.payloadSha256(), current.values());
        assertThrows(ConfigAuthorityProtocolException.class,
                () -> pending.acceptClientSnapshot(wrongSchema));
        assertFalse(pending.ready());

        ConfigAuthoritySnapshot wrongHash = new ConfigAuthoritySnapshot(
                current.epoch(), current.protocol(), current.schemaFingerprint(),
                "f".repeat(64), current.values());
        assertThrows(ConfigAuthorityProtocolException.class,
                () -> ConfigAuthoritySession.clientPending().acceptClientSnapshot(wrongHash));
    }

    @Test
    void serverOnlyAcceptsExactAckForItsCurrentPayloadAndEpoch() {
        ConfigAuthoritySnapshot snapshot = ConfigAuthorityProtocol.snapshot(
                77L, ConfigAuthorityRemoteValuesTest.defaultValues());
        ConfigAuthoritySession server = ConfigAuthoritySession.serverPending(snapshot);

        ConfigAuthorityAck stale = new ConfigAuthorityAck(
                76L, snapshot.protocol(), snapshot.schemaFingerprint(), snapshot.payloadSha256());
        assertThrows(ConfigAuthorityProtocolException.class, () -> server.acceptServerAck(stale));
        assertFalse(server.ready());

        ConfigAuthorityAck wrongPayload = new ConfigAuthorityAck(
                snapshot.epoch(),
                snapshot.protocol(),
                snapshot.schemaFingerprint(),
                "0".repeat(64));
        assertThrows(
                ConfigAuthorityProtocolException.class,
                () -> server.acceptServerAck(wrongPayload),
                "the current epoch cannot become READY without the current payload");
        assertFalse(server.ready());

        ConfigAuthorityAck exact = ConfigAuthorityAck.forSnapshot(snapshot);
        server.acceptServerAck(exact);
        assertTrue(server.ready());
        assertEquals(snapshot.values(), server.values());
        assertThrows(ConfigAuthorityProtocolException.class, () -> server.acceptServerAck(exact));
    }

    @Test
    void reconfigurationPendingRequiresAnEpochStrictlyNewerThanPreviousReady() {
        ConfigAuthorityRemoteValues values =
                ConfigAuthorityRemoteValuesTest.defaultValues();
        ConfigAuthoritySession session =
                ConfigAuthoritySession.clientPending(41L);

        assertThrows(ConfigAuthorityProtocolException.class,
                () -> session.acceptClientSnapshot(
                        ConfigAuthorityProtocol.snapshot(41L, values)));
        assertFalse(session.ready());

        session.acceptClientSnapshot(
                ConfigAuthorityProtocol.snapshot(42L, values));
        assertTrue(session.ready());
        assertEquals(42L, session.epoch());
    }

    @Test
    void closeIsTerminalAndAlwaysFailClosed() {
        ConfigAuthoritySnapshot snapshot = ConfigAuthorityProtocol.snapshot(
                9L, ConfigAuthorityRemoteValuesTest.defaultValues());
        ConfigAuthoritySession client = ConfigAuthoritySession.clientPending();
        client.acceptClientSnapshot(snapshot);
        client.close();

        assertFalse(client.ready());
        assertThrows(ConfigAuthorityUnavailableException.class, client::values);
        assertThrows(ConfigAuthorityProtocolException.class,
                () -> client.acceptClientSnapshot(snapshot));
    }

    @Test
    void readyClientRefreshAtomicallyReplacesAllNineteenWithoutChangingEpoch() {
        ConfigAuthorityRemoteValues initial =
                ConfigAuthorityRemoteValuesTest.defaultValues();
        ConfigAuthorityRemoteValues edited =
                ConfigAuthorityRemoteValuesTest.editedValues(1);
        ConfigAuthoritySession session =
                ConfigAuthoritySession.clientPending();
        session.acceptClientSnapshot(
                ConfigAuthorityProtocol.snapshot(91L, initial));

        String oldSha = session.values().payloadSha256();
        assertTrue(session.refreshClientSnapshot(
                epoch -> ConfigAuthorityProtocol.snapshot(epoch, edited)));

        assertTrue(session.ready());
        assertEquals(91L, session.epoch());
        assertEquals(edited, session.values());
        assertEquals(edited.zombieFoods(), session.values().zombieFoods());
        assertEquals(
                edited.integerValues(), session.values().integerValues());
        assertEquals(
                edited.spiderMountSpeed(),
                session.values().spiderMountSpeed());
        assertNotEquals(oldSha, session.values().payloadSha256());
    }

    @Test
    void nonClientReadyStatesIgnoreRefreshWithoutCallingFactory() {
        ConfigAuthorityRemoteValues values =
                ConfigAuthorityRemoteValuesTest.defaultValues();
        ConfigAuthoritySnapshot snapshot =
                ConfigAuthorityProtocol.snapshot(92L, values);
        ConfigAuthoritySession absent = ConfigAuthoritySession.absent();
        ConfigAuthoritySession pending =
                ConfigAuthoritySession.clientPending();
        ConfigAuthoritySession server =
                ConfigAuthoritySession.serverPending(snapshot);
        server.acceptServerAck(ConfigAuthorityAck.forSnapshot(snapshot));
        ConfigAuthoritySession closed =
                ConfigAuthoritySession.clientPending();
        closed.acceptClientSnapshot(snapshot);
        closed.close();
        AtomicInteger calls = new AtomicInteger();

        for (ConfigAuthoritySession session :
                java.util.List.of(absent, pending, server, closed)) {
            assertFalse(session.refreshClientSnapshot(epoch -> {
                calls.incrementAndGet();
                return ConfigAuthorityProtocol.snapshot(epoch, values);
            }));
        }

        assertEquals(0, calls.get(),
                "non-client-READY states must not read canonical SERVER values");
    }

    @Test
    void invalidReplacementFailsClosedWithoutRetainingOldSnapshot() {
        ConfigAuthorityRemoteValues initial =
                ConfigAuthorityRemoteValuesTest.defaultValues();
        ConfigAuthorityRemoteValues edited =
                ConfigAuthorityRemoteValuesTest.editedValues(1);
        ConfigAuthoritySession session =
                ConfigAuthoritySession.clientPending();
        ConfigAuthoritySnapshot initialSnapshot =
                ConfigAuthorityProtocol.snapshot(93L, initial);
        session.acceptClientSnapshot(initialSnapshot);

        ConfigAuthoritySnapshot wrongHash = new ConfigAuthoritySnapshot(
                initialSnapshot.epoch(),
                initialSnapshot.protocol(),
                initialSnapshot.schemaFingerprint(),
                "0".repeat(64),
                edited);
        assertThrows(
                ConfigAuthorityProtocolException.class,
                () -> session.refreshClientSnapshot(epoch -> wrongHash));

        assertFalse(session.ready());
        assertThrows(ConfigAuthorityUnavailableException.class, session::values,
                "a rejected refresh must not leave the old READY snapshot readable");
    }

    @Test
    void refreshFactoryFailureAndEpochChangeBothFailClosed() {
        ConfigAuthorityRemoteValues values =
                ConfigAuthorityRemoteValuesTest.defaultValues();

        ConfigAuthoritySession factoryFailure =
                ConfigAuthoritySession.clientPending();
        factoryFailure.acceptClientSnapshot(
                ConfigAuthorityProtocol.snapshot(94L, values));
        assertThrows(IllegalStateException.class,
                () -> factoryFailure.refreshClientSnapshot(epoch -> {
                    throw new IllegalStateException("capture failed");
                }));
        assertFalse(factoryFailure.ready());
        assertThrows(
                ConfigAuthorityUnavailableException.class,
                factoryFailure::values);

        ConfigAuthoritySession epochChange =
                ConfigAuthoritySession.clientPending();
        epochChange.acceptClientSnapshot(
                ConfigAuthorityProtocol.snapshot(95L, values));
        assertThrows(ConfigAuthorityProtocolException.class,
                () -> epochChange.refreshClientSnapshot(
                        epoch -> ConfigAuthorityProtocol.snapshot(
                                epoch + 1L,
                                ConfigAuthorityRemoteValuesTest.editedValues(1))));
        assertFalse(epochChange.ready());
        assertThrows(
                ConfigAuthorityUnavailableException.class,
                epochChange::values);
    }

    @Test
    void rapidReloadsExposeTheLatestWholeSnapshot() {
        ConfigAuthorityRemoteValues first =
                ConfigAuthorityRemoteValuesTest.editedValues(1);
        ConfigAuthorityRemoteValues latest =
                ConfigAuthorityRemoteValuesTest.editedValues(2);
        ConfigAuthoritySession session =
                ConfigAuthoritySession.clientPending();
        session.acceptClientSnapshot(ConfigAuthorityProtocol.snapshot(
                96L, ConfigAuthorityRemoteValuesTest.defaultValues()));

        assertTrue(session.refreshClientSnapshot(
                epoch -> ConfigAuthorityProtocol.snapshot(epoch, first)));
        assertTrue(session.refreshClientSnapshot(
                epoch -> ConfigAuthorityProtocol.snapshot(epoch, latest)));

        assertEquals(96L, session.epoch());
        assertEquals(latest, session.values());
        assertEquals(latest.zombieFoods(), session.values().zombieFoods());
        assertEquals(
                latest.integerValues(), session.values().integerValues());
        assertEquals(
                latest.spiderMountSpeed(),
                session.values().spiderMountSpeed());
        assertEquals(
                latest.payloadSha256(),
                session.values().payloadSha256());
    }
}
