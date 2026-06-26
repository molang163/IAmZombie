package dev.molang.iamzombieq.platform;

import net.neoforged.neoforge.attachment.AttachmentType;
import org.jetbrains.annotations.ApiStatus;

/**
 * Portability seam (PLAN D3) for reading, writing, and network-syncing a player {@link AttachmentType}. The
 * NeoForge implementation simply delegates to {@code entity.getData/setData/syncData}; abstracting it here lets
 * the internal facade ({@code internal.core.ServerZombiePlayer}) stay loader-agnostic so a future multi-loader
 * port only swaps the implementation, not the facade.
 *
 * <p>This interface is consumed only by NEW Phase-1 code. The existing gameplay handlers still call the raw
 * attachment API directly and are not migrated in Phase-1.
 */
@ApiStatus.Internal
public interface AttachmentService {

    /**
     * The current value of {@code type} on {@code holder}, materializing the attachment default if absent
     * (mirrors {@code IAttachmentHolder#getData}).
     */
    <T> T get(Object holder, AttachmentType<T> type);

    /** Writes {@code value} for {@code type} on {@code holder} (mirrors {@code IAttachmentHolder#setData}). */
    <T> void set(Object holder, AttachmentType<T> type, T value);

    /**
     * Pushes the current value of {@code type} to the owning client (mirrors {@code Entity#syncData}). This is a
     * no-op for a connectionless player (e.g. a FakePlayer), which is the FakePlayer-safety guarantee Phase-1
     * relies on.
     */
    void sync(Object holder, AttachmentType<?> type);
}
