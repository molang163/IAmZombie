package dev.molang.iamzombieq.mixin.client;

import dev.molang.iamzombieq.rules.mount.MountKind;
import dev.molang.iamzombieq.rules.mount.ZombieMountRules;
import dev.molang.iamzombieq.util.MountCapability;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Resolves the physical client's spider simulation speed only from the READY
 * authority payload. The common mixin handles the logical server and ignores
 * this method on client levels.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityAuthorityMixin {
    private static final MethodHandle SPIDER_MOUNT_SPEED =
            iamzombieq$authorityHandle();

    @Inject(method = "getRiddenSpeed", at = @At("HEAD"), cancellable = true)
    private void iamzombieq$authorityRiddenSpeed(
            Player controller, CallbackInfoReturnable<Float> callback) {
        Object self = this;
        if (!(self instanceof Mob mob)) {
            return;
        }
        MountCapability.controlledCapability(mob, controller)
                .ifPresent(capability -> {
                    float spiderSpeed = ZombieMountRules.DEFAULT_SPIDER_MOUNT_SPEED;
                    if (capability.mountKind() == MountKind.SPIDER) {
                        ClientPacketListener listener =
                                Minecraft.getInstance().getConnection();
                        if (listener == null) {
                            throw new IllegalStateException(
                                    "Spider SERVER authority is unavailable without a connection");
                        }
                        try {
                            spiderSpeed = (float) SPIDER_MOUNT_SPEED.invokeExact(
                                    listener.getConnection());
                        } catch (Throwable failure) {
                            throw iamzombieq$failClosed(failure);
                        }
                    }
                    callback.setReturnValue(capability.riddenSpeed(
                            ZombieMountRules.spiderRiddenSpeed(spiderSpeed)));
                });
    }

    private static MethodHandle iamzombieq$authorityHandle() {
        try {
            Class<?> runtime = Class.forName(
                    "dev.molang.iamzombieq.config.ConfigAuthorityRuntime",
                    true,
                    LivingEntityAuthorityMixin.class.getClassLoader());
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    runtime, MethodHandles.lookup());
            return lookup.findStatic(
                    runtime,
                    "spiderMountSpeed",
                    MethodType.methodType(float.class, Connection.class));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static RuntimeException iamzombieq$failClosed(
            Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(
                "Configuration authority runtime invocation failed",
                failure);
    }
}
