package dev.molang.iamzombieq.mixin;

import dev.molang.iamzombieq.util.MountCapability;
import dev.molang.iamzombieq.util.ZombiePlayerGates;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures an applicable spider's vanilla sync flags before the level tracker
 * clears them.
 *
 * <p>The one-shot marker is bound to the controlling connection and vehicle
 * UUID. It does not write a vanilla flag, send a packet, or initialize any
 * bridge for non-applicable tracked entities.
 */
@Mixin(ServerEntity.class)
abstract class ServerEntitySpiderImpulseMixin {
    @Unique private static final int IAMZOMBIEQ_NEEDS_SYNC = 0;
    @Unique private static final int IAMZOMBIEQ_SYNC_POSITION = 1;
    @Unique private static final int IAMZOMBIEQ_MARK_IMPULSE = 2;
    @Unique private static final int IAMZOMBIEQ_MARK_PARTICIPATION = 3;
    @Unique private static volatile MethodHandle[] iamzombieq$impulseHandles;

    @Shadow @Final private Entity entity;

    @Inject(method = "sendChanges()V", at = @At("HEAD"), require = 1)
    private void iamzombieq$captureSpiderImpulse(CallbackInfo callback) {
        if (!(entity instanceof Spider spider)
                || !(spider.getControllingPassenger()
                        instanceof ServerPlayer controller)
                || !ZombiePlayerGates.isServerZombiePlayer(controller)
                || !MountCapability.isOwnedSpider(
                        spider, controller.getUUID())) {
            return;
        }

        boolean needsSync =
                iamzombieq$readFlag(IAMZOMBIEQ_NEEDS_SYNC);
        boolean syncPosition =
                iamzombieq$readFlag(IAMZOMBIEQ_SYNC_POSITION);
        int flagBits =
                (needsSync ? 1 : 0)
                        | (entity.hurtMarked ? 2 : 0)
                        | (syncPosition ? 4 : 0);
        if (flagBits == 0) {
            return;
        }
        Vec3 movement = entity.getDeltaMovement();
        iamzombieq$markImpulse(
                controller.connection.getConnection(),
                spider.getUUID(),
                entity.getX(),
                entity.getZ(),
                movement.x,
                movement.z);
    }

    @Unique
    private boolean iamzombieq$readFlag(int handleIndex) {
        try {
            return (boolean)
                    iamzombieq$impulseHandles()[handleIndex]
                            .invokeExact(entity);
        } catch (Throwable failure) {
            throw iamzombieq$impulseFailure("read Entity flag", failure);
        }
    }

    @Unique
    private static void iamzombieq$markImpulse(
            Connection connection,
            UUID vehicleId,
            double x,
            double z,
            double deltaX,
            double deltaZ) {
        try {
            iamzombieq$impulseHandles()[IAMZOMBIEQ_MARK_PARTICIPATION]
                    .invokeExact(connection);
            iamzombieq$impulseHandles()[IAMZOMBIEQ_MARK_IMPULSE]
                    .invokeExact(
                            connection,
                            vehicleId,
                            x,
                            z,
                            deltaX,
                            deltaZ);
        } catch (Throwable failure) {
            throw iamzombieq$impulseFailure("mark", failure);
        }
    }

    @Unique
    private static MethodHandle[] iamzombieq$impulseHandles() {
        MethodHandle[] handles = iamzombieq$impulseHandles;
        if (handles != null) {
            return handles;
        }
        synchronized (ServerEntity.class) {
            handles = iamzombieq$impulseHandles;
            if (handles != null) {
                return handles;
            }
            try {
                MethodHandles.Lookup entityLookup =
                        MethodHandles.privateLookupIn(
                                Entity.class, MethodHandles.lookup());
                MethodHandles.Lookup connectionLookup =
                        MethodHandles.privateLookupIn(
                                Connection.class, MethodHandles.lookup());
                Class<?> relayClass =
                        Class.forName(
                                "dev.molang.iamzombieq.internal.mount.SpiderVehicleImpulseRelay",
                                true,
                                ServerEntity.class.getClassLoader());
                MethodHandles.Lookup relayLookup =
                        MethodHandles.privateLookupIn(
                                relayClass, MethodHandles.lookup());
                handles =
                        new MethodHandle[] {
                            entityLookup.findGetter(
                                    Entity.class,
                                    "needsSync",
                                    boolean.class),
                            entityLookup.findGetter(
                                    Entity.class,
                                    "syncPosition",
                                    boolean.class),
                            relayLookup
                                    .findStatic(
                                            relayClass,
                                            "mark",
                                            MethodType.methodType(
                                                    void.class,
                                                    Connection.class,
                                                    UUID.class,
                                                    double.class,
                                                    double.class,
                                                    double.class,
                                                    double.class))
                                    .asType(
                                            MethodType.methodType(
                                                    void.class,
                                                    Connection.class,
                                                    UUID.class,
                                                    double.class,
                                                    double.class,
                                                    double.class,
                                                    double.class)),
                            connectionLookup
                                    .findVirtual(
                                            Connection.class,
                                            "iamzombieq$markSpiderAuthorityParticipation",
                                            MethodType.methodType(void.class))
                                    .asType(
                                            MethodType.methodType(
                                                    void.class,
                                                    Connection.class))
                        };
                iamzombieq$impulseHandles = handles;
                return handles;
            } catch (ReflectiveOperationException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }

    @Unique
    private static RuntimeException iamzombieq$impulseFailure(
            String operation, Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(
                "Spider impulse bridge failed to " + operation, failure);
    }
}
