package dev.molang.iamzombieq.platform.neoforge;

import dev.molang.iamzombieq.platform.AttachmentService;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jetbrains.annotations.ApiStatus;

/**
 * NeoForge implementation of {@link AttachmentService}: delegates to the standard data-attachment API
 * ({@code IAttachmentHolder#getData/setData} and {@code Entity#syncData}). This is the same mechanism the
 * existing gameplay handlers use directly; the seam only relocates the call.
 *
 * <p>{@link #sync} is a no-op for a connectionless player (e.g. a FakePlayer), so the facade is FakePlayer-safe
 * by construction.
 */
@ApiStatus.Internal
public final class NeoForgeAttachmentService implements AttachmentService {

    @Override
    public <T> T get(Object holder, AttachmentType<T> type) {
        return ((IAttachmentHolder) holder).getData(type);
    }

    @Override
    public <T> void set(Object holder, AttachmentType<T> type, T value) {
        ((IAttachmentHolder) holder).setData(type, value);
    }

    @Override
    public void sync(Object holder, AttachmentType<?> type) {
        ((Entity) holder).syncData(type);
    }
}
