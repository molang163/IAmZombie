package dev.molang.iamzombieq.gametest;

import dev.molang.iamzombieq.IAmZombieConfig;
import dev.molang.iamzombieq.IAmZombieEntities;
import dev.molang.iamzombieq.entity.HerobrineEntity;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.herobrine.HerobrineEncounter;
import dev.molang.iamzombieq.rules.herobrine.HerobrineRules;
import dev.molang.iamzombieq.state.HerobrineEncounterState;
import dev.molang.iamzombieq.state.HerobrineRespawnSnapshot;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Runtime-only Herobrine assertions that need the real event bus and server entity ticker. */
final class IAmZombieHerobrineGameTestBodies {
    private static final Vec3 GAZE_PLAYER_POS = new Vec3(1.5, 2.0, 1.5);
    private static final Vec3 GAZE_HEROBRINE_POS = new Vec3(1.5, 2.0, 5.5);
    private static final Vec3 LETHAL_HEROBRINE_POS = new Vec3(1.5, 2.0, 2.5);
    private static final Vec3 CAVE_PLAYER_POS = new Vec3(24.5, 8.0, 24.5);
    private static final double HEROBRINE_CLEANUP_RADIUS = 32.0;
    private static final float DEATH_Y_ROT = 37.0F;
    private static final float DEATH_X_ROT = -21.0F;

    private IAmZombieHerobrineGameTestBodies() {
    }

    static void herobrineLethalAttackRespawnsInPlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ConfigSnapshot config = ConfigSnapshot.capture();
        ServerPlayer oldPlayer = null;
        UUID playerId = null;
        HerobrineEntity herobrine = null;
        EmbeddedChannel channel = null;
        Vec3 deathPosition = null;

        try {
            configureLethal();
            oldPlayer = GameTestPlayers.spawnConnectedZombiePlayer(
                    helper, ZombieForm.NORMAL, ZombieSize.ADULT);
            playerId = oldPlayer.getUUID();

            Channel rawChannel = oldPlayer.connection.getConnection().channel();
            GameTestAssertions.assertTrue(helper, rawChannel instanceof EmbeddedChannel,
                    "connected GameTest player must use an EmbeddedChannel");
            channel = (EmbeddedChannel) rawChannel;

            oldPlayer.setData(IAmZombieAttachments.HEROBRINE_ENCOUNTER,
                    new HerobrineEncounterState(0, Long.MIN_VALUE, -1L, false));
            oldPlayer.getInventory().clearContent();
            oldPlayer.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 3));
            oldPlayer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            oldPlayer.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            oldPlayer.experienceLevel = 7;
            oldPlayer.experienceProgress = 0.25F;
            oldPlayer.totalExperience = 123;

            deathPosition = helper.absoluteVec(GAZE_PLAYER_POS);
            oldPlayer.snapTo(deathPosition.x, deathPosition.y, deathPosition.z, DEATH_Y_ROT, DEATH_X_ROT);
            List<ItemStack> expectedInventory = snapshotInventory(oldPlayer);

            herobrine = createHerobrine(level);
            GameTestAssertions.assertTrue(helper, herobrine != null, "failed to create Herobrine for the lethal test");
            Vec3 herobrinePosition = helper.absoluteVec(LETHAL_HEROBRINE_POS);
            herobrine.snapTo(herobrinePosition.x, herobrinePosition.y, herobrinePosition.z, 180.0F, 0.0F);
            GameTestAssertions.assertTrue(helper, level.addFreshEntity(herobrine),
                    "failed to add Herobrine to the server level");

            releaseOutbound(channel);
            long triggerTick = level.getGameTime();
            oldPlayer.attack(herobrine);

            GameTestAssertions.assertFalse(helper, oldPlayer.isAlive(), "the lethal Herobrine attack must really kill the old player");
            GameTestAssertions.assertTrue(helper, oldPlayer.getHealth() <= 0.0F,
                    "the old player's health must reach zero after the lethal attack");
            assertInventoryEmpty(helper, oldPlayer, "the dying player's live inventory");
            GameTestAssertions.assertTrue(helper, herobrine.isRemoved(), "the lethal Herobrine must discard itself");
            assertEncounterState(helper, oldPlayer, triggerTick, "the dead player");

            HerobrineRespawnSnapshot pending =
                    oldPlayer.getData(IAmZombieAttachments.HEROBRINE_PENDING_RESPAWN);
            GameTestAssertions.assertTrue(helper, pending.isPresent(), "the dead player must carry a durable pending respawn");
            assertPendingSnapshot(helper, pending, deathPosition, expectedInventory);

            ServerGamePacketListenerImpl listener = oldPlayer.connection;
            listener.handleClientCommand(new ServerboundClientCommandPacket(
                    ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            ServerPlayer newPlayer = listener.getPlayer();

            GameTestAssertions.assertTrue(helper, newPlayer != oldPlayer, "death respawn must create a new ServerPlayer instance");
            GameTestAssertions.assertTrue(helper, newPlayer.getUUID().equals(playerId),
                    "the respawned ServerPlayer must retain the old player's UUID");
            GameTestAssertions.assertTrue(helper, oldPlayer.isRemoved(), "the old player must be removed during respawn");
            assertPlayerReplacement(helper, level, playerId, oldPlayer, newPlayer, listener);
            assertInventoryMatches(helper, newPlayer.getInventory(), expectedInventory,
                    "the respawned player's inventory");
            assertExperience(helper, newPlayer, "the respawned player");
            assertEncounterState(helper, newPlayer, triggerTick, "the respawned player");
            GameTestAssertions.assertFalse(helper, newPlayer.getData(IAmZombieAttachments.HEROBRINE_PENDING_RESPAWN).isPresent(),
                    "the respawn event must clear the durable pending snapshot");

            ClientboundPlayerPositionPacket deathPacket = drainLastPositionPacket(channel);
            GameTestAssertions.assertTrue(helper, deathPacket != null,
                    "respawn must emit a player-position packet for the restored death position");
            GameTestAssertions.assertTrue(helper, deathPacket.relatives().isEmpty(),
                    "the restored death position packet must use absolute coordinates");
            assertPosition(helper, deathPacket.change().position(), deathPosition,
                    "the restored death position packet");
            GameTestAssertions.assertTrue(helper, Float.compare(deathPacket.change().yRot(), DEATH_Y_ROT) == 0,
                    "the restored death position packet must preserve yRot=" + DEATH_Y_ROT);
            GameTestAssertions.assertTrue(helper, Float.compare(deathPacket.change().xRot(), DEATH_X_ROT) == 0,
                    "the restored death position packet must preserve xRot=" + DEATH_X_ROT);
            GameTestAssertions.assertTrue(helper, deathPacket.change().yRot() != 0.0F && deathPacket.change().xRot() != 0.0F,
                    "the position-packet rotation snapshot must be non-zero");

            GameTestAssertions.assertFalse(helper, GameTestPlayers.hasClientLoaded(listener),
                    "the mock connection must wait for test-side PlayerLoaded after respawn");
            // Test-side protocol completion only. This does not claim observation of a real client's packet order.
            listener.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(deathPacket.id()));
            listener.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            listener.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(
                    deathPosition, DEATH_Y_ROT, DEATH_X_ROT, false, false));

            GameTestAssertions.assertTrue(helper, GameTestPlayers.hasClientLoaded(listener),
                    "test-side PlayerLoaded must complete the respawn handshake");
            assertPosition(helper, newPlayer.position(), deathPosition, "the final server player");
            GameTestAssertions.assertTrue(helper, Float.compare(newPlayer.getYRot(), DEATH_Y_ROT) == 0,
                    "the final server player must preserve yRot=" + DEATH_Y_ROT);
            GameTestAssertions.assertTrue(helper, Float.compare(newPlayer.getXRot(), DEATH_X_ROT) == 0,
                    "the final server player must preserve xRot=" + DEATH_X_ROT);
        } finally {
            try {
                if (playerId != null) {
                    completeDeadRespawnForCleanup(level, playerId);
                }
            } finally {
                try {
                    if (playerId != null) {
                        GameTestPlayers.disconnectConnectedPlayer(helper, playerId);
                    }
                } finally {
                    try {
                        if (herobrine != null && !herobrine.isRemoved()) {
                            herobrine.discard();
                        }
                    } finally {
                        try {
                            if (deathPosition != null) {
                                discardDeathDrops(level, deathPosition);
                            }
                        } finally {
                            try {
                                if (channel != null) {
                                    try {
                                        releaseOutbound(channel);
                                    } finally {
                                        channel.finishAndReleaseAll();
                                    }
                                }
                            } finally {
                                config.restore();
                            }
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    static void herobrineGazeRecordsNonlethalSighting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        HerobrineEncounterState originalState = player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER);
        ConfigSnapshot config = ConfigSnapshot.capture();
        HerobrineEntity herobrine = null;

        try {
            configureCommon();
            IAmZombieConfig.HEROBRINE_CAVE_CHECK_INTERVAL_TICKS.set(1);
            IAmZombieConfig.HEROBRINE_CAVE_SPAWN_CHANCE.set(0.0);
            IAmZombieConfig.HEROBRINE_OMEN_ENABLED.set(false);
            player.setData(IAmZombieAttachments.HEROBRINE_ENCOUNTER, new HerobrineEncounterState());

            Vec3 playerPos = helper.absoluteVec(GAZE_PLAYER_POS);
            Vec3 herobrinePos = helper.absoluteVec(GAZE_HEROBRINE_POS);
            player.snapTo(playerPos.x, playerPos.y, playerPos.z, 0.0F, 0.0F);
            herobrine = createHerobrine(level);
            if (herobrine == null) {
                GameTestAssertions.fail(helper, "failed to create Herobrine for the gaze test");
                return;
            }
            herobrine.snapTo(herobrinePos.x, herobrinePos.y, herobrinePos.z, 180.0F, 0.0F);
            if (!level.addFreshEntity(herobrine)) {
                GameTestAssertions.fail(helper, "failed to add Herobrine to the server level");
                return;
            }

            long expectedSightingTick = level.getGameTime();
            player.doTick();

            HerobrineEncounterState state = player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER);
            if (state.sightings() != 1) {
                GameTestAssertions.fail(helper, "first non-lethal gaze should record exactly one sighting, got " + state.sightings());
                return;
            }
            if (state.lastSightingTick() != expectedSightingTick) {
                GameTestAssertions.fail(helper, "lastSightingTick should equal the gaze game time " + expectedSightingTick
                        + ", got " + state.lastSightingTick());
                return;
            }
            if (state.lastLethalTick() != -1L) {
                GameTestAssertions.fail(helper, "a non-lethal gaze must preserve lastLethalTick=-1, got " + state.lastLethalTick());
                return;
            }
            if (state.escalatedBefore()) {
                GameTestAssertions.fail(helper, "a first non-lethal gaze must not set escalatedBefore");
                return;
            }
            if (!herobrine.isRemoved()) {
                GameTestAssertions.fail(helper, "Herobrine should discard itself after a non-lethal gaze");
                return;
            }
            helper.succeed();
        } finally {
            if (herobrine != null && !herobrine.isRemoved()) {
                herobrine.discard();
            }
            discardHerobrines(level, player.getBoundingBox().inflate(HEROBRINE_CLEANUP_RADIUS));
            player.setData(IAmZombieAttachments.HEROBRINE_ENCOUNTER, originalState);
            config.restore();
        }
    }

    static void herobrineRightClickIsCancelled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = GameTestPlayers.spawnConnectedZombiePlayer(
                helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        UUID playerId = player.getUUID();
        player.setData(IAmZombieAttachments.HEROBRINE_ENCOUNTER,
                new HerobrineEncounterState(2, 123L, 456L, true));
        HerobrineEncounterState originalState =
                player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER);
        HerobrineEntity herobrine = createHerobrine(level);
        if (herobrine == null) {
            GameTestPlayers.disconnectConnectedPlayer(helper, playerId);
            GameTestAssertions.fail(helper, "failed to create Herobrine for the right-click test");
            return;
        }

        HerobrineInteractObserver observer =
                new HerobrineInteractObserver(herobrine.getUUID(), player.getUUID());
        boolean observerRegistered = false;
        try {
            Vec3 spawn = helper.absoluteVec(LETHAL_HEROBRINE_POS);
            herobrine.snapTo(spawn.x, spawn.y, spawn.z, 0.0F, 0.0F);
            GameTestAssertions.assertTrue(helper, level.addFreshEntity(herobrine),
                    "failed to add Herobrine for the right-click test");

            NeoForge.EVENT_BUS.register(observer);
            observerRegistered = true;
            ServerboundInteractPacket locationPacket =
                    //? if >=26.1
                    new ServerboundInteractPacket(herobrine.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, false);
                    //? if <26.1
                    //ServerboundInteractPacket.createInteractionPacket(herobrine, false, InteractionHand.MAIN_HAND, Vec3.ZERO);
            player.connection.handleInteract(locationPacket);

            //? if >=26.2 {
            GameTestAssertions.assertTrue(helper, observer.generalEvents == 1,
                    "the merged location packet must publish exactly one general EntityInteract event");
            GameTestAssertions.assertTrue(helper, observer.specificEvents == 0,
                    "26.2 must not publish the removed split-specific event");
            GameTestAssertions.assertTrue(helper, observer.lastGeneralCanceled,
                    "the general handler must cancel the merged Herobrine interaction");
            GameTestAssertions.assertTrue(helper, observer.lastGeneralResult == InteractionResult.SUCCESS_SERVER,
                    "the merged Herobrine interaction must return SUCCESS_SERVER");
            //?} else {
            /*GameTestAssertions.assertTrue(helper, observer.specificEvents == 1,
                    "a location-bearing packet must publish exactly one EntityInteractSpecific event");
            GameTestAssertions.assertTrue(helper, observer.generalEvents == 0,
                    "a canceled location-bearing packet must not fall through to EntityInteract");
            GameTestAssertions.assertTrue(helper, observer.lastSpecificCanceled,
                    "the specific handler must cancel the location-bearing Herobrine interaction");
            GameTestAssertions.assertTrue(helper, observer.lastSpecificResult == InteractionResult.SUCCESS_SERVER,
                    "the location-bearing Herobrine interaction must return SUCCESS_SERVER");
            *///?}
            GameTestAssertions.assertFalse(helper, herobrine.isRemoved(),
                    "a canceled location-bearing interaction must not remove Herobrine");
            GameTestAssertions.assertTrue(helper,
                    player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER).equals(originalState),
                    "a canceled location-bearing interaction must not advance the Herobrine encounter");

            observer.reset();
            dispatchNodeNativeGeneralInteraction(player, herobrine);

            GameTestAssertions.assertTrue(helper, observer.generalEvents == 1,
                    "the node-native general interaction must publish exactly one EntityInteract event");
            GameTestAssertions.assertTrue(helper, observer.specificEvents == 0,
                    "the node-native general interaction must not publish the split-specific event");
            GameTestAssertions.assertTrue(helper, observer.lastGeneralCanceled,
                    "the general handler must cancel the general Herobrine interaction");
            GameTestAssertions.assertTrue(helper, observer.lastGeneralResult == InteractionResult.SUCCESS_SERVER,
                    "the general Herobrine interaction must return SUCCESS_SERVER");
            GameTestAssertions.assertFalse(helper, herobrine.isRemoved(),
                    "a canceled general interaction must not remove Herobrine");
            GameTestAssertions.assertTrue(helper,
                    player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER).equals(originalState),
                    "a canceled general interaction must not advance the Herobrine encounter");
            helper.succeed();
        } finally {
            try {
                if (observerRegistered) {
                    NeoForge.EVENT_BUS.unregister(observer);
                }
                if (!herobrine.isRemoved()) {
                    herobrine.discard();
                }
            } finally {
                GameTestPlayers.disconnectConnectedPlayer(helper, playerId);
            }
        }
    }

    static void herobrineNaturalCaveSpawnSetsPhase(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos playerBlock = BlockPos.containing(helper.absoluteVec(CAVE_PLAYER_POS));
        BlockPos floorBlock = helper.absolutePos(new BlockPos(24, 7, 24));
        BlockPos roofBlock = helper.absolutePos(new BlockPos(24, 16, 24));

        if (playerBlock.getY() >= level.getSeaLevel() - HerobrineRules.CAVE_SPAWN_SEA_LEVEL_OFFSET) {
            GameTestAssertions.fail(helper, "precondition: baked cave player must be below the Herobrine cave height gate"
                    + " (playerY=" + playerBlock.getY()
                    + ", seaLevel=" + level.getSeaLevel()
                    + ", localOriginY=" + helper.absolutePos(BlockPos.ZERO).getY() + ")");
            return;
        }
        if (!level.getBlockState(floorBlock).is(Blocks.STONE)) {
            GameTestAssertions.fail(helper, "precondition: baked cave floor must be stone at " + floorBlock);
            return;
        }
        if (!level.getBlockState(roofBlock).is(Blocks.STONE)) {
            GameTestAssertions.fail(helper, "precondition: baked cave roof must be stone at " + roofBlock);
            return;
        }

        boolean[] skylightTimedOut = {false};
        //? if >=26.1
        helper.runBeforeTestEnd(() -> {
        //? if <26.1
        //helper.runAtTickTime(helper.testInfo.getTimeoutTicks() - 1, () -> {
            skylightTimedOut[0] = true;
            GameTestAssertions.fail(helper, "fixture skylight readiness did not complete before the cave test deadline"
                    + " (player=" + playerBlock
                    + ", skyVisible=" + level.canSeeSky(playerBlock)
                    + ", skyBrightness=" + level.getBrightness(LightLayer.SKY, playerBlock)
                    + ", floor=" + level.getBlockState(floorBlock)
                    + ", roof=" + level.getBlockState(roofBlock) + ")");
        });
        helper.startSequence()
                .thenWaitUntil(() -> {
                    GameTestAssertions.assertFalse(helper, skylightTimedOut[0], "baked cave skylight readiness timed out");
                    GameTestAssertions.assertFalse(helper, level.canSeeSky(playerBlock), "waiting for baked cave skylight to settle");
                })
                .thenExecute(() -> {
                    try {
                        FakePlayer player =
                                GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
                        HerobrineEncounterState originalState =
                                player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER);
                        ConfigSnapshot config = ConfigSnapshot.capture();

                        try {
                            configureCommon();
                            IAmZombieConfig.HEROBRINE_CAVE_CHECK_INTERVAL_TICKS.set(1);
                            IAmZombieConfig.HEROBRINE_CAVE_SPAWN_CHANCE.set(1.0);
                            IAmZombieConfig.HEROBRINE_OMEN_ENABLED.set(false);

                            Vec3 playerPos = helper.absoluteVec(CAVE_PLAYER_POS);
                            player.snapTo(playerPos.x, playerPos.y, playerPos.z, 0.0F, 0.0F);
                            long now = level.getGameTime();
                            player.setData(IAmZombieAttachments.HEROBRINE_ENCOUNTER,
                                    new HerobrineEncounterState(2, now, -1L, false));

                            player.doTick();

                            List<HerobrineEntity> spawned = level.getEntitiesOfClass(
                                    HerobrineEntity.class,
                                    player.getBoundingBox().inflate(HEROBRINE_CLEANUP_RADIUS),
                                    Entity::isAlive);
                            if (spawned.size() != 1) {
                                GameTestAssertions.fail(helper, "one cave check at chance 1.0 should spawn exactly one Herobrine, got "
                                        + spawned.size());
                                return;
                            }

                            HerobrineEntity herobrine = spawned.getFirst();
                            BlockPos spawnBlock = herobrine.blockPosition();
                            double dx = spawnBlock.getX() - playerBlock.getX();
                            double dz = spawnBlock.getZ() - playerBlock.getZ();
                            double horizontalDistance = Math.hypot(dx, dz);
                            double roundingTolerance = Math.sqrt(0.5);
                            double minDistance = HerobrineRules.CAVE_SPAWN_HORIZONTAL_DISTANCE - roundingTolerance;
                            double maxDistance =
                                    HerobrineRules.CAVE_SPAWN_HORIZONTAL_DISTANCE * 2 - 1 + roundingTolerance;
                            if (horizontalDistance < minDistance || horizontalDistance > maxDistance) {
                                GameTestAssertions.fail(helper, "natural Herobrine should land in the rounded 12-23 block ring, got "
                                        + horizontalDistance);
                                return;
                            }
                            if (spawnBlock.getY() != playerBlock.getY()
                                    || !level.getBlockState(spawnBlock).isAir()
                                    || !level.getBlockState(spawnBlock.above()).isAir()
                                    || level.getBlockState(spawnBlock.below()).isAir()) {
                                GameTestAssertions.fail(helper, "natural Herobrine should occupy the baked cave's"
                                        + " supported two-block air column");
                                return;
                            }
                            if (herobrine.getEncounterPhase() != HerobrineEncounter.Phase.ESCALATION) {
                                GameTestAssertions.fail(helper, "spawned Herobrine should publish the player's ESCALATION phase, got "
                                        + herobrine.getEncounterPhase());
                                return;
                            }
                        } finally {
                            try {
                                discardHerobrines(
                                        level, player.getBoundingBox().inflate(HEROBRINE_CLEANUP_RADIUS));
                            } finally {
                                try {
                                    player.setData(IAmZombieAttachments.HEROBRINE_ENCOUNTER, originalState);
                                } finally {
                                    config.restore();
                                }
                            }
                        }
                    } catch (GameTestAssertException failure) {
                        throw failure;
                    } catch (RuntimeException failure) {
                        GameTestAssertException assertion = helper.assertionException(Component.literal(
                                "cave fixture execution failed: "
                                        + failure.getClass().getSimpleName() + ": " + failure.getMessage()));
                        assertion.addSuppressed(failure);
                        throw assertion;
                    }
                })
                .thenSucceed();
    }

    static void herobrineDiscardsAfterMaxLifetime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ConfigSnapshot config = ConfigSnapshot.capture();
        HerobrineEntity herobrine = createHerobrine(level);
        if (herobrine == null) {
            GameTestAssertions.fail(helper, "failed to create Herobrine for the lifetime test");
            return;
        }

        boolean[] cleaned = {false};
        Runnable cleanup = () -> {
            if (cleaned[0]) {
                return;
            }
            cleaned[0] = true;
            try {
                if (!herobrine.isRemoved()) {
                    herobrine.discard();
                }
            } finally {
                config.restore();
            }
        };

        try {
            configureCommon();
            IAmZombieConfig.HEROBRINE_CAVE_CHECK_INTERVAL_TICKS.set(1);
            IAmZombieConfig.HEROBRINE_CAVE_SPAWN_CHANCE.set(0.0);
            IAmZombieConfig.HEROBRINE_OMEN_ENABLED.set(false);
            // GameTest force-loads only structure chunks, not padding; anchor to the first structure block.
            Vec3 spawn = Vec3.atBottomCenterOf(helper.absolutePos(BlockPos.ZERO)).add(0.0, 2.0, 0.0);
            herobrine.snapTo(spawn.x, spawn.y, spawn.z, 0.0F, 0.0F);
            herobrine.setPersistenceRequired();
            if (!level.addFreshEntity(herobrine)) {
                cleanup.run();
                GameTestAssertions.fail(helper, "failed to add Herobrine to the server level");
                return;
            }
            GameTestAssertions.assertTrue(helper, level.isPositionEntityTicking(herobrine.blockPosition()),
                    "lifetime fixture must start Herobrine in an entity-ticking chunk");

            helper.runAtTickTime(900L, () -> {
                try {
                    if (!level.isPositionEntityTicking(herobrine.blockPosition())) {
                        GameTestAssertions.fail(helper, "lifetime fixture left entity-ticking coverage before tick 900");
                    }
                    if (herobrine.tickCount != 900) {
                        GameTestAssertions.fail(helper, "natural server ticking should reach tickCount 900 exactly, got "
                                + herobrine.tickCount);
                    }
                    if (herobrine.isRemoved() || !herobrine.isAlive()) {
                        GameTestAssertions.fail(helper, "Herobrine must still exist at tickCount 900");
                    }
                } catch (RuntimeException | Error failure) {
                    cleanup.run();
                    throw failure;
                }
            });
            helper.runAtTickTime(901L, () -> {
                try {
                    if (herobrine.tickCount != 901) {
                        GameTestAssertions.fail(helper, "natural server ticking should reach tickCount 901 exactly, got "
                                + herobrine.tickCount);
                    }
                    if (!herobrine.isRemoved()
                            || herobrine.getRemovalReason() != Entity.RemovalReason.DISCARDED) {
                        GameTestAssertions.fail(helper, "Herobrine must be discarded by its natural tick at tickCount 901");
                    }
                    helper.succeed();
                } finally {
                    cleanup.run();
                }
            });
            //? if >=26.1
            helper.runBeforeTestEnd(() -> {
            //? if <26.1
            //helper.runAtTickTime(helper.testInfo.getTimeoutTicks() - 1, () -> {
                try {
                    GameTestAssertions.fail(helper, "Herobrine lifetime callbacks did not complete before the timeout");
                } finally {
                    cleanup.run();
                }
            });
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }

    private static List<ItemStack> snapshotInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> snapshot = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            snapshot.add(inventory.getItem(slot).copy());
        }
        return snapshot;
    }

    private static void assertInventoryEmpty(GameTestHelper helper, ServerPlayer player, String label) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            GameTestAssertions.assertTrue(helper, inventory.getItem(slot).isEmpty(), label + " must be empty at slot " + slot);
        }
    }

    private static void assertInventoryMatches(
            GameTestHelper helper, Inventory actual, List<ItemStack> expected, String label) {
        GameTestAssertions.assertTrue(helper, actual.getContainerSize() == expected.size(),
                label + " must retain the complete main, armor, offhand, body, and saddle slot range");
        for (int slot = 0; slot < expected.size(); slot++) {
            GameTestAssertions.assertTrue(helper, ItemStack.matches(actual.getItem(slot), expected.get(slot)),
                    label + " differs at slot " + slot + ": expected " + expected.get(slot)
                            + ", got " + actual.getItem(slot));
        }
    }

    private static void assertInventoryMatches(
            GameTestHelper helper, List<ItemStack> actual, List<ItemStack> expected, String label) {
        GameTestAssertions.assertTrue(helper, actual.size() == expected.size(), label + " must retain every inventory slot");
        for (int slot = 0; slot < expected.size(); slot++) {
            GameTestAssertions.assertTrue(helper, ItemStack.matches(actual.get(slot), expected.get(slot)),
                    label + " differs at slot " + slot + ": expected " + expected.get(slot)
                            + ", got " + actual.get(slot));
        }
    }

    private static void assertPendingSnapshot(
            GameTestHelper helper,
            HerobrineRespawnSnapshot pending,
            Vec3 deathPosition,
            List<ItemStack> expectedInventory) {
        assertPosition(helper, new Vec3(pending.x(), pending.y(), pending.z()), deathPosition,
                "the durable pending snapshot");
        GameTestAssertions.assertTrue(helper, Float.compare(pending.yRot(), DEATH_Y_ROT) == 0,
                "the durable pending snapshot must preserve yRot=" + DEATH_Y_ROT);
        GameTestAssertions.assertTrue(helper, Float.compare(pending.xRot(), DEATH_X_ROT) == 0,
                "the durable pending snapshot must preserve xRot=" + DEATH_X_ROT);
        assertInventoryMatches(helper, pending.inventory(), expectedInventory, "the durable pending inventory");
        GameTestAssertions.assertTrue(helper, pending.experienceLevel() == 7,
                "the durable pending snapshot must preserve experience level 7");
        GameTestAssertions.assertTrue(helper, Float.compare(pending.experienceProgress(), 0.25F) == 0,
                "the durable pending snapshot must preserve experience progress 0.25");
        GameTestAssertions.assertTrue(helper, pending.totalExperience() == 123,
                "the durable pending snapshot must preserve total experience 123");
    }

    private static void assertExperience(GameTestHelper helper, ServerPlayer player, String label) {
        GameTestAssertions.assertTrue(helper, player.experienceLevel == 7, label + " must preserve experience level 7");
        GameTestAssertions.assertTrue(helper, Float.compare(player.experienceProgress, 0.25F) == 0,
                label + " must preserve experience progress 0.25");
        GameTestAssertions.assertTrue(helper, player.totalExperience == 123, label + " must preserve total experience 123");
    }

    private static void assertEncounterState(
            GameTestHelper helper, ServerPlayer player, long expectedLethalTick, String label) {
        HerobrineEncounterState expected =
                new HerobrineEncounterState(0, Long.MIN_VALUE, expectedLethalTick, true);
        HerobrineEncounterState actual = player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER);
        GameTestAssertions.assertTrue(helper, actual.equals(expected),
                label + " must retain encounter state " + expected + ", got " + actual);
    }

    private static void assertPlayerReplacement(
            GameTestHelper helper,
            ServerLevel level,
            UUID playerId,
            ServerPlayer oldPlayer,
            ServerPlayer newPlayer,
            ServerGamePacketListenerImpl listener) {
        PlayerList playerList = level.getServer().getPlayerList();
        GameTestAssertions.assertTrue(helper, playerList.getPlayer(playerId) == newPlayer,
                "PlayerList UUID map must point only to the respawned instance");
        GameTestAssertions.assertTrue(helper, playerList.getPlayers().stream()
                        .filter(player -> playerId.equals(player.getUUID())).count() == 1,
                "PlayerList must contain exactly one player with the respawned UUID");
        GameTestAssertions.assertTrue(helper, playerList.getPlayers().stream().anyMatch(player -> player == newPlayer),
                "PlayerList must contain the respawned instance");
        GameTestAssertions.assertFalse(helper, playerList.getPlayers().stream().anyMatch(player -> player == oldPlayer),
                "PlayerList must no longer contain the dead instance");
        GameTestAssertions.assertTrue(helper, level.players().stream()
                        .filter(player -> playerId.equals(player.getUUID())).count() == 1,
                "ServerLevel.players must contain exactly one player with the respawned UUID");
        GameTestAssertions.assertTrue(helper, level.players().stream().anyMatch(player -> player == newPlayer),
                "ServerLevel.players must contain the respawned instance");
        GameTestAssertions.assertFalse(helper, level.players().stream().anyMatch(player -> player == oldPlayer),
                "ServerLevel.players must no longer contain the dead instance");
        GameTestAssertions.assertTrue(helper, level.getEntity(playerId) == newPlayer,
                "the level entity UUID index must point to the respawned instance");
        GameTestAssertions.assertTrue(helper, listener.getPlayer() == newPlayer,
                "the connection listener must switch to the respawned instance");
        GameTestAssertions.assertTrue(helper, newPlayer.connection == listener,
                "the respawned instance must retain the same connection listener");
    }

    private static void assertPosition(GameTestHelper helper, Vec3 actual, Vec3 expected, String label) {
        GameTestAssertions.assertTrue(helper, Double.compare(actual.x, expected.x) == 0
                        && Double.compare(actual.y, expected.y) == 0
                        && Double.compare(actual.z, expected.z) == 0,
                label + " must equal " + expected + ", got " + actual);
    }

    private static ClientboundPlayerPositionPacket drainLastPositionPacket(EmbeddedChannel channel) {
        ClientboundPlayerPositionPacket lastPosition = null;
        Object message;
        while ((message = channel.readOutbound()) != null) {
            try {
                if (message instanceof ClientboundPlayerPositionPacket positionPacket) {
                    lastPosition = positionPacket;
                }
            } finally {
                ReferenceCountUtil.release(message);
            }
        }
        return lastPosition;
    }

    private static void releaseOutbound(EmbeddedChannel channel) {
        Object message;
        while ((message = channel.readOutbound()) != null) {
            ReferenceCountUtil.release(message);
        }
    }

    private static void completeDeadRespawnForCleanup(ServerLevel level, UUID playerId) {
        PlayerList playerList = level.getServer().getPlayerList();
        ServerPlayer current = playerList.getPlayer(playerId);
        if (current == null) {
            current = level.players().stream()
                    .filter(player -> playerId.equals(player.getUUID()))
                    .findFirst()
                    .orElse(null);
        }
        if (current != null && !current.isAlive() && current.connection.getPlayer() == current) {
            current.connection.handleClientCommand(new ServerboundClientCommandPacket(
                    ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        }
    }

    private static void discardDeathDrops(ServerLevel level, Vec3 deathPosition) {
        AABB area = new AABB(deathPosition, deathPosition).inflate(4.0);
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) {
            orb.discard();
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            item.discard();
        }
    }

    private static HerobrineEntity createHerobrine(ServerLevel level) {
        return IAmZombieEntities.HEROBRINE.get().create(level, EntitySpawnReason.EVENT);
    }

    private static void dispatchNodeNativeGeneralInteraction(ServerPlayer player, HerobrineEntity target) {
        // 26.x has one location-bearing protocol action; this direct node-native call is only the
        // reverse guard for the general subscriber. The packet-path proof above never uses it.
        //? if >=26.1
        player.interactOn(target, InteractionHand.MAIN_HAND, Vec3.ZERO);
        // 1.21.x still has a distinct protocol-level general interaction action.
        //? if <26.1 {
        /*player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(
                target, false, InteractionHand.MAIN_HAND));
        *///?}
    }

    private static void configureLethal() {
        IAmZombieConfig.HEROBRINE_ESCALATION_SIGHTINGS.set(0);
        IAmZombieConfig.HEROBRINE_LETHAL_SIGHTINGS.set(0);
        IAmZombieConfig.HEROBRINE_LETHAL_COOLDOWN_TICKS.set(0);
        IAmZombieConfig.HEROBRINE_CAVE_CHECK_INTERVAL_TICKS.set(0);
        IAmZombieConfig.HEROBRINE_JOLT_ENABLED.set(false);
    }

    private static void configureCommon() {
        IAmZombieConfig.HEROBRINE_ESCALATION_SIGHTINGS.set(2);
        IAmZombieConfig.HEROBRINE_LETHAL_SIGHTINGS.set(1);
        IAmZombieConfig.HEROBRINE_MEMORY_WINDOW_TICKS.set(0);
        IAmZombieConfig.HEROBRINE_LETHAL_COOLDOWN_TICKS.set(0);
        IAmZombieConfig.HEROBRINE_JOLT_ENABLED.set(false);
    }

    private static void discardHerobrines(ServerLevel level, AABB area) {
        for (HerobrineEntity herobrine : level.getEntitiesOfClass(HerobrineEntity.class, area)) {
            herobrine.discard();
        }
    }

    private static final class HerobrineInteractObserver {
        private final UUID targetId;
        private final UUID playerId;
        private int generalEvents;
        private int specificEvents;
        private boolean lastGeneralCanceled;
        private boolean lastSpecificCanceled;
        private InteractionResult lastGeneralResult = InteractionResult.PASS;
        private InteractionResult lastSpecificResult = InteractionResult.PASS;

        private HerobrineInteractObserver(UUID targetId, UUID playerId) {
            this.targetId = targetId;
            this.playerId = playerId;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
            if (matches(event.getTarget().getUUID(), event.getEntity().getUUID(), event.getHand())) {
                generalEvents++;
                lastGeneralCanceled = event.isCanceled();
                lastGeneralResult = event.getCancellationResult();
            }
        }

        //? if <26.2 {
        /*@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
            if (matches(event.getTarget().getUUID(), event.getEntity().getUUID(), event.getHand())) {
                specificEvents++;
                lastSpecificCanceled = event.isCanceled();
                lastSpecificResult = event.getCancellationResult();
            }
        }
        *///?}

        private boolean matches(UUID candidateTargetId, UUID candidatePlayerId, InteractionHand hand) {
            return targetId.equals(candidateTargetId)
                    && playerId.equals(candidatePlayerId)
                    && hand == InteractionHand.MAIN_HAND;
        }

        private void reset() {
            generalEvents = 0;
            specificEvents = 0;
            lastGeneralCanceled = false;
            lastSpecificCanceled = false;
            lastGeneralResult = InteractionResult.PASS;
            lastSpecificResult = InteractionResult.PASS;
        }
    }

    private record ConfigSnapshot(
            int escalationSightings,
            int lethalSightings,
            int memoryWindowTicks,
            int lethalCooldownTicks,
            int caveCheckIntervalTicks,
            double caveSpawnChance,
            boolean omenEnabled,
            boolean joltEnabled) {
        private static ConfigSnapshot capture() {
            return new ConfigSnapshot(
                    IAmZombieConfig.HEROBRINE_ESCALATION_SIGHTINGS.get(),
                    IAmZombieConfig.HEROBRINE_LETHAL_SIGHTINGS.get(),
                    IAmZombieConfig.HEROBRINE_MEMORY_WINDOW_TICKS.get(),
                    IAmZombieConfig.HEROBRINE_LETHAL_COOLDOWN_TICKS.get(),
                    IAmZombieConfig.HEROBRINE_CAVE_CHECK_INTERVAL_TICKS.get(),
                    IAmZombieConfig.HEROBRINE_CAVE_SPAWN_CHANCE.get(),
                    IAmZombieConfig.HEROBRINE_OMEN_ENABLED.get(),
                    IAmZombieConfig.HEROBRINE_JOLT_ENABLED.get());
        }

        private void restore() {
            IAmZombieConfig.HEROBRINE_ESCALATION_SIGHTINGS.set(escalationSightings);
            IAmZombieConfig.HEROBRINE_LETHAL_SIGHTINGS.set(lethalSightings);
            IAmZombieConfig.HEROBRINE_MEMORY_WINDOW_TICKS.set(memoryWindowTicks);
            IAmZombieConfig.HEROBRINE_LETHAL_COOLDOWN_TICKS.set(lethalCooldownTicks);
            IAmZombieConfig.HEROBRINE_CAVE_CHECK_INTERVAL_TICKS.set(caveCheckIntervalTicks);
            IAmZombieConfig.HEROBRINE_CAVE_SPAWN_CHANCE.set(caveSpawnChance);
            IAmZombieConfig.HEROBRINE_OMEN_ENABLED.set(omenEnabled);
            IAmZombieConfig.HEROBRINE_JOLT_ENABLED.set(joltEnabled);
        }
    }
}
