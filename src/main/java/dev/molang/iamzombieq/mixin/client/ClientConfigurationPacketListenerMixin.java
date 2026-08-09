package dev.molang.iamzombieq.mixin.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Starts a fail-closed authority epoch for every initial configuration or
 * same-Connection reconfiguration before any authority snapshot is handled.
 */
@Mixin(ClientConfigurationPacketListenerImpl.class)
abstract class ClientConfigurationPacketListenerMixin {
    private static final MethodHandle BEGIN_CLIENT_CONFIGURATION =
            iamzombieq$authorityHandle("beginClientConfiguration");
    private static final MethodHandle REQUIRE_READY =
            iamzombieq$authorityHandle("requireReady");
    private static final MethodHandle CLEAR_AUTHORITY =
            iamzombieq$authorityHandle("clear");

    @Unique
    private Connection iamzombieq$authorityConnection;

    @Inject(
            method = "<init>(Lnet/minecraft/client/Minecraft;"
                    + "Lnet/minecraft/network/Connection;"
                    + "Lnet/minecraft/client/multiplayer/CommonListenerCookie;)V",
            at = @At("TAIL"),
            require = 1)
    private void iamzombieq$beginAuthorityEpoch(
            Minecraft minecraft,
            Connection connection,
            CommonListenerCookie cookie,
            CallbackInfo callback) {
        iamzombieq$authorityConnection = connection;
        try {
            BEGIN_CLIENT_CONFIGURATION.invokeExact(connection);
        } catch (Throwable failure) {
            throw iamzombieq$failClosed(failure);
        }
    }

    @Inject(
            method = "handleConfigurationFinished("
                    + "Lnet/minecraft/network/protocol/configuration/"
                    + "ClientboundFinishConfigurationPacket;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;"
                            + "ensureRunningOnSameThread("
                            + "Lnet/minecraft/network/protocol/Packet;"
                            + "Lnet/minecraft/network/PacketListener;"
                    //? if >=1.21.10 {
                            + "Lnet/minecraft/network/PacketProcessor;)V",
                    //?} else {
                            /*+ "Lnet/minecraft/util/thread/BlockableEventLoop;)V",
                    *///?}
                    shift = At.Shift.AFTER),
            cancellable = true,
            require = 1)
    private void iamzombieq$requireAuthorityBeforeWorld(
            ClientboundFinishConfigurationPacket packet,
            CallbackInfo callback) {
        try {
            REQUIRE_READY.invokeExact(iamzombieq$authorityConnection);
        } catch (Throwable failure) {
            try {
                CLEAR_AUTHORITY.invokeExact(iamzombieq$authorityConnection);
            } catch (Throwable clearFailure) {
                failure.addSuppressed(clearFailure);
            }
            iamzombieq$authorityConnection.disconnect(Component.literal(
                    "I Am Zombie? configuration authority payload "
                            + "was not confirmed before world entry"));
            callback.cancel();
        }
    }

    private static MethodHandle iamzombieq$authorityHandle(
            String methodName) {
        try {
            Class<?> runtime = Class.forName(
                    "dev.molang.iamzombieq.config.ConfigAuthorityRuntime",
                    true,
                    ClientConfigurationPacketListenerMixin.class
                            .getClassLoader());
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    runtime, MethodHandles.lookup());
            return lookup.findStatic(
                    runtime,
                    methodName,
                    MethodType.methodType(void.class, Connection.class));
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
