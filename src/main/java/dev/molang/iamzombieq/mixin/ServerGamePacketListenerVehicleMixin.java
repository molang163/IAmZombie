package dev.molang.iamzombieq.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.molang.iamzombieq.IAmZombieServerConfig;
import dev.molang.iamzombieq.rules.mount.ZombieMountRules;
import dev.molang.iamzombieq.util.MountCapability;
import dev.molang.iamzombieq.util.ZombiePlayerGates;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SERVER-authoritative horizontal rate validator for the vanilla vehicle-move packet.
 *
 * <p>The cancellable pre-operation hook is deliberately at the one
 * {@code Entity.move(MoverType.PLAYER, Vec3)} invocation, after vanilla has admitted the
 * connection, current root vehicle, controller, and finite packet. The operation remains wrapped
 * with {@code require=1}; a RETURN observer commits a model assessment only when vanilla left the
 * vehicle at the packet target.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerVehicleMixin {
    // Exact coordinate clamps used by ServerGamePacketListenerImpl in 26.2.
    @Unique private static final double IAMZOMBIEQ_MAX_HORIZONTAL_COORDINATE = 3.0E7;
    @Unique private static final double IAMZOMBIEQ_MAX_VERTICAL_COORDINATE = 2.0E7;
    @Unique private static final int IAMZOMBIEQ_ALLOW = 0;
    @Unique private static final int IAMZOMBIEQ_REJECT = 1;
    @Unique private static final int IAMZOMBIEQ_REBASE = 2;
    @Unique private static final int IAMZOMBIEQ_START_HANDLE = 0;
    @Unique private static final int IAMZOMBIEQ_ASSESS_HANDLE = 1;
    @Unique private static final int IAMZOMBIEQ_COMMIT_HANDLE = 2;
    @Unique private static final int IAMZOMBIEQ_REBASE_HANDLE = 3;
    @Unique private static final int IAMZOMBIEQ_MATCHES_HANDLE = 4;
    @Unique private static final int IAMZOMBIEQ_PENDING_HANDLE = 5;
    @Unique private static final int IAMZOMBIEQ_NEEDS_SYNC_HANDLE = 6;
    @Unique private static final int IAMZOMBIEQ_SYNC_POSITION_HANDLE = 7;
    @Unique private static final int IAMZOMBIEQ_CONSUME_IMPULSE_HANDLE = 8;
    @Unique private static final int IAMZOMBIEQ_CLEAR_IMPULSE_HANDLE = 9;
    @Unique private static final int IAMZOMBIEQ_SUPPRESS_IMPULSE_HANDLE = 10;
    @Unique private static final int IAMZOMBIEQ_MARK_PARTICIPATION_HANDLE = 11;
    @Unique private static volatile MethodHandle[] iamzombieq$authorityHandles;

    @Shadow public ServerPlayer player;
    @Shadow @Nullable private Entity lastVehicle;

    @Shadow
    private void resyncPlayerWithVehicle(Entity vehicle) {
        throw new AssertionError("Mixin shadow");
    }

    @Unique private Object iamzombieq$session;
    @Unique private Spider iamzombieq$scopeVehicle;
    @Unique private ResourceKey<Level> iamzombieq$scopeDimension;
    @Unique private UUID iamzombieq$scopeController;
    @Unique private Vec3 iamzombieq$pendingCandidate;
    @Unique private boolean iamzombieq$pendingImpulseCorrection;
    @Unique private boolean iamzombieq$authorityParticipated;

    @Inject(
            method =
                    "handleMoveVehicle(Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;)V",
            at = @At("HEAD"))
    private void iamzombieq$beginVehiclePacket(
            ServerboundMoveVehiclePacket packet, CallbackInfo callback) {
        iamzombieq$pendingCandidate = null;
    }

    /**
     * Vanilla refreshes {@code lastVehicle} and its authoritative position in
     * {@code tickPlayer} before a vehicle packet can pass the next tick's
     * {@code entity == lastVehicle} admission. Capturing that baseline and
     * monotonic origin here gives the first legal packet its real elapsed credit
     * instead of an unvalidated bypass or a corrective first rejection.
     */
    @Inject(method = "tick()V", at = @At("RETURN"), require = 1)
    private void iamzombieq$prepareVehicleEnvelope(CallbackInfo callback) {
        Entity entity = this.player.getRootVehicle();
        if (entity != this.lastVehicle || !iamzombieq$isApplicableSpider(entity)) {
            iamzombieq$resetScope();
            return;
        }

        Spider spider = (Spider) entity;
        ServerLevel level = this.player.level();
        long monotonicNanos = System.nanoTime();
        if (!iamzombieq$sameScope(spider, level)) {
            iamzombieq$startScope(spider, level, monotonicNanos);
        }

        boolean relayedImpulse =
                iamzombieq$consumeImpulseMarker(spider.getUUID());
        int directFlagBits = iamzombieq$impulseFlagBits(spider);
        boolean directImpulse = directFlagBits != 0;
        if (relayedImpulse || directImpulse) {
            if (directImpulse) {
                iamzombieq$suppressNextImpulseMarker(spider);
            }
            iamzombieq$rebase(level, spider, monotonicNanos);
            iamzombieq$pendingImpulseCorrection = true;
        } else if (!iamzombieq$matchesAuthoritativePosition(spider)) {
            iamzombieq$rebase(level, spider, monotonicNanos);
        }
    }

    @Inject(
            method =
                    "handleMoveVehicle(Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/Entity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"),
            cancellable = true,
            require = 1)
    private void iamzombieq$validateSpiderVehicleMove(
            ServerboundMoveVehiclePacket packet, CallbackInfo callback) {
        Entity entity = player.getRootVehicle();
        if (!iamzombieq$isApplicableSpider(entity)) {
            iamzombieq$resetScope();
            return;
        }
        Spider spider = (Spider) entity;
        ServerLevel level = this.player.level();
        long monotonicNanos = System.nanoTime();

        if (!iamzombieq$sameScope(spider, level)) {
            // A packet that somehow reaches the move operation before vanilla's
            // listener tick established lastVehicle has no source-derived
            // elapsed-time origin. Fail closed; the ordinary first-packet path
            // is pre-armed by prepareVehicleEnvelope above.
            iamzombieq$startScope(spider, level, monotonicNanos);
            iamzombieq$rejectCurrentPacket(spider, callback);
            return;
        }

        boolean relayedImpulse =
                iamzombieq$consumeImpulseMarker(spider.getUUID());
        int directFlagBits = iamzombieq$impulseFlagBits(spider);
        boolean directImpulse = directFlagBits != 0;
        if (relayedImpulse || directImpulse) {
            if (directImpulse) {
                iamzombieq$suppressNextImpulseMarker(spider);
            }
            iamzombieq$rebase(level, spider, monotonicNanos);
            iamzombieq$pendingImpulseCorrection = true;
        }
        if (iamzombieq$pendingImpulseCorrection) {
            iamzombieq$pendingImpulseCorrection = false;
            iamzombieq$rejectCurrentPacket(spider, callback);
            return;
        }

        Vec3 candidate =
                new Vec3(
                        Mth.clamp(
                                packet.position().x,
                                -IAMZOMBIEQ_MAX_HORIZONTAL_COORDINATE,
                                IAMZOMBIEQ_MAX_HORIZONTAL_COORDINATE),
                        Mth.clamp(
                                packet.position().y,
                                -IAMZOMBIEQ_MAX_VERTICAL_COORDINATE,
                                IAMZOMBIEQ_MAX_VERTICAL_COORDINATE),
                        Mth.clamp(
                                packet.position().z,
                                -IAMZOMBIEQ_MAX_HORIZONTAL_COORDINATE,
                                IAMZOMBIEQ_MAX_HORIZONTAL_COORDINATE));
        float configuredSpeed =
                ZombieMountRules.spiderRiddenSpeed(
                        IAmZombieServerConfig.SPIDER_MOUNT_SPEED.get().floatValue());
        switch (iamzombieq$assess(
                level, spider, candidate, configuredSpeed, monotonicNanos)) {
            case IAMZOMBIEQ_ALLOW -> iamzombieq$pendingCandidate = candidate;
            case IAMZOMBIEQ_REJECT, IAMZOMBIEQ_REBASE ->
                    iamzombieq$rejectCurrentPacket(spider, callback);
            default ->
                    throw new IllegalStateException(
                            "Unknown spider vehicle envelope outcome");
        }
    }

    @WrapOperation(
            method =
                    "handleMoveVehicle(Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/Entity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"),
            require = 1)
    private void iamzombieq$moveOnlyAfterEnvelopeAdmission(
            Entity entity, MoverType moverType, Vec3 movement, Operation<Void> original) {
        if (entity == iamzombieq$scopeVehicle
                && iamzombieq$session != null
                && !iamzombieq$hasPendingAdmission()) {
            throw new IllegalStateException(
                    "Spider vehicle move reached vanilla without envelope admission");
        }
        original.call(entity, moverType, movement);
    }

    @Inject(
            method =
                    "handleMoveVehicle(Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;)V",
            at = @At("RETURN"))
    private void iamzombieq$finishVehiclePacket(
            ServerboundMoveVehiclePacket packet, CallbackInfo callback) {
        Spider spider = iamzombieq$scopeVehicle;
        Vec3 candidate = iamzombieq$pendingCandidate;
        iamzombieq$pendingCandidate = null;
        if (spider == null || candidate == null || iamzombieq$session == null) {
            return;
        }

        ServerLevel level = this.player.level();
        if (!iamzombieq$sameScope(spider, level)) {
            iamzombieq$resetScope();
            return;
        }

        Vec3 actual = spider.position();
        if (iamzombieq$same(actual.x, candidate.x)
                && iamzombieq$same(actual.y, candidate.y)
                && iamzombieq$same(actual.z, candidate.z)) {
            iamzombieq$commitAccepted();
            return;
        }

        // Vanilla collision correction did not accept the candidate. The
        // server's actual position, never the packet target, becomes the next
        // authoritative baseline.
        iamzombieq$rebase(level, spider, System.nanoTime());
    }

    @Unique
    private void iamzombieq$startScope(
            Spider spider, ServerLevel level, long monotonicNanos) {
        iamzombieq$authorityParticipated = true;
        iamzombieq$pendingImpulseCorrection = false;
        iamzombieq$scopeVehicle = spider;
        iamzombieq$scopeDimension = level.dimension();
        iamzombieq$scopeController = this.player.getUUID();
        iamzombieq$session =
                iamzombieq$startSession(level, spider, monotonicNanos);
    }

    @Unique
    private boolean iamzombieq$sameScope(Spider spider, ServerLevel level) {
        return iamzombieq$session != null
                && iamzombieq$scopeVehicle == spider
                && iamzombieq$scopeDimension == level.dimension()
                && this.player.getUUID().equals(iamzombieq$scopeController)
                && spider.getControllingPassenger() == this.player
                && MountCapability.isOwnedSpider(spider, this.player.getUUID());
    }

    @Unique
    private boolean iamzombieq$isApplicableSpider(Entity entity) {
        return entity instanceof Spider spider
                && ZombiePlayerGates.isServerZombiePlayer(this.player)
                && entity.getControllingPassenger() == this.player
                && MountCapability.isOwnedSpider(spider, this.player.getUUID());
    }

    @Unique
    private static Object iamzombieq$startSession(
            ServerLevel level, Spider spider, long monotonicNanos) {
        try {
            return (Object)
                    iamzombieq$authorityHandles()[IAMZOMBIEQ_START_HANDLE]
                            .invokeExact(level, spider, monotonicNanos);
        } catch (Throwable failure) {
            throw iamzombieq$bridgeFailure("start", failure);
        }
    }

    @Unique
    private int iamzombieq$assess(
            ServerLevel level,
            Spider spider,
            Vec3 candidate,
            float configuredSpeed,
            long monotonicNanos) {
        try {
            return (int)
                    iamzombieq$authorityHandles()[IAMZOMBIEQ_ASSESS_HANDLE]
                            .invokeExact(
                                    iamzombieq$requireSession(),
                                    level,
                                    spider,
                                    candidate,
                                    configuredSpeed,
                                    monotonicNanos);
        } catch (Throwable failure) {
            throw iamzombieq$bridgeFailure("assess", failure);
        }
    }

    @Unique
    private void iamzombieq$commitAccepted() {
        try {
            iamzombieq$authorityHandles()[IAMZOMBIEQ_COMMIT_HANDLE]
                    .invokeExact(iamzombieq$requireSession());
        } catch (Throwable failure) {
            throw iamzombieq$bridgeFailure("commit", failure);
        }
    }

    @Unique
    private void iamzombieq$rebase(
            ServerLevel level, Spider spider, long monotonicNanos) {
        try {
            iamzombieq$authorityHandles()[IAMZOMBIEQ_REBASE_HANDLE]
                    .invokeExact(
                            iamzombieq$requireSession(),
                            level,
                            spider,
                            monotonicNanos);
        } catch (Throwable failure) {
            throw iamzombieq$bridgeFailure("rebase", failure);
        }
    }

    @Unique
    private boolean iamzombieq$matchesAuthoritativePosition(Spider spider) {
        try {
            return (boolean)
                    iamzombieq$authorityHandles()[IAMZOMBIEQ_MATCHES_HANDLE]
                            .invokeExact(iamzombieq$requireSession(), spider);
        } catch (Throwable failure) {
            throw iamzombieq$bridgeFailure("matches position", failure);
        }
    }

    @Unique
    private boolean iamzombieq$hasPendingAdmission() {
        try {
            return (boolean)
                    iamzombieq$authorityHandles()[IAMZOMBIEQ_PENDING_HANDLE]
                            .invokeExact(iamzombieq$requireSession());
        } catch (Throwable failure) {
            throw iamzombieq$bridgeFailure("query pending admission", failure);
        }
    }

    @Unique
    private static boolean iamzombieq$readEntityFlag(
            Entity entity, int handleIndex) {
        try {
            return (boolean)
                    iamzombieq$authorityHandles()[handleIndex]
                            .invokeExact(entity);
        } catch (Throwable failure) {
            throw iamzombieq$bridgeFailure("read Entity flag", failure);
        }
    }

    @Unique
    private static int iamzombieq$impulseFlagBits(Entity entity) {
        return (iamzombieq$readEntityFlag(
                                        entity,
                                        IAMZOMBIEQ_NEEDS_SYNC_HANDLE)
                                ? 1
                                : 0)
                | (entity.hurtMarked ? 2 : 0)
                | (iamzombieq$readEntityFlag(
                                        entity,
                                        IAMZOMBIEQ_SYNC_POSITION_HANDLE)
                                ? 4
                                : 0);
    }

    @Unique
    private boolean iamzombieq$consumeImpulseMarker(UUID vehicleId) {
        Connection connection =
                ((ServerGamePacketListenerImpl) (Object) this)
                        .getConnection();
        try {
            return (boolean)
                    iamzombieq$authorityHandles()[
                                    IAMZOMBIEQ_CONSUME_IMPULSE_HANDLE]
                            .invokeExact(connection, vehicleId);
        } catch (Throwable failure) {
            throw iamzombieq$bridgeFailure(
                    "consume impulse marker", failure);
        }
    }

    @Unique
    private void iamzombieq$clearImpulseMarker() {
        Connection connection =
                ((ServerGamePacketListenerImpl) (Object) this)
                        .getConnection();
        try {
            iamzombieq$authorityHandles()[
                            IAMZOMBIEQ_CLEAR_IMPULSE_HANDLE]
                    .invokeExact(connection);
        } catch (Throwable failure) {
            throw iamzombieq$bridgeFailure(
                    "clear impulse marker", failure);
        }
    }

    @Unique
    private void iamzombieq$suppressNextImpulseMarker(Spider spider) {
        Connection connection =
                ((ServerGamePacketListenerImpl) (Object) this)
                        .getConnection();
        Vec3 movement = spider.getDeltaMovement();
        try {
            iamzombieq$authorityHandles()[
                            IAMZOMBIEQ_MARK_PARTICIPATION_HANDLE]
                    .invokeExact(connection);
            iamzombieq$authorityHandles()[
                            IAMZOMBIEQ_SUPPRESS_IMPULSE_HANDLE]
                    .invokeExact(
                            connection,
                            spider.getUUID(),
                            spider.getX(),
                            spider.getZ(),
                            movement.x,
                            movement.z);
        } catch (Throwable failure) {
            throw iamzombieq$bridgeFailure(
                    "suppress duplicate impulse marker", failure);
        }
    }

    @Unique
    private Object iamzombieq$requireSession() {
        Object session = iamzombieq$session;
        if (session == null) {
            throw new IllegalStateException(
                    "Spider vehicle envelope session is absent");
        }
        return session;
    }

    @Unique
    private static MethodHandle[] iamzombieq$authorityHandles() {
        MethodHandle[] handles = iamzombieq$authorityHandles;
        if (handles != null) {
            return handles;
        }
        synchronized (ServerGamePacketListenerImpl.class) {
            handles = iamzombieq$authorityHandles;
            if (handles != null) {
                return handles;
            }
            try {
                Class<?> sessionClass =
                        Class.forName(
                                "dev.molang.iamzombieq.internal.mount.SpiderVehicleAuthoritySession",
                                true,
                                ServerGamePacketListenerImpl.class
                                        .getClassLoader());
                MethodHandles.Lookup sessionLookup =
                        MethodHandles.privateLookupIn(
                                sessionClass, MethodHandles.lookup());
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
                                ServerGamePacketListenerImpl.class
                                        .getClassLoader());
                MethodHandles.Lookup relayLookup =
                        MethodHandles.privateLookupIn(
                                relayClass, MethodHandles.lookup());
                handles =
                        new MethodHandle[] {
                            sessionLookup
                                    .findStatic(
                                            sessionClass,
                                            "start",
                                            MethodType.methodType(
                                                    sessionClass,
                                                    ServerLevel.class,
                                                    Spider.class,
                                                    long.class))
                                    .asType(
                                            MethodType.methodType(
                                                    Object.class,
                                                    ServerLevel.class,
                                                    Spider.class,
                                                    long.class)),
                            sessionLookup
                                    .findVirtual(
                                            sessionClass,
                                            "assess",
                                            MethodType.methodType(
                                                    int.class,
                                                    ServerLevel.class,
                                                    Spider.class,
                                                    Vec3.class,
                                                    float.class,
                                                    long.class))
                                    .asType(
                                            MethodType.methodType(
                                                    int.class,
                                                    Object.class,
                                                    ServerLevel.class,
                                                    Spider.class,
                                                    Vec3.class,
                                                    float.class,
                                                    long.class)),
                            sessionLookup
                                    .findVirtual(
                                            sessionClass,
                                            "commitAccepted",
                                            MethodType.methodType(void.class))
                                    .asType(
                                            MethodType.methodType(
                                                    void.class,
                                                    Object.class)),
                            sessionLookup
                                    .findVirtual(
                                            sessionClass,
                                            "rebase",
                                            MethodType.methodType(
                                                    void.class,
                                                    ServerLevel.class,
                                                    Spider.class,
                                                    long.class))
                                    .asType(
                                            MethodType.methodType(
                                                    void.class,
                                                    Object.class,
                                                    ServerLevel.class,
                                                    Spider.class,
                                                    long.class)),
                            sessionLookup
                                    .findVirtual(
                                            sessionClass,
                                            "matchesAuthoritativePosition",
                                            MethodType.methodType(
                                                    boolean.class,
                                                    Spider.class))
                                    .asType(
                                            MethodType.methodType(
                                                    boolean.class,
                                                    Object.class,
                                                    Spider.class)),
                            sessionLookup
                                    .findVirtual(
                                            sessionClass,
                                            "hasPendingAdmission",
                                            MethodType.methodType(
                                                    boolean.class))
                                    .asType(
                                            MethodType.methodType(
                                                    boolean.class,
                                                    Object.class)),
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
                                            "consume",
                                            MethodType.methodType(
                                                    boolean.class,
                                                    Connection.class,
                                                    UUID.class))
                                    .asType(
                                            MethodType.methodType(
                                                    boolean.class,
                                                    Connection.class,
                                                    UUID.class)),
                            relayLookup
                                    .findStatic(
                                            relayClass,
                                            "clear",
                                            MethodType.methodType(
                                                    void.class,
                                                    Connection.class))
                                    .asType(
                                            MethodType.methodType(
                                                    void.class,
                                                    Connection.class)),
                            relayLookup
                                    .findStatic(
                                            relayClass,
                                            "suppressNextMark",
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
                iamzombieq$authorityHandles = handles;
                return handles;
            } catch (ReflectiveOperationException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }

    @Unique
    private static RuntimeException iamzombieq$bridgeFailure(
            String operation, Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(
                "Spider vehicle envelope bridge failed to " + operation,
                failure);
    }

    @Unique
    private void iamzombieq$rejectCurrentPacket(Entity vehicle, CallbackInfo callback) {
        resyncPlayerWithVehicle(vehicle);
        ((ServerGamePacketListenerImpl) (Object) this)
                .send(ClientboundMoveVehiclePacket.fromEntity(vehicle));
        iamzombieq$pendingCandidate = null;
        callback.cancel();
    }

    @Unique
    private void iamzombieq$resetScope() {
        if (iamzombieq$authorityParticipated) {
            iamzombieq$clearImpulseMarker();
        }
        iamzombieq$session = null;
        iamzombieq$scopeVehicle = null;
        iamzombieq$scopeDimension = null;
        iamzombieq$scopeController = null;
        iamzombieq$pendingCandidate = null;
        iamzombieq$pendingImpulseCorrection = false;
        iamzombieq$authorityParticipated = false;
    }

    @Unique
    private static boolean iamzombieq$same(double left, double right) {
        return Double.doubleToLongBits(left) == Double.doubleToLongBits(right);
    }
}
