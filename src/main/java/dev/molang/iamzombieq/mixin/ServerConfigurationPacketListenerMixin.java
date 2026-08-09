package dev.molang.iamzombieq.mixin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Queue;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Queues the mandatory SERVER-authority task for both initial configuration
 * and same-connection reconfiguration. This injection runs before vanilla
 * appends PrepareSpawnTask and JoinWorldTask.
 */
@Mixin(ServerConfigurationPacketListenerImpl.class)
abstract class ServerConfigurationPacketListenerMixin {
    private static final MethodHandle BEGIN_SERVER_CONFIGURATION =
            iamzombieq$authorityHandle(
                    "beginServerConfiguration",
                    MethodType.methodType(
                            ConfigurationTask.class,
                            Connection.class));

    //? if <1.21.10 {
    /*private static final MethodHandle TICK_LEGACY_SERVER_CONFIGURATION =
            iamzombieq$authorityHandle(
                    "tickLegacyServerConfiguration",
                    MethodType.methodType(
                            void.class,
                            ConfigurationTask.class,
                            ServerConfigurationPacketListenerImpl.class));
    *///?}

    @Shadow @Final private Queue<ConfigurationTask> configurationTasks;

    //? if <1.21.10 {
    /*@Shadow private ConfigurationTask currentTask;
    *///?}

    @Inject(
            method = "returnToWorld()V",
            at = @At("HEAD"),
            require = 1)
    private void iamzombieq$queueAuthorityOnReturn(
            CallbackInfo callback) {
        iamzombieq$queueAuthorityTask();
    }

    //? if <1.21.10 {
    /*@Inject(
            method = "addOptionalTasks()V",
            at = @At("RETURN"),
            require = 1)
    private void iamzombieq$queueInitialAuthority(
            CallbackInfo callback) {
        iamzombieq$queueAuthorityTask();
    }
    *///?}

    private void iamzombieq$queueAuthorityTask() {
        ServerConfigurationPacketListenerImpl listener =
                (ServerConfigurationPacketListenerImpl) (Object) this;
        Connection connection = listener.getConnection();
        try {
            ConfigurationTask task =
                    (ConfigurationTask) BEGIN_SERVER_CONFIGURATION
                            .invokeExact(connection);
            configurationTasks.add(task);
        } catch (Throwable failure) {
            throw iamzombieq$failClosed(failure);
        }
    }

    //? if <1.21.10 {
    /*@Inject(
            method = "tick()V",
            at = @At("RETURN"),
            require = 1)
    private void iamzombieq$tickAuthorityTask(
            CallbackInfo callback) {
        ServerConfigurationPacketListenerImpl listener =
                (ServerConfigurationPacketListenerImpl) (Object) this;
        try {
            TICK_LEGACY_SERVER_CONFIGURATION.invokeExact(
                    currentTask, listener);
        } catch (Throwable failure) {
            throw iamzombieq$failClosed(failure);
        }
    }
    *///?}

    private static MethodHandle iamzombieq$authorityHandle(
            String methodName, MethodType methodType) {
        try {
            Class<?> runtime = Class.forName(
                    "dev.molang.iamzombieq.config.ConfigAuthorityRuntime",
                    true,
                    ServerConfigurationPacketListenerMixin.class
                            .getClassLoader());
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    runtime, MethodHandles.lookup());
            return lookup.findStatic(
                    runtime,
                    methodName,
                    methodType);
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
