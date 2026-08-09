package dev.molang.iamzombieq.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.core.ZombieState;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import dev.molang.iamzombieq.state.PlayerZombieData;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Helpers to spawn and configure server-side player fixtures inside a running GameTest. Most tests use a
 * {@link FakePlayer}; lifecycle tests that require a connection or world-player membership use Minecraft's connected
 * mock {@link ServerPlayer}.
 *
 * <p>The mod's facade ({@code IZombiePlayerAPI}) and the raw attachment are both FakePlayer-safe: the network
 * sync automatically triggered by {@code setData} produces no network send for a connectionless player.
 */
final class GameTestPlayers {

    private GameTestPlayers() {
    }

    /**
     * A {@link FakePlayer} placed at the test's local-origin {@code (1,2,1)} (one block above the 1x1x1 structure
     * floor) in SURVIVAL mode. A fresh {@link GameProfile} per call avoids the {@code FakePlayerFactory} per-level
     * cache returning a stale player carrying state from a prior test.
     */
    static FakePlayer spawnZombieFakePlayer(GameTestHelper helper, ZombieForm form, ZombieSize size) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "iamzombieq-test-" + UUID.randomUUID());
        FakePlayer player = FakePlayerFactory.get(level, profile);

        // Survival, so isCreative()-gated branches in the handlers behave as for an ordinary zombie player.
        player.setGameMode(GameType.SURVIVAL);
        // FakePlayer sets itself invulnerable in its constructor; clear it so sunlight fire / damage paths apply.
        player.setInvulnerable(false);

        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        Vec3 pos = Vec3.atBottomCenterOf(origin);
        player.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);

        // Establish zombie state directly on the attachment (FakePlayer-safe; the automatic sync produces no network
        // send for a connectionless player). This is the same write the facade performs, used here so the starting
        // state is unambiguous.
        PlayerZombieData data = PlayerZombieData.DEFAULT.withState(new ZombieState(form, size));
        player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, data);

        return player;
    }

    static boolean hasClientLoaded(ServerGamePacketListenerImpl listener) {
        // CROSS_VERSION-GAME-TEST-PLAYER-LOADED-API: the state moved from Player to the connection in 1.21.11.
        //? if >=1.21.11 {
        return listener.hasClientLoaded();
        //?} else {
        /*return listener.getPlayer().hasClientLoaded();
        *///?}
    }

    /**
     * Creates an ordinary connected {@link ServerPlayer} over a NeoForge-configured mock connection, completes its
     * load handshake, and puts it in SURVIVAL before applying the requested zombie state. The explicit NeoForge
     * connection setup is required for attachment-update packets to reach the fixture's {@link EmbeddedChannel};
     * Minecraft's deprecated anonymous GameTest player reports CREATIVE permanently and does not negotiate modded
     * payload channels.
     */
    static ServerPlayer spawnConnectedZombiePlayer(GameTestHelper helper, ZombieForm form, ZombieSize size) {
        ServerPlayer player = makeConnectedPlayer(helper);
        UUID playerId = player.getUUID();
        try {
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            player.setGameMode(GameType.SURVIVAL);
            clearCreativeAbilities(player.getAbilities());
            player.setInvulnerable(false);
            player.onUpdateAbilities();

            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

            BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
            Vec3 pos = Vec3.atBottomCenterOf(origin);
            player.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);

            PlayerZombieData data = PlayerZombieData.DEFAULT.withState(new ZombieState(form, size));
            player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, data);

            GameTestAssertions.assertTrue(helper, GameTestPlayers.hasClientLoaded(player.connection),
                    "connected GameTest player did not finish PlayerLoaded");
            GameTestAssertions.assertFalse(helper, player.hasInfiniteMaterials(), "connected GameTest player retained instabuild");
            GameTestAssertions.assertFalse(helper, player.getAbilities().invulnerable, "connected GameTest player retained ability invulnerability");
            GameTestAssertions.assertFalse(helper, player.isInvulnerable(), "connected GameTest player retained entity invulnerability");
            GameTestAssertions.assertFalse(helper, player.getAbilities().flying, "connected GameTest player retained flying");
            GameTestAssertions.assertFalse(helper, mayFly(player.getAbilities()), "connected GameTest player retained mayfly");
            return player;
        } catch (RuntimeException | Error failure) {
            try {
                disconnectConnectedPlayer(helper, playerId);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    static void disconnectConnectedPlayer(GameTestHelper helper, UUID playerId) {
        ServerLevel level = helper.getLevel();
        PlayerList playerList = level.getServer().getPlayerList();
        ServerPlayer current = playerList.getPlayer(playerId);
        if (current == null) {
            current = level.players().stream()
                    .filter(player -> playerId.equals(player.getUUID()))
                    .findFirst()
                    .orElse(null);
        }

        try {
            if (current != null) {
                disconnectCurrentPlayer(current);
            }
        } finally {
            GameTestAssertions.assertTrue(helper, playerList.getPlayer(playerId) == null,
                    "connected GameTest player remained in the PlayerList UUID lookup after disconnect");
            GameTestAssertions.assertTrue(helper, level.players().stream().noneMatch(player -> playerId.equals(player.getUUID())),
                    "connected GameTest player remained in ServerLevel.players after disconnect");
        }
    }

    static ZombieState stateOf(ServerPlayer player) {
        return player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state();
    }

    private static ServerPlayer makeConnectedPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(
                UUID.randomUUID(),
                "izq-" + UUID.randomUUID().toString().substring(0, 12));
        CommonListenerCookie cookie = new CommonListenerCookie(
                profile,
                0,
                ClientInformation.createDefault(),
                false,
                ConnectionType.NEOFORGE);
        // Keep the fixture as a marker-only GameTest subclass (with no gameplay overrides). NeoForge ConfigSync
        // deliberately exempts GameTest ServerPlayer subclasses from the configuration-phase pending-map invariant;
        // a class-exact ServerPlayer placed directly into PLAY has no preceding SyncConfig task and crashes at the
        // next server tick. Unlike Minecraft's deprecated fixture, this subclass does not pin gameMode to CREATIVE.
        ServerPlayer player = new ServerPlayer(
                level.getServer(), level, profile, cookie.clientInformation()) {
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static void disconnectCurrentPlayer(ServerPlayer player) {
        try {
            player.closeContainer();
        } finally {
            try {
                player.stopRiding();
            } finally {
                player.connection.disconnect(Component.literal("GameTest connected-player cleanup"));
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void clearCreativeAbilities(Abilities abilities) {
        abilities.instabuild = false;
        abilities.invulnerable = false;
        abilities.mayfly = false;
        abilities.flying = false;
    }

    @SuppressWarnings("deprecation")
    private static boolean mayFly(Abilities abilities) {
        return abilities.mayfly;
    }
}
