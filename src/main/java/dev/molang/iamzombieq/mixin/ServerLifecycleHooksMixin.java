package dev.molang.iamzombieq.mixin;

import dev.molang.iamzombieq.config.ConfigMigrationBootstrap;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerLifecycleHooks.class, remap = false)
abstract class ServerLifecycleHooksMixin {
    @Inject(
            method = "handleServerAboutToStart(Lnet/minecraft/server/MinecraftServer;)V",
            at = @At("HEAD"),
            remap = false,
            require = 1)
    private static void iamzombieq$migrateServerConfig(
            MinecraftServer server, CallbackInfo callback) {
        ConfigMigrationBootstrap.migrateServer(server);
    }
}
