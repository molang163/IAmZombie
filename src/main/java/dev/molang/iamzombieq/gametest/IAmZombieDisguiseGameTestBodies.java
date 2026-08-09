package dev.molang.iamzombieq.gametest;

import java.util.Set;
import java.util.UUID;

import dev.molang.iamzombieq.IAmZombieItems;
import dev.molang.iamzombieq.rules.VillagerFearRules;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
//? if >=26.2
import net.minecraft.world.entity.EntityTypes;
//? if <26.2
//import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Real server interaction bodies for the disguise trade gate. The positive path completes the vanilla merchant
 * result-slot transaction, which publishes NeoForge's TradeWithVillagerEvent from AbstractVillager.notifyTrade.
 */
final class IAmZombieDisguiseGameTestBodies {
    private static final BlockPos FIXTURE_POS = new BlockPos(2, 2, 1);
    private static final BlockPos FEAR_PLAYER_POS = new BlockPos(12, 1, 17);
    private static final BlockPos FEAR_MOB_POS = new BlockPos(17, 1, 17);
    private static final int PAYMENT_SLOT = 9;
    private static final int MASKED_SENSOR_TICKS = 45;
    private static final int TRADER_START_ATTEMPTS = 40;

    private IAmZombieDisguiseGameTestBodies() {
    }

    static void undisguisedZombieIsDenied(GameTestHelper helper) {
        ServerPlayer player = null;
        UUID playerId = null;
        Villager villager = null;
        try {
            player = GameTestPlayers.spawnConnectedZombiePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
            playerId = player.getUUID();
            GameTestAssertions.assertFalse(helper, player.hasInfiniteMaterials(),
                    "connected trade player must not retain infinite materials");
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            villager = helper.spawn(EntityTypes.VILLAGER, FIXTURE_POS);
            villager.setNoAi(true);
            MerchantOffer offer = installFixedOffer(villager);
            GameTestAssertions.assertTrue(helper, offer.getUses() == 0, "fixed villager offer should begin unused");
            GameTestAssertions.assertTrue(helper, player.containerMenu == player.inventoryMenu,
                    "connected trade player should begin in its inventory menu");

            InteractionResult result = player.interactOn(villager, InteractionHand.MAIN_HAND
                    //? if >=26.1
                    , Vec3.ZERO
            );

            GameTestAssertions.assertTrue(helper, result == InteractionResult.SUCCESS_SERVER,
                    "undisguised zombie interaction should return SUCCESS_SERVER");
            GameTestAssertions.assertTrue(helper, player.containerMenu == player.inventoryMenu,
                    "undisguised zombie must not open a merchant menu");
            GameTestAssertions.assertTrue(helper, villager.getTradingPlayer() == null,
                    "denied villager must not acquire a trading player");
            GameTestAssertions.assertTrue(helper, offer.getUses() == 0,
                    "denied villager offer must remain unused");
        } finally {
            cleanup(helper, player, playerId, villager);
        }
        helper.succeed();
    }

    static void villagerFearRespectsDisguise(GameTestHelper helper) {
        ServerPlayer player = null;
        UUID playerId = null;
        Villager villager = null;
        double originalMovementSpeed = 0.0;
        try {
            player = GameTestPlayers.spawnConnectedZombiePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
            playerId = player.getUUID();
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(IAmZombieItems.DISGUISE_MASK.get()));
            moveTo(helper, player, FEAR_PLAYER_POS);

            villager = helper.spawn(EntityTypes.VILLAGER, FEAR_MOB_POS);
            originalMovementSpeed = villager.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
            villager.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        } catch (RuntimeException | Error failure) {
            cleanupFear(helper, playerId, villager);
            throw failure;
        }

        ServerPlayer testPlayer = player;
        UUID testPlayerId = playerId;
        Villager testVillager = villager;
        double movementSpeed = originalMovementSpeed;
        double[] initialPlayerDistance = new double[1];
        Runnable cleanup = () -> cleanupFear(helper, testPlayerId, testVillager);

        //? if >=26.1
        helper.runBeforeTestEnd(() -> {
        //? if <26.1
        //helper.runAtTickTime(helper.testInfo.getTimeoutTicks() - 1, () -> {
            try {
                GameTestAssertions.fail(helper, "villager fear sequence did not complete before the timeout");
            } finally {
                cleanup.run();
            }
        });

        helper.startSequence()
                .thenExecuteAfter(MASKED_SENSOR_TICKS, () -> {
                    try {
                        Brain<Villager> brain = testVillager.getBrain();
                        assertPlayerVisible(helper, testVillager, brain, testPlayer, "masked villager");
                        GameTestAssertions.assertTrue(helper, brain.getMemory(MemoryModuleType.NEAREST_HOSTILE).isEmpty(),
                                "masked visible zombie player must not become the villager's nearest hostile");

                        testVillager.getNavigation().stop();
                        brain.eraseMemory(MemoryModuleType.PATH);
                        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                        testVillager.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(movementSpeed);
                        initialPlayerDistance[0] = testVillager.distanceToSqr(testPlayer);
                        testPlayer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    } catch (RuntimeException | Error failure) {
                        cleanup.run();
                        throw failure;
                    }
                })
                .thenWaitUntil(() -> assertUnmaskedVillagerFlees(
                        helper, testVillager, testPlayer, initialPlayerDistance[0]))
                .thenExecute(cleanup)
                .thenSucceed();
    }

    static void wanderingTraderFearRespectsDisguise(GameTestHelper helper) {
        ServerPlayer player = null;
        UUID playerId = null;
        WanderingTrader trader = null;
        try {
            ServerLevel level = helper.getLevel();
            player = GameTestPlayers.spawnConnectedZombiePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
            playerId = player.getUUID();
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(IAmZombieItems.DISGUISE_MASK.get()));
            moveTo(helper, player, FEAR_PLAYER_POS);

            trader = EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.STRUCTURE);
            if (trader == null) {
                GameTestAssertions.fail(helper, "failed to create the wandering-trader fear fixture");
                return;
            }
            moveTo(helper, trader, FEAR_MOB_POS);
            trader.goalSelector.removeAllGoals(goal -> true);
            GameTestAssertions.assertTrue(helper, trader.goalSelector.getAvailableGoals().isEmpty(),
                    "wandering trader must have no vanilla goals before joining the level");
            GameTestAssertions.assertTrue(helper, level.addFreshEntity(trader),
                    "wandering trader must be added through the real server entity lifecycle");
            GameTestAssertions.assertFalse(helper, level.getBlockState(trader.blockPosition().below()).isAir(),
                    "wandering-trader fear fixture must provide a pre-baked floor beneath the entity");
            // EntityType.create has not run a physics tick; initialize the support state proven by the fixture so
            // GroundPathNavigation can execute during the explicitly required synchronous selector tick.
            trader.setOnGround(true);

            Set<WrappedGoal> joinedGoals = trader.goalSelector.getAvailableGoals();
            GameTestAssertions.assertTrue(helper, joinedGoals.size() == 1,
                    "the production join handler must inject exactly one goal after vanilla goals are removed");
            WrappedGoal injectedGoal = joinedGoals.iterator().next();
            GameTestAssertions.assertTrue(helper, injectedGoal.getGoal() instanceof AvoidEntityGoal<?>,
                    "the sole join-injected wandering-trader goal must be an AvoidEntityGoal");
            GameTestAssertions.assertTrue(helper, injectedGoal.getPriority() == VillagerFearRules.AVOID_ZOMBIE_PLAYER_GOAL_PRIORITY,
                    "the join-injected wandering-trader goal must keep the production fear priority");

            double initialPlayerDistance = trader.distanceToSqr(player);
            trader.goalSelector.tick();
            GameTestAssertions.assertFalse(helper, injectedGoal.isRunning(),
                    "disguised zombie player must not start the wandering-trader avoid goal");
            GameTestAssertions.assertTrue(helper, trader.getNavigation().getPath() == null,
                    "disguised zombie player must not produce a wandering-trader escape path");

            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            for (int attempt = 0; attempt < TRADER_START_ATTEMPTS && !injectedGoal.isRunning(); attempt++) {
                trader.goalSelector.tick();
            }

            GameTestAssertions.assertTrue(helper, injectedGoal.isRunning(),
                    "removing the disguise must start the production wandering-trader avoid goal");
            Path path = trader.getNavigation().getPath();
            GameTestAssertions.assertTrue(helper, path != null && !path.isDone() && path.getNodeCount() > 0,
                    "the running wandering-trader avoid goal must install a non-empty path");
            Vec3 pathTarget = Vec3.atBottomCenterOf(path.getTarget());
            GameTestAssertions.assertTrue(helper, pathTarget.distanceToSqr(player.position()) > initialPlayerDistance,
                    "the wandering-trader path target must be farther from the player than its initial position");
        } finally {
            cleanupFear(helper, playerId, trader);
        }
        helper.succeed();
    }

    static void disguisedZombieOpensAndDamagesMask(GameTestHelper helper) {
        ServerPlayer player = null;
        UUID playerId = null;
        Villager villager = null;
        try {
            player = GameTestPlayers.spawnConnectedZombiePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
            playerId = player.getUUID();
            GameTestAssertions.assertFalse(helper, player.hasInfiniteMaterials(),
                    "connected trade player must not retain infinite materials");

            ItemStack mask = new ItemStack(IAmZombieItems.DISGUISE_MASK.get());
            player.setItemSlot(EquipmentSlot.HEAD, mask);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.getInventory().setItem(PAYMENT_SLOT, new ItemStack(Items.EMERALD));

            villager = helper.spawn(EntityTypes.VILLAGER, FIXTURE_POS);
            villager.setNoAi(true);
            MerchantOffer offer = installFixedOffer(villager);
            int emeraldsBefore = inventoryCount(player, Items.EMERALD);
            int breadBefore = inventoryCount(player, Items.BREAD);
            int usesBefore = offer.getUses();
            int maskDamageBefore = mask.getDamageValue();
            GameTestAssertions.assertTrue(helper, emeraldsBefore == 1, "trade fixture should contain exactly one emerald");
            GameTestAssertions.assertTrue(helper, breadBefore == 0, "trade fixture should begin without bread");
            GameTestAssertions.assertTrue(helper, usesBefore == 0, "fixed villager offer should begin unused");

            InteractionResult result = player.interactOn(villager, InteractionHand.MAIN_HAND
                    //? if >=26.1
                    , Vec3.ZERO
            );
            GameTestAssertions.assertTrue(helper, result.consumesAction(), "disguised villager interaction should be accepted");
            GameTestAssertions.assertTrue(helper, player.containerMenu instanceof MerchantMenu,
                    "disguised zombie should open a MerchantMenu");
            GameTestAssertions.assertTrue(helper, villager.getTradingPlayer() == player,
                    "villager trading player should be the connected zombie player");

            MerchantMenu menu = (MerchantMenu) player.containerMenu;
            menu.tryMoveItems(0);
            GameTestAssertions.assertTrue(helper, menu.getSlot(2).getItem().is(Items.BREAD),
                    "fixed emerald payment should populate the real merchant result slot with bread");
            ItemStack movedResult = menu.quickMoveStack(player, 2);
            GameTestAssertions.assertTrue(helper, movedResult.is(Items.BREAD) && movedResult.getCount() == 1,
                    "quick-moving the merchant result should complete one bread trade");

            GameTestAssertions.assertTrue(helper, inventoryCount(player, Items.EMERALD) == emeraldsBefore - 1,
                    "successful trade should consume exactly one emerald");
            GameTestAssertions.assertTrue(helper, inventoryCount(player, Items.BREAD) == breadBefore + 1,
                    "successful trade should place exactly one bread in player inventory");
            GameTestAssertions.assertTrue(helper, offer.getUses() == usesBefore + 1,
                    "successful trade should increase offer uses exactly once");

            ItemStack wornMask = player.getItemBySlot(EquipmentSlot.HEAD);
            GameTestAssertions.assertTrue(helper, wornMask.is(IAmZombieItems.DISGUISE_MASK.get()),
                    "disguise mask should remain equipped after one trade");
            GameTestAssertions.assertTrue(helper, wornMask.getDamageValue() == maskDamageBefore + 1,
                    "successful trade should damage the disguise mask exactly once");
            GameTestAssertions.assertTrue(helper, wornMask.getDamageValue() < wornMask.getMaxDamage(),
                    "disguise mask should not break after one trade");
        } finally {
            cleanup(helper, player, playerId, villager);
        }
        helper.succeed();
    }

    private static MerchantOffer installFixedOffer(Villager villager) {
        MerchantOffer offer = new MerchantOffer(
                new ItemCost(Items.EMERALD, 1),
                new ItemStack(Items.BREAD),
                12,
                0,
                0.0F);
        MerchantOffers offers = new MerchantOffers();
        offers.add(offer);
        villager.setOffers(offers);
        return offer;
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

    private static void assertPlayerVisible(
            GameTestHelper helper,
            Villager villager,
            Brain<Villager> brain,
            ServerPlayer player,
            String phase) {
        GameTestAssertions.assertTrue(helper, brain.getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES)
                        .filter(nearby -> nearby.contains(player))
                        .isPresent(),
                phase + " must include the connected player in nearby-entity memory; villager="
                        + villager.position() + ", player=" + player.position());
        GameTestAssertions.assertTrue(helper, villager.getSensing().hasLineOfSight(player),
                phase + " must have unobstructed line of sight to the connected player; villager="
                        + villager.position() + ", player=" + player.position());
        NearestVisibleLivingEntities visible = brain
                .getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .orElseThrow(() -> helper.assertionException(phase + " has not populated visible-entity memory"));
        GameTestAssertions.assertTrue(helper, visible.contains(player), phase + " must genuinely see the connected player");
    }

    private static void assertUnmaskedVillagerFlees(
            GameTestHelper helper,
            Villager villager,
            ServerPlayer player,
            double initialPlayerDistance) {
        Brain<Villager> brain = villager.getBrain();
        assertPlayerVisible(helper, villager, brain, player, "unmasked villager");
        GameTestAssertions.assertTrue(helper, brain.getMemory(MemoryModuleType.NEAREST_HOSTILE)
                        .filter(hostile -> hostile == player)
                        .isPresent(),
                "unmasked villager must remember exactly the connected player as its nearest hostile");
        GameTestAssertions.assertTrue(helper, brain.isActive(Activity.PANIC),
                "unmasked villager must activate the vanilla PANIC activity");
        WalkTarget walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET)
                .orElseThrow(() -> helper.assertionException(
                        "waiting for the unmasked villager to produce a panic walk target"));
        GameTestAssertions.assertTrue(helper, walkTarget.getTarget().currentPosition().distanceToSqr(player.position())
                        > initialPlayerDistance,
                "the unmasked villager's panic walk target must lead away from the player");
    }

    private static void moveTo(GameTestHelper helper, Entity entity, BlockPos relativePos) {
        Vec3 position = Vec3.atBottomCenterOf(helper.absolutePos(relativePos));
        entity.snapTo(position.x, position.y, position.z, 0.0F, 0.0F);
    }

    private static void cleanupFear(GameTestHelper helper, UUID playerId, Entity mob) {
        try {
            if (mob != null) {
                mob.discard();
            }
        } finally {
            if (playerId != null) {
                GameTestPlayers.disconnectConnectedPlayer(helper, playerId);
            }
        }
    }

    private static void cleanup(
            GameTestHelper helper,
            ServerPlayer player,
            UUID playerId,
            Villager villager) {
        try {
            if (player != null) {
                player.closeContainer();
            }
        } finally {
            try {
                if (villager != null) {
                    villager.discard();
                }
            } finally {
                if (playerId != null) {
                    GameTestPlayers.disconnectConnectedPlayer(helper, playerId);
                }
            }
        }
    }
}
