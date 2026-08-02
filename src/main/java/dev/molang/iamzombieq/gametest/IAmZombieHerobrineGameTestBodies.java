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
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

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
            helper.assertTrue(rawChannel instanceof EmbeddedChannel,
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
            helper.assertTrue(herobrine != null, "failed to create Herobrine for the lethal test");
            Vec3 herobrinePosition = helper.absoluteVec(LETHAL_HEROBRINE_POS);
            herobrine.snapTo(herobrinePosition.x, herobrinePosition.y, herobrinePosition.z, 180.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(herobrine),
                    "failed to add Herobrine to the server level");

            releaseOutbound(channel);
            long triggerTick = level.getGameTime();
            oldPlayer.attack(herobrine);

            helper.assertFalse(oldPlayer.isAlive(), "the lethal Herobrine attack must really kill the old player");
            helper.assertTrue(oldPlayer.getHealth() <= 0.0F,
                    "the old player's health must reach zero after the lethal attack");
            assertInventoryEmpty(helper, oldPlayer, "the dying player's live inventory");
            helper.assertTrue(herobrine.isRemoved(), "the lethal Herobrine must discard itself");
            assertEncounterState(helper, oldPlayer, triggerTick, "the dead player");

            HerobrineRespawnSnapshot pending =
                    oldPlayer.getData(IAmZombieAttachments.HEROBRINE_PENDING_RESPAWN);
            helper.assertTrue(pending.isPresent(), "the dead player must carry a durable pending respawn");
            assertPendingSnapshot(helper, pending, deathPosition, expectedInventory);

            ServerGamePacketListenerImpl listener = oldPlayer.connection;
            listener.handleClientCommand(new ServerboundClientCommandPacket(
                    ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            ServerPlayer newPlayer = listener.getPlayer();

            helper.assertTrue(newPlayer != oldPlayer, "death respawn must create a new ServerPlayer instance");
            helper.assertTrue(newPlayer.getUUID().equals(playerId),
                    "the respawned ServerPlayer must retain the old player's UUID");
            helper.assertTrue(oldPlayer.isRemoved(), "the old player must be removed during respawn");
            assertPlayerReplacement(helper, level, playerId, oldPlayer, newPlayer, listener);
            assertInventoryMatches(helper, newPlayer.getInventory(), expectedInventory,
                    "the respawned player's inventory");
            assertExperience(helper, newPlayer, "the respawned player");
            assertEncounterState(helper, newPlayer, triggerTick, "the respawned player");
            helper.assertFalse(newPlayer.getData(IAmZombieAttachments.HEROBRINE_PENDING_RESPAWN).isPresent(),
                    "the respawn event must clear the durable pending snapshot");

            ClientboundPlayerPositionPacket deathPacket = drainLastPositionPacket(channel);
            helper.assertTrue(deathPacket != null,
                    "respawn must emit a player-position packet for the restored death position");
            helper.assertTrue(deathPacket.relatives().isEmpty(),
                    "the restored death position packet must use absolute coordinates");
            assertPosition(helper, deathPacket.change().position(), deathPosition,
                    "the restored death position packet");
            helper.assertTrue(Float.compare(deathPacket.change().yRot(), DEATH_Y_ROT) == 0,
                    "the restored death position packet must preserve yRot=" + DEATH_Y_ROT);
            helper.assertTrue(Float.compare(deathPacket.change().xRot(), DEATH_X_ROT) == 0,
                    "the restored death position packet must preserve xRot=" + DEATH_X_ROT);
            helper.assertTrue(deathPacket.change().yRot() != 0.0F && deathPacket.change().xRot() != 0.0F,
                    "the position-packet rotation snapshot must be non-zero");

            helper.assertFalse(listener.hasClientLoaded(),
                    "the mock connection must wait for test-side PlayerLoaded after respawn");
            // Test-side protocol completion only. This does not claim observation of a real client's packet order.
            listener.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(deathPacket.id()));
            listener.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            listener.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(
                    deathPosition, DEATH_Y_ROT, DEATH_X_ROT, false, false));

            helper.assertTrue(listener.hasClientLoaded(),
                    "test-side PlayerLoaded must complete the respawn handshake");
            assertPosition(helper, newPlayer.position(), deathPosition, "the final server player");
            helper.assertTrue(Float.compare(newPlayer.getYRot(), DEATH_Y_ROT) == 0,
                    "the final server player must preserve yRot=" + DEATH_Y_ROT);
            helper.assertTrue(Float.compare(newPlayer.getXRot(), DEATH_X_ROT) == 0,
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
                helper.fail("failed to create Herobrine for the gaze test");
                return;
            }
            herobrine.snapTo(herobrinePos.x, herobrinePos.y, herobrinePos.z, 180.0F, 0.0F);
            if (!level.addFreshEntity(herobrine)) {
                helper.fail("failed to add Herobrine to the server level");
                return;
            }

            long expectedSightingTick = level.getGameTime();
            player.doTick();

            HerobrineEncounterState state = player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER);
            if (state.sightings() != 1) {
                helper.fail("first non-lethal gaze should record exactly one sighting, got " + state.sightings());
                return;
            }
            if (state.lastSightingTick() != expectedSightingTick) {
                helper.fail("lastSightingTick should equal the gaze game time " + expectedSightingTick
                        + ", got " + state.lastSightingTick());
                return;
            }
            if (state.lastLethalTick() != -1L) {
                helper.fail("a non-lethal gaze must preserve lastLethalTick=-1, got " + state.lastLethalTick());
                return;
            }
            if (state.escalatedBefore()) {
                helper.fail("a first non-lethal gaze must not set escalatedBefore");
                return;
            }
            if (!herobrine.isRemoved()) {
                helper.fail("Herobrine should discard itself after a non-lethal gaze");
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

    static void herobrineNaturalCaveSpawnSetsPhase(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        HerobrineEncounterState originalState = player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER);
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

            BlockPos playerBlock = player.blockPosition();
            if (playerBlock.getY() >= level.getSeaLevel() - HerobrineRules.CAVE_SPAWN_SEA_LEVEL_OFFSET) {
                helper.fail("precondition: baked cave player must be below the Herobrine cave height gate"
                        + " (playerY=" + playerBlock.getY()
                        + ", seaLevel=" + level.getSeaLevel()
                        + ", localOriginY=" + helper.absolutePos(BlockPos.ZERO).getY() + ")");
                return;
            }
            if (level.canSeeSky(playerBlock)) {
                helper.fail("precondition: baked cave roof must block sky access at the player");
                return;
            }
            if (level.getBlockState(playerBlock.below()).isAir()) {
                helper.fail("precondition: baked cave floor must support the player");
                return;
            }

            player.doTick();

            List<HerobrineEntity> spawned = level.getEntitiesOfClass(
                    HerobrineEntity.class,
                    player.getBoundingBox().inflate(HEROBRINE_CLEANUP_RADIUS),
                    Entity::isAlive);
            if (spawned.size() != 1) {
                helper.fail("one cave check at chance 1.0 should spawn exactly one Herobrine, got " + spawned.size());
                return;
            }

            HerobrineEntity herobrine = spawned.getFirst();
            BlockPos spawnBlock = herobrine.blockPosition();
            double dx = spawnBlock.getX() - playerBlock.getX();
            double dz = spawnBlock.getZ() - playerBlock.getZ();
            double horizontalDistance = Math.hypot(dx, dz);
            double roundingTolerance = Math.sqrt(0.5);
            double minDistance = HerobrineRules.CAVE_SPAWN_HORIZONTAL_DISTANCE - roundingTolerance;
            double maxDistance = HerobrineRules.CAVE_SPAWN_HORIZONTAL_DISTANCE * 2 - 1 + roundingTolerance;
            if (horizontalDistance < minDistance || horizontalDistance > maxDistance) {
                helper.fail("natural Herobrine should land in the rounded 12-23 block ring, got "
                        + horizontalDistance);
                return;
            }
            if (spawnBlock.getY() != playerBlock.getY()
                    || !level.getBlockState(spawnBlock).isAir()
                    || !level.getBlockState(spawnBlock.above()).isAir()
                    || level.getBlockState(spawnBlock.below()).isAir()) {
                helper.fail("natural Herobrine should occupy the baked cave's supported two-block air column");
                return;
            }
            if (herobrine.getEncounterPhase() != HerobrineEncounter.Phase.ESCALATION) {
                helper.fail("spawned Herobrine should publish the player's ESCALATION phase, got "
                        + herobrine.getEncounterPhase());
                return;
            }
            helper.succeed();
        } finally {
            discardHerobrines(level, player.getBoundingBox().inflate(HEROBRINE_CLEANUP_RADIUS));
            player.setData(IAmZombieAttachments.HEROBRINE_ENCOUNTER, originalState);
            config.restore();
        }
    }

    static void herobrineDiscardsAfterMaxLifetime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ConfigSnapshot config = ConfigSnapshot.capture();
        HerobrineEntity herobrine = createHerobrine(level);
        if (herobrine == null) {
            helper.fail("failed to create Herobrine for the lifetime test");
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
                helper.fail("failed to add Herobrine to the server level");
                return;
            }
            helper.assertTrue(level.isPositionEntityTicking(herobrine.blockPosition()),
                    "lifetime fixture must start Herobrine in an entity-ticking chunk");

            helper.runAtTickTime(900L, () -> {
                try {
                    if (!level.isPositionEntityTicking(herobrine.blockPosition())) {
                        helper.fail("lifetime fixture left entity-ticking coverage before tick 900");
                    }
                    if (herobrine.tickCount != 900) {
                        helper.fail("natural server ticking should reach tickCount 900 exactly, got "
                                + herobrine.tickCount);
                    }
                    if (herobrine.isRemoved() || !herobrine.isAlive()) {
                        helper.fail("Herobrine must still exist at tickCount 900");
                    }
                } catch (RuntimeException | Error failure) {
                    cleanup.run();
                    throw failure;
                }
            });
            helper.runAtTickTime(901L, () -> {
                try {
                    if (herobrine.tickCount != 901) {
                        helper.fail("natural server ticking should reach tickCount 901 exactly, got "
                                + herobrine.tickCount);
                    }
                    if (!herobrine.isRemoved()
                            || herobrine.getRemovalReason() != Entity.RemovalReason.DISCARDED) {
                        helper.fail("Herobrine must be discarded by its natural tick at tickCount 901");
                    }
                    helper.succeed();
                } finally {
                    cleanup.run();
                }
            });
            helper.runBeforeTestEnd(() -> {
                try {
                    helper.fail("Herobrine lifetime callbacks did not complete before the timeout");
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
            helper.assertTrue(inventory.getItem(slot).isEmpty(), label + " must be empty at slot " + slot);
        }
    }

    private static void assertInventoryMatches(
            GameTestHelper helper, Inventory actual, List<ItemStack> expected, String label) {
        helper.assertTrue(actual.getContainerSize() == expected.size(),
                label + " must retain the complete main, armor, offhand, body, and saddle slot range");
        for (int slot = 0; slot < expected.size(); slot++) {
            helper.assertTrue(ItemStack.matches(actual.getItem(slot), expected.get(slot)),
                    label + " differs at slot " + slot + ": expected " + expected.get(slot)
                            + ", got " + actual.getItem(slot));
        }
    }

    private static void assertInventoryMatches(
            GameTestHelper helper, List<ItemStack> actual, List<ItemStack> expected, String label) {
        helper.assertTrue(actual.size() == expected.size(), label + " must retain every inventory slot");
        for (int slot = 0; slot < expected.size(); slot++) {
            helper.assertTrue(ItemStack.matches(actual.get(slot), expected.get(slot)),
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
        helper.assertTrue(Float.compare(pending.yRot(), DEATH_Y_ROT) == 0,
                "the durable pending snapshot must preserve yRot=" + DEATH_Y_ROT);
        helper.assertTrue(Float.compare(pending.xRot(), DEATH_X_ROT) == 0,
                "the durable pending snapshot must preserve xRot=" + DEATH_X_ROT);
        assertInventoryMatches(helper, pending.inventory(), expectedInventory, "the durable pending inventory");
        helper.assertTrue(pending.experienceLevel() == 7,
                "the durable pending snapshot must preserve experience level 7");
        helper.assertTrue(Float.compare(pending.experienceProgress(), 0.25F) == 0,
                "the durable pending snapshot must preserve experience progress 0.25");
        helper.assertTrue(pending.totalExperience() == 123,
                "the durable pending snapshot must preserve total experience 123");
    }

    private static void assertExperience(GameTestHelper helper, ServerPlayer player, String label) {
        helper.assertTrue(player.experienceLevel == 7, label + " must preserve experience level 7");
        helper.assertTrue(Float.compare(player.experienceProgress, 0.25F) == 0,
                label + " must preserve experience progress 0.25");
        helper.assertTrue(player.totalExperience == 123, label + " must preserve total experience 123");
    }

    private static void assertEncounterState(
            GameTestHelper helper, ServerPlayer player, long expectedLethalTick, String label) {
        HerobrineEncounterState expected =
                new HerobrineEncounterState(0, Long.MIN_VALUE, expectedLethalTick, true);
        HerobrineEncounterState actual = player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER);
        helper.assertTrue(actual.equals(expected),
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
        helper.assertTrue(playerList.getPlayersByUUID().get(playerId) == newPlayer,
                "PlayerList UUID map must point only to the respawned instance");
        helper.assertTrue(playerList.getPlayers().stream()
                        .filter(player -> playerId.equals(player.getUUID())).count() == 1,
                "PlayerList must contain exactly one player with the respawned UUID");
        helper.assertTrue(playerList.getPlayers().stream().anyMatch(player -> player == newPlayer),
                "PlayerList must contain the respawned instance");
        helper.assertFalse(playerList.getPlayers().stream().anyMatch(player -> player == oldPlayer),
                "PlayerList must no longer contain the dead instance");
        helper.assertTrue(level.players().stream()
                        .filter(player -> playerId.equals(player.getUUID())).count() == 1,
                "ServerLevel.players must contain exactly one player with the respawned UUID");
        helper.assertTrue(level.players().stream().anyMatch(player -> player == newPlayer),
                "ServerLevel.players must contain the respawned instance");
        helper.assertFalse(level.players().stream().anyMatch(player -> player == oldPlayer),
                "ServerLevel.players must no longer contain the dead instance");
        helper.assertTrue(level.getEntity(playerId) == newPlayer,
                "the level entity UUID index must point to the respawned instance");
        helper.assertTrue(listener.getPlayer() == newPlayer,
                "the connection listener must switch to the respawned instance");
        helper.assertTrue(newPlayer.connection == listener,
                "the respawned instance must retain the same connection listener");
    }

    private static void assertPosition(GameTestHelper helper, Vec3 actual, Vec3 expected, String label) {
        helper.assertTrue(Double.compare(actual.x, expected.x) == 0
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
        ServerPlayer current = playerList.getPlayersByUUID().get(playerId);
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
