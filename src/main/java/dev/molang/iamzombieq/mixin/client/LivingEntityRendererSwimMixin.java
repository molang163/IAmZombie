package dev.molang.iamzombieq.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.molang.iamzombieq.client.ZombiePlayerRenderReplacement;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.extensions.IRenderStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla mob renderers (unlike the player's AvatarRenderer) apply no swimming/crawl tilt. The third-person
 * zombie-player "shape" is drawn by a vanilla mob renderer, so without this it stays upright while the player
 * swims. This re-adds the swim tilt — but ONLY for shape render states (tagged by ZombiePlayerRenderReplacement
 * .SHAPE_SWIM_TILT); real mobs get a fresh, untagged render state and are unaffected. Mirrors the swimming branch
 * of AvatarRenderer.setupRotations.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererSwimMixin {
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void iamzombieq$shapeSwimTilt(
            LivingEntityRenderState state,
            PoseStack poseStack,
            float bodyRot,
            float entityScale,
            CallbackInfo callback
    ) {
        if (!(state instanceof HumanoidRenderState humanoid)
                || !Boolean.TRUE.equals(((IRenderStateExtension) state).getRenderData(ZombiePlayerRenderReplacement.SHAPE_SWIM_TILT))) {
            return;
        }
        // death / riptide poses are mutually exclusive with the swim lean and are already applied by the base.
        if (humanoid.swimAmount <= 0.0F || humanoid.deathTime > 0.0F || humanoid.isAutoSpinAttack) {
            return;
        }
        float targetXRot = humanoid.isInWater ? -90.0F - humanoid.xRot : -90.0F;
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(humanoid.swimAmount, 0.0F, targetXRot)));
        if (humanoid.isVisuallySwimming) {
            poseStack.translate(0.0F, -1.0F, 0.3F);
        }
    }
}
