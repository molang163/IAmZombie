package dev.molang.iamzombieq.api.core;

import dev.molang.iamzombieq.internal.core.ServerZombiePlayer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Public entry point to the zombie-player facade (design §4.2). Call {@link #get(ServerPlayer)} on the server
 * thread to obtain the server-authoritative {@link IZombiePlayer} view of a player.
 *
 * <p><b>Server-thread-only.</b> The returned facade is FakePlayer-safe: a FakePlayer is a {@code ServerPlayer},
 * so it is an accepted argument, and the facade never reads the player's connection.
 *
 * <p>Part of the STABLE public API surface (semver 1.x).
 */
public final class IZombiePlayerAPI {

    private IZombiePlayerAPI() {
    }

    /**
     * The server-authoritative facade for {@code player}. Server-thread-only; safe to call with a FakePlayer.
     */
    @NotNull
    public static IZombiePlayer get(@NotNull ServerPlayer player) {
        return ServerZombiePlayer.of(player);
    }
}
