package dev.molang.iamzombieq.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.molang.iamzombieq.internal.mount.SpiderPassengerRestorationAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.spider.Spider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Marks only vanilla's synchronous cross-dimension passenger restoration invocation. */
@Mixin(Entity.class)
abstract class EntitySpiderPassengerRestorationMixin {
    //? if >=1.21.10 {
    @WrapOperation(
            method =
                    "teleportCrossDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/Entity;startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z"),
            require = 1)
    private boolean iamzombieq$restoreTeleportedSpiderPassenger(
            Entity passenger,
            Entity entityToRide,
            boolean force,
            boolean sendEventAndTriggers,
            Operation<Boolean> original) {
        if (!(passenger instanceof ServerPlayer player)
                || !(entityToRide instanceof Spider)) {
            return original.call(
                    passenger, entityToRide, force, sendEventAndTriggers);
        }
        SpiderPassengerRestorationAccess restoration =
                (SpiderPassengerRestorationAccess) player;
        restoration.iamzombieq$beginSpiderPassengerRestoration(entityToRide);
        try {
            return original.call(
                    passenger, entityToRide, force, sendEventAndTriggers);
        } finally {
            restoration.iamzombieq$endSpiderPassengerRestoration(entityToRide);
        }
    }
    //?} else {
    /*@WrapOperation(
            method =
                    "teleportCrossDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/Entity;startRiding(Lnet/minecraft/world/entity/Entity;Z)Z"),
            require = 1)
    private boolean iamzombieq$restoreTeleportedSpiderPassenger(
            Entity passenger,
            Entity entityToRide,
            boolean force,
            Operation<Boolean> original) {
        if (!(passenger instanceof ServerPlayer player)
                || !(entityToRide instanceof Spider)) {
            return original.call(passenger, entityToRide, force);
        }
        SpiderPassengerRestorationAccess restoration =
                (SpiderPassengerRestorationAccess) player;
        restoration.iamzombieq$beginSpiderPassengerRestoration(entityToRide);
        try {
            return original.call(passenger, entityToRide, force);
        } finally {
            restoration.iamzombieq$endSpiderPassengerRestoration(entityToRide);
        }
    }
    *///?}
}
