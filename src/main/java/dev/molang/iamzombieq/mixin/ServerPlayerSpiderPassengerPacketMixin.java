package dev.molang.iamzombieq.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.molang.iamzombieq.internal.mount.SpiderPassengerRestorationAccess;
import dev.molang.iamzombieq.util.MountCapability;
import dev.molang.iamzombieq.util.ZombiePlayerGates;
import java.util.ArrayDeque;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.spider.Spider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps restored owned-spider passenger publication in vanilla tracking order.
 *
 * <p>The two vanilla restoration callers mark only their synchronous virtual
 * {@code startRiding} invocation. The instance-local stacks bind that context to the exact
 * ServerPlayer and expected replacement vehicle, remain safe under nested restoration, and are
 * cleared in the caller's {@code finally} block. During parent-vehicle load, the joining player's
 * chunk is still pending, so the newly loaded vehicle is not yet paired to that connection; during
 * cross-dimension replacement, the new-dimension tracking pairing is likewise still pending.
 * Vanilla's later tracking bundle publishes the association in both cases. Normal gameplay has no
 * restoration context and therefore retains immediate publication.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerSpiderPassengerPacketMixin
        implements SpiderPassengerRestorationAccess {
    @Unique private ArrayDeque<Entity> iamzombieq$restorationVehicles;

    //? if >=1.21.10 {
    @WrapOperation(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;"
                                            + "send(Lnet/minecraft/network/protocol/Packet;)V"),
            require = 1)
    private void iamzombieq$deferRestoredSpiderPassengers(
            ServerGamePacketListenerImpl listener,
            Packet<?> packet,
            Operation<Void> original,
            Entity entityToRide,
            boolean force,
            boolean sendEventAndTriggers) {
        iamzombieq$handleRestoredSpiderPassengers(
                listener,
                packet,
                original,
                entityToRide,
                force && !sendEventAndTriggers);
    }
    //?} else {
    /*@WrapOperation(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;"
                                            + "send(Lnet/minecraft/network/protocol/Packet;)V"),
            require = 1)
    private void iamzombieq$deferRestoredSpiderPassengers(
            ServerGamePacketListenerImpl listener,
            Packet<?> packet,
            Operation<Void> original,
            Entity entityToRide,
            boolean force) {
        iamzombieq$handleRestoredSpiderPassengers(
                listener, packet, original, entityToRide, force);
    }
    *///?}

    //? if >=1.21.10 {
    @WrapOperation(
            method =
                    "loadAndSpawnParentVehicle(Lnet/minecraft/world/level/storage/ValueInput;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerPlayer;startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z"),
            require = 2)
    private boolean iamzombieq$restoreLoadedSpiderPassenger(
            ServerPlayer player,
            Entity entityToRide,
            boolean force,
            boolean sendEventAndTriggers,
            Operation<Boolean> original) {
        if (!(entityToRide instanceof Spider)) {
            return original.call(
                    player, entityToRide, force, sendEventAndTriggers);
        }
        iamzombieq$beginSpiderPassengerRestoration(entityToRide);
        try {
            return original.call(
                    player, entityToRide, force, sendEventAndTriggers);
        } finally {
            iamzombieq$endSpiderPassengerRestoration(entityToRide);
        }
    }
    //?} else {
    /*@WrapOperation(
            method =
                    "loadAndSpawnParentVehicle(Lnet/minecraft/world/level/storage/ValueInput;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerPlayer;startRiding(Lnet/minecraft/world/entity/Entity;Z)Z"),
            require = 2)
    private boolean iamzombieq$restoreLoadedSpiderPassenger(
            ServerPlayer player,
            Entity entityToRide,
            boolean force,
            Operation<Boolean> original) {
        if (!(entityToRide instanceof Spider)) {
            return original.call(player, entityToRide, force);
        }
        iamzombieq$beginSpiderPassengerRestoration(entityToRide);
        try {
            return original.call(player, entityToRide, force);
        } finally {
            iamzombieq$endSpiderPassengerRestoration(entityToRide);
        }
    }
    *///?}

    @Unique
    private void iamzombieq$handleRestoredSpiderPassengers(
            ServerGamePacketListenerImpl listener,
            Packet<?> packet,
            Operation<Void> original,
            Entity entityToRide,
            boolean nodeNativeRestorationShape) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!(packet instanceof ClientboundSetPassengersPacket passengers)
                || !(entityToRide instanceof Spider spider)) {
            original.call(listener, packet);
            return;
        }

        boolean shouldDefer =
                iamzombieq$shouldDefer(
                        iamzombieq$matchesSpiderPassengerRestoration(entityToRide),
                        nodeNativeRestorationShape,
                        listener == player.connection,
                        ZombiePlayerGates.isServerZombiePlayer(player),
                        player.level() == spider.level()
                                && player.level().dimension()
                                        == spider.level().dimension(),
                        MountCapability.isOwnedSpider(spider, player.getUUID()),
                        spider.getFirstPassenger() == player
                                && spider.getControllingPassenger() == player,
                        player.getVehicle() == spider,
                        spider.getId(),
                        passengers.getVehicle(),
                        player.getId(),
                        passengers.getPassengers());
        if (!shouldDefer) {
            original.call(listener, packet);
        }
    }

    @Override
    public void iamzombieq$beginSpiderPassengerRestoration(Entity entityToRide) {
        if (iamzombieq$restorationVehicles == null) {
            iamzombieq$restorationVehicles = new ArrayDeque<>();
        }
        iamzombieq$restorationVehicles.push(entityToRide);
    }

    @Unique
    private boolean iamzombieq$matchesSpiderPassengerRestoration(
            Entity entityToRide) {
        return iamzombieq$restorationVehicles != null
                && iamzombieq$restorationVehicles.peek() == entityToRide;
    }

    @Override
    public void iamzombieq$endSpiderPassengerRestoration(Entity entityToRide) {
        if (iamzombieq$restorationVehicles == null
                || iamzombieq$restorationVehicles.peek() != entityToRide) {
            throw new IllegalStateException(
                    "Spider passenger restoration context lost its vehicle identity");
        }
        iamzombieq$restorationVehicles.pop();
        if (iamzombieq$restorationVehicles.isEmpty()) {
            iamzombieq$restorationVehicles = null;
        }
    }

    /** Pure decision seam for the one premature restoration association. */
    @Unique
    private static boolean iamzombieq$shouldDefer(
            boolean restorationContext,
            boolean nodeNativeRestorationShape,
            boolean sameConnection,
            boolean serverZombie,
            boolean sameLevelAndDimension,
            boolean owned,
            boolean controlledByPlayer,
            boolean currentVehicle,
            int targetVehicleId,
            int packetVehicleId,
            int playerEntityId,
            int[] passengerEntityIds) {
        return restorationContext
                && nodeNativeRestorationShape
                && sameConnection
                && serverZombie
                && sameLevelAndDimension
                && owned
                && controlledByPlayer
                && currentVehicle
                && targetVehicleId == packetVehicleId
                && passengerEntityIds != null
                && passengerEntityIds.length == 1
                && passengerEntityIds[0] == playerEntityId;
    }
}
