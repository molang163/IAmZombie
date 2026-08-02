package dev.molang.iamzombieq.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.molang.iamzombieq.api.event.ZombieEvolvePreEvent;
import dev.molang.iamzombieq.api.event.ZombieEvolvedEvent;
import dev.molang.iamzombieq.api.event.ZombieTransformPreEvent;
import dev.molang.iamzombieq.api.event.ZombieTransformedEvent;
import dev.molang.iamzombieq.gameplay.IAmZombieAdvancements;
import dev.molang.iamzombieq.gameplay.ZombieFormAttributes;
import dev.molang.iamzombieq.rules.DeathOutcome;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.core.ZombieState;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import dev.molang.iamzombieq.state.PlayerZombieData;
import dev.molang.iamzombieq.util.ModIds;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;

/**
 * FakePlayer- and connected-ServerPlayer-driven NeoForge GameTest bodies for the FORM and ATTR
 * test cases of {@code iamzombieq}, registered by {@link IAmZombieFormGameTests}. These are the runtime
 * counterparts of the pure-logic (L0) coverage in {@code ZombieEvolutionRulesTest} /
 * {@code ZombieBalanceRulesTest}: rather than asserting the rule functions in isolation, each drives the real
 * server-side seam the production handler subscribes to.
 *
 * <p>The CREATIVE giant-kill transform kills the vanilla {@code Giant} MOB through the real damage pipeline
 * ({@code hurtServer}), so NeoForge fires the real {@code LivingDeathEvent} that the
 * giant-kill branch of {@code ZombiePlayerEvents#onLivingDeath} acts on (keyed on the KILLER, not the dying entity),
 * then rewrites the killer's attachment. The (still-alive) FakePlayer's resulting form/size is read back via
 * {@link GameTestPlayers#stateOf}, and the forced {@code refreshFormAttributesForced} makes the new form's attributes
 * (giant max-health) observable right after.
 *
 * <p><b>Self-death fixture boundary.</b> NeoForge's {@code FakePlayer} still hard-overrides
 * {@code die(DamageSource)} to a no-op, so it cannot publish the required death event. Starvation and drowning are
 * covered here with Minecraft's connected GameTest {@code ServerPlayer}, which reaches the real lethal damage and
 * canceled {@code LivingDeathEvent} path. Other environment-gated evolution rows remain under their existing
 * coverage.
 */
final class IAmZombieFormGameTestBodies {
    private static final double ATTRIBUTE_EPSILON = 1.0e-6;
    private static final Identifier NON_GIANT_ATTACK_DAMAGE_ID = ModIds.id("non_giant_attack_damage");
    private static final Identifier GIANT_ATTACK_ID = ModIds.id("giant_attack");
    private static final Identifier DIFFICULTY_ATTACK_DAMAGE_ID = ModIds.id("difficulty_attack_damage");
    private static final ZombieForm[] NON_GIANT_FORMS = {
            ZombieForm.NORMAL,
            ZombieForm.DROWNED,
            ZombieForm.HUSK,
            ZombieForm.ZOMBIFIED_PIGLIN
    };

    private IAmZombieFormGameTestBodies() {
    }

    /**
     * FORM-001 (state portion): a freshly-attached zombie FakePlayer carries the default NORMAL/ADULT state. The
     * spawn helper performs the same attachment write the mod's first-login handler performs
     * ({@code PlayerZombieData.DEFAULT} state), so this asserts the attach-time invariant the runtime depends on. The
     * login-side effects (root advancement, starting items, recipe unlock) need a real {@code PlayerLoggedInEvent}
     * with a connection and are deferred to L0/manual.
     */
    static void formDefaultState(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        if (!player.hasData(IAmZombieAttachments.PLAYER_ZOMBIE)) {
            helper.fail("a zombie FakePlayer must carry the PLAYER_ZOMBIE attachment");
            return;
        }
        if (GameTestPlayers.stateOf(player).form() != ZombieForm.NORMAL
                || GameTestPlayers.stateOf(player).size() != ZombieSize.ADULT) {
            helper.fail("a freshly-attached zombie player must default to NORMAL/ADULT");
            return;
        }
        helper.succeed();
    }

    /** FORM-002: starvation evolves a NORMAL/ADULT connected player to NORMAL/BABY without replacing it. */
    static void starvationAdultBecomesBabyInPlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = null;
        UUID playerId = null;
        DeathObserver observer = null;
        boolean observerRegistered = false;
        try {
            player = GameTestPlayers.spawnConnectedZombiePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
            playerId = player.getUUID();
            prepareEvolutionFixture(player);

            observer = new DeathObserver(playerId, DamageTypes.STARVE);
            NeoForge.EVENT_BUS.register(observer);
            observerRegistered = true;

            boolean damaged = player.hurtServer(level, level.damageSources().starve(), Float.MAX_VALUE);
            helper.assertTrue(damaged, "starvation damage should be accepted by the connected player");
            assertCanceledDeath(helper, observer, "starvation");
            helper.assertTrue(GameTestPlayers.stateOf(player).equals(
                            new ZombieState(ZombieForm.NORMAL, ZombieSize.BABY)),
                    "starvation should evolve NORMAL/ADULT to NORMAL/BABY");
            assertInPlacePlayer(helper, level, player, playerId, "starvation");
            assertEvolutionRecoveryAndRetention(helper, player, "starvation");
        } finally {
            try {
                if (observerRegistered) {
                    NeoForge.EVENT_BUS.unregister(observer);
                }
            } finally {
                if (playerId != null) {
                    GameTestPlayers.disconnectConnectedPlayer(helper, playerId);
                }
            }
        }
        helper.succeed();
    }

    /** FORM-003: drowning evolves a NORMAL/ADULT connected player to DROWNED/ADULT without replacing it. */
    static void drowningNormalBecomesDrownedInPlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = null;
        UUID playerId = null;
        DeathObserver observer = null;
        boolean observerRegistered = false;
        try {
            player = GameTestPlayers.spawnConnectedZombiePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
            playerId = player.getUUID();
            prepareEvolutionFixture(player);

            observer = new DeathObserver(playerId, DamageTypes.DROWN);
            NeoForge.EVENT_BUS.register(observer);
            observerRegistered = true;

            boolean damaged = player.hurtServer(level, level.damageSources().drown(), Float.MAX_VALUE);
            helper.assertTrue(damaged, "drowning damage should be accepted by the connected player");
            assertCanceledDeath(helper, observer, "drowning");
            helper.assertTrue(GameTestPlayers.stateOf(player).equals(
                            new ZombieState(ZombieForm.DROWNED, ZombieSize.ADULT)),
                    "drowning should evolve NORMAL/ADULT to DROWNED/ADULT");
            assertInPlacePlayer(helper, level, player, playerId, "drowning");
            assertEvolutionRecoveryAndRetention(helper, player, "drowning");

            PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
            helper.assertTrue(data.receivedFirstDrownedReward(),
                    "first drowning evolution should record the drowned reward claim");
            helper.assertTrue(inventoryCount(player, Items.TRIDENT) == 1,
                    "first drowning evolution should grant exactly one trident");
        } finally {
            try {
                if (observerRegistered) {
                    NeoForge.EVENT_BUS.unregister(observer);
                }
            } finally {
                if (playerId != null) {
                    GameTestPlayers.disconnectConnectedPlayer(helper, playerId);
                }
            }
        }
        helper.succeed();
    }

    static void s1TransformPreGiantKillVeto(GameTestHelper helper) {
        runS1TransformPreGiantKill(helper, true);
    }

    static void s1TransformPreGiantKillPass(GameTestHelper helper) {
        runS1TransformPreGiantKill(helper, false);
    }

    static void s1TransformPreCloneResetVetoPreservesState(GameTestHelper helper) {
        runS1TransformPreCloneReset(helper, true);
    }

    static void s1TransformPreCloneResetPass(GameTestHelper helper) {
        runS1TransformPreCloneReset(helper, false);
    }

    static void s1EvolvePreDrowningVetoRealDeath(GameTestHelper helper) {
        runS1EvolvePre(helper, true, true);
    }

    static void s1EvolvePreDrowningPassOnce(GameTestHelper helper) {
        runS1EvolvePre(helper, true, false);
    }

    static void s1EvolvePreStarvationVetoRealDeath(GameTestHelper helper) {
        runS1EvolvePre(helper, false, true);
    }

    static void s1EvolvePreStarvationPass(GameTestHelper helper) {
        runS1EvolvePre(helper, false, false);
    }

    /**
     * Evolve-Pre drowning and starvation matrix. The same connected-player fixture drives a real lethal
     * {@link ServerPlayer#hurtServer} call for both outcomes. Nested Pre/Post callbacks, the outcome advancement
     * callback, and the outer death event at LOWEST provide four observable ordering boundaries without posting any
     * event by hand.
     */
    private static void runS1EvolvePre(GameTestHelper helper, boolean drowning, boolean veto) {
        String trigger = drowning ? "drowning" : "starvation";
        String label = trigger + (veto ? " veto" : " pass");
        ServerLevel level = helper.getLevel();
        ResourceKey<DamageType> damageType = drowning ? DamageTypes.DROWN : DamageTypes.STARVE;
        DeathOutcome expectedOutcome = drowning
                ? DeathOutcome.EVOLVE_TO_DROWNED
                : DeathOutcome.EVOLVE_TO_BABY;
        ZombieState beforeState = new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT);
        ZombieState afterState = drowning
                ? new ZombieState(ZombieForm.DROWNED, ZombieSize.ADULT)
                : new ZombieState(ZombieForm.NORMAL, ZombieSize.BABY);
        Identifier outcomeAdvancement = drowning
                ? IAmZombieAdvancements.DROWNED
                : IAmZombieAdvancements.BABY;

        // Distinct reward-flag patterns make a partial state-only write visible. Drowning begins unclaimed so its
        // first reward must atomically add one trident and flip only that flag; starvation has no item reward and
        // must retain all three flags byte-for-byte.
        PlayerZombieData initialData = drowning
                ? new PlayerZombieData(beforeState, false, true, true)
                : new PlayerZombieData(beforeState, true, false, true);
        PlayerZombieData expectedData = initialData.withState(afterState);
        if (drowning) {
            expectedData = expectedData.withFirstDrownedRewardClaimed();
        }

        ServerPlayer player = null;
        UUID playerId = null;
        EmbeddedChannel channel = null;
        EvolveObserver evolveObserver = null;
        EvolveDeathObserver deathObserver = null;
        boolean evolveObserverRegistered = false;
        boolean deathObserverRegistered = false;

        try {
            player = GameTestPlayers.spawnConnectedZombiePlayer(
                    helper, ZombieForm.NORMAL, ZombieSize.ADULT);
            playerId = player.getUUID();
            channel = embeddedChannel(helper, player);
            prepareEvolutionFixture(player, initialData);

            helper.assertFalse(hasAdvancement(player, outcomeAdvancement),
                    label + " fixture must begin without its outcome advancement");
            helper.assertFalse(hasAdvancement(player, IAmZombieAdvancements.FIRST_EVOLUTION),
                    label + " fixture must begin without FIRST_EVOLUTION");

            // Login and fixture writes are not part of the production death-handler count.
            drainPlayerZombiePayloadTargets(channel);
            int stableEntityId = player.getId();
            int deathsBefore = deathStat(player);
            ServerGamePacketListenerImpl listener = player.connection;

            evolveObserver = new EvolveObserver(
                    playerId,
                    veto,
                    outcomeAdvancement);
            deathObserver = new EvolveDeathObserver(
                    playerId,
                    damageType,
                    evolveObserver,
                    outcomeAdvancement);
            NeoForge.EVENT_BUS.register(evolveObserver);
            evolveObserverRegistered = true;
            NeoForge.EVENT_BUS.register(deathObserver);
            deathObserverRegistered = true;

            boolean damaged = player.hurtServer(
                    level,
                    drowning ? level.damageSources().drown() : level.damageSources().starve(),
                    Float.MAX_VALUE);
            List<Integer> payloadTargets = drainPlayerZombiePayloadTargets(channel);

            helper.assertTrue(damaged, label + " must accept real lethal " + trigger + " damage");
            assertEvolvePreSnapshot(
                    helper,
                    evolveObserver,
                    player,
                    initialData,
                    expectedOutcome,
                    beforeState,
                    afterState,
                    veto,
                    label);
            helper.assertTrue(deathObserver.matchingDeathEvents == 1,
                    label + " must reach the matching outer LivingDeathEvent exactly once at LOWEST");

            if (veto) {
                assertEvolveVeto(
                        helper,
                        player,
                        listener,
                        deathsBefore,
                        stableEntityId,
                        payloadTargets,
                        initialData,
                        outcomeAdvancement,
                        evolveObserver,
                        deathObserver,
                        label);
            } else {
                assertEvolvePass(
                        helper,
                        level,
                        player,
                        playerId,
                        listener,
                        deathsBefore,
                        stableEntityId,
                        payloadTargets,
                        initialData,
                        expectedData,
                        outcomeAdvancement,
                        drowning,
                        evolveObserver,
                        deathObserver,
                        label);
            }
        } finally {
            try {
                if (evolveObserverRegistered) {
                    NeoForge.EVENT_BUS.unregister(evolveObserver);
                }
            } finally {
                try {
                    if (deathObserverRegistered) {
                        NeoForge.EVENT_BUS.unregister(deathObserver);
                    }
                } finally {
                    closeConnectedFixture(helper, playerId, channel);
                }
            }
        }
        helper.succeed();
    }

    /**
     * Transform-Pre giant-kill pair. A connected player is required here even though the original FORM-007
     * coverage uses a FakePlayer: these tests must observe the exact PLAYER_ZOMBIE attachment update count.
     */
    private static void runS1TransformPreGiantKill(GameTestHelper helper, boolean veto) {
        String label = veto ? "giant-kill veto" : "giant-kill pass";
        ServerLevel level = helper.getLevel();
        ServerPlayer player = null;
        UUID playerId = null;
        EmbeddedChannel channel = null;
        Giant giant = null;
        TransformObserver transformObserver = null;
        DeathObserver deathObserver = null;
        boolean transformObserverRegistered = false;
        boolean deathObserverRegistered = false;

        try {
            player = GameTestPlayers.spawnConnectedZombiePlayer(
                    helper, ZombieForm.NORMAL, ZombieSize.BABY);
            playerId = player.getUUID();
            channel = embeddedChannel(helper, player);

            PlayerZombieData initialData = distinguishableData(
                    ZombieForm.NORMAL, ZombieSize.BABY);
            player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, initialData);
            ZombieFormAttributes.refreshFormAttributesForced(player, initialData);
            player.setGameMode(GameType.CREATIVE);
            player.setInvulnerable(false);
            player.setHealth(7.0F);
            player.onUpdateAbilities();
            helper.assertTrue(player.isCreative(), label + " fixture must report CREATIVE");
            helper.assertTrue(hasExpectedFormAttributeValues(
                            helper, player, ZombieForm.NORMAL, ZombieSize.BABY),
                    label + " fixture must begin with NORMAL/BABY attributes");

            // Discard login, setup setData, ability, and attribute traffic. Counts below begin at the real kill.
            drainPlayerZombiePayloadTargets(channel);
            int stableEntityId = player.getId();

            giant = helper.spawn(EntityTypes.GIANT, new BlockPos(2, 2, 1));
            transformObserver = new TransformObserver(playerId, veto, false);
            deathObserver = new DeathObserver(giant.getUUID(), DamageTypes.PLAYER_ATTACK);
            NeoForge.EVENT_BUS.register(transformObserver);
            transformObserverRegistered = true;
            NeoForge.EVENT_BUS.register(deathObserver);
            deathObserverRegistered = true;

            boolean damaged = giant.hurtServer(
                    level, level.damageSources().playerAttack(player), Float.MAX_VALUE);
            List<Integer> payloadTargets = drainPlayerZombiePayloadTargets(channel);

            helper.assertTrue(damaged, label + " must deal accepted lethal player-attack damage");
            helper.assertFalse(giant.isAlive(), label + " must leave the vanilla giant genuinely dead");
            helper.assertTrue(giant.getHealth() <= 0.0F,
                    label + " must reduce the vanilla giant to zero health");
            helper.assertTrue(deathObserver.matchingDeathEvents == 1,
                    label + " must publish exactly one giant LivingDeathEvent");
            helper.assertFalse(deathObserver.lastDeathCanceled,
                    label + " must not cancel the giant's real death");

            PlayerZombieData expectedAfter = initialData.withState(
                    new ZombieState(ZombieForm.GIANT, ZombieSize.ADULT));
            assertTransformPreSnapshot(
                    helper,
                    transformObserver,
                    player,
                    stableEntityId,
                    ZombieForm.NORMAL,
                    ZombieForm.GIANT,
                    initialData,
                    label);
            helper.assertTrue(transformObserver.preCancellationApplied == veto,
                    label + " Transform Pre cancellation flag must match the test listener");

            if (veto) {
                helper.assertTrue(transformObserver.postCount == 0,
                        label + " must suppress Transform Post");
                helper.assertTrue(player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).equals(initialData),
                        label + " must preserve the complete state and reward flags");
                helper.assertTrue(hasExpectedFormAttributeValues(
                                helper, player, ZombieForm.NORMAL, ZombieSize.BABY),
                        label + " must preserve NORMAL/BABY attributes");
                helper.assertTrue(Float.compare(player.getHealth(), 7.0F) == 0,
                        label + " must not heal or otherwise change the killer's health");
                assertStableEntityPayloadCount(
                        helper, payloadTargets, stableEntityId, 0, label);
            } else {
                assertTransformPostSnapshot(
                        helper,
                        transformObserver,
                        player,
                        stableEntityId,
                        ZombieForm.NORMAL,
                        ZombieForm.GIANT,
                        expectedAfter,
                        label);
                // Post is deliberately before forced attributes and healing: the attachment is authoritative, while
                // the old BABY attribute/health snapshot is still visible inside the callback.
                helper.assertTrue(Math.abs(transformObserver.postScale - 0.5) <= ATTRIBUTE_EPSILON,
                        label + " Post must run before the GIANT/ADULT scale refresh");
                helper.assertTrue(Float.compare(transformObserver.postMaxHealth, 20.0F) == 0,
                        label + " Post must run before the GIANT max-health refresh");
                helper.assertTrue(Float.compare(transformObserver.postHealth, 7.0F) == 0,
                        label + " Post must run before giant-kill healing");

                helper.assertTrue(player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).equals(expectedAfter),
                        label + " must commit GIANT/ADULT once while preserving reward flags");
                helper.assertTrue(hasExpectedFormAttributeValues(
                                helper, player, ZombieForm.GIANT, ZombieSize.ADULT),
                        label + " must finish with GIANT/ADULT attributes");
                helper.assertTrue(Float.compare(player.getMaxHealth(), 100.0F) == 0,
                        label + " must finish with 100 max health");
                helper.assertTrue(Float.compare(player.getHealth(), 100.0F) == 0,
                        label + " must heal to the refreshed GIANT max health");
                assertStableEntityPayloadCount(
                        helper, payloadTargets, stableEntityId, 1, label);
            }
        } finally {
            try {
                if (transformObserverRegistered) {
                    NeoForge.EVENT_BUS.unregister(transformObserver);
                }
            } finally {
                try {
                    if (deathObserverRegistered) {
                        NeoForge.EVENT_BUS.unregister(deathObserver);
                    }
                } finally {
                    try {
                        if (giant != null && !giant.isRemoved()) {
                            giant.discard();
                        }
                    } finally {
                        closeConnectedFixture(helper, playerId, channel);
                    }
                }
            }
        }
        helper.succeed();
    }

    /**
     * Transform-Pre clone pair. This follows the Herobrine lifecycle fixture: real lethal
     * damage, the connection's PERFORM_RESPAWN command, and the resulting PlayerList respawn/Clone callback. The Pre
     * observer is in fresh-holder mode and therefore never reads or materializes PLAYER_ZOMBIE.
     */
    private static void runS1TransformPreCloneReset(GameTestHelper helper, boolean veto) {
        String label = veto ? "clone-reset veto" : "clone-reset pass";
        ServerLevel level = helper.getLevel();
        ServerPlayer oldPlayer = null;
        UUID playerId = null;
        EmbeddedChannel channel = null;
        TransformObserver transformObserver = null;
        DeathObserver deathObserver = null;
        boolean transformObserverRegistered = false;
        boolean deathObserverRegistered = false;

        try {
            oldPlayer = GameTestPlayers.spawnConnectedZombiePlayer(
                    helper, ZombieForm.ZOMBIFIED_PIGLIN, ZombieSize.BABY);
            playerId = oldPlayer.getUUID();
            channel = embeddedChannel(helper, oldPlayer);

            PlayerZombieData initialData = distinguishableData(
                    ZombieForm.ZOMBIFIED_PIGLIN, ZombieSize.BABY);
            oldPlayer.setData(IAmZombieAttachments.PLAYER_ZOMBIE, initialData);
            ZombieFormAttributes.refreshFormAttributesForced(oldPlayer, initialData);
            oldPlayer.removeEffect(MobEffects.FIRE_RESISTANCE);
            oldPlayer.setInvulnerable(false);
            oldPlayer.setHealth(oldPlayer.getMaxHealth());
            oldPlayer.invulnerableTime = 0;
            oldPlayer.hurtTime = 0;
            oldPlayer.deathTime = 0;

            // Discard login and fixture setup updates before observing the clone's handler update and initial sync.
            drainPlayerZombiePayloadTargets(channel);
            int oldEntityId = oldPlayer.getId();
            int deathsBefore = deathStat(oldPlayer);
            ServerGamePacketListenerImpl listener = oldPlayer.connection;

            transformObserver = new TransformObserver(playerId, veto, true);
            deathObserver = new DeathObserver(playerId, DamageTypes.GENERIC_KILL);
            NeoForge.EVENT_BUS.register(transformObserver);
            transformObserverRegistered = true;
            NeoForge.EVENT_BUS.register(deathObserver);
            deathObserverRegistered = true;

            boolean damaged = oldPlayer.hurtServer(
                    level, level.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(damaged, label + " must accept lethal generic-kill damage");
            helper.assertFalse(oldPlayer.isAlive(), label + " must genuinely kill the original player");
            helper.assertTrue(oldPlayer.getHealth() <= 0.0F,
                    label + " must reduce the original player to zero health");
            helper.assertTrue(deathObserver.matchingDeathEvents == 1,
                    label + " must publish exactly one matching LivingDeathEvent");
            helper.assertFalse(deathObserver.lastDeathCanceled,
                    label + " must not cancel the original player's real death");
            helper.assertTrue(deathStat(oldPlayer) == deathsBefore + 1,
                    label + " must increment the vanilla DEATHS stat exactly once");
            helper.assertFalse(listener.hasClientLoaded(),
                    label + " must mark the connection client-unloaded at the death screen");

            listener.handleClientCommand(new ServerboundClientCommandPacket(
                    ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            ServerPlayer newPlayer = listener.getPlayer();
            int finalEntityId = newPlayer.getId();
            List<Integer> payloadTargets = drainPlayerZombiePayloadTargets(channel);

            assertRealRespawnReplacement(
                    helper, level, playerId, oldPlayer, newPlayer, listener, label);
            helper.assertTrue(finalEntityId == oldEntityId,
                    label + " must restore the original entity ID after Clone");
            helper.assertTrue(transformObserver.preEntityId != finalEntityId,
                    label + " must observe a distinct temporary entity ID in Transform Pre");
            helper.assertTrue(transformObserver.prePlayer == newPlayer,
                    label + " Transform Pre must expose the fresh respawn holder");
            helper.assertFalse(transformObserver.preAttachmentRead,
                    label + " Transform Pre listener must not read the fresh-holder attachment");
            helper.assertTrue(transformObserver.preCount == 1,
                    label + " must publish Transform Pre exactly once");
            helper.assertTrue(transformObserver.preFrom == ZombieForm.ZOMBIFIED_PIGLIN
                            && transformObserver.preTo == ZombieForm.NORMAL,
                    label + " Transform Pre must carry the authoritative ZOMBIFIED_PIGLIN -> NORMAL snapshot");
            helper.assertTrue(transformObserver.preCancellationApplied == veto,
                    label + " Transform Pre cancellation flag must match the test listener");

            PlayerZombieData expectedReset = initialData.resetStateForOrdinaryDeath();
            if (veto) {
                helper.assertTrue(transformObserver.postCount == 0,
                        label + " must suppress Transform Post");
                helper.assertTrue(newPlayer.getData(IAmZombieAttachments.PLAYER_ZOMBIE).equals(initialData),
                        label + " must copy the complete previous state, size, and flags to the fresh holder");
                helper.assertTrue(hasExpectedFormAttributeValues(
                                helper, newPlayer, ZombieForm.ZOMBIFIED_PIGLIN, ZombieSize.BABY),
                        label + " must force retained ZOMBIFIED_PIGLIN/BABY attributes");
                helper.assertTrue(newPlayer.hasEffect(MobEffects.FIRE_RESISTANCE),
                        label + " must reapply retained ZOMBIFIED_PIGLIN passive abilities");
            } else {
                assertTransformPostSnapshot(
                        helper,
                        transformObserver,
                        newPlayer,
                        transformObserver.preEntityId,
                        ZombieForm.ZOMBIFIED_PIGLIN,
                        ZombieForm.NORMAL,
                        expectedReset,
                        label);
                helper.assertTrue(transformObserver.postPlayer == newPlayer,
                        label + " Transform Post must expose the same fresh respawn holder");
                helper.assertTrue(newPlayer.getData(IAmZombieAttachments.PLAYER_ZOMBIE).equals(expectedReset),
                        label + " must reset to NORMAL/ADULT while preserving all reward flags");
                helper.assertTrue(hasExpectedFormAttributeValues(
                                helper, newPlayer, ZombieForm.NORMAL, ZombieSize.ADULT),
                        label + " must force reset NORMAL/ADULT attributes");
                helper.assertFalse(newPlayer.hasEffect(MobEffects.FIRE_RESISTANCE),
                        label + " must not retain the old ZOMBIFIED_PIGLIN passive ability");
            }

            assertClonePayloadCounts(
                    helper,
                    payloadTargets,
                    transformObserver.preEntityId,
                    finalEntityId,
                    label);

            helper.assertFalse(listener.hasClientLoaded(),
                    label + " mock connection must await the respawn PlayerLoaded handshake");
            listener.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            helper.assertTrue(listener.hasClientLoaded(),
                    label + " test-side PlayerLoaded must complete the respawn handshake");
        } finally {
            try {
                if (transformObserverRegistered) {
                    NeoForge.EVENT_BUS.unregister(transformObserver);
                }
            } finally {
                try {
                    if (deathObserverRegistered) {
                        NeoForge.EVENT_BUS.unregister(deathObserver);
                    }
                } finally {
                    closeConnectedFixture(helper, playerId, channel);
                }
            }
        }
        helper.succeed();
    }

    /**
     * FORM-007: a CREATIVE-mode zombie FakePlayer that kills a vanilla {@code minecraft:giant} transforms into the
     * GIANT form, respawned to full health (the handler sets health to the new GIANT max). Drives the real
     * player-attack death of the Giant so vanilla fires the {@code LivingDeathEvent} the giant-kill branch acts on.
     * Also covers ATTR-007 at runtime: the forced attribute refresh makes the GIANT +80 MAX_HEALTH modifier
     * observable, so the player's max health is 100 and it is healed to full. The same HARD-environment player
     * verifies the actual diamond-sword equipment lifecycle and every non-giant form/size attack profile.
     */
    static void formCreativeGiantKillBecomesGiant(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        PlayerZombieData normalData = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
        ZombieFormAttributes.refreshFormAttributesForced(player, normalData);
        if (!hasExpectedFormAttributeValues(helper, player, ZombieForm.NORMAL, ZombieSize.ADULT)) {
            return;
        }
        if (!hasExpectedAttackProfile(helper, player, 4.5, true, false, true)) {
            return;
        }
        // The giant-kill branch is gated on the killer being in creative mode (ZombieEvolutionRules.canTransformFromGiantKill).
        player.setGameMode(GameType.CREATIVE);
        // setGameMode re-applies creative invulnerability; clear it again so nothing downstream is skewed (the kill
        // itself is dealt TO the giant, not the player, so this is belt-and-braces).
        player.setInvulnerable(false);
        ServerLevel level = helper.getLevel();

        Giant giant = helper.spawn(EntityTypes.GIANT, new BlockPos(1, 2, 1));
        GameTestSeams.killByPlayerAttack(level, player, giant);

        if (GameTestPlayers.stateOf(player).form() != ZombieForm.GIANT) {
            helper.fail("a creative player killing a vanilla giant must transform into the GIANT form");
            return;
        }
        // ATTR-007 (runtime): the forced refresh applied the +80 GIANT max-health modifier, and the handler healed to full.
        if (player.getMaxHealth() != 100.0F) {
            helper.fail("GIANT form max health should be 100 after the transform, was " + player.getMaxHealth());
            return;
        }
        if (player.getHealth() != player.getMaxHealth()) {
            helper.fail("a giant-kill transform must respawn the player at full health");
            return;
        }
        if (!hasExpectedFormAttributeValues(helper, player, ZombieForm.GIANT, ZombieSize.ADULT)) {
            return;
        }
        if (!hasExpectedAttackProfile(helper, player, 55.0, false, true, false)) {
            return;
        }

        // GIANT/BABY is a valid state combination. It keeps the baby scale and speed modifiers stacked with
        // the giant deltas rather than normalizing one side away (final scale 5.5, movement speed 0.15).
        PlayerZombieData giantBabyData = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE)
                .withState(new ZombieState(ZombieForm.GIANT, ZombieSize.BABY));
        player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, giantBabyData);
        ZombieFormAttributes.refreshFormAttributesForced(player, giantBabyData);
        if (!hasExpectedFormAttributeValues(helper, player, ZombieForm.GIANT, ZombieSize.BABY)
                || !hasExpectedAttackProfile(helper, player, 55.0, false, true, false)) {
            return;
        }

        PlayerZombieData restoredNormalData = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE)
                .withState(new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT));
        player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, restoredNormalData);
        ZombieFormAttributes.refreshFormAttributesForced(player, restoredNormalData);
        // A repeated forced refresh must update the stable modifier IDs rather than accumulating duplicates.
        ZombieFormAttributes.refreshFormAttributesForced(player, restoredNormalData);
        if (!hasExpectedAttackProfile(helper, player, 4.5, true, false, true)) {
            return;
        }

        helper.startSequence()
                .thenExecute(() -> player.setItemSlot(
                        EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD)))
                .thenExecuteAfter(1, () -> {
                    // FakePlayer.tick() is intentionally empty. ServerPlayer.doTick() calls the real Player /
                    // LivingEntity tick path, where vanilla detects the equipment change and applies its modifiers.
                    player.doTick();
                    if (!player.getMainHandItem().is(Items.DIAMOND_SWORD)) {
                        helper.fail("the FakePlayer should actually hold the diamond sword");
                        return;
                    }
                    if (!hasExpectedAttackProfile(helper, player, 13.5, true, false, true, true)) {
                        return;
                    }
                })
                .thenExecute(() -> player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY))
                .thenExecuteAfter(1, () -> {
                    player.doTick();
                    if (!player.getMainHandItem().isEmpty()) {
                        helper.fail("the FakePlayer main hand should be empty after removing the diamond sword");
                        return;
                    }
                    if (!hasExpectedAttackProfile(helper, player, 4.5, true, false, true)) {
                        return;
                    }
                    assertAllNonGiantAttributeProfiles(helper, player);
                })
                .thenSucceed();
    }

    private static void assertEvolvePreSnapshot(
            GameTestHelper helper,
            EvolveObserver observer,
            ServerPlayer player,
            PlayerZombieData initialData,
            DeathOutcome expectedOutcome,
            ZombieState beforeState,
            ZombieState afterState,
            boolean veto,
            String label) {
        helper.assertTrue(observer.preCount == 1,
                label + " must publish Evolve Pre exactly once");
        helper.assertTrue(observer.prePlayer == player && observer.preEntityId == player.getId(),
                label + " Evolve Pre must expose the live connected player");
        helper.assertTrue(observer.preBefore.equals(beforeState)
                        && observer.preAfter.equals(afterState)
                        && observer.preOutcome == expectedOutcome,
                label + " Evolve Pre must carry the resolved before/after/outcome snapshot");
        helper.assertTrue(observer.preCancellationApplied == veto,
                label + " Evolve Pre cancellation flag must match the test listener");
        helper.assertTrue(initialData.equals(observer.preData),
                label + " Evolve Pre must run before any state or reward-flag write");
        helper.assertTrue(observer.preTridents == 0,
                label + " Evolve Pre must run before any item reward");
        helper.assertFalse(observer.preOutcomeAdvancement,
                label + " Evolve Pre must run before the outcome advancement");
        helper.assertFalse(observer.preFirstEvolutionAdvancement,
                label + " Evolve Pre must run before FIRST_EVOLUTION");
        helper.assertTrue(Math.abs(observer.preScale - 1.0) <= ATTRIBUTE_EPSILON
                        && Math.abs(observer.preSubmergedMiningSpeed - 0.2) <= ATTRIBUTE_EPSILON,
                label + " Evolve Pre must observe the old NORMAL/ADULT attributes");
        helper.assertTrue(observer.preHealth <= 0.0F
                        && observer.preAirSupply == 1
                        && observer.preFoodLevel == 2
                        && Float.compare(observer.preSaturation, 1.0F) == 0
                        && Math.abs(observer.preFallDistance - 7.0) <= ATTRIBUTE_EPSILON
                        && observer.preFireTicks == 80,
                label + " Evolve Pre must run before recovery");
    }

    private static void assertEvolveVeto(
            GameTestHelper helper,
            ServerPlayer player,
            ServerGamePacketListenerImpl listener,
            int deathsBefore,
            int stableEntityId,
            List<Integer> payloadTargets,
            PlayerZombieData initialData,
            Identifier outcomeAdvancement,
            EvolveObserver evolveObserver,
            EvolveDeathObserver deathObserver,
            String label) {
        helper.assertFalse(deathObserver.lastDeathCanceled,
                label + " must leave the real outer death uncanceled at LOWEST");
        helper.assertTrue(evolveObserver.callbackOrder.equals(List.of("pre", "death-lowest")),
                label + " callback order must be Pre -> death LOWEST with no Post");
        helper.assertTrue(evolveObserver.postCount == 0,
                label + " must suppress Evolve Post");
        helper.assertTrue(evolveObserver.outcomeAdvancementCount == 0,
                label + " must not reach the outcome advancement callback");

        helper.assertTrue(initialData.equals(deathObserver.dataAtLowest),
                label + " must retain the complete old state and flags at LOWEST");
        helper.assertTrue(deathObserver.tridentsAtLowest == 0,
                label + " must grant no reward before real death continues");
        helper.assertFalse(deathObserver.outcomeAdvancementAtLowest,
                label + " must grant no outcome advancement");
        helper.assertFalse(deathObserver.firstEvolutionAdvancementAtLowest,
                label + " must grant no FIRST_EVOLUTION advancement");
        helper.assertTrue(deathObserver.postCountAtLowest == 0,
                label + " must have no Post callback at LOWEST");
        helper.assertTrue(Math.abs(deathObserver.scaleAtLowest - 1.0) <= ATTRIBUTE_EPSILON
                        && Math.abs(deathObserver.submergedMiningSpeedAtLowest - 0.2) <= ATTRIBUTE_EPSILON,
                label + " must leave the old attributes untouched");
        helper.assertTrue(deathObserver.healthAtLowest <= 0.0F
                        && deathObserver.airSupplyAtLowest == 1
                        && deathObserver.foodLevelAtLowest == 2
                        && Float.compare(deathObserver.saturationAtLowest, 1.0F) == 0
                        && Math.abs(deathObserver.fallDistanceAtLowest - 7.0) <= ATTRIBUTE_EPSILON
                        && deathObserver.fireTicksAtLowest == 80,
                label + " must perform no recovery before real death continues");

        helper.assertFalse(player.isAlive(),
                label + " must leave the player genuinely dead");
        helper.assertTrue(player.getHealth() <= 0.0F,
                label + " must leave the player at zero health");
        helper.assertTrue(deathStat(player) == deathsBefore + 1,
                label + " must increment the vanilla DEATHS stat exactly once");
        helper.assertFalse(listener.hasClientLoaded(),
                label + " must put the connection into the death-screen unloaded state");
        helper.assertTrue(player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).equals(initialData),
                label + " must perform no state or reward-flag write");
        helper.assertTrue(inventoryCount(player, Items.TRIDENT) == 0,
                label + " must leave no trident reward");
        helper.assertFalse(hasAdvancement(player, outcomeAdvancement),
                label + " must leave the outcome advancement incomplete");
        helper.assertFalse(hasAdvancement(player, IAmZombieAdvancements.FIRST_EVOLUTION),
                label + " must leave FIRST_EVOLUTION incomplete");
        assertStableEntityPayloadCount(helper, payloadTargets, stableEntityId, 0, label);
    }

    private static void assertEvolvePass(
            GameTestHelper helper,
            ServerLevel level,
            ServerPlayer player,
            UUID playerId,
            ServerGamePacketListenerImpl listener,
            int deathsBefore,
            int stableEntityId,
            List<Integer> payloadTargets,
            PlayerZombieData initialData,
            PlayerZombieData expectedData,
            Identifier outcomeAdvancement,
            boolean drowning,
            EvolveObserver evolveObserver,
            EvolveDeathObserver deathObserver,
            String label) {
        int expectedTridents = drowning ? 1 : 0;
        helper.assertTrue(deathObserver.lastDeathCanceled,
                label + " must finally cancel the outer death at LOWEST");
        helper.assertTrue(evolveObserver.callbackOrder.equals(List.of("pre", "post", "death-lowest")),
                label + " callback order must be Pre -> Post -> death LOWEST");

        helper.assertTrue(evolveObserver.postCount == 1,
                label + " must publish Evolve Post exactly once");
        helper.assertTrue(evolveObserver.postPlayer == player
                        && evolveObserver.postEntityId == stableEntityId,
                label + " Evolve Post must expose the same in-place player");
        helper.assertTrue(evolveObserver.postBefore.equals(initialData.state())
                        && evolveObserver.postAfter.equals(expectedData.state())
                        && evolveObserver.postOutcome == (drowning
                                ? DeathOutcome.EVOLVE_TO_DROWNED
                                : DeathOutcome.EVOLVE_TO_BABY),
                label + " Evolve Post must carry the resolved snapshot");
        helper.assertTrue(expectedData.equals(evolveObserver.postData),
                label + " Post must observe the single committed state-and-flags value");
        helper.assertTrue(evolveObserver.postTridents == expectedTridents,
                label + " reward must be complete before Post");
        helper.assertFalse(evolveObserver.postOutcomeAdvancement,
                label + " Post must run before the outcome advancement");
        helper.assertFalse(evolveObserver.postFirstEvolutionAdvancement,
                label + " Post must run before FIRST_EVOLUTION");
        helper.assertTrue(Math.abs(evolveObserver.postScale - 1.0) <= ATTRIBUTE_EPSILON
                        && Math.abs(evolveObserver.postSubmergedMiningSpeed - 0.2) <= ATTRIBUTE_EPSILON,
                label + " Post must run before forced attribute refresh");
        helper.assertTrue(evolveObserver.postHealth <= 0.0F
                        && evolveObserver.postAirSupply == 1
                        && evolveObserver.postFoodLevel == 2
                        && Float.compare(evolveObserver.postSaturation, 1.0F) == 0
                        && Math.abs(evolveObserver.postFallDistance - 7.0) <= ATTRIBUTE_EPSILON
                        && evolveObserver.postFireTicks == 80,
                label + " Post must run before passive/recovery side effects");

        helper.assertTrue(evolveObserver.outcomeAdvancementCount == 1,
                label + " must earn its outcome advancement exactly once");
        helper.assertTrue(evolveObserver.postCountAtOutcomeAdvancement == 1,
                label + " outcome advancement must run after Post");
        helper.assertTrue(expectedData.equals(evolveObserver.dataAtOutcomeAdvancement),
                label + " outcome advancement must observe the committed data");
        helper.assertTrue(evolveObserver.tridentsAtOutcomeAdvancement == expectedTridents,
                label + " outcome advancement must observe the completed reward");
        helper.assertTrue(evolveObserver.outcomeAdvancementDoneInCallback,
                label + " outcome advancement callback must observe completed progress");
        if (drowning) {
            helper.assertTrue(Math.abs(evolveObserver.scaleAtOutcomeAdvancement - 1.0) <= ATTRIBUTE_EPSILON
                            && Math.abs(evolveObserver.submergedMiningSpeedAtOutcomeAdvancement - 1.0)
                                    <= ATTRIBUTE_EPSILON,
                    label + " outcome advancement must run after DROWNED attributes");
            helper.assertTrue(evolveObserver.airSupplyAtOutcomeAdvancement == player.getMaxAirSupply(),
                    label + " outcome advancement must run after DROWNED passive air restoration");
        } else {
            helper.assertTrue(Math.abs(evolveObserver.scaleAtOutcomeAdvancement - 0.5) <= ATTRIBUTE_EPSILON
                            && Math.abs(evolveObserver.submergedMiningSpeedAtOutcomeAdvancement - 0.2)
                                    <= ATTRIBUTE_EPSILON,
                    label + " outcome advancement must run after BABY attributes");
            helper.assertTrue(evolveObserver.airSupplyAtOutcomeAdvancement == 1,
                    label + " BABY evolution has no pre-advancement passive air side effect");
        }
        helper.assertTrue(evolveObserver.healthAtOutcomeAdvancement <= 0.0F
                        && evolveObserver.foodLevelAtOutcomeAdvancement == 2
                        && Float.compare(evolveObserver.saturationAtOutcomeAdvancement, 1.0F) == 0
                        && Math.abs(evolveObserver.fallDistanceAtOutcomeAdvancement - 7.0)
                                <= ATTRIBUTE_EPSILON
                        && evolveObserver.fireTicksAtOutcomeAdvancement == 80,
                label + " outcome advancement must run before recovery");

        helper.assertTrue(expectedData.equals(deathObserver.dataAtLowest),
                label + " LOWEST must observe the committed state and flags");
        helper.assertTrue(deathObserver.tridentsAtLowest == expectedTridents,
                label + " LOWEST must observe the completed reward exactly once");
        helper.assertTrue(deathObserver.outcomeAdvancementAtLowest,
                label + " LOWEST must observe the outcome advancement");
        helper.assertTrue(deathObserver.firstEvolutionAdvancementAtLowest == drowning,
                label + " FIRST_EVOLUTION must match the real form-change outcome");
        helper.assertTrue(deathObserver.postCountAtLowest == 1,
                label + " LOWEST must observe exactly one completed Post");
        helper.assertTrue(deathObserver.healthAtLowest > 0.0F
                        && deathObserver.airSupplyAtLowest == player.getMaxAirSupply()
                        && deathObserver.foodLevelAtLowest == 6
                        && Float.compare(deathObserver.saturationAtLowest, 0.0F) == 0
                        && Math.abs(deathObserver.fallDistanceAtLowest) <= ATTRIBUTE_EPSILON
                        && deathObserver.fireTicksAtLowest <= 0,
                label + " LOWEST must observe recovery after advancements");

        helper.assertTrue(player.isAlive(),
                label + " accepted evolution must keep the same player alive");
        helper.assertTrue(deathStat(player) == deathsBefore,
                label + " accepted evolution must not increment DEATHS");
        helper.assertTrue(listener.hasClientLoaded(),
                label + " accepted evolution must not unload the connection");
        helper.assertTrue(player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).equals(expectedData),
                label + " must commit the exact expected state and reward flags");
        helper.assertTrue(inventoryCount(player, Items.TRIDENT) == expectedTridents,
                label + " must finish with exactly " + expectedTridents + " trident(s)");
        helper.assertTrue(hasAdvancement(player, outcomeAdvancement),
                label + " must finish with its outcome advancement");
        helper.assertTrue(hasAdvancement(player, IAmZombieAdvancements.FIRST_EVOLUTION) == drowning,
                label + " FIRST_EVOLUTION completion must match the form-changing outcome");
        helper.assertTrue(hasExpectedFormAttributeValues(
                        helper, player, expectedData.state().form(), expectedData.state().size()),
                label + " must finish with refreshed evolved attributes");
        assertInPlacePlayer(helper, level, player, playerId, label);
        assertEvolutionRecoveryAndRetention(helper, player, label);
        assertStableEntityPayloadCount(helper, payloadTargets, stableEntityId, 1, label);

        if (!drowning) {
            helper.assertTrue(expectedData.receivedFirstDrownedReward()
                            == initialData.receivedFirstDrownedReward()
                            && expectedData.receivedFirstHuskReward()
                                    == initialData.receivedFirstHuskReward()
                            && expectedData.receivedFirstZombifiedPiglinReward()
                                    == initialData.receivedFirstZombifiedPiglinReward(),
                    label + " must retain every reward flag");
        }
    }

    private static void prepareEvolutionFixture(ServerPlayer player) {
        prepareEvolutionFixture(
                player,
                PlayerZombieData.DEFAULT.withState(
                        new ZombieState(ZombieForm.NORMAL, ZombieSize.ADULT)));
    }

    private static void prepareEvolutionFixture(ServerPlayer player, PlayerZombieData data) {
        player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, data);
        ZombieFormAttributes.refreshFormAttributesForced(player, data);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 3));

        player.experienceLevel = 7;
        player.experienceProgress = 0.25F;
        player.totalExperience = 123;
        player.setHealth(player.getMaxHealth());
        player.setAbsorptionAmount(0.0F);
        player.setAirSupply(1);
        player.getFoodData().setFoodLevel(2);
        player.getFoodData().setSaturation(1.0F);
        player.fallDistance = 7.0;
        player.setRemainingFireTicks(80);
        player.invulnerableTime = 0;
        player.hurtTime = 0;
        player.deathTime = 0;
        player.getAbilities().instabuild = false;
        player.getAbilities().invulnerable = false;
        player.getAbilities().flying = false;
        player.setInvulnerable(false);
    }

    private static void assertCanceledDeath(GameTestHelper helper, DeathObserver observer, String label) {
        helper.assertTrue(observer.matchingDeathEvents == 1,
                label + " should publish exactly one matching LivingDeathEvent, observed "
                        + observer.matchingDeathEvents);
        helper.assertTrue(observer.lastDeathCanceled,
                label + " LivingDeathEvent should be finally canceled at LOWEST priority");
    }

    private static void assertInPlacePlayer(
            GameTestHelper helper,
            ServerLevel level,
            ServerPlayer player,
            UUID playerId,
            String label) {
        helper.assertTrue(player.isAlive(), label + " evolution should leave the same player alive");
        helper.assertFalse(player.isRemoved(), label + " evolution should not remove the player entity");
        helper.assertTrue(level.getServer().getPlayerList().getPlayersByUUID().get(playerId) == player,
                label + " evolution should retain the same instance in the PlayerList UUID map");
        helper.assertTrue(level.players().stream().filter(candidate -> playerId.equals(candidate.getUUID())).count() == 1,
                label + " evolution should leave exactly one same-UUID player in ServerLevel.players");
        helper.assertTrue(level.players().stream().anyMatch(candidate -> candidate == player),
                label + " evolution should retain the same instance in ServerLevel.players");
    }

    private static void assertEvolutionRecoveryAndRetention(
            GameTestHelper helper,
            ServerPlayer player,
            String label) {
        float expectedHealth = Math.max(1.0F, player.getMaxHealth() * 0.5F);
        helper.assertTrue(Math.abs(player.getHealth() - expectedHealth) <= 0.001F,
                label + " evolution should restore half max health");
        helper.assertTrue(player.getAirSupply() == player.getMaxAirSupply(),
                label + " evolution should restore full air");
        helper.assertTrue(player.getFoodData().getFoodLevel() >= 6,
                label + " evolution should restore at least six food points");
        helper.assertTrue(Float.compare(player.getFoodData().getSaturationLevel(), 0.0F) == 0,
                label + " evolution should reset saturation to zero");
        helper.assertTrue(Math.abs(player.fallDistance) <= ATTRIBUTE_EPSILON,
                label + " evolution should reset fall distance");
        helper.assertTrue(player.getRemainingFireTicks() <= 0,
                label + " evolution should clear fire");

        ItemStack marker = player.getInventory().getItem(9);
        helper.assertTrue(marker.is(Items.DIAMOND) && marker.getCount() == 3,
                label + " evolution should retain DIAMOND x3 in inventory slot 9");
        helper.assertTrue(player.experienceLevel == 7,
                label + " evolution should retain experience level 7");
        helper.assertTrue(Float.compare(player.experienceProgress, 0.25F) == 0,
                label + " evolution should retain experience progress 0.25");
        helper.assertTrue(player.totalExperience == 123,
                label + " evolution should retain total experience 123");
    }

    private static int inventoryCount(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean hasAdvancement(ServerPlayer player, Identifier id) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(id);
        return advancement != null
                && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static void assertAllNonGiantAttributeProfiles(GameTestHelper helper, FakePlayer player) {
        for (ZombieForm form : NON_GIANT_FORMS) {
            for (ZombieSize size : ZombieSize.values()) {
                PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE)
                        .withState(new ZombieState(form, size));
                player.setData(IAmZombieAttachments.PLAYER_ZOMBIE, data);
                ZombieFormAttributes.refreshFormAttributesForced(player, data);

                ZombieState actualState = GameTestPlayers.stateOf(player);
                if (actualState.form() != form || actualState.size() != size) {
                    helper.fail("expected attack-profile state " + form + "/" + size + ", was " + actualState);
                    return;
                }
                if (!hasExpectedFormAttributeValues(helper, player, form, size)
                        || !hasExpectedAttackProfile(helper, player, 4.5, true, false, true)) {
                    return;
                }
            }
        }
    }

    private static boolean hasExpectedFormAttributeValues(
            GameTestHelper helper,
            ServerPlayer player,
            ZombieForm form,
            ZombieSize size) {
        boolean baby = size == ZombieSize.BABY;
        boolean drowned = form == ZombieForm.DROWNED;
        boolean giant = form == ZombieForm.GIANT;
        return hasExpectedAttributeValue(helper, player.getAttribute(Attributes.SCALE),
                        giant ? (baby ? 5.5 : 6.0) : (baby ? 0.5 : 1.0), "scale", form, size)
                && hasExpectedAttributeValue(helper, player.getAttribute(Attributes.MOVEMENT_SPEED),
                        baby ? 0.15 : 0.1, "movement speed", form, size)
                && hasExpectedAttributeValue(helper, player.getAttribute(Attributes.SUBMERGED_MINING_SPEED),
                        drowned ? 1.0 : 0.2, "submerged mining speed", form, size)
                && hasExpectedAttributeValue(helper, player.getAttribute(Attributes.MAX_HEALTH),
                        giant ? 100.0 : 20.0, "max health", form, size)
                && hasExpectedAttributeValue(helper, player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE),
                        giant ? 27.0 : 4.5, "block interaction range", form, size)
                && hasExpectedAttributeValue(helper, player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE),
                        giant ? 18.0 : 3.0, "entity interaction range", form, size)
                && hasExpectedAttributeValue(helper, player.getAttribute(Attributes.STEP_HEIGHT),
                        giant ? 3.6 : 0.6, "step height", form, size)
                && hasExpectedAttributeValue(helper, player.getAttribute(Attributes.SAFE_FALL_DISTANCE),
                        giant ? 6.0 : 3.0, "safe fall distance", form, size);
    }

    private static boolean hasExpectedAttributeValue(
            GameTestHelper helper,
            AttributeInstance attribute,
            double expectedValue,
            String name,
            ZombieForm form,
            ZombieSize size) {
        if (attribute == null) {
            helper.fail("FakePlayer is missing the " + name + " attribute");
            return false;
        }
        if (Math.abs(attribute.getValue() - expectedValue) > ATTRIBUTE_EPSILON) {
            helper.fail(form + "/" + size + " " + name + " expected " + expectedValue
                    + ", was " + attribute.getValue());
            return false;
        }
        return true;
    }

    private static boolean hasExpectedAttackProfile(
            GameTestHelper helper,
            FakePlayer player,
            double expectedValue,
            boolean expectNonGiant,
            boolean expectGiant,
            boolean expectDifficulty) {
        return hasExpectedAttackProfile(
                helper, player, expectedValue, expectNonGiant, expectGiant, expectDifficulty, false);
    }

    private static boolean hasExpectedAttackProfile(
            GameTestHelper helper,
            FakePlayer player,
            double expectedValue,
            boolean expectNonGiant,
            boolean expectGiant,
            boolean expectDifficulty,
            boolean expectDiamondSword) {
        AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) {
            helper.fail("FakePlayer is missing the ATTACK_DAMAGE attribute");
            return false;
        }
        if (Math.abs(attackDamage.getBaseValue() - 1.0) > 1.0e-9) {
            helper.fail("form refresh must preserve the player's attack base at 1.0, was "
                    + attackDamage.getBaseValue());
            return false;
        }
        if (Math.abs(attackDamage.getValue() - expectedValue) > 1.0e-9) {
            helper.fail("unexpected attack damage: expected " + expectedValue + ", was " + attackDamage.getValue());
            return false;
        }
        if (!hasExpectedModifier(helper, attackDamage, NON_GIANT_ATTACK_DAMAGE_ID, expectNonGiant,
                2.0, AttributeModifier.Operation.ADD_VALUE)
                || !hasExpectedModifier(helper, attackDamage, GIANT_ATTACK_ID, expectGiant,
                        54.0, AttributeModifier.Operation.ADD_VALUE)
                || !hasExpectedModifier(helper, attackDamage, DIFFICULTY_ATTACK_DAMAGE_ID, expectDifficulty,
                        0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
                || !hasExpectedModifier(helper, attackDamage, Item.BASE_ATTACK_DAMAGE_ID, expectDiamondSword,
                        6.0, AttributeModifier.Operation.ADD_VALUE)) {
            return false;
        }

        long ownedModifierCount = attackDamage.getModifiers().stream()
                .filter(modifier -> modifier.is(NON_GIANT_ATTACK_DAMAGE_ID)
                        || modifier.is(GIANT_ATTACK_ID)
                        || modifier.is(DIFFICULTY_ATTACK_DAMAGE_ID))
                .count();
        long expectedModifierCount = (expectNonGiant ? 1 : 0) + (expectGiant ? 1 : 0) + (expectDifficulty ? 1 : 0);
        if (ownedModifierCount != expectedModifierCount) {
            helper.fail("attack modifiers duplicated or retained across form transition: expected "
                    + expectedModifierCount + ", was " + ownedModifierCount);
            return false;
        }
        long expectedTotalModifierCount = expectedModifierCount + (expectDiamondSword ? 1 : 0);
        if (attackDamage.getModifiers().size() != expectedTotalModifierCount) {
            helper.fail("unexpected total attack modifier count: expected "
                    + expectedTotalModifierCount + ", was " + attackDamage.getModifiers().size());
            return false;
        }
        return true;
    }

    private static boolean hasExpectedModifier(
            GameTestHelper helper,
            AttributeInstance attribute,
            Identifier id,
            boolean expected,
            double expectedAmount,
            AttributeModifier.Operation expectedOperation) {
        AttributeModifier modifier = attribute.getModifier(id);
        if (!expected) {
            if (modifier != null) {
                helper.fail("unexpected residual attack modifier " + id);
                return false;
            }
            return true;
        }
        if (modifier == null) {
            helper.fail("missing attack modifier " + id);
            return false;
        }
        if (Math.abs(modifier.amount() - expectedAmount) > 1.0e-9 || modifier.operation() != expectedOperation) {
            helper.fail("attack modifier " + id + " had amount/operation "
                    + modifier.amount() + "/" + modifier.operation());
            return false;
        }
        return true;
    }

    private static PlayerZombieData distinguishableData(ZombieForm form, ZombieSize size) {
        return new PlayerZombieData(
                new ZombieState(form, size),
                true,
                false,
                true);
    }

    private static EmbeddedChannel embeddedChannel(GameTestHelper helper, ServerPlayer player) {
        Channel rawChannel = player.connection.getConnection().channel();
        helper.assertTrue(rawChannel instanceof EmbeddedChannel,
                "connected S1 GameTest player must use an EmbeddedChannel");
        return (EmbeddedChannel) rawChannel;
    }

    private static int deathStat(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
    }

    private static void assertTransformPreSnapshot(
            GameTestHelper helper,
            TransformObserver observer,
            ServerPlayer player,
            int expectedEntityId,
            ZombieForm expectedFrom,
            ZombieForm expectedTo,
            PlayerZombieData expectedData,
            String label) {
        helper.assertTrue(observer.preCount == 1,
                label + " must publish Transform Pre exactly once");
        helper.assertTrue(observer.prePlayer == player,
                label + " Transform Pre must expose the transforming player");
        helper.assertTrue(observer.preEntityId == expectedEntityId,
                label + " Transform Pre must use the stable player entity ID");
        helper.assertTrue(observer.preFrom == expectedFrom && observer.preTo == expectedTo,
                label + " Transform Pre must carry " + expectedFrom + " -> " + expectedTo);
        helper.assertTrue(observer.preAttachmentRead,
                label + " Transform Pre timing probe must read the already-established attachment");
        helper.assertTrue(expectedData.equals(observer.preData),
                label + " Transform Pre must run before any state or flag write");
        helper.assertTrue(Math.abs(observer.preScale - 0.5) <= ATTRIBUTE_EPSILON,
                label + " Transform Pre must run before any attribute side effect");
        helper.assertTrue(Float.compare(observer.preMaxHealth, 20.0F) == 0,
                label + " Transform Pre must observe the old max health");
        helper.assertTrue(Float.compare(observer.preHealth, 7.0F) == 0,
                label + " Transform Pre must run before any healing side effect");
    }

    private static void assertTransformPostSnapshot(
            GameTestHelper helper,
            TransformObserver observer,
            ServerPlayer player,
            int expectedEntityId,
            ZombieForm expectedFrom,
            ZombieForm expectedTo,
            PlayerZombieData expectedData,
            String label) {
        helper.assertTrue(observer.postCount == 1,
                label + " must publish Transform Post exactly once");
        helper.assertTrue(observer.postPlayer == player,
                label + " Transform Post must expose the transformed player");
        helper.assertTrue(observer.postEntityId == expectedEntityId,
                label + " Transform Post must use the expected entity ID");
        helper.assertTrue(observer.postFrom == expectedFrom && observer.postTo == expectedTo,
                label + " Transform Post must carry " + expectedFrom + " -> " + expectedTo);
        helper.assertTrue(observer.postAttachmentRead,
                label + " Transform Post timing probe must read the committed attachment");
        helper.assertTrue(expectedData.equals(observer.postData),
                label + " Transform Post must observe the single committed state-and-flags value");
    }

    private static void assertStableEntityPayloadCount(
            GameTestHelper helper,
            List<Integer> payloadTargets,
            int entityId,
            int expected,
            String label) {
        helper.assertTrue(payloadTargets.size() == expected,
                label + " expected exactly " + expected + " PLAYER_ZOMBIE payload(s), got "
                        + payloadTargets);
        helper.assertTrue(countTarget(payloadTargets, entityId) == expected,
                label + " expected exactly " + expected + " PLAYER_ZOMBIE payload(s) for entity "
                        + entityId + ", got " + payloadTargets);
    }

    private static void assertClonePayloadCounts(
            GameTestHelper helper,
            List<Integer> payloadTargets,
            int temporaryEntityId,
            int finalEntityId,
            String label) {
        helper.assertTrue(temporaryEntityId != finalEntityId,
                label + " requires distinct temporary and final entity IDs");
        helper.assertTrue(payloadTargets.size() == 2,
                label + " must emit only the handler update and mandatory initial snapshot, got "
                        + payloadTargets);
        helper.assertTrue(countTarget(payloadTargets, temporaryEntityId) == 1,
                label + " must emit exactly one handler update for temporary entity ID "
                        + temporaryEntityId + ", got " + payloadTargets);
        helper.assertTrue(countTarget(payloadTargets, finalEntityId) == 1,
                label + " must emit exactly one mandatory initial snapshot for final entity ID "
                        + finalEntityId + ", got " + payloadTargets);
    }

    private static int countTarget(List<Integer> payloadTargets, int entityId) {
        int count = 0;
        for (int target : payloadTargets) {
            if (target == entityId) {
                count++;
            }
        }
        return count;
    }

    /**
     * Drains every outbound message, releasing it exactly once, while retaining only PLAYER_ZOMBIE attachment
     * targets. Filtering the actual payload type avoids confusing vanilla traffic or other synced attachments with
     * the production handler's state update.
     */
    private static List<Integer> drainPlayerZombiePayloadTargets(EmbeddedChannel channel) {
        channel.runPendingTasks();
        channel.runScheduledPendingTasks();
        List<Integer> targets = new ArrayList<>();
        Object message;
        while ((message = channel.readOutbound()) != null) {
            try {
                if (message instanceof ClientboundCustomPayloadPacket customPayloadPacket
                        && customPayloadPacket.payload() instanceof SyncAttachmentsPayload payload
                        && payload.types().contains(IAmZombieAttachments.PLAYER_ZOMBIE.get())) {
                    if (payload.target() instanceof SyncAttachmentsPayload.EntityTarget entityTarget) {
                        targets.add(entityTarget.entity());
                    } else {
                        // Preserve an unexpected target in the total so exact-count assertions fail loudly.
                        targets.add(Integer.MIN_VALUE);
                    }
                }
            } finally {
                ReferenceCountUtil.release(message);
            }
        }
        return targets;
    }

    private static void assertRealRespawnReplacement(
            GameTestHelper helper,
            ServerLevel level,
            UUID playerId,
            ServerPlayer oldPlayer,
            ServerPlayer newPlayer,
            ServerGamePacketListenerImpl listener,
            String label) {
        helper.assertTrue(newPlayer != oldPlayer,
                label + " must create a fresh ServerPlayer instance");
        helper.assertTrue(newPlayer.getUUID().equals(playerId),
                label + " fresh player must retain the original UUID");
        helper.assertTrue(oldPlayer.isRemoved(),
                label + " must remove the dead ServerPlayer during respawn");
        helper.assertTrue(level.getServer().getPlayerList().getPlayersByUUID().get(playerId) == newPlayer,
                label + " PlayerList UUID map must point to the fresh instance");
        helper.assertTrue(level.getServer().getPlayerList().getPlayers().stream()
                        .filter(player -> playerId.equals(player.getUUID())).count() == 1,
                label + " PlayerList must contain exactly one same-UUID player");
        helper.assertTrue(level.getServer().getPlayerList().getPlayers().stream()
                        .anyMatch(player -> player == newPlayer),
                label + " PlayerList must contain the fresh instance");
        helper.assertFalse(level.getServer().getPlayerList().getPlayers().stream()
                        .anyMatch(player -> player == oldPlayer),
                label + " PlayerList must not retain the dead instance");
        helper.assertTrue(level.players().stream()
                        .filter(player -> playerId.equals(player.getUUID())).count() == 1,
                label + " level must contain exactly one same-UUID player");
        helper.assertTrue(level.players().stream().anyMatch(player -> player == newPlayer),
                label + " level must contain the fresh instance");
        helper.assertFalse(level.players().stream().anyMatch(player -> player == oldPlayer),
                label + " level must not retain the dead instance");
        helper.assertTrue(level.getEntity(playerId) == newPlayer,
                label + " level UUID index must point to the fresh instance");
        helper.assertTrue(listener.getPlayer() == newPlayer && newPlayer.connection == listener,
                label + " connection listener must switch to and remain on the fresh instance");
    }

    private static void closeConnectedFixture(
            GameTestHelper helper,
            UUID playerId,
            EmbeddedChannel channel) {
        try {
            if (playerId != null) {
                GameTestPlayers.disconnectConnectedPlayer(helper, playerId);
            }
        } finally {
            if (channel != null) {
                channel.finishAndReleaseAll();
            }
        }
    }

    private static final class TransformObserver {
        private final UUID playerId;
        private final boolean cancelPre;
        private final boolean freshHolderPre;

        private int preCount;
        private ServerPlayer prePlayer;
        private int preEntityId = Integer.MIN_VALUE;
        private ZombieForm preFrom;
        private ZombieForm preTo;
        private boolean preAttachmentRead;
        private PlayerZombieData preData;
        private double preScale;
        private float preMaxHealth;
        private float preHealth;
        private boolean preCancellationApplied;

        private int postCount;
        private ServerPlayer postPlayer;
        private int postEntityId = Integer.MIN_VALUE;
        private ZombieForm postFrom;
        private ZombieForm postTo;
        private boolean postAttachmentRead;
        private PlayerZombieData postData;
        private double postScale;
        private float postMaxHealth;
        private float postHealth;

        private TransformObserver(UUID playerId, boolean cancelPre, boolean freshHolderPre) {
            this.playerId = playerId;
            this.cancelPre = cancelPre;
            this.freshHolderPre = freshHolderPre;
        }

        @SubscribeEvent
        public void onTransformPre(ZombieTransformPreEvent event) {
            if (!playerId.equals(event.player().getUUID())) {
                return;
            }
            preCount++;
            prePlayer = event.player();
            preEntityId = event.player().getId();
            preFrom = event.from();
            preTo = event.to();

            // Clone Pre's player is a fresh holder. Reading PLAYER_ZOMBIE here would materialize DEFAULT and emit an
            // addon-induced extra update for the temporary ID, invalidating the base-handler payload invariant.
            if (!freshHolderPre) {
                preAttachmentRead = true;
                preData = event.player().getData(IAmZombieAttachments.PLAYER_ZOMBIE);
                preScale = event.player().getAttributeValue(Attributes.SCALE);
                preMaxHealth = event.player().getMaxHealth();
                preHealth = event.player().getHealth();
            }
            if (cancelPre) {
                event.setCanceled(true);
            }
            preCancellationApplied = event.isCanceled();
        }

        @SubscribeEvent
        public void onTransformed(ZombieTransformedEvent event) {
            if (!playerId.equals(event.player().getUUID())) {
                return;
            }
            postCount++;
            postPlayer = event.player();
            postEntityId = event.player().getId();
            postFrom = event.from();
            postTo = event.to();
            postAttachmentRead = true;
            postData = event.player().getData(IAmZombieAttachments.PLAYER_ZOMBIE);
            postScale = event.player().getAttributeValue(Attributes.SCALE);
            postMaxHealth = event.player().getMaxHealth();
            postHealth = event.player().getHealth();
        }
    }

    private static final class EvolveObserver {
        private final UUID playerId;
        private final boolean cancelPre;
        private final Identifier outcomeAdvancement;
        private final List<String> callbackOrder = new ArrayList<>();

        private int preCount;
        private ServerPlayer prePlayer;
        private int preEntityId = Integer.MIN_VALUE;
        private ZombieState preBefore;
        private ZombieState preAfter;
        private DeathOutcome preOutcome;
        private boolean preCancellationApplied;
        private PlayerZombieData preData;
        private int preTridents;
        private boolean preOutcomeAdvancement;
        private boolean preFirstEvolutionAdvancement;
        private double preScale;
        private double preSubmergedMiningSpeed;
        private float preHealth;
        private int preAirSupply;
        private int preFoodLevel;
        private float preSaturation;
        private double preFallDistance;
        private int preFireTicks;

        private int postCount;
        private ServerPlayer postPlayer;
        private int postEntityId = Integer.MIN_VALUE;
        private ZombieState postBefore;
        private ZombieState postAfter;
        private DeathOutcome postOutcome;
        private PlayerZombieData postData;
        private int postTridents;
        private boolean postOutcomeAdvancement;
        private boolean postFirstEvolutionAdvancement;
        private double postScale;
        private double postSubmergedMiningSpeed;
        private float postHealth;
        private int postAirSupply;
        private int postFoodLevel;
        private float postSaturation;
        private double postFallDistance;
        private int postFireTicks;

        private int outcomeAdvancementCount;
        private int postCountAtOutcomeAdvancement;
        private PlayerZombieData dataAtOutcomeAdvancement;
        private int tridentsAtOutcomeAdvancement;
        private boolean outcomeAdvancementDoneInCallback;
        private double scaleAtOutcomeAdvancement;
        private double submergedMiningSpeedAtOutcomeAdvancement;
        private float healthAtOutcomeAdvancement;
        private int airSupplyAtOutcomeAdvancement;
        private int foodLevelAtOutcomeAdvancement;
        private float saturationAtOutcomeAdvancement;
        private double fallDistanceAtOutcomeAdvancement;
        private int fireTicksAtOutcomeAdvancement;

        private EvolveObserver(
                UUID playerId,
                boolean cancelPre,
                Identifier outcomeAdvancement) {
            this.playerId = playerId;
            this.cancelPre = cancelPre;
            this.outcomeAdvancement = outcomeAdvancement;
        }

        @SubscribeEvent
        public void onEvolvePre(ZombieEvolvePreEvent event) {
            if (!playerId.equals(event.player().getUUID())) {
                return;
            }
            ServerPlayer player = event.player();
            callbackOrder.add("pre");
            preCount++;
            prePlayer = player;
            preEntityId = player.getId();
            preBefore = event.before();
            preAfter = event.after();
            preOutcome = event.outcome();
            preData = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
            preTridents = inventoryCount(player, Items.TRIDENT);
            preOutcomeAdvancement = hasAdvancement(player, outcomeAdvancement);
            preFirstEvolutionAdvancement =
                    hasAdvancement(player, IAmZombieAdvancements.FIRST_EVOLUTION);
            preScale = player.getAttributeValue(Attributes.SCALE);
            preSubmergedMiningSpeed =
                    player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
            preHealth = player.getHealth();
            preAirSupply = player.getAirSupply();
            preFoodLevel = player.getFoodData().getFoodLevel();
            preSaturation = player.getFoodData().getSaturationLevel();
            preFallDistance = player.fallDistance;
            preFireTicks = player.getRemainingFireTicks();
            if (cancelPre) {
                event.setCanceled(true);
            }
            preCancellationApplied = event.isCanceled();
        }

        @SubscribeEvent
        public void onEvolved(ZombieEvolvedEvent event) {
            if (!playerId.equals(event.player().getUUID())) {
                return;
            }
            ServerPlayer player = event.player();
            callbackOrder.add("post");
            postCount++;
            postPlayer = player;
            postEntityId = player.getId();
            postBefore = event.before();
            postAfter = event.after();
            postOutcome = event.outcome();
            postData = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
            postTridents = inventoryCount(player, Items.TRIDENT);
            postOutcomeAdvancement = hasAdvancement(player, outcomeAdvancement);
            postFirstEvolutionAdvancement =
                    hasAdvancement(player, IAmZombieAdvancements.FIRST_EVOLUTION);
            postScale = player.getAttributeValue(Attributes.SCALE);
            postSubmergedMiningSpeed =
                    player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
            postHealth = player.getHealth();
            postAirSupply = player.getAirSupply();
            postFoodLevel = player.getFoodData().getFoodLevel();
            postSaturation = player.getFoodData().getSaturationLevel();
            postFallDistance = player.fallDistance;
            postFireTicks = player.getRemainingFireTicks();
        }

        @SubscribeEvent
        public void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !playerId.equals(player.getUUID())
                    || !outcomeAdvancement.equals(event.getAdvancement().id())) {
                return;
            }
            outcomeAdvancementCount++;
            postCountAtOutcomeAdvancement = postCount;
            dataAtOutcomeAdvancement =
                    player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
            tridentsAtOutcomeAdvancement = inventoryCount(player, Items.TRIDENT);
            outcomeAdvancementDoneInCallback =
                    hasAdvancement(player, outcomeAdvancement);
            scaleAtOutcomeAdvancement = player.getAttributeValue(Attributes.SCALE);
            submergedMiningSpeedAtOutcomeAdvancement =
                    player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
            healthAtOutcomeAdvancement = player.getHealth();
            airSupplyAtOutcomeAdvancement = player.getAirSupply();
            foodLevelAtOutcomeAdvancement = player.getFoodData().getFoodLevel();
            saturationAtOutcomeAdvancement =
                    player.getFoodData().getSaturationLevel();
            fallDistanceAtOutcomeAdvancement = player.fallDistance;
            fireTicksAtOutcomeAdvancement = player.getRemainingFireTicks();
        }
    }

    private static final class EvolveDeathObserver {
        private final UUID playerId;
        private final ResourceKey<DamageType> damageType;
        private final EvolveObserver evolveObserver;
        private final Identifier outcomeAdvancement;

        private int matchingDeathEvents;
        private boolean lastDeathCanceled;
        private PlayerZombieData dataAtLowest;
        private int tridentsAtLowest;
        private boolean outcomeAdvancementAtLowest;
        private boolean firstEvolutionAdvancementAtLowest;
        private int postCountAtLowest;
        private double scaleAtLowest;
        private double submergedMiningSpeedAtLowest;
        private float healthAtLowest;
        private int airSupplyAtLowest;
        private int foodLevelAtLowest;
        private float saturationAtLowest;
        private double fallDistanceAtLowest;
        private int fireTicksAtLowest;

        private EvolveDeathObserver(
                UUID playerId,
                ResourceKey<DamageType> damageType,
                EvolveObserver evolveObserver,
                Identifier outcomeAdvancement) {
            this.playerId = playerId;
            this.damageType = damageType;
            this.evolveObserver = evolveObserver;
            this.outcomeAdvancement = outcomeAdvancement;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void onDeath(LivingDeathEvent event) {
            if (!playerId.equals(event.getEntity().getUUID())
                    || !event.getSource().is(damageType)
                    || !(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            matchingDeathEvents++;
            lastDeathCanceled = event.isCanceled();
            evolveObserver.callbackOrder.add("death-lowest");
            dataAtLowest = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
            tridentsAtLowest = inventoryCount(player, Items.TRIDENT);
            outcomeAdvancementAtLowest =
                    hasAdvancement(player, outcomeAdvancement);
            firstEvolutionAdvancementAtLowest =
                    hasAdvancement(player, IAmZombieAdvancements.FIRST_EVOLUTION);
            postCountAtLowest = evolveObserver.postCount;
            scaleAtLowest = player.getAttributeValue(Attributes.SCALE);
            submergedMiningSpeedAtLowest =
                    player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
            healthAtLowest = player.getHealth();
            airSupplyAtLowest = player.getAirSupply();
            foodLevelAtLowest = player.getFoodData().getFoodLevel();
            saturationAtLowest = player.getFoodData().getSaturationLevel();
            fallDistanceAtLowest = player.fallDistance;
            fireTicksAtLowest = player.getRemainingFireTicks();
        }
    }

    private static final class DeathObserver {
        private final UUID playerId;
        private final ResourceKey<DamageType> damageType;
        private int matchingDeathEvents;
        private boolean lastDeathCanceled;

        private DeathObserver(UUID playerId, ResourceKey<DamageType> damageType) {
            this.playerId = playerId;
            this.damageType = damageType;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void onDeath(LivingDeathEvent event) {
            if (playerId.equals(event.getEntity().getUUID()) && event.getSource().is(damageType)) {
                matchingDeathEvents++;
                lastDeathCanceled = event.isCanceled();
            }
        }
    }
}
