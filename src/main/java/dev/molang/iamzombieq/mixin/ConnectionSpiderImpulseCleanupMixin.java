package dev.molang.iamzombieq.mixin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears a relay marker only for a Connection that actually participated in
 * spider authority. Never-participating connections return before resolving
 * the package-private relay bridge.
 */
@Mixin(Connection.class)
abstract class ConnectionSpiderImpulseCleanupMixin {
    @Unique private boolean iamzombieq$spiderAuthorityParticipated;
    @Unique private static volatile MethodHandle iamzombieq$clearImpulse;

    @Unique
    private void iamzombieq$markSpiderAuthorityParticipation() {
        iamzombieq$spiderAuthorityParticipated = true;
    }

    @Inject(method = "handleDisconnection()V", at = @At("HEAD"), require = 1)
    private void iamzombieq$clearVehicleImpulseOnDisconnect(
            CallbackInfo callback) {
        if (!iamzombieq$spiderAuthorityParticipated) {
            return;
        }
        iamzombieq$spiderAuthorityParticipated = false;
        try {
            iamzombieq$clearImpulseHandle()
                    .invokeExact((Connection) (Object) this);
        } catch (Throwable failure) {
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(
                    "Spider impulse bridge failed to clear on disconnect",
                    failure);
        }
    }

    @Unique
    private static MethodHandle iamzombieq$clearImpulseHandle() {
        MethodHandle handle = iamzombieq$clearImpulse;
        if (handle != null) {
            return handle;
        }
        synchronized (Connection.class) {
            handle = iamzombieq$clearImpulse;
            if (handle != null) {
                return handle;
            }
            try {
                Class<?> relayClass =
                        Class.forName(
                                "dev.molang.iamzombieq.internal.mount.SpiderVehicleImpulseRelay",
                                true,
                                Connection.class.getClassLoader());
                handle =
                        MethodHandles.privateLookupIn(
                                        relayClass, MethodHandles.lookup())
                                .findStatic(
                                        relayClass,
                                        "clear",
                                        MethodType.methodType(
                                                void.class,
                                                Connection.class))
                                .asType(
                                        MethodType.methodType(
                                                void.class,
                                                Connection.class));
                iamzombieq$clearImpulse = handle;
                return handle;
            } catch (ReflectiveOperationException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }
}
