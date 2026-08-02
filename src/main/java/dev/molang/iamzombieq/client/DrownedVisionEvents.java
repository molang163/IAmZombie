package dev.molang.iamzombieq.client;

import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import dev.molang.iamzombieq.state.PlayerZombieData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.material.FogType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client-only handler for drowned wet-state clear vision.
 *
 * <p>When the LOCAL player is in the drowned zombie form and is underwater (or otherwise wet),
 * the murky water fog is pushed far out so the underwater view becomes "通透" (clear).</p>
 *
 * <p>This class references {@code net.minecraft.client.*} types and MUST only be loaded on the
 * physical client. It is registered from {@link IAmZombieClient#register}, which is itself only
 * invoked when {@code FMLEnvironment.getDist() == Dist.CLIENT} (see {@code IAmZombieMod}). The
 * RenderFog event is also fired only on the logical client. Following the rest of this package, it
 * relies on that client-gated registration rather than {@code @OnlyIn} member-stripping.</p>
 */
public final class DrownedVisionEvents {
    /**
     * Default-on toggle. The client config spec ({@code IAmZombieClientConfig}) is owned by a
     * different scope, so this rendering-only behavior is kept as a code constant. It does not
     * depend on synchronized gameplay configuration and is evaluated only by this client-only
     * event handler.
     */
    private static final boolean CLEAR_DROWNED_WATER_FOG = true;

    /**
     * Far-plane distance (in blocks) used while the drowned player is wet. Pushing the fog far
     * out effectively removes the murky underwater haze while staying within the player's render
     * distance, giving a clear view.
     */
    private static final float CLEAR_FAR_PLANE = 1024.0F;

    private DrownedVisionEvents() {
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!CLEAR_DROWNED_WATER_FOG) {
            return;
        }

        // Only touch underwater (water) fog; lava/powder-snow/atmospheric fog stays vanilla.
        if (event.getType() != FogType.WATER) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
        if (data.state().form() != ZombieForm.DROWNED) {
            return;
        }

        // Drowned form sees clearly while wet: touching water OR in rain.
        if (!player.isInWaterOrRain()) {
            return;
        }

        // Clear the murky water fog: start it at the camera and push the end far out.
        event.setNearPlaneDistance(0.0F);
        event.setFarPlaneDistance(CLEAR_FAR_PLANE);
    }
}
