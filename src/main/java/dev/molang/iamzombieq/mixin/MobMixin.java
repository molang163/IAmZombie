package dev.molang.iamzombieq.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.molang.iamzombieq.util.MountCapability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the two narrow vanilla hooks required by mod-driven mounts. A mount reports its rider as the
 * controlling passenger so vanilla's riding flow
 * (LivingEntity.travel -> travelRidden -> getRiddenInput/getRiddenSpeed) drives it AND the controlling
 * client sends the rider's movement input via the vanilla ServerboundMoveVehiclePacket
 * (ServerboundPlayerInputPacket is only sent for a vehicle the client is controlling). This is the
 * "controlling-client authority" technique from neoforge-26.2-rideable-mounts-impl.md (PI-4/PI-10): without
 * it the mount cannot be steered, and steering it server-only would desync on a dedicated server.
 *
 * <p>getControllingPassenger is declared in Mob (verified against the decompiled 26.2 jar:
 * Mob.java line 226, {@code public @Nullable LivingEntity getControllingPassenger()}), so the mixin targets
 * Mob. The per-mount classification (which mobs are mod-driven mounts and who validly controls them) lives in
 * {@link dev.molang.iamzombieq.util.MountCapability}; this mixin just delegates to it, so all other mobs are
 * unaffected (the inject returns without setting a value). The registry covers the spider (tamed-owner ride),
 * chicken, and big-zombie (baby-zombie-player rider) mounts. The second hook changes only the source delivered
 * by a validly ridden big zombie to its victim: the direct attacker remains the mount while the rider receives
 * player-kill attribution. The rest of {@code Mob#doHurtTarget} continues with its original source.
 */
@Mixin(Mob.class)
abstract class MobMixin {
    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void iamzombieq$modMountRider(CallbackInfoReturnable<LivingEntity> callback) {
        Player rider = MountCapability.activeRider((Mob) (Object) this);
        if (rider != null) {
            callback.setReturnValue(rider);
        }
    }

    @WrapOperation(
            method = "doHurtTarget(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean iamzombieq$creditBigZombieKillToRider(
            Entity target,
            ServerLevel level,
            DamageSource originalSource,
            float amount,
            Operation<Boolean> original) {
        Mob mount = (Mob) (Object) this;
        if (MountCapability.activeFor(mount).orElse(null) != MountCapability.BIG_ZOMBIE
                || !(mount.getFirstPassenger() instanceof Player rider)) {
            return original.call(target, level, originalSource, amount);
        }

        DamageSource attributedSource = new DamageSource(
                originalSource.typeHolder(), mount, rider, originalSource.sourcePositionRaw());
        float attributedAmount = amount;
        if (target instanceof Player targetPlayer
                && originalSource.type().scaling() == DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER) {
            attributedAmount = originalSource.type().scaling().getScalingFunction()
                    .scaleDamage(originalSource, targetPlayer, amount, level.getDifficulty());
        }
        return original.call(target, level, attributedSource, attributedAmount);
    }
}
