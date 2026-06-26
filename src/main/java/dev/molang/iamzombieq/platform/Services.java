package dev.molang.iamzombieq.platform;

import dev.molang.iamzombieq.platform.neoforge.NeoForgeAttachmentService;
import dev.molang.iamzombieq.platform.neoforge.NeoForgeEventBusService;
import org.jetbrains.annotations.ApiStatus;

/**
 * Static holder for the platform services used by the new internal facade (PLAN D3 / A5). The implementations are
 * the NeoForge ones, constructed with a plain {@code new} — this is a pure object allocation with NO NeoForge
 * runtime call at class-init (A5: {@code Services} may statically construct the NeoForge impls). A future
 * multi-loader port would resolve these via {@code ServiceLoader} instead; for now they are hardcoded.
 *
 * <p>NOTE: there is intentionally no static initializer here and no global mutation — only two final field
 * initializers that allocate the (side-effect-free) NeoForge service objects.
 */
@ApiStatus.Internal
public final class Services {

    public static final AttachmentService ATTACHMENTS = new NeoForgeAttachmentService();
    public static final EventBusService EVENTS = new NeoForgeEventBusService();

    private Services() {
    }
}
