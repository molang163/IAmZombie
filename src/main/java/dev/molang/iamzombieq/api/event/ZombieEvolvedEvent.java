package dev.molang.iamzombieq.api.event;

import dev.molang.iamzombieq.rules.DeathOutcome;
import dev.molang.iamzombieq.rules.core.ZombieState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.NotNull;

/**
 * Observer event fired AFTER a death-driven evolution has been applied and synced (design §5.a). Not cancellable;
 * treat its before/after {@link ZombieState}s and {@link DeathOutcome} as a read-only snapshot.
 *
 * <p>Posted on the native {@code NeoForge.EVENT_BUS}; subscribe with {@code @SubscribeEvent}.
 *
 * <p>Part of the STABLE public API surface (semver 1.x).
 */
public final class ZombieEvolvedEvent extends Event {

    private final ServerPlayer player;
    private final ZombieState before;
    private final ZombieState after;
    private final DeathOutcome outcome;

    public ZombieEvolvedEvent(@NotNull ServerPlayer player, @NotNull ZombieState before,
            @NotNull ZombieState after, @NotNull DeathOutcome outcome) {
        this.player = player;
        this.before = before;
        this.after = after;
        this.outcome = outcome;
    }

    @NotNull
    public ServerPlayer player() {
        return player;
    }

    @NotNull
    public ZombieState before() {
        return before;
    }

    @NotNull
    public ZombieState after() {
        return after;
    }

    @NotNull
    public DeathOutcome outcome() {
        return outcome;
    }
}
