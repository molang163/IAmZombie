package dev.molang.iamzombieq.internal.logging;

import dev.molang.iamzombieq.IAmZombieServerConfig;
import dev.molang.iamzombieq.IAmZombieMod;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.ApiStatus;

/**
 * Single gate for the {@code debugLogging} config: {@code messageSupplier} is only evaluated, and only
 * reaches {@link IAmZombieMod#LOGGER}, while the config is enabled. The config is read fresh on every call (no
 * cached flag), so toggling it at runtime takes effect immediately.
 *
 * <p>This is a best-effort, no-throw diagnostic boundary. A misbehaving config read, message build, or
 * log sink must never interrupt the gameplay state transition it is only trying to describe, so any
 * {@link RuntimeException} from any of the three steps is swallowed here (never rethrown, and never re-logged --
 * logging the failure would itself risk failing). {@link Error}s are NOT caught; a genuine VM failure still
 * propagates.</p>
 */
@ApiStatus.Internal
public final class ZombieLog {
    private ZombieLog() {
    }

    public static void debug(Supplier<String> messageSupplier) {
        debug(() -> IAmZombieServerConfig.DEBUG_LOGGING.get(), messageSupplier, IAmZombieMod.LOGGER::debug);
    }

    /** Pure seam (no Minecraft/NeoForge types) so the no-throw gating behavior is directly JUnit-testable. */
    static void debug(BooleanSupplier enabledSupplier, Supplier<String> messageSupplier, Consumer<String> sink) {
        try {
            if (enabledSupplier.getAsBoolean()) {
                sink.accept(messageSupplier.get());
            }
        } catch (RuntimeException e) {
            // Best-effort: swallowed on purpose (see class javadoc). Diagnostics must never interrupt a state
            // transition, and logging this failure would risk a recursive logging failure -- so it stays silent.
        }
    }
}
