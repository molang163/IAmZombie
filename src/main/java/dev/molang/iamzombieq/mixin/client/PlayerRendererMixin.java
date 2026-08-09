package dev.molang.iamzombieq.mixin.client;

//? if <1.21.10 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import dev.molang.iamzombieq.client.ZombiePlayerRenderReplacement;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
abstract class PlayerRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void iamzombieq$renderZombieShape(
            PlayerRenderState state,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo callback
    ) {
        ZombiePlayerRenderReplacement replacement = ZombiePlayerRenderReplacement.get(state);
        if (replacement == null || state.isSpectator || state.isInvisible || state.isInvisibleToPlayer) {
            return;
        }

        if (replacement.renderState() instanceof LivingEntityRenderState livingShape) {
            livingShape.bodyRot = state.bodyRot;
            livingShape.yRot = state.yRot;
            livingShape.xRot = state.xRot;
        }
        replacement.renderer().render(
                replacement.renderState(), poseStack, bufferSource, packedLight);
        callback.cancel();
    }
}
*///?}
