package dev.molang.iamzombieq.mixin;

import dev.molang.iamzombieq.util.MountCapability;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a mod-driven mount report its rider as the controlling passenger so vanilla's riding flow
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
 * chicken, and big-zombie (baby-zombie-player rider) mounts.
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

    /**
     * B4 defensive backstop: a mod-driven mount that is actively serving as a player's ride must never despawn
     * for distance, even if setPersistenceRequired() was somehow not applied (e.g. a mount entered the ride
     * by a path other than the interact handlers). Returns false (do not remove) only while a mod-driven
     * mount is player-ridden; every other mob keeps vanilla despawn behavior.
     *
     * <p>Target verified against the decompiled 26.2 jar: {@code Mob#removeWhenFarAway(double)} (Mob.java
     * line ~678, {@code public boolean removeWhenFarAway(double distSqr)}).
     */
    @Inject(method = "removeWhenFarAway", at = @At("HEAD"), cancellable = true)
    private void iamzombieq$keepRiddenModMount(double distSqr, CallbackInfoReturnable<Boolean> callback) {
        if (MountCapability.activeFor((Mob) (Object) this).isPresent()) {
            callback.setReturnValue(false);
        }
    }
}
