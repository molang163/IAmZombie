package dev.molang.iamzombieq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.junit.jupiter.api.Test;

class ConfigAuthorityConfigurationTaskTest {
    @Test
    void taskSendsExactlyTheCurrentSnapshotAndDoesNotSelfFinish() {
        ConfigAuthoritySnapshot snapshot = ConfigAuthorityProtocol.snapshot(
                81L, ConfigAuthorityRemoteValuesTest.defaultValues());
        ConfigAuthorityConfigurationTask task =
                new ConfigAuthorityConfigurationTask(snapshot, () -> 0L);
        List<CustomPacketPayload> sent = new ArrayList<>();

        task.run(sent::add);

        assertEquals(ConfigAuthorityConfigurationTask.TYPE, task.type());
        assertEquals(List.of(snapshot), sent);
        assertFalse(task.tick(),
                "only the exact current ACK handler may finish this task");
    }

    @Test
    void deadlineIsNotArmedUntilRunActuallyStartsTheCurrentTask() {
        ConfigAuthoritySnapshot snapshot = ConfigAuthorityProtocol.snapshot(
                82L, ConfigAuthorityRemoteValuesTest.defaultValues());
        AtomicInteger clockReads = new AtomicInteger();
        ConfigAuthorityConfigurationTask task =
                new ConfigAuthorityConfigurationTask(
                        snapshot,
                        () -> {
                            clockReads.incrementAndGet();
                            return Long.MAX_VALUE;
                        });

        assertFalse(task.tick());
        assertFalse(task.tick());
        assertEquals(0, clockReads.get(),
                "an unstarted queued task has no acknowledgement deadline");
    }

    @Test
    void missingAcknowledgementFailsAtTheExactVanillaLatencyBoundary() {
        ConfigAuthoritySnapshot snapshot = ConfigAuthorityProtocol.snapshot(
                83L, ConfigAuthorityRemoteValuesTest.defaultValues());
        AtomicLong now = new AtomicLong(10_000L);
        ConfigAuthorityConfigurationTask task =
                new ConfigAuthorityConfigurationTask(snapshot, now::get);

        task.run(ignored -> {});
        assertFalse(task.tick());

        now.set(10_000L
                + ConfigAuthorityConfigurationTask.ACK_DEADLINE_MILLIS
                - 1L);
        assertFalse(task.tick());

        now.incrementAndGet();
        assertThrows(
                ConfigAuthorityProtocolException.class,
                task::tick,
                "missing payload/ACK must disconnect instead of stalling "
                        + "configuration forever");
        assertEquals(
                ServerCommonPacketListenerImpl.LATENCY_CHECK_INTERVAL,
                ConfigAuthorityConfigurationTask.ACK_DEADLINE_MILLIS,
                "the authority deadline must reuse vanilla's latency interval");
    }

    @Test
    void aBackwardsDeadlineClockFailsClosed() {
        ConfigAuthoritySnapshot snapshot = ConfigAuthorityProtocol.snapshot(
                84L, ConfigAuthorityRemoteValuesTest.defaultValues());
        AtomicLong now = new AtomicLong(10_000L);
        ConfigAuthorityConfigurationTask task =
                new ConfigAuthorityConfigurationTask(snapshot, now::get);

        task.run(ignored -> {});
        now.set(9_999L);

        ConfigAuthorityProtocolException failure = assertThrows(
                ConfigAuthorityProtocolException.class,
                task::tick);
        assertEquals(
                "Configuration authority acknowledgement clock moved backwards",
                failure.getMessage());
    }
}
