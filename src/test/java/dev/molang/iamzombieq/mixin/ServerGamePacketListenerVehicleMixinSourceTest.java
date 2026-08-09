package dev.molang.iamzombieq.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import dev.molang.iamzombieq.util.StonecutterCapabilityMatrix;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ServerGamePacketListenerVehicleMixinSourceTest {
    @Test
    void vehiclePacketValidatorWrapsTheOneVanillaPlayerMoveAndObservesReturn() throws IOException {
        String mixins = SourceScan.resource("iamzombieq.mixins.json");
        String source = SourceScan.mainJava(
                "dev/molang/iamzombieq/mixin/ServerGamePacketListenerVehicleMixin.java");
        String compact = SourceScan.compact(source);

        assertTrue(mixins.contains("\"ServerGamePacketListenerVehicleMixin\""));
        assertTrue(source.contains("@Mixin(ServerGamePacketListenerImpl.class)"));
        assertTrue(source.contains("@WrapOperation("));
        assertTrue(compact.contains(
                "method=\"handleMoveVehicle(Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;)V\""));
        assertTrue(compact.contains(
                "target=\"Lnet/minecraft/world/entity/Entity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V\""));
        assertTrue(compact.contains("require=1"));
        assertTrue(source.contains("@At(\"RETURN\")"));
        assertTrue(source.contains("SpiderVehicleAuthoritySession"));
        assertTrue(source.contains("commitAccepted"));
    }

    @Test
    void scopeIsTheZombieOwnerControllingAnOwnedSpider() throws IOException {
        String source = SourceScan.mainJava(
                "dev/molang/iamzombieq/mixin/ServerGamePacketListenerVehicleMixin.java");
        String compact = SourceScan.compact(source);

        assertTrue(compact.contains("ZombiePlayerGates.isServerZombiePlayer(this.player)"));
        assertTrue(compact.contains("entityinstanceofSpiderspider"));
        assertTrue(compact.contains("entity.getControllingPassenger()==this.player"));
        assertTrue(compact.contains("spider.getControllingPassenger()==this.player"));
        assertTrue(compact.contains("MountCapability.isOwnedSpider(spider,this.player.getUUID())"));
        assertTrue(compact.contains("IAmZombieServerConfig.SPIDER_MOUNT_SPEED.get()"));
    }

    @Test
    void overEnvelopePacketUsesOnlyVanillaCorrectionWithoutPunishment() throws IOException {
        String source = SourceScan.mainJava(
                "dev/molang/iamzombieq/mixin/ServerGamePacketListenerVehicleMixin.java");
        String validation = SourceScan.methodBody(
                source, "private void iamzombieq$validateSpiderVehicleMove");
        String rejection = SourceScan.methodBody(
                source, "private void iamzombieq$rejectCurrentPacket");

        assertTrue(source.contains("cancellable = true"));
        assertTrue(validation.contains("iamzombieq$rejectCurrentPacket("));
        assertTrue(SourceScan.compact(validation).contains(
                "caseIAMZOMBIEQ_REJECT,IAMZOMBIEQ_REBASE"
                        + "->iamzombieq$rejectCurrentPacket(spider,callback);"));
        assertTrue(source.contains("resyncPlayerWithVehicle(vehicle)"));
        assertTrue(source.contains("ClientboundMoveVehiclePacket.fromEntity(vehicle)"));
        assertTrue(rejection.contains("callback.cancel()"),
                "correction must return before vanilla can absSnapTo the rejected target");
        assertFalse(source.contains(".disconnect("));
        assertFalse(source.contains(".hurt("));
        assertFalse(source.contains(".stopRiding("));
        assertFalse(source.contains("strike"));
        assertFalse(source.contains(".setDeltaMovement("));
        assertFalse(source.contains(".absSnapTo("));
    }

    @Test
    void contextUsesOnlyNamedVanillaFormulaInputsAndNoDistanceAllowance() throws IOException {
        String source = SourceScan.mainJava(
                "dev/molang/iamzombieq/internal/mount/SpiderVehicleMovementContext.java");
        String supercover = SourceScan.mainJava(
                "dev/molang/iamzombieq/internal/mount/SweptAabbVoxelSupercover.java");

        assertTrue(source.contains("VANILLA_GROUND_ACCELERATION_NUMERATOR"));
        assertTrue(source.contains("VANILLA_GROUND_DRAG_FACTOR"));
        assertTrue(source.contains("VANILLA_AIR_ACCELERATION_FACTOR"));
        assertTrue(source.contains("VANILLA_WATER_BASE_ACCELERATION"));
        assertTrue(source.contains("VANILLA_LAVA_ACCELERATION"));
        assertTrue(source.contains("Attributes.WATER_MOVEMENT_EFFICIENCY"));
        assertTrue(source.contains("Attributes.FRICTION_MODIFIER"));
        assertTrue(source.contains("Attributes.AIR_DRAG_MODIFIER"));
        assertTrue(source.contains("Attributes.MOVEMENT_EFFICIENCY"));
        assertTrue(source.contains("Attributes.BOUNCINESS"));
        assertTrue(source.contains("NeoForgeMod.SWIM_SPEED"));
        assertTrue(source.contains("state.getFriction(level, pos, spider)"));
        assertTrue(source.contains("state.getBounceRestitution(level, pos, spider)"));
        assertTrue(source.contains("float configuredSpeed"));
        assertTrue(source.contains("float waterEfficiency"));
        assertTrue(source.contains("float movementEfficiency"));
        assertTrue(source.contains("float frictionModifier"));
        assertTrue(source.contains("float airDragModifier"));
        assertTrue(source.contains("float swimSpeed"));
        assertTrue(source.contains("Math.nextUp"));
        assertTrue(source.contains("VANILLA_YAW_TABLE_SIZE = 65_536"));
        assertTrue(source.contains("computeYawRotationNormBound()"));
        assertTrue(source.contains("index < VANILLA_YAW_TABLE_SIZE"));
        assertTrue(source.contains("Math.hypot(sin, cos)"));
        assertTrue(source.contains(
                "sourceAcceleration,\n                        VANILLA_YAW_ROTATION_NORM_BOUND"));
        assertTrue(source.contains("VANILLA_WATER_CURRENT_SCALE = 0.014"));
        assertTrue(source.contains("VANILLA_FAST_LAVA_CURRENT_SCALE = 0.007"));
        assertTrue(source.contains("VANILLA_SLOW_LAVA_CURRENT_SCALE"));
        assertTrue(source.contains("VANILLA_CURRENT_MIN_IMPULSE"));
        assertTrue(source.contains("spider.isPushedByFluid()"));
        assertTrue(source.contains("EnvironmentAttributes.FAST_LAVA"));
        assertTrue(source.contains("fluid.is(FluidTags.WATER)"));
        assertTrue(source.contains("fluid.is(FluidTags.LAVA)"));
        assertTrue(source.contains("scan.hasWater |= fluid.is(FluidTags.WATER)"));
        assertTrue(source.contains("scan.hasLava |= fluid.is(FluidTags.LAVA)"));
        assertTrue(source.contains("Math.nextDown(box.maxX)"));
        assertTrue(source.contains("Math.nextDown(box.maxY)"));
        assertTrue(source.contains("Math.nextDown(box.maxZ)"));
        assertTrue(source.contains("SweptAabbVoxelSupercover.sampleFractions("));
        assertTrue(source.contains("for (double fraction : sampleFractions)"));
        assertFalse(source.contains("sweptSampleSteps"));
        assertTrue(supercover.contains("final class SweptAabbVoxelSupercover"));
        assertFalse(supercover.contains("public final class SweptAabbVoxelSupercover"));
        assertTrue(supercover.contains("new FaceEvents(origin.minX, deltaX)"));
        assertTrue(supercover.contains("new FaceEvents(origin.maxX, deltaX)"));
        assertTrue(supercover.contains("new FaceEvents(origin.minY, deltaY)"));
        assertTrue(supercover.contains("new FaceEvents(origin.maxY, deltaY)"));
        assertTrue(supercover.contains("new FaceEvents(origin.minZ, deltaZ)"));
        assertTrue(supercover.contains("new FaceEvents(origin.maxZ, deltaZ)"));
        assertTrue(supercover.contains("appendInterior(samples, previous, next)"));
        assertFalse(supercover.contains("MAX_SAMPLES"));
        assertFalse(supercover.contains("MAX_WORK"));
        assertTrue(source.contains("upperSum(\n                        drivenAcceleration,\n                        fluidCurrentAcceleration(inputs))"));
        assertFalse(source.contains("TOLERANCE"));
        assertFalse(source.contains("EPSILON"));
    }

    @Test
    void contextUsesNodeNativeTypedPlatformMotionInputs() throws IOException {
        String relative =
                "dev/molang/iamzombieq/internal/mount/SpiderVehicleMovementContext.java";
        String canonicalSource = SourceScan.mainJava(relative);
        String node = StonecutterCapabilityMatrix.nodeId();
        String generatedSource = Files.readString(Path.of(
                "versions", node, "build/generated/stonecutter/main/java").resolve(relative));
        String rawResolve = SourceScan.methodBody(
                canonicalSource,
                "static Optional<SpiderVehicleHorizontalEnvelope.MotionBound> resolve");
        String rawScan = SourceScan.methodBody(
                canonicalSource, "private static boolean scanBox");
        String activeSource = SourceScan.compact(SourceScan.stripComments(generatedSource));
        String activeResolve = SourceScan.compact(SourceScan.stripComments(SourceScan.methodBody(
                generatedSource,
                "static Optional<SpiderVehicleHorizontalEnvelope.MotionBound> resolve")));
        String activeScan = SourceScan.compact(SourceScan.stripComments(SourceScan.methodBody(
                generatedSource, "private static boolean scanBox")));
        String formula = SourceScan.methodBody(
                canonicalSource,
                "static Optional<SpiderVehicleHorizontalEnvelope.MotionBound> fromInputs");
        boolean modernMotionInputs = node.equals("26.2.x");
        boolean dimensionEnvironmentFastLava = modernMotionInputs
                || node.equals("26.1.x");
        boolean positionalEnvironmentFastLava = node.equals("1.21.11");

        String modernFriction = SourceScan.compact(
                "float frictionModifier = (float) spider.getAttributeValue("
                        + "Attributes.FRICTION_MODIFIER);");
        String modernAirDrag = SourceScan.compact(
                "float airDragModifier = (float) spider.getAttributeValue("
                        + "Attributes.AIR_DRAG_MODIFIER);");
        String modernEntityBounce = SourceScan.compact(
                "double entityBounciness = spider.getAttributeValue(Attributes.BOUNCINESS);");
        String legacyFriction = SourceScan.compact("float frictionModifier = 1.0F;");
        String legacyAirDrag = SourceScan.compact("float airDragModifier = 1.0F;");
        String legacyEntityBounce = SourceScan.compact("double entityBounciness = 0.0;");
        String dimensionEnvironmentFastLavaInput = SourceScan.compact(
                "level.environmentAttributes().getDimensionValue(EnvironmentAttributes.FAST_LAVA)");
        String positionalEnvironmentFastLavaInput = SourceScan.compact(
                "level.environmentAttributes().getValue("
                        + "EnvironmentAttributes.WATER_EVAPORATES, spider.blockPosition())");
        String legacyDimensionFastLavaInput = SourceScan.compact(
                "level.dimensionType().ultraWarm()");
        String modernBlockRestitution = SourceScan.compact(
                "double blockBounciness = state.getBounceRestitution(level, pos, spider);");
        String legacyBlockRestitution = SourceScan.compact(
                "double blockBounciness = 0.0;");
        String legacyFluidTypeGate = SourceScan.compact(
                "if ((fluid.is(FluidTags.WATER)"
                        + " && fluid.getFluidType() != NeoForgeMod.WATER_TYPE.value())"
                        + " || (fluid.is(FluidTags.LAVA)"
                        + " && fluid.getFluidType() != NeoForgeMod.LAVA_TYPE.value()))"
                        + " { return false; }");

        String activeEnvironmentImport =
                "//? if >=1.21.11\nimport net.minecraft.world.attribute.EnvironmentAttributes;";
        String inactiveEnvironmentImport =
                "//? if >=1.21.11\n//import net.minecraft.world.attribute.EnvironmentAttributes;";
        assertTrue(canonicalSource.contains(activeEnvironmentImport)
                        || canonicalSource.contains(inactiveEnvironmentImport),
                "the reversible source must retain the import boundary regardless of the active branch");
        assertEquals(4, SourceScan.countOccurrences(rawResolve, "//? if >=26.2 {"));
        assertEquals(1, SourceScan.countOccurrences(rawScan, "//? if >=26.2 {"));
        assertEquals(1, SourceScan.countOccurrences(rawResolve, "//? if >=26.1 {"));
        assertEquals(1, SourceScan.countOccurrences(
                rawResolve, "//? if >=1.21.11 && <26.1 {"));
        assertEquals(1, SourceScan.countOccurrences(rawResolve, "//? if <1.21.11 {"));
        assertEquals(1, SourceScan.countOccurrences(rawScan, "//? if <26.1 {"));
        assertEquals(4, SourceScan.countOccurrences(rawResolve, "//?} else {"));
        assertEquals(1, SourceScan.countOccurrences(rawScan, "//?} else {"));
        assertTrue(rawResolve.contains("float frictionModifier = 1.0F;"));
        assertTrue(rawResolve.contains("float airDragModifier = 1.0F;"));
        assertTrue(rawResolve.contains("double entityBounciness = 0.0;"));
        assertEquals(1, SourceScan.countOccurrences(rawResolve, "true,"));
        assertTrue(rawScan.contains("double blockBounciness = 0.0;"));
        assertFalse(formula.contains("//?"),
                "platform branches must stay out of the shared formula layer");
        String compactFormula = SourceScan.compact(SourceScan.stripComments(formula));
        assertTrue(compactFormula.contains(
                "inputs.pre262NativeMotion||minFriction>0.6"));
        assertTrue(compactFormula.contains(
                "inputs.pre262NativeMotion?inputs.minFriction:computeModifiedFriction("));
        assertTrue(compactFormula.contains(
                "inputs.pre262NativeMotion?(float)VANILLA_GROUND_DRAG_FACTOR:computeModifiedFriction("));
        assertFalse(rawResolve.contains("Class.forName")
                        || rawResolve.contains("java.lang.reflect")
                        || rawResolve.contains("@SuppressWarnings")
                        || rawScan.contains("Class.forName")
                        || rawScan.contains("java.lang.reflect")
                        || rawScan.contains("@SuppressWarnings"),
                "the platform input seam must remain typed and reflection-free");

        assertEquals(modernMotionInputs ? 1 : 0,
                SourceScan.countOccurrences(activeResolve, modernFriction));
        assertEquals(modernMotionInputs ? 1 : 0,
                SourceScan.countOccurrences(activeResolve, modernAirDrag));
        assertEquals(modernMotionInputs ? 1 : 0,
                SourceScan.countOccurrences(activeResolve, modernEntityBounce));
        assertEquals(modernMotionInputs ? 0 : 1,
                SourceScan.countOccurrences(activeResolve, legacyFriction));
        assertEquals(modernMotionInputs ? 0 : 1,
                SourceScan.countOccurrences(activeResolve, legacyAirDrag));
        assertEquals(modernMotionInputs ? 0 : 1,
                SourceScan.countOccurrences(activeResolve, legacyEntityBounce));
        assertEquals(modernMotionInputs ? 1 : 0,
                SourceScan.countOccurrences(activeResolve, "scan.hasLava,false,"));
        assertEquals(modernMotionInputs ? 0 : 1,
                SourceScan.countOccurrences(activeResolve, "scan.hasLava,true,"));
        assertEquals(dimensionEnvironmentFastLava ? 1 : 0,
                SourceScan.countOccurrences(activeResolve, dimensionEnvironmentFastLavaInput));
        assertEquals(positionalEnvironmentFastLava ? 1 : 0,
                SourceScan.countOccurrences(activeResolve, positionalEnvironmentFastLavaInput));
        assertEquals(dimensionEnvironmentFastLava || positionalEnvironmentFastLava ? 0 : 1,
                SourceScan.countOccurrences(activeResolve, legacyDimensionFastLavaInput));
        assertEquals(modernMotionInputs ? 1 : 0,
                SourceScan.countOccurrences(activeScan, modernBlockRestitution));
        assertEquals(modernMotionInputs ? 0 : 1,
                SourceScan.countOccurrences(activeScan, legacyBlockRestitution));
        assertEquals(Set.of("1.21.11", "1.21.10", "1.21.8").contains(node) ? 1 : 0,
                SourceScan.countOccurrences(activeScan, legacyFluidTypeGate));
        assertEquals(dimensionEnvironmentFastLava || positionalEnvironmentFastLava ? 1 : 0,
                SourceScan.countOccurrences(
                        activeSource,
                        "importnet.minecraft.world.attribute.EnvironmentAttributes;"));
    }

    @Test
    void sessionRebasesOnObservableAuthorityOrTimeDiscontinuity() throws IOException {
        String source = SourceScan.mainJava(
                "dev/molang/iamzombieq/mixin/ServerGamePacketListenerVehicleMixin.java");
        String compact = SourceScan.compact(source);
        String model = SourceScan.mainJava(
                "dev/molang/iamzombieq/internal/mount/SpiderVehicleHorizontalEnvelope.java");
        String bridge = SourceScan.mainJava(
                "dev/molang/iamzombieq/internal/mount/SpiderVehicleAuthoritySession.java");

        assertTrue(bridge.contains("level.getGameTime()"));
        assertTrue(bridge.contains("pending.resultingVelocityBound()"));
        assertTrue(bridge.contains("Math.max("));
        assertTrue(bridge.contains("snapshot.acceptedY()"));
        assertTrue(model.contains("Math.max(velocityBound, observedVelocity)"));
        assertTrue(model.contains("same(frame.serverY(), acceptedY)"));
        assertTrue(model.contains("acceptedY = assessment.frame.candidateY()"));
        assertTrue(model.contains("retainedVelocityAfter("));
        assertTrue(model.contains("distance == 0.0"));
        assertTrue(source.contains("System.nanoTime()"));
        assertTrue(compact.contains("iamzombieq$scopeVehicle==spider"));
        assertTrue(compact.contains("iamzombieq$scopeDimension==level.dimension()"));
        assertTrue(compact.contains("this.player.getUUID().equals(iamzombieq$scopeController)"));
        assertTrue(source.contains("entity.hurtMarked"));
        assertTrue(source.contains("IAMZOMBIEQ_NEEDS_SYNC_HANDLE"));
        assertTrue(source.contains("IAMZOMBIEQ_SYNC_POSITION_HANDLE"));
        assertTrue(source.contains("iamzombieq$readEntityFlag("));
        assertTrue(source.contains("iamzombieq$consumeImpulseMarker("));
        assertTrue(source.contains("iamzombieq$pendingImpulseCorrection = true"));
        assertTrue(source.contains("iamzombieq$pendingImpulseCorrection = false"));
        assertTrue(source.contains("iamzombieq$rebase("));
        assertTrue(model.contains("serverDelta > representableServerQuanta"));
        assertTrue(model.contains("current.monotonicNanos()"));
    }

    @Test
    void vanillaListenerTickPreparesTheFirstAndSwitchedVehicleSample() throws IOException {
        String source = SourceScan.mainJava(
                "dev/molang/iamzombieq/mixin/ServerGamePacketListenerVehicleMixin.java");
        String prepare = SourceScan.methodBody(
                source, "private void iamzombieq$prepareVehicleEnvelope");

        assertTrue(source.contains(
                "@Inject(method = \"tick()V\", at = @At(\"RETURN\"), require = 1)"));
        assertTrue(prepare.contains("entity != this.lastVehicle"));
        assertTrue(prepare.contains("iamzombieq$startScope("));
        assertFalse(prepare.contains("iamzombieq$rejectCurrentPacket("),
                "the vanilla-recognized first/switch baseline must not reject its next legal packet");
    }

    @Test
    void packagePrivateRuntimeModelIsReachedOnlyByResolvableOpaqueHandles()
            throws Throwable {
        String bridge = SourceScan.mainJava(
                "dev/molang/iamzombieq/internal/mount/SpiderVehicleAuthoritySession.java");
        String mixin = SourceScan.mainJava(
                "dev/molang/iamzombieq/mixin/ServerGamePacketListenerVehicleMixin.java");
        Class<?> bridgeClass =
                Class.forName(
                        "dev.molang.iamzombieq.internal.mount.SpiderVehicleAuthoritySession");
        Class<?> relayClass =
                Class.forName(
                        "dev.molang.iamzombieq.internal.mount.SpiderVehicleImpulseRelay");

        assertTrue(bridge.contains("final class SpiderVehicleAuthoritySession"));
        assertFalse(bridge.contains("public final class SpiderVehicleAuthoritySession"));
        assertFalse(bridge.contains("dev.molang.iamzombieq.mixin"));
        assertFalse(Modifier.isPublic(bridgeClass.getModifiers()));
        for (var method : bridgeClass.getDeclaredMethods()) {
            assertFalse(
                    Modifier.isPublic(method.getModifiers()),
                    () -> "unexpected public bridge method: " + method);
        }
        assertFalse(Modifier.isPublic(relayClass.getModifiers()));
        for (var method : relayClass.getDeclaredMethods()) {
            assertFalse(
                    Modifier.isPublic(method.getModifiers()),
                    () -> "unexpected public relay method: " + method);
        }

        assertTrue(mixin.contains("MethodHandles.privateLookupIn"));
        assertTrue(mixin.contains("findStatic"));
        assertTrue(mixin.contains("findVirtual"));
        assertTrue(mixin.contains("Object iamzombieq$session"));
        assertFalse(mixin.contains("SpiderVehicleAuthoritySession iamzombieq$session"));

        MethodHandles.Lookup lookup =
                MethodHandles.privateLookupIn(
                        bridgeClass, MethodHandles.lookup());
        lookup.findStatic(
                bridgeClass,
                "start",
                MethodType.methodType(
                        bridgeClass,
                        ServerLevel.class,
                        Spider.class,
                        long.class));
        lookup.findVirtual(
                bridgeClass,
                "assess",
                MethodType.methodType(
                        int.class,
                        ServerLevel.class,
                        Spider.class,
                        Vec3.class,
                        float.class,
                        long.class));
        lookup.findVirtual(
                bridgeClass,
                "commitAccepted",
                MethodType.methodType(void.class));
        lookup.findVirtual(
                bridgeClass,
                "rebase",
                MethodType.methodType(
                        void.class,
                        ServerLevel.class,
                        Spider.class,
                        long.class));
        lookup.findVirtual(
                bridgeClass,
                "matchesAuthoritativePosition",
                MethodType.methodType(boolean.class, Spider.class));
        lookup.findVirtual(
                bridgeClass,
                "hasPendingAdmission",
                MethodType.methodType(boolean.class));

        MethodHandles.Lookup relayLookup =
                MethodHandles.privateLookupIn(
                        relayClass, MethodHandles.lookup());
        relayLookup.findStatic(
                relayClass,
                "mark",
                MethodType.methodType(
                        void.class,
                        Connection.class,
                        java.util.UUID.class,
                        double.class,
                        double.class,
                        double.class,
                        double.class));
        relayLookup.findStatic(
                relayClass,
                "consume",
                MethodType.methodType(
                        boolean.class,
                        Connection.class,
                        java.util.UUID.class));
        relayLookup.findStatic(
                relayClass,
                "clear",
                MethodType.methodType(void.class, Connection.class));
        relayLookup.findStatic(
                relayClass,
                "suppressNextMark",
                MethodType.methodType(
                        void.class,
                        Connection.class,
                        java.util.UUID.class,
                        double.class,
                        double.class,
                        double.class,
                        double.class));

        String relayMixin = SourceScan.mainJava(
                "dev/molang/iamzombieq/mixin/ServerEntitySpiderImpulseMixin.java");
        assertTrue(relayMixin.contains("MethodHandles.privateLookupIn"));
        assertTrue(relayMixin.contains("findGetter"));
        assertTrue(relayMixin.contains("findStatic"));
        assertTrue(relayMixin.contains("ServerEntity.class.getClassLoader()"));
    }

    @Test
    void levelTrackerRelaysAnImpulseMarkerExactlyOnceToConnectionPreparation()
            throws IOException {
        String mixins = SourceScan.resource("iamzombieq.mixins.json");
        String relay = SourceScan.mainJava(
                "dev/molang/iamzombieq/mixin/ServerEntitySpiderImpulseMixin.java");
        String compact = SourceScan.compact(relay);
        String head = SourceScan.methodBody(
                relay, "private void iamzombieq$captureSpiderImpulse");
        String model = SourceScan.mainJava(
                "dev/molang/iamzombieq/internal/mount/SpiderVehicleImpulseRelay.java");
        String listener = SourceScan.mainJava(
                "dev/molang/iamzombieq/mixin/ServerGamePacketListenerVehicleMixin.java");
        String cleanup = SourceScan.mainJava(
                "dev/molang/iamzombieq/mixin/ConnectionSpiderImpulseCleanupMixin.java");

        assertTrue(mixins.contains("\"ServerEntitySpiderImpulseMixin\""));
        assertTrue(mixins.contains("\"ConnectionSpiderImpulseCleanupMixin\""));
        assertTrue(relay.contains("@Mixin(ServerEntity.class)"));
        assertTrue(compact.contains(
                "@Inject(method=\"sendChanges()V\",at=@At(\"HEAD\"),require=1)"));
        assertTrue(head.contains("entity instanceof Spider spider"));
        assertTrue(head.contains("ZombiePlayerGates.isServerZombiePlayer(controller)"));
        assertTrue(head.contains("MountCapability.isOwnedSpider("));
        assertTrue(
                head.indexOf("entity instanceof Spider spider")
                        < head.indexOf("iamzombieq$readFlag("),
                "non-applicable tracked entities must not resolve handles or read private flags");
        assertTrue(head.contains("IAMZOMBIEQ_NEEDS_SYNC"));
        assertTrue(head.contains("entity.hurtMarked"));
        assertTrue(head.contains("IAMZOMBIEQ_SYNC_POSITION"));
        assertTrue(head.contains("controller.connection.getConnection()"));
        assertTrue(head.contains("spider.getUUID()"));
        assertTrue(head.contains("entity.getDeltaMovement()"));
        assertTrue(head.contains("entity.getX()"));
        assertTrue(head.contains("flagBits"));
        assertTrue(model.contains("WeakHashMap<Connection, Marker>"));
        assertTrue(model.contains("static synchronized void mark("));
        assertTrue(model.contains("static synchronized boolean consume("));
        assertTrue(model.contains("MARKERS.remove("));
        assertTrue(model.contains("static synchronized void suppressNextMark("));
        assertTrue(model.contains("static synchronized void clear("));
        assertTrue(model.contains("Suppression suppression"));
        assertTrue(model.contains("horizontalVelocityBound"));
        assertTrue(model.contains("Math.hypot(deltaX, deltaZ)"));
        assertTrue(model.contains("currentX == x"));
        assertTrue(model.contains("currentZ == z"));
        assertTrue(model.contains("currentMagnitude <= horizontalVelocityBound"));
        assertFalse(model.contains("deltaY"));
        assertFalse(model.contains("flagBits"));
        assertTrue(listener.contains("iamzombieq$consumeImpulseMarker(spider.getUUID())"));
        assertTrue(listener.contains(".getConnection()"));
        assertTrue(listener.contains("spider.getDeltaMovement()"));
        assertTrue(listener.contains("IAMZOMBIEQ_MARK_PARTICIPATION_HANDLE"));
        assertTrue(listener.contains("iamzombieq$pendingImpulseCorrection = true"));
        assertTrue(listener.contains("iamzombieq$rejectCurrentPacket(spider, callback)"));
        assertTrue(cleanup.contains("@Mixin(Connection.class)"));
        assertTrue(cleanup.contains("handleDisconnection()V"));
        assertTrue(cleanup.contains("iamzombieq$spiderAuthorityParticipated"));
        assertTrue(cleanup.contains("(Connection) (Object) this"));
        assertFalse(relay.contains("iamzombieq$setSyncPosition"));
        assertFalse(relay.contains("Clientbound"));
        assertFalse(model.contains("Entity "));
        assertFalse(mixins.contains("\"ServerCommonPacketListenerImpulseCleanupMixin\""));
        assertFalse(mixins.contains("\"EntityImpulseAccessor\""));
    }

    @Test
    void neverApplicableListenerDoesNotInitializeTheOpaqueBridge()
            throws IOException {
        String listener = SourceScan.mainJava(
                "dev/molang/iamzombieq/mixin/ServerGamePacketListenerVehicleMixin.java");
        String prepare = SourceScan.methodBody(
                listener, "private void iamzombieq$prepareVehicleEnvelope");
        String reset = SourceScan.methodBody(
                listener, "private void iamzombieq$resetScope");
        String cleanup = SourceScan.mainJava(
                "dev/molang/iamzombieq/mixin/ConnectionSpiderImpulseCleanupMixin.java");
        String disconnect = SourceScan.methodBody(
                cleanup, "private void iamzombieq$clearVehicleImpulseOnDisconnect");

        assertTrue(
                prepare.indexOf("!iamzombieq$isApplicableSpider(entity)")
                        < prepare.indexOf("iamzombieq$startScope("));
        assertTrue(reset.contains("if (iamzombieq$authorityParticipated)"));
        assertTrue(reset.contains("iamzombieq$clearImpulseMarker()"));
        assertTrue(reset.contains("iamzombieq$authorityParticipated = false"));
        assertTrue(disconnect.contains(
                "if (!iamzombieq$spiderAuthorityParticipated)"));
        assertTrue(
                disconnect.indexOf(
                                "if (!iamzombieq$spiderAuthorityParticipated)")
                        < disconnect.indexOf(
                                "iamzombieq$clearImpulseHandle()"),
                "a never-applicable connection must return before bridge lookup");
    }
}
