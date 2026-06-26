package dev.molang.iamzombieq.api.event;

import dev.molang.iamzombieq.rules.core.ZombieForm;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.NotNull;

/**
 * Observer event fired AFTER a player's form has been changed and synced (design §5.a). Not cancellable; treat
 * its fields as a read-only snapshot (the authoritative change has already been applied and synced to the client).
 *
 * <p>Posted on the native {@code NeoForge.EVENT_BUS}; subscribe with {@code @SubscribeEvent}.
 *
 * <p>Part of the STABLE public API surface (semver 1.x).
 */
public final class ZombieTransformedEvent extends Event {

    private final ServerPlayer player;
    private final ZombieForm from;
    private final ZombieForm to;

    public ZombieTransformedEvent(@NotNull ServerPlayer player, @NotNull ZombieForm from, @NotNull ZombieForm to) {
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
