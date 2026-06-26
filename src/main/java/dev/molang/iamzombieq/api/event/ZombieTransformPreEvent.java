package dev.molang.iamzombieq.api.event;

import dev.molang.iamzombieq.rules.core.ZombieForm;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Cancellable event fired BEFORE a player's form is actively changed (design §5.a), e.g. inside
 * {@code IZombiePlayer.transformToForm}. Cancel it to veto the transform.
 *
 * <p>Posted on the native {@code NeoForge.EVENT_BUS}; subscribe with {@code @SubscribeEvent}. Fields are an
 * immutable snapshot of the transform's {@code from}/{@code to} forms.
 *
 * <p>Part of the STABLE public API surface (semver 1.x).
 */
public final class ZombieTransformPreEvent extends Event implements ICancellableEvent {

    private final ServerPlayer player;
    private final ZombieForm from;
    private final ZombieForm to;

    public ZombieTransformPreEvent(@NotNull ServerPlayer player, @NotNull ZombieForm from, @NotNull ZombieForm to) {
        this.player = player;
        this.from = from;
        this.to = to;
    }

    @NotNull
    public ServerPlayer player() {
        return player;
    }

    @NotNull
    public ZombieForm from() {
        return from;
    }

    @NotNull
    public ZombieForm to() {
        return to;
    }
}
