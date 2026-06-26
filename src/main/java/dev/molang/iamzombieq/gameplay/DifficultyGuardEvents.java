package dev.molang.iamzombieq.gameplay;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Applies the startup half of the Peaceful guard: when the server has started (player list ready, no players yet),
 * correct a world that was saved on Peaceful to the playable fallback. The runtime half — coercing every later
 * difficulty change — lives in {@code MinecraftServerMixin}. See {@link PeacefulGuard}.
 */
public final class DifficultyGuardEvents {
    private DifficultyGuardEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        PeacefulGuard.enforce(event.getServer());
    }
}
