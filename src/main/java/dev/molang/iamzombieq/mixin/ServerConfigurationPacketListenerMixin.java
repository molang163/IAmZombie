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
            iamzombieq$authorityHandle();

    @Shadow @Final private Queue<ConfigurationTask> configurationTasks;

    @Inject(
            method = "returnToWorld()V",
            at = @At("HEAD"),
            require = 1)
    private void iamzombieq$queueAuthorityTask(
            CallbackInfo callback) {
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

    private static MethodHandle iamzombieq$authorityHandle() {
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
                    "beginServerConfiguration",
                    MethodType.methodType(
                            ConfigurationTask.class,
                            Connection.class));
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
