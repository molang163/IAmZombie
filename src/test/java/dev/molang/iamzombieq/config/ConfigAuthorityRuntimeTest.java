package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.embedded.EmbeddedChannel;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.junit.jupiter.api.Test;

class ConfigAuthorityRuntimeTest {
    @Test
    void clientWorldTransitionRequiresCurrentConnectionReady() {
        TestConnection test = connection();
        try {
            ConfigAuthorityRuntime.beginClientConfiguration(
                    test.connection());
            assertThrows(
                    ConfigAuthorityUnavailableException.class,
                    () -> ConfigAuthorityRuntime.requireReady(
                            test.connection()));

            ConfigAuthorityConnections.acceptClientSnapshot(
                    test.connection(),
                    ConfigAuthorityProtocol.snapshot(
                            1L,
                            ConfigAuthorityRemoteValuesTest.defaultValues()));
            assertDoesNotThrow(
                    () -> ConfigAuthorityRuntime.requireReady(
                            test.connection()));

            ConfigAuthorityRuntime.beginClientConfiguration(
                    test.connection());
            assertThrows(
                    ConfigAuthorityUnavailableException.class,
                    () -> ConfigAuthorityRuntime.requireReady(
                            test.connection()),
                    "same-Connection reconfiguration must demote READY "
                            + "before the next world transition");
        } finally {
            test.channel().close();
        }
    }

    @Test
    void twoRealConnectionsHaveIndependentAttributeBoundSessions() {
        TestConnection first = connection();
        TestConnection second = connection();
        try {
            ConfigAuthorityRuntime.beginClientConfiguration(first.connection());
            ConfigAuthorityRuntime.beginClientConfiguration(second.connection());
            ConfigAuthoritySnapshot firstSnapshot = ConfigAuthorityProtocol.snapshot(
                    1L, ConfigAuthorityRemoteValuesTest.defaultValues());
            ConfigAuthorityConnections.acceptClientSnapshot(first.connection(), firstSnapshot);

            assertTrue(ConfigAuthorityRuntime.isReady(first.connection()));
            assertFalse(ConfigAuthorityRuntime.isReady(second.connection()));
            assertEquals(firstSnapshot.values().zombieFoods(),
                    ConfigAuthorityRuntime.configuredZombieFoods(first.connection()));
            assertThrows(ConfigAuthorityUnavailableException.class,
                    () -> ConfigAuthorityRuntime.configuredZombieFoods(second.connection()));
        } finally {
            first.channel().close();
            second.channel().close();
        }
    }

    @Test
    void sameConnectionReconfigurationDemotesOldReadyAndRequiresNewEpochPayload() {
        TestConnection test = connection();
        try {
            ConfigAuthorityRuntime.beginClientConfiguration(test.connection());
            ConfigAuthoritySnapshot old = ConfigAuthorityProtocol.snapshot(
                    1L, ConfigAuthorityRemoteValuesTest.defaultValues());
            ConfigAuthorityConnections.acceptClientSnapshot(test.connection(), old);
            assertTrue(ConfigAuthorityRuntime.isReady(test.connection()));

            ConfigAuthorityRuntime.beginClientConfiguration(test.connection());
            assertFalse(ConfigAuthorityRuntime.isReady(test.connection()));
            assertThrows(ConfigAuthorityUnavailableException.class,
                    () -> ConfigAuthorityRuntime.spiderMountSpeed(test.connection()));
            assertThrows(ConfigAuthorityProtocolException.class,
                    () -> ConfigAuthorityConnections.acceptClientSnapshot(
                            test.connection(), old),
                    "the previous READY epoch must not become READY again after reconfiguration");
            assertFalse(ConfigAuthorityRuntime.isReady(test.connection()));

            ConfigAuthoritySnapshot current = ConfigAuthorityProtocol.snapshot(
                    2L, ConfigAuthorityRemoteValuesTest.defaultValues());
            ConfigAuthorityConnections.acceptClientSnapshot(test.connection(), current);
            assertTrue(ConfigAuthorityRuntime.isReady(test.connection()));
            assertNotEquals(old.epoch(), ConfigAuthorityConnections.require(test.connection()).epoch());
        } finally {
            test.channel().close();
        }
    }

    @Test
    void closeClearsReadyStateAndEveryAccessorFailsClosed() {
        TestConnection test = connection();
        ConfigAuthorityRuntime.beginClientConfiguration(test.connection());
        ConfigAuthorityConnections.acceptClientSnapshot(test.connection(),
                ConfigAuthorityProtocol.snapshot(1L, ConfigAuthorityRemoteValuesTest.defaultValues()));
        assertTrue(ConfigAuthorityRuntime.isReady(test.connection()));

        test.channel().close();

        assertFalse(ConfigAuthorityRuntime.isReady(test.connection()));
        assertThrows(ConfigAuthorityUnavailableException.class,
                () -> ConfigAuthorityRuntime.configuredZombieFoods(test.connection()));
        assertThrows(ConfigAuthorityUnavailableException.class,
                () -> ConfigAuthorityRuntime.resolveFoodConfig(test.connection(),
                        ZombieFoodRules.KEY_SPIDER_EYE_NIGHT_VISION_TICKS));
        assertThrows(ConfigAuthorityUnavailableException.class,
                () -> ConfigAuthorityRuntime.spiderMountSpeed(test.connection()));
    }

    @Test
    void protocolFingerprintIsMechanicallyDerivedAndNeverUsesModVersion() {
        assertEquals(64, ConfigAuthorityProtocol.schemaFingerprint().length());
        assertTrue(ConfigAuthorityProtocol.schemaFingerprint().matches("[0-9a-f]{64}"));
        assertTrue(ConfigAuthorityProtocol.negotiationVersion()
                .endsWith(ConfigAuthorityProtocol.schemaFingerprint()));
        assertFalse(ConfigAuthorityProtocol.negotiationVersion().contains("1.0.3"));
        assertFalse(ConfigAuthorityProtocol.negotiationVersion().contains("mod_version"));
    }

    @Test
    void explicitClearDemotesReadyAndRemovesTheConnectionSession() {
        TestConnection test = connection();
        try {
            ConfigAuthorityRuntime.beginClientConfiguration(test.connection());
            ConfigAuthorityConnections.acceptClientSnapshot(test.connection(),
                    ConfigAuthorityProtocol.snapshot(
                            1L, ConfigAuthorityRemoteValuesTest.defaultValues()));
            assertTrue(ConfigAuthorityRuntime.isReady(test.connection()));

            ConfigAuthorityRuntime.clear(test.connection());

            assertFalse(ConfigAuthorityRuntime.isReady(test.connection()));
            assertThrows(ConfigAuthorityUnavailableException.class,
                    () -> ConfigAuthorityRuntime.configuredZombieFoods(
                            test.connection()));
        } finally {
            test.channel().close();
        }
    }

    @Test
    void closeRacingAPendingSnapshotCanNeverPublishReadyValues() {
        TestConnection test = connection();
        ConfigAuthorityRuntime.beginClientConfiguration(test.connection());
        ConfigAuthoritySnapshot snapshot = ConfigAuthorityProtocol.snapshot(
                1L, ConfigAuthorityRemoteValuesTest.defaultValues());

        test.channel().close();

        assertThrows(ConfigAuthorityUnavailableException.class,
                () -> ConfigAuthorityConnections.acceptClientSnapshot(
                        test.connection(), snapshot));
        assertFalse(ConfigAuthorityRuntime.isReady(test.connection()));
    }

    @Test
    void refreshingOneReadyConnectionCannotAffectAnother() {
        TestConnection first = connection();
        TestConnection second = connection();
        try {
            ConfigAuthorityRemoteValues initial =
                    ConfigAuthorityRemoteValuesTest.defaultValues();
            ConfigAuthorityRemoteValues edited =
                    ConfigAuthorityRemoteValuesTest.editedValues(1);
            ConfigAuthorityRuntime.beginClientConfiguration(
                    first.connection());
            ConfigAuthorityRuntime.beginClientConfiguration(
                    second.connection());
            ConfigAuthorityConnections.acceptClientSnapshot(
                    first.connection(),
                    ConfigAuthorityProtocol.snapshot(101L, initial));
            ConfigAuthorityConnections.acceptClientSnapshot(
                    second.connection(),
                    ConfigAuthorityProtocol.snapshot(202L, initial));

            assertTrue(ConfigAuthorityConnections.refreshClientIfReady(
                    first.connection(),
                    epoch -> ConfigAuthorityProtocol.snapshot(epoch, edited)));

            assertEquals(101L,
                    ConfigAuthorityConnections.require(
                            first.connection()).epoch());
            assertEquals(edited,
                    ConfigAuthorityConnections.require(
                            first.connection()).values());
            assertEquals(202L,
                    ConfigAuthorityConnections.require(
                            second.connection()).epoch());
            assertEquals(initial,
                    ConfigAuthorityConnections.require(
                            second.connection()).values());
        } finally {
            first.channel().close();
            second.channel().close();
        }
    }

    @Test
    void clearBeforeRefreshIgnoresItWithoutReadingCanonicalValues() {
        TestConnection test = connection();
        try {
            ConfigAuthorityRuntime.beginClientConfiguration(
                    test.connection());
            ConfigAuthorityConnections.acceptClientSnapshot(
                    test.connection(),
                    ConfigAuthorityProtocol.snapshot(
                            103L,
                            ConfigAuthorityRemoteValuesTest.defaultValues()));
            ConfigAuthorityRuntime.clear(test.connection());
            AtomicInteger calls = new AtomicInteger();

            assertFalse(ConfigAuthorityConnections.refreshClientIfReady(
                    test.connection(), epoch -> {
                        calls.incrementAndGet();
                        return ConfigAuthorityProtocol.snapshot(
                                epoch,
                                ConfigAuthorityRemoteValuesTest.editedValues(1));
                    }));
            assertEquals(0, calls.get());
            assertFalse(ConfigAuthorityRuntime.isReady(test.connection()));
        } finally {
            test.channel().close();
        }
    }

    @Test
    void clearRacingARefreshCannotResurrectDetachedSession()
            throws Exception {
        TestConnection test = connection();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            ConfigAuthorityRuntime.beginClientConfiguration(
                    test.connection());
            ConfigAuthorityConnections.acceptClientSnapshot(
                    test.connection(),
                    ConfigAuthorityProtocol.snapshot(
                            104L,
                            ConfigAuthorityRemoteValuesTest.defaultValues()));
            CountDownLatch captureEntered = new CountDownLatch(1);
            CountDownLatch allowCapture = new CountDownLatch(1);
            Future<Boolean> refresh = executor.submit(
                    () -> ConfigAuthorityConnections.refreshClientIfReady(
                            test.connection(), epoch -> {
                                captureEntered.countDown();
                                try {
                                    assertTrue(allowCapture.await(
                                            5L, TimeUnit.SECONDS));
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    throw new AssertionError(interrupted);
                                }
                                return ConfigAuthorityProtocol.snapshot(
                                        epoch,
                                        ConfigAuthorityRemoteValuesTest.editedValues(1));
                            }));
            assertTrue(captureEntered.await(5L, TimeUnit.SECONDS));
            Future<?> clear = executor.submit(
                    () -> ConfigAuthorityRuntime.clear(test.connection()));

            allowCapture.countDown();
            assertTrue(refresh.get(5L, TimeUnit.SECONDS));
            clear.get(5L, TimeUnit.SECONDS);

            assertFalse(ConfigAuthorityRuntime.isReady(test.connection()));
            assertThrows(ConfigAuthorityUnavailableException.class,
                    () -> ConfigAuthorityRuntime.configuredZombieFoods(
                            test.connection()));
        } finally {
            executor.shutdownNow();
            test.channel().close();
        }
    }

    private static TestConnection connection() {
        Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        return new TestConnection(connection, channel);
    }

    private record TestConnection(Connection connection, EmbeddedChannel channel) {
    }
}
