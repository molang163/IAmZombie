package dev.molang.iamzombieq.client;
import dev.molang.iamzombieq.util.ModIds;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.extensions.IRenderStateExtension;

public record ZombiePlayerRenderReplacement(
        EntityRenderer<LivingEntity, EntityRenderState> renderer,
        EntityRenderState renderState
) {
    public static final ContextKey<ZombiePlayerRenderReplacement> KEY = new ContextKey<>(
            ModIds.id("zombie_player_render_replacement")
    );

    /**
     * Marker on a zombie-player SHAPE render state telling the LivingEntityRenderer swim mixin to apply the
     * player-style swim tilt (vanilla mob renderers don't). Safe because createRenderState returns a fresh state
     * per call, so this only ever lands on shape states, never on real mobs.
     */
    public static final ContextKey<Boolean> SHAPE_SWIM_TILT = new ContextKey<>(
            ModIds.id("shape_swim_tilt")
    );

    public static void set(AvatarRenderState state, ZombiePlayerRenderReplacement replacement) {
        ((IRenderStateExtension) state).setRenderData(KEY, replacement);
    }

    public static ZombiePlayerRenderReplacement get(AvatarRenderState state) {
        return ((IRenderStateExtension) state).getRenderData(KEY);
    }

    public static void copyAvatarAnimation(AvatarRenderState avatar, EntityRenderState shape) {
        if (shape instanceof LivingEntityRenderState livingShape) {
            livingShape.pose = avatar.pose;
            livingShape.walkAnimationPos = avatar.walkAnimationPos;
            livingShape.walkAnimationSpeed = avatar.walkAnimationSpeed;
            livingShape.bodyRot = avatar.bodyRot;
            livingShape.yRot = avatar.yRot;
            livingShape.xRot = avatar.xRot;
            livingShape.wornHeadAnimationPos = avatar.wornHeadAnimationPos;
            livingShape.isInWater = avatar.isInWater;
            // A baby shape already renders small via its baby model geometry; don't also apply the player's
            // baby SCALE shrink or it renders ~quarter size. (Baby + GIANT cannot co-occur.)
            livingShape.scale = livingShape.isBaby ? 1.0F : avatar.scale;
            livingShape.isFullyFrozen = avatar.isFullyFrozen;
            livingShape.isAutoSpinAttack = avatar.isAutoSpinAttack;
            livingShape.deathTime = avatar.deathTime;
        }

        if (shape instanceof ArmedEntityRenderState armedShape) {
            armedShape.mainArm = avatar.mainArm;
        }

        if (shape instanceof HumanoidRenderState humanoidShape) {
            humanoidShape.swimAmount = avatar.swimAmount;
            humanoidShape.isVisuallySwimming = avatar.isVisuallySwimming;
            humanoidShape.attackArm = avatar.attackArm;
            humanoidShape.attackTime = avatar.attackTime;
            humanoidShape.speedValue = avatar.speedValue;
            humanoidShape.isCrouching = avatar.isCrouching;
            humanoidShape.isPassenger = avatar.isPassenger;
            humanoidShape.isFallFlying = avatar.isFallFlying;
            humanoidShape.ticksUsingItem = avatar.ticksUsingItem;
            humanoidShape.isUsingItem = avatar.isUsingItem;
            // Tag this shape state so the LivingEntityRenderer swim mixin applies the player-style swim/crawl
            // tilt (vanilla mob renderers never tilt for swimAmount/isVisuallySwimming). Only humanoid states
            // carry those swim fields, so the marker only belongs on them.
            ((IRenderStateExtension) humanoidShape).setRenderData(SHAPE_SWIM_TILT, Boolean.TRUE);
        }
    }
}
