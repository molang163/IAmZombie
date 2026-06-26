package dev.molang.iamzombieq.api.event;

import dev.molang.iamzombieq.rules.DeathOutcome;
import dev.molang.iamzombieq.rules.core.ZombieState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Cancellable event fired BEFORE a death-driven evolution ("向死而生") is applied (design §5.a), e.g. inside
 * {@code IZombiePlayer.evolveFromDeath}. Cancel it to veto the evolution.
 *
 * <p>Posted on the native {@code NeoForge.EVENT_BUS}; subscribe with {@code @SubscribeEvent}. The before/after
 * {@link ZombieState}s and the {@link DeathOutcome} are immutable snapshots.
 *
 * <p>Part of the STABLE public API surface (semver 1.x).
 */
public final class ZombieEvolvePreEvent extends Event implements ICancellableEvent {

    private final ServerPlayer player;
    private final ZombieState before;
    private final ZombieState after;
    private final DeathOutcome outcome;

    public ZombieEvolvePreEvent(@NotNull ServerPlayer player, @NotNull ZombieState before,
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
