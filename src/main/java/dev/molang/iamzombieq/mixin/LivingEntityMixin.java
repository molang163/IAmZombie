package dev.molang.iamzombieq.mixin;

import java.util.Optional;

import dev.molang.iamzombieq.IAmZombieConfig;
import dev.molang.iamzombieq.rules.mount.MountKind;
import dev.molang.iamzombieq.rules.mount.ZombieMountRules;
import dev.molang.iamzombieq.rules.ZombiePotionRules;
import dev.molang.iamzombieq.util.MountCapability;
import dev.molang.iamzombieq.util.RideHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * getRiddenInput/getRiddenSpeed are declared in LivingEntity (verified against the decompiled 26.2 jar:
 * LivingEntity.java getRiddenInput line ~2759, getRiddenSpeed line ~2763, both invoked from the private
 * travelRidden at line ~2745). LivingEntity#travelRidden only runs when getControllingPassenger() is a
 * Player (see MobMixin), so these injects scope the mod-driven mounts' WASD steering. This is the
 * controlling-client authority flow: the rider's input drives the mount's normal travel() locally and is
 * reported to the server via the vanilla vehicle-move packet -- NO server-only setDeltaMovement.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Inject(method = "isInvertedHealAndHarm", at = @At("HEAD"), cancellable = true)
    private void iamzombieq$invertZombiePlayerHealAndHarm(CallbackInfoReturnable<Boolean> callback) {
        Object self = this;
        if (self instanceof Player player && ZombiePotionRules.shouldInvertHealAndHarm(true, player.isCreative(), player.isSpectator())) {
            callback.setReturnValue(true);
        }
    }

    // Mod-mount steering: drive the mount from the rider's movement input (the default getRiddenInput returns
    // the mount's own zero input). RideHelper.riddenInput mirrors AbstractHorse.getRiddenInput (strafe x0.5,
    // backward x0.25), shared by all mod-driven mounts via the MountCapability registry.
    @Inject(method = "getRiddenInput", at = @At("HEAD"), cancellable = true)
    private void iamzombieq$modMountRiddenInput(Player controller, Vec3 selfInput, CallbackInfoReturnable<Vec3> callback) {
        if (iamzombieq$controlledCapability(controller).isPresent()) {
            callback.setReturnValue(RideHelper.riddenInput(controller));
        }
    }

    @Inject(method = "getRiddenSpeed", at = @At("HEAD"), cancellable = true)
    private void iamzombieq$modMountRiddenSpeed(Player controller, CallbackInfoReturnable<Float> callback) {
        // B3: spider/chicken/big-zombie ridden speed is resolved by the MountCapability (spider keeps its
        // config override; the others use the ZombieMountRules table).
        iamzombieq$controlledCapability(controller).ifPresent(capability -> callback.setReturnValue(
                capability.riddenSpeed(ZombieMountRules.spiderRiddenSpeed(IAmZombieConfig.SPIDER_MOUNT_SPEED.get().floatValue()))));
    }

    // Mount faces where the rider looks. LivingEntity#tickRidden (line ~2756) is the empty default; injecting
    // here applies the AbstractHorse-style rotation (RideHelper.tickRiddenRotation) for every mod-driven mount.
    // Runs only for the controlling Player, so it stays dedicated-server-correct (the controlling client owns
    // the rotation it reports).
    @Inject(method = "tickRidden", at = @At("TAIL"))
    private void iamzombieq$modMountTickRiddenRotation(Player controller, Vec3 riddenInput, CallbackInfo callback) {
        Object self = this;
        if (self instanceof Mob mob) {
            iamzombieq$controlledCapability(controller).ifPresent(capability -> {
                RideHelper.tickRiddenRotation(mob, controller);
                // Forward the rider's jump intent so the mount performs its NATIVE vanilla jump. Scoped to the
                // chicken / big-zombie kinds: the SPIDER keeps its own climb-based vertical movement untouched,
                // and horses are never driven by this capability.
                if (capability.mountKind() != MountKind.SPIDER) {
                    RideHelper.tickRiddenJump(mob, controller);
                }
            });
        }
    }

    // The mod-driven mount capability for which {@code controller} is the valid controller of this entity, if
    // any. All mod-driven mounts are Mobs, so non-Mob LivingEntities (e.g. players) never match.
    private Optional<MountCapability> iamzombieq$controlledCapability(Player controller) {
        Object self = this;
        return self instanceof Mob mob ? MountCapability.controlledCapability(mob, controller) : Optional.empty();
    }
}
