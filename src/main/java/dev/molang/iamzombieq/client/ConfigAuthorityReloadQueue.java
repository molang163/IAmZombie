package dev.molang.iamzombieq.client;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.network.Connection;

/**
 * Defers a config reload to the client executor while retaining the exact
 * connection identity that produced it.
 */
final class ConfigAuthorityReloadQueue {
    private ConfigAuthorityReloadQueue() {
    }

    static void enqueue(
            Connection captured,
            Supplier<Connection> currentConnection,
            Executor executor,
            Predicate<Connection> ready,
            Consumer<Connection> refresh) {
        Objects.requireNonNull(captured, "captured");
        Objects.requireNonNull(currentConnection, "currentConnection");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(ready, "ready");
        Objects.requireNonNull(refresh, "refresh");
        executor.execute(() -> {
            if (currentConnection.get() != captured
                    || !ready.test(captured)) {
                return;
            }
            refresh.accept(captured);
        });
    }
}
