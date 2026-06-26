package dev.molang.iamzombieq.platform.neoforge;

import dev.molang.iamzombieq.platform.EventBusService;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.ApiStatus;

/**
 * NeoForge implementation of {@link EventBusService}: posts onto the native {@code NeoForge.EVENT_BUS} (design
 * §2.3 / A1 — the mod uses a single, native bus rather than a private one). {@code NeoForge.EVENT_BUS} is touched
 * only inside these methods at post time, never at class-init, so constructing this service is side-effect-free.
 */
@ApiStatus.Internal
public final class NeoForgeEventBusService implements EventBusService {

    @Override
    public <T extends Event> T post(T event) {
        return NeoForge.EVENT_BUS.post(event);
    }

    @Override
    public <T extends Event & ICancellableEvent> boolean postCancelable(T event) {
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }
}
