package dev.molang.iamzombieq.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.molang.iamzombieq.client.ZombiePlayerRenderReplacement;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void iamzombieq$submitZombieShape(
            AvatarRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            CallbackInfo callback
    ) {
        ZombiePlayerRenderReplacement replacement = ZombiePlayerRenderReplacement.get(state);
        if (replacement == null || state.isSpectator || state.isInvisible || state.isInvisibleToPlayer) {
            return;
        }

        // The inventory/creative GUI overrides the AVATAR state's rotation to face front + follow the mouse
        // (InventoryScreen.renderEntityInInventoryFollowsAngle, AFTER extractRenderState). Our render-state modifier
        // already ran during that extraction, so the shape state still carries the player's WORLD rotation. submit
        // runs AFTER the override, so copy the avatar's FINAL rotation onto the shape here: frontal in the inventory,
        // the player's real direction in-world (no 3rd-person regression). 26.2 has no separate head-yaw field —
        // bodyRot + yRot + xRot fully define body+head orientation.
        if (replacement.renderState() instanceof LivingEntityRenderState livingShape) {
            livingShape.bodyRot = state.bodyRot;
            livingShape.yRot = state.yRot;
            livingShape.xRot = state.xRot;
        }
        replacement.renderer().submit(replacement.renderState(), poseStack, collector, camera);
        state.shadowRadius = replacement.renderState().shadowRadius;
        callback.cancel();
    }
}
