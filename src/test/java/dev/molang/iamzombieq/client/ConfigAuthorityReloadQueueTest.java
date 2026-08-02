package dev.molang.iamzombieq.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.junit.jupiter.api.Test;

class ConfigAuthorityReloadQueueTest {
    @Test
    void delayedServerAReloadCannotRefreshCurrentServerB() {
        Connection serverA = connection();
        Connection serverB = connection();
        AtomicReference<Connection> current =
                new AtomicReference<>(serverA);
        Queue<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger readyChecks = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();

        ConfigAuthorityReloadQueue.enqueue(
                serverA,
                current::get,
                tasks::add,
                connection -> {
                    readyChecks.incrementAndGet();
                    return true;
                },
                connection -> refreshes.incrementAndGet());
        current.set(serverB);
        tasks.remove().run();

        assertEquals(0, readyChecks.get(),
                "identity rejection must happen before the READY gate");
        assertEquals(0, refreshes.get());
    }

    @Test
    void nonReadyOrLoggedOutConnectionCannotPublishRefresh() {
        Connection connection = connection();
        AtomicReference<Connection> current =
                new AtomicReference<>(connection);
        Queue<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger refreshes = new AtomicInteger();

        ConfigAuthorityReloadQueue.enqueue(
                connection,
                current::get,
                tasks::add,
                ignored -> false,
                ignored -> refreshes.incrementAndGet());
        tasks.remove().run();

        assertEquals(0, refreshes.get());
    }

    @Test
    void rapidQueuedReloadsReadAtExecutionAndFinishWithLatestState() {
        Connection connection = connection();
        AtomicReference<Connection> current =
                new AtomicReference<>(connection);
        AtomicInteger canonicalGeneration = new AtomicInteger(1);
        AtomicInteger publishedGeneration = new AtomicInteger();
        Queue<Runnable> tasks = new ArrayDeque<>();

        for (int ignored = 0; ignored < 2; ignored++) {
            ConfigAuthorityReloadQueue.enqueue(
                    connection,
                    current::get,
                    tasks::add,
                    candidate -> true,
                    candidate -> publishedGeneration.set(
                            canonicalGeneration.get()));
        }
        canonicalGeneration.set(2);
        tasks.remove().run();
        tasks.remove().run();

        assertEquals(2, publishedGeneration.get(),
                "queued reloads must capture canonical values at execution, "
                        + "not preserve an earlier event-time snapshot");
    }

    private static Connection connection() {
        return new Connection(PacketFlow.CLIENTBOUND);
    }
}
