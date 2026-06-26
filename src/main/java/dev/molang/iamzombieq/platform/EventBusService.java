package dev.molang.iamzombieq.platform;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Portability seam (PLAN D3) for posting the mod's lifecycle events. The NeoForge implementation delegates to
 * {@code NeoForge.EVENT_BUS.post(...)}; abstracting it lets new internal code post without naming the concrete
 * bus, easing a future multi-loader port.
 *
 * <p>Consumed only by NEW Phase-1 code (via {@code internal.event.ZombieEventPublisher}). Existing handlers are
 * unchanged.
 */
@ApiStatus.Internal
public interface EventBusService {

    /** Posts an observer event onto the bus. Returns the same event instance (mirrors {@code IEventBus#post}). */
    <T extends Event> T post(T event);

    /**
     * Posts a cancellable event and reports whether a listener canceled it. The event is posted via {@link #post}
     * and the result is read from {@link ICancellableEvent#isCanceled()}.
     */
    <T extends Event & ICancellableEvent> boolean postCancelable(T event);
}
