package dev.molang.iamzombieq.gametest;

import java.util.UUID;

import dev.molang.iamzombieq.IAmZombieBlocks;
import dev.molang.iamzombieq.IAmZombieItems;
import dev.molang.iamzombieq.block.CoffinBlock;
import dev.molang.iamzombieq.gameplay.CoffinNapManager;
import dev.molang.iamzombieq.gameplay.ZombieMobTargetingEvents;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
//? if >=26.1
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if >=26.1
import net.minecraft.world.clock.ClockTimeMarkers;
//? if >=26.1
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.EntityReference;
//? if >=26.2
import net.minecraft.world.entity.EntityTypes;
//? if <26.2
//import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * FakePlayer-driven GameTest bodies for the MOB-targeting, SLEEP (bed/coffin) and DOOR
 * (break-speed) domains of {@code iamzombieq}, registered by {@link IAmZombieMobSleepGameTests}.
 *
 * <p>Each body drives the EXACT server-side NeoForge hook the mod's handler subscribes to, the way vanilla fires it,
 * rather than synthesizing behaviour:
 * <ul>
 *   <li><b>MOB</b> — {@code CommonHooks.onLivingChangeTarget(mob, player, MOB_TARGET)} (the central seam vanilla
 *       fires from {@code Mob.setTarget}); the mod's {@code ZombieMobTargetingEvents.onChangeTarget} consumes it and
 *       NULLs (or leaves) the about-to-be-set target. We assert the returned event's target.</li>
 *   <li><b>SLEEP</b> — {@code CommonHooks.onRightClickBlock(player, MAIN_HAND, absPos, hit)} (the seam vanilla fires
 *       on a block right-click); the mod's {@code ZombieSleepEvents.onRightClickBlock} explodes the bed. We assert the
 *       bed blocks are gone.</li>
 *   <li><b>DOOR</b> — {@code EventHooks.getBreakSpeed(player, doorState, original, absPos)} (the seam vanilla fires
 *       from {@code Player.getDestroySpeed}); the mod's {@code ZombiePlayerEvents.onBreakSpeed} boosts the empty-hand
 *       wooden-door break speed x3. We assert the returned speed.</li>
 * </ul>
 *
 * <p>Batched tests share one level, so live-entity assertions use a tight radius / local positions around this test's
 * own structure (which {@code padding} spaces well apart from its neighbours). All driven values assert the behaviour
 * at the frozen build's config defaults ({@code undeadIgnoreZombiePlayer=true}, {@code reinforcementsEnabled=true}).
 */
final class IAmZombieMobSleepGameTestBodies {
    private static final long COFFIN_TEST_DAY_CLOCK = 1000L;
    //? if <26.1
    //private static final long COFFIN_TEST_NIGHT_CLOCK = 13000L;
    private static final int DEEP_SLEEP_TICKS = 100;

    private IAmZombieMobSleepGameTestBodies() {
    }

    // ---------------------------------------------------------------------------------------------------------
    // MOB: who is allowed to target the zombie player (the LivingChangeTargetEvent deny-list)
    // ---------------------------------------------------------------------------------------------------------

    /**
     * MOB-001: a vanilla Zombie (a fellow undead, classified IGNORED) that is about to target the zombie player has
     * that target CLEARED — undead ignore the zombie player at the config default. Driven through the real
     * {@code onLivingChangeTarget} seam vanilla fires from {@code Mob.setTarget}.
     */
    static void mobUndeadIgnoresZombiePlayer(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 2, 2));

        if (GameTestSeams.targetDenied(zombie, player)) {
            helper.succeed();
        } else {
            GameTestAssertions.fail(helper, "a vanilla zombie's target on the zombie player should be cleared (undead ignore the zombie player)");
        }
    }

    /**
     * MOB-009: the iron golem always attacks the zombie player and the crude disguise mask does NOT fool it — its
     * about-to-be-set target on the (masked) zombie player is LEFT INTACT (not cleared). Mask worn to assert the
     * "mask doesn't fool it" intent (targeting is mask-independent in the frozen build).
     */
    static void mobIronGolemNotFooledByMask(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(IAmZombieItems.DISGUISE_MASK.get()));
        IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(2, 2, 2));

        if (!GameTestSeams.targetDenied(golem, player)) {
            helper.succeed();
        } else {
            GameTestAssertions.fail(helper, "the iron golem must keep its target on the zombie player even through the disguise mask");
        }
    }

    /**
     * MOB-005 (form-aware, positive): the axolotl hunts the DROWNED-form zombie player — its target is LEFT INTACT.
     */
    static void mobAxolotlAttacksDrownedForm(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.DROWNED, ZombieSize.ADULT);
        Axolotl axolotl = helper.spawn(EntityTypes.AXOLOTL, new BlockPos(2, 2, 2));

        if (!GameTestSeams.targetDenied(axolotl, player)) {
            helper.succeed();
        } else {
            GameTestAssertions.fail(helper, "the axolotl must keep its target on a DROWNED-form zombie player");
        }
    }

    /**
     * MOB-006 (form-aware, negative): the axolotl does NOT hunt a non-drowned (NORMAL-form) zombie player — its
     * target is CLEARED.
     */
    static void mobAxolotlIgnoresNormalForm(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        Axolotl axolotl = helper.spawn(EntityTypes.AXOLOTL, new BlockPos(2, 2, 2));

        if (GameTestSeams.targetDenied(axolotl, player)) {
            helper.succeed();
        } else {
            GameTestAssertions.fail(helper, "the axolotl must NOT target a NORMAL-form zombie player (it hunts only the drowned form)");
        }
    }

    /**
     * MOB-003 (form-aware, positive): the trader llama spits at every form EXCEPT zombified piglin — its target on a
     * NORMAL-form zombie player is LEFT INTACT.
     */
    static void mobTraderLlamaAttacksNormalForm(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        TraderLlama llama = helper.spawn(EntityTypes.TRADER_LLAMA, new BlockPos(2, 2, 2));

        if (!GameTestSeams.targetDenied(llama, player)) {
            helper.succeed();
        } else {
            GameTestAssertions.fail(helper, "the trader llama must keep its target on a NORMAL-form zombie player");
        }
    }

    /**
     * MOB-004 (form-aware, negative): the trader llama's spit list excludes piglins, so it does NOT target a
     * ZOMBIFIED_PIGLIN-form zombie player — its target is CLEARED.
     */
    static void mobTraderLlamaIgnoresZombifiedPiglinForm(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.ZOMBIFIED_PIGLIN, ZombieSize.ADULT);
        TraderLlama llama = helper.spawn(EntityTypes.TRADER_LLAMA, new BlockPos(2, 2, 2));

        if (GameTestSeams.targetDenied(llama, player)) {
            helper.succeed();
        } else {
            GameTestAssertions.fail(helper, "the trader llama must NOT target a ZOMBIFIED_PIGLIN-form zombie player (its spit list excludes piglins)");
        }
    }


    // ---------------------------------------------------------------------------------------------------------
    // SLEEP: a zombie player's bed right-click explodes the bed (SLEEP-001)
    // ---------------------------------------------------------------------------------------------------------

    /**
     * SLEEP-001: a zombie FakePlayer right-clicking a vanilla bed explodes/destroys it (a zombie can never sleep in a
     * bed). Places a full two-block red bed, drives the real {@code onRightClickBlock} seam on the FOOT half with an
     * empty main hand (a non-secondary use that the handler treats as "use the bed"), and asserts both bed halves are
     * gone afterwards.
     */
    static void sleepBedExplodesOnRightClick(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);

        // Two-block bed laid foot@(2,2,2) -> head one block NORTH at (2,2,1), with matching FACING/PART.
        BlockPos footRel = new BlockPos(2, 2, 2);
        BlockPos headRel = new BlockPos(2, 2, 1);
        Direction facing = Direction.NORTH;
        // MC 26.2 groups dyed beds in a ColorCollection; older nodes expose one field per colour.
        //? if >=26.2
        Block bed = Blocks.BED.pick(DyeColor.RED);
        //? if <26.2
        //Block bed = Blocks.RED_BED;
        BlockState footState = bed.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);
        BlockState headState = bed.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.HEAD);
        helper.setBlock(footRel, footState);
        helper.setBlock(headRel, headState);

        // The handler reads event.getPos() against player.level() (absolute coords), so pass the absolute foot pos.
        BlockPos footAbs = helper.absolutePos(footRel);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(footAbs), Direction.UP, footAbs, false);
        CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, footAbs, hit);

        if (helper.getBlockState(footRel).getBlock() instanceof BedBlock
                || helper.getBlockState(headRel).getBlock() instanceof BedBlock) {
            GameTestAssertions.fail(helper, "a zombie player's bed right-click should explode/destroy both bed halves");
            return;
        }
        helper.succeed();
    }

    /**
     * Breaking one half of a two-part coffin drops exactly one coffin item. The coffin is a
     * bed-style two-part block; breaking the FOOT cascades the orphaned HEAD to air via updateShape, and BOTH halves
     * run the loot table. The fix gates the loot item entry to part=head, so only the head half yields an item ->
     * exactly one. (Before the fix, both halves dropped -> 2, an infinite dupe.) Break via the level's
     * destroyBlock(pos, dropBlock=true), the same drops+cascade a survival break takes.
     */
    static void coffinBreakDropsExactlyOne(GameTestHelper helper) {
        BlockPos footRel = new BlockPos(2, 2, 2);
        BlockPos headRel = new BlockPos(2, 2, 1);
        Direction facing = Direction.NORTH;
        Block coffin = dev.molang.iamzombieq.IAmZombieBlocks.COFFIN.get();
        helper.setBlock(footRel, coffin.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(dev.molang.iamzombieq.block.CoffinBlock.PART, BedPart.FOOT));
        helper.setBlock(headRel, coffin.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(dev.molang.iamzombieq.block.CoffinBlock.PART, BedPart.HEAD));

        // Break the FOOT half WITH drops; the orphaned HEAD is cascaded to air by updateShape and also runs the loot
        // table. With the part=head loot gate, only the head half yields an item.
        helper.getLevel().destroyBlock(helper.absolutePos(footRel), true);

        helper.runAfterDelay(5L, () -> {
            int coffins = 0;
            for (net.minecraft.world.entity.item.ItemEntity item :
                    helper.getEntities(EntityTypes.ITEM, footRel, 4.0)) {
                if (item.getItem().is(IAmZombieItems.COFFIN.get())) {
                    coffins += item.getItem().getCount();
                }
            }
            if (coffins != 1) {
                GameTestAssertions.fail(helper, "breaking one half of a 2-part coffin must drop exactly 1 coffin (got " + coffins + ")");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Two connected NORMAL zombies enter separate coffins through the real block-use path. Both must accumulate the
     * full vanilla 100-tick deep-sleep timer before the 100% vote advances the default clock to NIGHT and wakes both
     * players, clearing their nap and occupied state together.
     */
    static void coffinSleepVoteAdvancesAndWakesAll(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CoffinSleepWorldState worldState = CoffinSleepWorldState.capture(helper);
        CoffinFixture firstCoffin = CoffinFixture.capture(helper, new BlockPos(2, 2, 3), Direction.NORTH);
        CoffinFixture secondCoffin = CoffinFixture.capture(helper, new BlockPos(5, 2, 3), Direction.NORTH);
        ServerPlayer firstPlayer = null;
        ServerPlayer secondPlayer = null;
        UUID firstPlayerId = null;
        UUID secondPlayerId = null;
        try {
            worldState.prepare(helper);
            firstCoffin.install(helper);
            secondCoffin.install(helper);

            firstPlayer = GameTestPlayers.spawnConnectedZombiePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
            firstPlayerId = firstPlayer.getUUID();
            prepareCoffinPlayer(helper, firstPlayer, firstCoffin.footPos().west());

            secondPlayer = GameTestPlayers.spawnConnectedZombiePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
            secondPlayerId = secondPlayer.getUUID();
            prepareCoffinPlayer(helper, secondPlayer, secondCoffin.footPos().east());
            assertConnectedPopulation(helper, firstPlayer, secondPlayer);

            enterCoffin(helper, firstPlayer, firstCoffin, "first vote sleeper");
            enterCoffin(helper, secondPlayer, secondCoffin, "second vote sleeper");
            assertClockTicks(helper, worldState, COFFIN_TEST_DAY_CLOCK,
                    "coffin interactions must not advance the day clock");

            for (int tick = 0; tick < DEEP_SLEEP_TICKS - 1; tick++) {
                firstPlayer.doTick();
                secondPlayer.doTick();
            }
            assertActiveNap(helper, firstPlayer, firstCoffin, DEEP_SLEEP_TICKS - 1, "first vote sleeper");
            assertActiveNap(helper, secondPlayer, secondCoffin, DEEP_SLEEP_TICKS - 1, "second vote sleeper");
            assertClockTicks(helper, worldState, COFFIN_TEST_DAY_CLOCK,
                    "the vote must not pass during the first 99 ticks");

            firstPlayer.doTick();
            GameTestAssertions.assertTrue(helper, firstPlayer.getSleepTimer() == DEEP_SLEEP_TICKS,
                    "the first vote sleeper should be deep after its 100th tick");
            assertActiveNap(helper, secondPlayer, secondCoffin, DEEP_SLEEP_TICKS - 1, "second vote sleeper");
            GameTestAssertions.assertTrue(helper, firstPlayer.isSleeping() && CoffinNapManager.isNapping(firstPlayerId),
                    "one deep sleeper must not pass a two-player 100% vote");
            assertClockTicks(helper, worldState, COFFIN_TEST_DAY_CLOCK,
                    "one deep sleeper must not advance the clock");

            secondPlayer.doTick();
            assertAwakeAfterNap(helper, firstPlayer, firstCoffin, "first vote sleeper");
            assertAwakeAfterNap(helper, secondPlayer, secondCoffin, "second vote sleeper");
            GameTestAssertions.assertTrue(helper, worldState.isNight(level),
                    "the completed coffin vote should advance the default clock to NIGHT");
            GameTestAssertions.assertTrue(helper, worldState.currentClockTicks(level) != COFFIN_TEST_DAY_CLOCK,
                    "the completed coffin vote should change the day clock");
        } finally {
            cleanupCoffinSleepTest(
                    helper, worldState, firstCoffin, secondCoffin, firstPlayerId, secondPlayerId);
        }
        helper.succeed();
    }

    /**
     * With two eligible connected zombies but only one sleeper, the vote remains short. Elapsed 300 is the inclusive
     * keep-sleeping boundary; elapsed 301 takes the production timeout branch, wakes only the napper, and leaves the
     * day clock unchanged.
     */
    static void coffinSleepTimeoutWakesWithoutSkip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CoffinSleepWorldState worldState = CoffinSleepWorldState.capture(helper);
        CoffinFixture sleeperCoffin = CoffinFixture.capture(helper, new BlockPos(2, 2, 3), Direction.NORTH);
        CoffinFixture holdoutCoffin = CoffinFixture.capture(helper, new BlockPos(5, 2, 3), Direction.NORTH);
        ServerPlayer sleeper = null;
        ServerPlayer holdout = null;
        UUID sleeperId = null;
        UUID holdoutId = null;
        try {
            worldState.prepare(helper);
            sleeperCoffin.install(helper);
            holdoutCoffin.install(helper);

            sleeper = GameTestPlayers.spawnConnectedZombiePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
            sleeperId = sleeper.getUUID();
            prepareCoffinPlayer(helper, sleeper, sleeperCoffin.footPos().west());

            holdout = GameTestPlayers.spawnConnectedZombiePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
            holdoutId = holdout.getUUID();
            prepareCoffinPlayer(helper, holdout, holdoutCoffin.footPos().east());
            assertConnectedPopulation(helper, sleeper, holdout);
            assertHoldoutAwake(helper, holdout, holdoutCoffin, "before the nap");

            long napStart = level.getGameTime();
            enterCoffin(helper, sleeper, sleeperCoffin, "timeout sleeper");
            for (int tick = 0; tick < DEEP_SLEEP_TICKS; tick++) {
                sleeper.doTick();
            }
            assertActiveNap(helper, sleeper, sleeperCoffin, DEEP_SLEEP_TICKS, "timeout sleeper at 100 ticks");
            GameTestAssertions.assertTrue(helper, sleeper.isSleepingLongEnough(),
                    "the timeout sleeper should be deep after 100 explicit ticks");
            assertHoldoutAwake(helper, holdout, holdoutCoffin, "after the sleeper reaches 100 ticks");
            assertClockTicks(helper, worldState, COFFIN_TEST_DAY_CLOCK,
                    "a one-of-two vote must not advance the clock");

            setGameTime(level, napStart + 300L);
            sleeper.doTick();
            assertActiveNap(helper, sleeper, sleeperCoffin, DEEP_SLEEP_TICKS,
                    "timeout sleeper at elapsed 300");
            assertHoldoutAwake(helper, holdout, holdoutCoffin, "at elapsed 300");
            assertClockTicks(helper, worldState, COFFIN_TEST_DAY_CLOCK,
                    "elapsed 300 must not skip to night");

            setGameTime(level, napStart + 301L);
            sleeper.doTick();
            assertAwakeAfterNap(helper, sleeper, sleeperCoffin, "timeout sleeper at elapsed 301");
            assertHoldoutAwake(helper, holdout, holdoutCoffin, "at elapsed 301");
            assertClockTicks(helper, worldState, COFFIN_TEST_DAY_CLOCK,
                    "the elapsed-301 timeout must wake without a time skip");
        } finally {
            cleanupCoffinSleepTest(
                    helper, worldState, sleeperCoffin, holdoutCoffin, sleeperId, holdoutId);
        }
        helper.succeed();
    }

    private static void prepareCoffinPlayer(GameTestHelper helper, ServerPlayer player, BlockPos standingPos) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        Vec3 position = Vec3.atBottomCenterOf(helper.absolutePos(standingPos));
        player.snapTo(position.x, position.y, position.z, 0.0F, 0.0F);

        GameTestAssertions.assertFalse(helper, player.hasInfiniteMaterials(),
                "connected coffin player must not retain infinite materials");
        GameTestAssertions.assertTrue(helper, player.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN),
                "connected coffin player must wear a carved pumpkin for sunlight isolation");
        GameTestAssertions.assertFalse(helper, CoffinNapManager.isNapping(player.getUUID()),
                "fresh connected coffin player must not already have a nap entry");
    }

    private static void assertConnectedPopulation(
            GameTestHelper helper, ServerPlayer firstPlayer, ServerPlayer secondPlayer) {
        ServerLevel level = helper.getLevel();
        GameTestAssertions.assertTrue(helper, level.getServer().getPlayerList().getPlayer(firstPlayer.getUUID()) == firstPlayer,
                "first connected coffin player must be in the PlayerList UUID map");
        GameTestAssertions.assertTrue(helper, level.getServer().getPlayerList().getPlayer(secondPlayer.getUUID()) == secondPlayer,
                "second connected coffin player must be in the PlayerList UUID map");
        GameTestAssertions.assertTrue(helper, level.players().contains(firstPlayer) && level.players().contains(secondPlayer),
                "both connected coffin players must be in ServerLevel.players");
        //? if >=26.2
        GameTestAssertions.assertTrue(helper, level.getServer().getPlayerList().getPlayersByUUID().size() == 2,
        //? if <26.2
        //GameTestAssertions.assertTrue(helper, level.getServer().getPlayerList().getPlayers().size() == 2,
                "coffin test must have exactly two connected players in PlayerList");
        GameTestAssertions.assertTrue(helper, level.players().size() == 2,
                "coffin test must have exactly two connected players in ServerLevel.players");
    }

    private static void enterCoffin(
            GameTestHelper helper, ServerPlayer player, CoffinFixture coffin, String description) {
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(coffin.footAbs()), Direction.UP, coffin.footAbs(), false);
        InteractionResult result = player.gameMode.useItemOn(
                player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);

        GameTestAssertions.assertTrue(helper, result == InteractionResult.SUCCESS_SERVER,
                description + " block interaction should return SUCCESS_SERVER");
        GameTestAssertions.assertTrue(helper, player.isSleeping(), description + " should enter the sleeping state");
        GameTestAssertions.assertTrue(helper, player.getSleepingPos().filter(coffin.headAbs()::equals).isPresent(),
                description + " sleeping position should be the coffin head");
        GameTestAssertions.assertTrue(helper, CoffinNapManager.isNapping(player.getUUID()),
                description + " should have an active CoffinNapManager entry");
        coffin.assertOccupied(helper, description);
    }

    private static void assertActiveNap(
            GameTestHelper helper,
            ServerPlayer player,
            CoffinFixture coffin,
            int expectedSleepTimer,
            String description) {
        GameTestAssertions.assertTrue(helper, player.isSleeping(), description + " should still be sleeping");
        GameTestAssertions.assertTrue(helper, player.getSleepingPos().filter(coffin.headAbs()::equals).isPresent(),
                description + " should retain the coffin-head sleeping position");
        GameTestAssertions.assertTrue(helper, player.getSleepTimer() == expectedSleepTimer,
                description + " should have sleepTimer=" + expectedSleepTimer
                        + " (got " + player.getSleepTimer() + ")");
        GameTestAssertions.assertTrue(helper, CoffinNapManager.isNapping(player.getUUID()),
                description + " should retain its CoffinNapManager entry");
        coffin.assertOccupied(helper, description);
    }

    private static void assertAwakeAfterNap(
            GameTestHelper helper, ServerPlayer player, CoffinFixture coffin, String description) {
        GameTestAssertions.assertFalse(helper, player.isSleeping(), description + " should be awake");
        GameTestAssertions.assertTrue(helper, player.getSleepingPos().isEmpty(),
                description + " should have no sleeping position after waking");
        GameTestAssertions.assertFalse(helper, CoffinNapManager.isNapping(player.getUUID()),
                description + " should have no CoffinNapManager entry after waking");
        coffin.assertUnoccupied(helper, description);
    }

    private static void assertHoldoutAwake(
            GameTestHelper helper, ServerPlayer holdout, CoffinFixture coffin, String checkpoint) {
        GameTestAssertions.assertFalse(helper, holdout.isSleeping(), "holdout must remain awake " + checkpoint);
        GameTestAssertions.assertTrue(helper, holdout.getSleepingPos().isEmpty(),
                "holdout must have no sleeping position " + checkpoint);
        GameTestAssertions.assertFalse(helper, CoffinNapManager.isNapping(holdout.getUUID()),
                "holdout must have no nap entry " + checkpoint);
        coffin.assertUnoccupied(helper, "holdout " + checkpoint);
    }

    private static void assertClockTicks(
            GameTestHelper helper, CoffinSleepWorldState worldState, long expectedTicks, String message) {
        long actualTicks = worldState.currentClockTicks(helper.getLevel());
        GameTestAssertions.assertTrue(helper, actualTicks == expectedTicks,
                message + " (expected " + expectedTicks + ", got " + actualTicks + ")");
    }

    private static void cleanupCoffinSleepTest(
            GameTestHelper helper,
            CoffinSleepWorldState worldState,
            CoffinFixture firstCoffin,
            CoffinFixture secondCoffin,
            UUID firstPlayerId,
            UUID secondPlayerId) {
        ServerLevel level = helper.getLevel();
        try {
            try {
                cleanupConnectedSleeper(helper, firstPlayerId);
            } finally {
                cleanupConnectedSleeper(helper, secondPlayerId);
            }
        } finally {
            try {
                if (firstPlayerId != null) {
                    GameTestAssertions.assertFalse(helper, CoffinNapManager.isNapping(firstPlayerId),
                            "first coffin player's nap entry remained after disconnect");
                }
                if (secondPlayerId != null) {
                    GameTestAssertions.assertFalse(helper, CoffinNapManager.isNapping(secondPlayerId),
                            "second coffin player's nap entry remained after disconnect");
                }
                firstCoffin.assertUnoccupiedIfPresent(helper, "first cleanup coffin");
                secondCoffin.assertUnoccupiedIfPresent(helper, "second cleanup coffin");
                //? if >=26.2
                GameTestAssertions.assertTrue(helper, level.getServer().getPlayerList().getPlayersByUUID().isEmpty(),
                //? if <26.2
                //GameTestAssertions.assertTrue(helper, level.getServer().getPlayerList().getPlayers().isEmpty(),
                        "PlayerList must be empty after coffin test cleanup");
                GameTestAssertions.assertTrue(helper, level.players().isEmpty(),
                        "ServerLevel.players must be empty after coffin test cleanup");
            } finally {
                try {
                    try {
                        firstCoffin.restore(level);
                    } finally {
                        secondCoffin.restore(level);
                    }
                } finally {
                    try {
                        worldState.restore(level);
                    } finally {
                        firstCoffin.assertRestored(helper);
                        secondCoffin.assertRestored(helper);
                        worldState.assertRestored(helper);
                    }
                }
            }
        }
    }

    private static void cleanupConnectedSleeper(GameTestHelper helper, UUID playerId) {
        if (playerId == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer current = level.getServer().getPlayerList().getPlayer(playerId);
        if (current == null) {
            current = level.players().stream()
                    .filter(player -> playerId.equals(player.getUUID()))
                    .findFirst()
                    .orElse(null);
        }
        try {
            if (current != null && current.isSleeping()) {
                current.stopSleeping();
            }
        } finally {
            GameTestPlayers.disconnectConnectedPlayer(helper, playerId);
        }
    }

    private static void setGameTime(ServerLevel level, long gameTime) {
        level.getServer().getWorldData().overworldData().setGameTime(gameTime);
    }

    private record CoffinSleepWorldState(
            //? if >=26.1
            Holder<WorldClock> clock,
            long clockTicks,
            long gameTime,
            int sleepingPercentage,
            boolean advanceTime) {

        static CoffinSleepWorldState capture(GameTestHelper helper) {
            ServerLevel level = helper.getLevel();
            GameTestAssertions.assertTrue(helper, level == level.getServer().overworld(),
                    "coffin lifecycle GameTests require the server Overworld");
            //? if >=26.1 {
            Holder<WorldClock> clock = level.dimensionType().defaultClock()
                    .orElseThrow(() -> new IllegalStateException(
                            "coffin lifecycle GameTests require a dimension default clock"));
            long clockTicks = level.clockManager().getTotalTicks(clock);
            //?} else {
            /*GameTestAssertions.assertFalse(helper, level.dimensionType().hasFixedTime(),
                    "coffin lifecycle GameTests require a dimension day-night cycle");
            long clockTicks = level.getDayTime();
            *///?}
            return new CoffinSleepWorldState(
                    //? if >=26.1
                    clock,
                    clockTicks,
                    level.getGameTime(),
                    level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE),
                    level.getGameRules().get(GameRules.ADVANCE_TIME));
        }

        void prepare(GameTestHelper helper) {
            ServerLevel level = helper.getLevel();
            level.getGameRules().set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 100, level.getServer());
            level.getGameRules().set(GameRules.ADVANCE_TIME, true, level.getServer());
            setClockTicks(level, COFFIN_TEST_DAY_CLOCK);

            GameTestAssertions.assertTrue(helper, level.getGameTime() == gameTime,
                    "coffin lifecycle setup must not change gameTime");
            assertClockTicks(helper, this, COFFIN_TEST_DAY_CLOCK,
                    "coffin lifecycle test clock should begin at daytime tick 1000");
            GameTestAssertions.assertTrue(helper, level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE) == 100,
                    "coffin lifecycle test requires players_sleeping_percentage=100");
            GameTestAssertions.assertTrue(helper, level.getGameRules().get(GameRules.ADVANCE_TIME),
                    "coffin lifecycle test requires advance_time=true");
        }

        void restore(ServerLevel level) {
            try {
                setGameTime(level, gameTime);
            } finally {
                try {
                    setClockTicks(level, clockTicks);
                } finally {
                    try {
                        level.getGameRules().set(
                                GameRules.PLAYERS_SLEEPING_PERCENTAGE, sleepingPercentage, level.getServer());
                    } finally {
                        level.getGameRules().set(GameRules.ADVANCE_TIME, advanceTime, level.getServer());
                    }
                }
            }
        }

        void assertRestored(GameTestHelper helper) {
            ServerLevel level = helper.getLevel();
            GameTestAssertions.assertTrue(helper, level.getGameTime() == gameTime,
                    "coffin cleanup must restore the original gameTime");
            assertClockTicks(helper, this, clockTicks,
                    "coffin cleanup must restore the original default-clock ticks");
            GameTestAssertions.assertTrue(helper,
                    level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE) == sleepingPercentage,
                    "coffin cleanup must restore players_sleeping_percentage");
            GameTestAssertions.assertTrue(helper, level.getGameRules().get(GameRules.ADVANCE_TIME) == advanceTime,
                    "coffin cleanup must restore advance_time");
        }

        long currentClockTicks(ServerLevel level) {
            //? if >=26.1
            return level.clockManager().getTotalTicks(clock);
            //? if <26.1
            //return level.getDayTime();
        }

        void setClockTicks(ServerLevel level, long ticks) {
            //? if >=26.1
            level.clockManager().setTotalTicks(clock, ticks);
            //? if <26.1
            //level.setDayTime(ticks);
        }

        boolean isNight(ServerLevel level) {
            //? if >=26.1
            return level.clockManager().isAtTimeMarker(clock, ClockTimeMarkers.NIGHT);
            //? if <26.1
            //return Math.floorMod(level.getDayTime(), 24000L) == COFFIN_TEST_NIGHT_CLOCK;
        }
    }

    private record CoffinFixture(
            BlockPos footPos,
            BlockPos headPos,
            BlockPos footAbs,
            BlockPos headAbs,
            Direction facing,
            BlockState originalFoot,
            BlockState originalHead) {

        static CoffinFixture capture(GameTestHelper helper, BlockPos footPos, Direction facing) {
            BlockPos headPos = footPos.relative(facing);
            BlockPos footAbs = helper.absolutePos(footPos);
            BlockPos headAbs = helper.absolutePos(headPos);
            return new CoffinFixture(
                    footPos,
                    headPos,
                    footAbs,
                    headAbs,
                    facing,
                    helper.getLevel().getBlockState(footAbs),
                    helper.getLevel().getBlockState(headAbs));
        }

        void install(GameTestHelper helper) {
            Block coffin = IAmZombieBlocks.COFFIN.get();
            helper.setBlock(footPos, coffin.defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, facing)
                    .setValue(CoffinBlock.PART, BedPart.FOOT)
                    .setValue(CoffinBlock.OCCUPIED, false));
            helper.setBlock(headPos, coffin.defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, facing)
                    .setValue(CoffinBlock.PART, BedPart.HEAD)
                    .setValue(CoffinBlock.OCCUPIED, false));
            GameTestAssertions.assertTrue(helper, helper.getBlockState(footPos).is(coffin),
                    "coffin fixture foot should be installed");
            GameTestAssertions.assertTrue(helper, helper.getBlockState(headPos).is(coffin),
                    "coffin fixture head should be installed");
            assertUnoccupied(helper, "fresh coffin fixture");
        }

        void assertOccupied(GameTestHelper helper, String description) {
            BlockState state = helper.getLevel().getBlockState(headAbs);
            GameTestAssertions.assertTrue(helper, state.is(IAmZombieBlocks.COFFIN.get())
                            && state.getValue(CoffinBlock.OCCUPIED),
                    description + " coffin head should be occupied");
        }

        void assertUnoccupied(GameTestHelper helper, String description) {
            BlockState state = helper.getLevel().getBlockState(headAbs);
            GameTestAssertions.assertTrue(helper, state.is(IAmZombieBlocks.COFFIN.get())
                            && !state.getValue(CoffinBlock.OCCUPIED),
                    description + " coffin head should be unoccupied");
        }

        void assertUnoccupiedIfPresent(GameTestHelper helper, String description) {
            BlockState state = helper.getLevel().getBlockState(headAbs);
            if (state.is(IAmZombieBlocks.COFFIN.get())) {
                GameTestAssertions.assertFalse(helper, state.getValue(CoffinBlock.OCCUPIED),
                        description + " head remained occupied during cleanup");
            }
        }

        void restore(ServerLevel level) {
            try {
                level.setBlock(footAbs, originalFoot, 3);
            } finally {
                level.setBlock(headAbs, originalHead, 3);
            }
        }

        void assertRestored(GameTestHelper helper) {
            GameTestAssertions.assertTrue(helper, helper.getLevel().getBlockState(footAbs).equals(originalFoot),
                    "coffin cleanup must restore the original foot block state");
            GameTestAssertions.assertTrue(helper, helper.getLevel().getBlockState(headAbs).equals(originalHead),
                    "coffin cleanup must restore the original head block state");
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // DOOR: empty-hand wooden-door break-speed x3 (DOOR-001) / item-in-hand no boost (DOOR-002)
    // ---------------------------------------------------------------------------------------------------------

    /**
     * DOOR-001: a bare-handed zombie player claws through a wooden door 3x faster — the mod's break-speed handler
     * multiplies the speed by {@code WOODEN_DOOR_BREAK_MULTIPLIER} (3.0). Driven through the real
     * {@code getBreakSpeed} seam vanilla fires from {@code Player.getDestroySpeed}.
     */
    static void doorEmptyHandBoostsWoodenDoorBreak(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

        float original = 1.0F;
        float boosted = breakSpeed(helper, player, Blocks.OAK_DOOR.defaultBlockState(), original);
        // Empty hand + wooden door => x3 (allow a tiny float epsilon).
        if (Math.abs(boosted - original * 3.0F) > 1.0e-4F) {
            GameTestAssertions.fail(helper, "an empty-handed zombie should break a wooden door 3x faster (got " + boosted + ")");
            return;
        }
        helper.succeed();
    }

    /**
     * DOOR-002: with an item in the main hand the wooden-door boost does NOT apply — the break speed is unchanged.
     */
    static void doorItemInHandNoBoost(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.STICK));

        float original = 1.0F;
        float speed = breakSpeed(helper, player, Blocks.OAK_DOOR.defaultBlockState(), original);
        if (Math.abs(speed - original) > 1.0e-4F) {
            GameTestAssertions.fail(helper, "a zombie holding an item should get NO wooden-door break boost (got " + speed + ")");
            return;
        }
        helper.succeed();
    }

    /**
     * Drive the real break-speed seam vanilla fires from {@code Player.getDestroySpeed}: posts {@code PlayerEvent.BreakSpeed}
     * for {@code state} at a local block position and returns the resulting (possibly boosted) speed.
     */
    private static float breakSpeed(GameTestHelper helper, FakePlayer player, BlockState state, float original) {
        BlockPos doorRel = new BlockPos(2, 2, 2);
        helper.setBlock(doorRel, state);
        BlockPos doorAbs = helper.absolutePos(doorRel);
        PlayerEvent.BreakSpeed event = new PlayerEvent.BreakSpeed(player, state, original, doorAbs);
        NeoForge.EVENT_BUS.post(event);
        return event.getNewSpeed();
    }

    /**
     * A monster the player genuinely struck stays allowed to target
     * the zombie player for as long as it remains ENGAGED -- the grudge SELF-REFRESHES on every onChangeTarget
     * re-assert while it is still live (record gate {@code trueHit || grudged}), so it persists past vanilla's ~100t
     * lastHurtByMob clear INDEFINITELY while the mob keeps the target. Once the mob LOSES the player (the seam stops
     * being re-posted, mimicking the player escaping past vanilla's TargetGoal), the grudge is forgiven GRUDGE_TICKS
     * =200 after the LAST engagement (forgive-after-escape) and the IGNORED deny-list clears its target again. A
     * Skeleton is an IGNORED-kind monster (classify() has no special case), so absent grudge/retaliation the deny-list
     * always clears its target on the zombie player. Driven through the SAME real onLivingChangeTarget seam as
     * clearedTarget; the mob ticks, so vanilla auto-expires lastHurtByMob at ~+100t -- which is exactly why the
     * +120/+240/+360 re-posts (all past that window, trueHit=false) prove the SELF-REFRESH, not lastHurtByMob, is what
     * keeps the mob engaged.
     */
    static void mobGrudgeStickyRetaliation(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        // NoAi + invulnerable skeleton on a stone floor: this test drives the deny-list seam directly (clearedTarget
        // posts the real onLivingChangeTarget), NOT the skeleton's own AI, so NoAi (no wandering) + a stone floor (no
        // void-fall) + setInvulnerable (no daylight burn -- over the ~660t/33s window an un-armored skeleton would
        // otherwise burn to death; this mob only CARRIES the target flag, no logic depends on it taking damage) make
        // the long window deterministic. Vanilla's ~100t lastHurtByMob auto-clear does NOT fire for this fixture -- it
        // lives in the mob's AI step, which does not run for a NoAi mob (and, as the piglin-swarm work found, does not
        // reliably run for gametest mobs at all) -- which is exactly why each engaged step below nulls lastHurtByMob
        // EXPLICITLY instead of relying on that timer. The ONLY seam posts are this test's explicit clearedTarget()
        // calls -> the refresh schedule is fully test-controlled (no AI re-post can perturb the timing).
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(2, 2, 2));
        skeleton.setNoAi(true);
        skeleton.setInvulnerable(true);

        // t0: a genuine hit. Posting now (trueHit==true) SEEDS the player-grudge (expiry t0+200) and is ALLOWED.
        skeleton.setLastHurtByMob(player);
        if (GameTestSeams.targetDenied(skeleton, player)) {
            GameTestAssertions.fail(helper, "a freshly-struck IGNORED monster (retaliating) must be ALLOWED to target the zombie player at t0");
            return;
        }

        // ENGAGED: re-post every 120t (< GRUDGE_TICKS=200, an 80t margin per gap) while still "fighting". At each
        // re-post we EXPLICITLY null lastHurtByMob first, so trueHit is deterministically false and ONLY the live
        // grudge can keep the target allowed -- a stronger, timer-independent proof of self-refresh than relying on
        // vanilla's ~100t lastHurtByMob auto-clear (which setInvulnerable suppresses). Each live re-post SELF-REFRESHES
        // the grudge to now+200, so it stays allowed indefinitely while engaged. Nested runAfterDelay is RELATIVE-to-
        // now, so 120 + 120 + 120 + 300 = +120/+240/+360/+660.
        helper.runAfterDelay(120L, () -> {                 // +120: refresh -> expiry +320
            if (!skeleton.isAlive()) {
                GameTestAssertions.fail(helper, "precondition: the struck skeleton must still be alive at +120t");
                return;
            }
            skeleton.setLastHurtByMob(null);               // trueHit=false: only the self-refreshing grudge remains
            if (GameTestSeams.targetDenied(skeleton, player)) {
                GameTestAssertions.fail(helper, "while engaged (+120t, lastHurtByMob cleared), the self-refreshing grudge must keep the monster ALLOWED to target the player");
                return;
            }
            helper.runAfterDelay(120L, () -> {             // +240: refresh -> expiry +440
                if (!skeleton.isAlive()) {
                    GameTestAssertions.fail(helper, "precondition: the struck skeleton must still be alive at +240t");
                    return;
                }
                skeleton.setLastHurtByMob(null);
                if (GameTestSeams.targetDenied(skeleton, player)) {
                    GameTestAssertions.fail(helper, "while engaged (+240t), the self-refreshing grudge must keep the monster ALLOWED to target the player");
                    return;
                }
                helper.runAfterDelay(120L, () -> {         // +360: LAST engaged post, refresh -> expiry +560
                    if (!skeleton.isAlive()) {
                        GameTestAssertions.fail(helper, "precondition: the struck skeleton must still be alive at +360t");
                        return;
                    }
                    skeleton.setLastHurtByMob(null);
                    if (GameTestSeams.targetDenied(skeleton, player)) {
                        GameTestAssertions.fail(helper, "while engaged (+360t), the self-refreshing grudge must keep the monster ALLOWED to target the player");
                        return;
                    }
                    // ESCAPE: STOP posting. The last engaged post (+360) set expiry +560, so the grudge is forgiven
                    // 200t after the last engagement. Wait 300t (GRUDGE_TICKS + 100t margin) WITHOUT re-posting, then
                    // post once more (still no fresh hit -> lastHurtByMob null): the grudge has lapsed, so the IGNORED
                    // deny-list must CLEAR the target again.
                    helper.runAfterDelay(300L, () -> {     // +660: expiry +560, 100t past -> lapsed
                        if (!skeleton.isAlive()) {
                            GameTestAssertions.fail(helper, "precondition: the struck skeleton must still be alive at +660t");
                            return;
                        }
                        skeleton.setLastHurtByMob(null);
                        if (!GameTestSeams.targetDenied(skeleton, player)) {
                            GameTestAssertions.fail(helper, "after the mob loses the player (no re-post for GRUDGE_TICKS), the forgiven grudge must let the IGNORED deny-list CLEAR the target again");
                            return;
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }

    /**
     * A zombified piglin group-angered at the zombie player is allowed to
     * target it -- the mechanism that restores the vanilla pack swarm. Driven seam-style (NOT via mob AI): this headless
     * harness does not run mob target-goals against a FakePlayer (a hit piglin never actually retaliates here), so the
     * end-to-end "piglin A retaliates -> alerts B" chain cannot be gametested; instead this proves the exact logic the
     * HurtByTargetGoalAlertMixin depends on. (1) An IDLE piglin's target to the player is DENIED -- the strand the bug
     * leaves the alerted pack in; the guard wouldDenyZombiePlayerTarget agrees it would be stranded, so the
     * mixin WOULD pre-anger it). (2) After establishing persistent anger EXACTLY as the mixin does on a group alert,
     * the SAME seam is ALLOWED (angeredNeutral) -- the piglin swarms instead of being nulled. (3) An iron golem (a
     * table attacker, never stranded) is skipped by the guard -- the fix pre-angers only the kin that need it. The
     * mixin APPLYING is separately confirmed by the gametest server booting under defaultRequire:1 (a wrong @Inject
     * target would crash startup); the in-world swarm is verified by review + manual play.
     */
    static void mobPiglinAngeredKinAllowed(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.ZOMBIFIED_PIGLIN, ZombieSize.ADULT);
        ServerLevel level = helper.getLevel();

        ZombifiedPiglin kin = helper.spawn(EntityTypes.ZOMBIFIED_PIGLIN, new BlockPos(2, 2, 2));

        // (1) Baseline: an idle, un-hit, un-angered IGNORED piglin targeting the zombie player is DENIED -- exactly the
        // stranded state the group alert leaves the pack in. The guard must flag it as one to rescue.
        if (!GameTestSeams.targetDenied(kin, player)) {
            GameTestAssertions.fail(helper, "baseline: an idle IGNORED zombified piglin must be DENIED targeting the zombie player");
            return;
        }
        if (!ZombieMobTargetingEvents.wouldDenyZombiePlayerTarget(kin, player)) {
            GameTestAssertions.fail(helper, "Fix A guard: an idle zombified piglin must be flagged as a kin the deny-list would strand");
            return;
        }

        // (2) Do EXACTLY what HurtByTargetGoalAlertMixin does on a group alert: establish persistent anger at the player.
        // CROSS_VERSION-PERSISTENT-ANGER-TARGET-API
        //? if >=1.21.11 {
        kin.setPersistentAngerTarget(EntityReference.of(player));
        //?} else {
        /*kin.setPersistentAngerTarget(player.getUUID());
        *///?}
        kin.startPersistentAngerTimer();

        // ... now the SAME seam must be ALLOWED: the group-angered piglin swarms instead of being nulled.
        if (GameTestSeams.targetDenied(kin, player)) {
            GameTestAssertions.fail(helper, "after group anger (Fix A), the zombified piglin must be ALLOWED to target the zombie player (swarm)");
            return;
        }

        // (3) Guard precision: an iron golem attacks every form, so the deny-list never strands it -> the mixin must
        // NOT needlessly pre-anger it.
        IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(1, 2, 3));
        if (ZombieMobTargetingEvents.wouldDenyZombiePlayerTarget(golem, player)) {
            GameTestAssertions.fail(helper, "Fix A guard precision: an iron golem (table attacker) must NOT be flagged for pre-anger");
            return;
        }
        helper.succeed();
    }
}
