package dev.molang.iamzombieq.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.molang.iamzombieq.util.MountCapability;
import dev.molang.iamzombieq.util.ZombiePlayerGates;
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
 * <p>{@link ServerPlayer#startRiding(Entity, boolean, boolean)} sends its own passenger packet
 * immediately after the server attaches a rider. For the two vanilla {@code (true, false)}
 * restoration paths—cross-dimension replacement and parent-vehicle load—the target has not yet
 * been paired to this connection. Vanilla's later tracking bundle already publishes AddEntity,
 * NeoForge pairing data, then the passenger relation. Suppressing only the matching premature
 * direct send avoids an unknown-entity packet without adding a delay, retry, custom packet, or
 * second passenger publication.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerSpiderPassengerPacketMixin {
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
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!(packet instanceof ClientboundSetPassengersPacket passengers)
                || !(entityToRide instanceof Spider spider)) {
            original.call(listener, packet);
            return;
        }

        boolean shouldDefer =
                iamzombieq$shouldDefer(
                        force,
                        sendEventAndTriggers,
                        listener == player.connection,
                        ZombiePlayerGates.isServerZombiePlayer(player),
                        player.level() == spider.level()
                                && player.level().dimension()
                                        == spider.level().dimension(),
                        MountCapability.isOwnedSpider(
                                spider, player.getUUID()),
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

    /**
     * Pure decision seam for the one premature passenger association emitted by
     * {@code ServerPlayer.startRiding(Entity, true, false)}.
     *
     * <p>This method is merged into {@link ServerPlayer}; it deliberately does not reference a
     * separately loadable helper in the declared mixin package.
     */
    @Unique
    private static boolean iamzombieq$shouldDefer(
            boolean force,
            boolean sendEventAndTriggers,
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
        return force
                && !sendEventAndTriggers
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
