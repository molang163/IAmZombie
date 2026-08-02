package dev.molang.iamzombieq.api.event;

import dev.molang.iamzombieq.rules.core.ZombieForm;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Cancellable event fired BEFORE a player's form is actively changed (design §5.a). Producers include
 * {@code IZombiePlayer.transformToForm}, the built-in giant-kill transform, and a real-death clone reset when the
 * player's form actually changes. Cancel it to veto the transform.
 *
 * <p>Posted on the native {@code NeoForge.EVENT_BUS}; subscribe with {@code @SubscribeEvent}. Fields are an
 * immutable snapshot of the transform's {@code from}/{@code to} forms.
 *
 * <p>For a giant kill, cancellation affects only the player's transform: the giant continues its real death, and
 * the handler performs no attachment write, Transform Post, forced attribute refresh, or healing.
 *
 * <p>For a death clone, {@link #player()} is the fresh respawn holder and may not yet have the player-zombie
 * attachment. The event's {@code from/to are the authoritative snapshot}; reading that attachment in the callback
 * can materialize a default value and cause an addon-induced sync. If canceled, the real death and respawn remain
 * in force; the built-in handler writes the complete previous state, size, and reward flags to the fresh holder
 * once, clears its transient sun-fire window, refreshes the retained attributes and passive abilities, and does
 * not fire Transform Post. A {@code NORMAL/BABY -> NORMAL/ADULT} size-only reset does not fire Transform Pre or
 * Post.
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
