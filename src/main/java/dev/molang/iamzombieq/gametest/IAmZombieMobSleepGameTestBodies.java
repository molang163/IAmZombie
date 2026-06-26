package dev.molang.iamzombieq.gametest;

import dev.molang.iamzombieq.IAmZombieItems;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * FakePlayer-driven GameTest bodies for the MOB-targeting, SLEEP (bed/coffin), REINF (reinforcement alert) and DOOR
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
 *   <li><b>REINF</b> — the real {@code hurtServer(mobAttack(attacker))} damage pipeline fires
 *       {@code LivingIncomingDamageEvent}; the mod's {@code ZombiePlayerEvents.onIncomingDamage -> reinforceZombiePlayer
 *       -> alertFormMatchedUndead} retargets nearby form-matched undead onto the attacker. We assert the kin retargets.</li>
 *   <li><b>DOOR</b> — {@code CommonHooks.getBreakSpeed(player, doorState, original, absPos)} (the seam vanilla fires
 *       from {@code Player.getDestroySpeed}); the mod's {@code ZombiePlayerEvents.onBreakSpeed} boosts the empty-hand
 *       wooden-door break speed x3. We assert the returned speed.</li>
 * </ul>
 *
 * <p>Batched tests share one level, so live-entity assertions use a tight radius / local positions around this test's
 * own structure (which {@code padding} spaces well apart from its neighbours). All driven values assert the behaviour
 * at the frozen build's config defaults ({@code undeadIgnoreZombiePlayer=true}, {@code reinforcementsEnabled=true}).
 */
final class IAmZombieMobSleepGameTestBodies {

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

        if (clearedTarget(zombie, player)) {
            helper.succeed();
        } else {
            helper.fail("a vanilla zombie's target on the zombie player should be cleared (undead ignore the zombie player)");
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

        if (!clearedTarget(golem, player)) {
            helper.succeed();
        } else {
            helper.fail("the iron golem must keep its target on the zombie player even through the disguise mask");
        }
    }

    /**
     * MOB-005 (form-aware, positive): the axolotl hunts the DROWNED-form zombie player — its target is LEFT INTACT.
     */
    static void mobAxolotlAttacksDrownedForm(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.DROWNED, ZombieSize.ADULT);
        Axolotl axolotl = helper.spawn(EntityTypes.AXOLOTL, new BlockPos(2, 2, 2));

        if (!clearedTarget(axolotl, player)) {
            helper.succeed();
        } else {
            helper.fail("the axolotl must keep its target on a DROWNED-form zombie player");
        }
    }

    /**
     * MOB-006 (form-aware, negative): the axolotl does NOT hunt a non-drowned (NORMAL-form) zombie player — its
     * target is CLEARED.
     */
    static void mobAxolotlIgnoresNormalForm(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        Axolotl axolotl = helper.spawn(EntityTypes.AXOLOTL, new BlockPos(2, 2, 2));

        if (clearedTarget(axolotl, player)) {
            helper.succeed();
        } else {
            helper.fail("the axolotl must NOT target a NORMAL-form zombie player (it hunts only the drowned form)");
        }
    }

    /**
     * MOB-003 (form-aware, positive): the trader llama spits at every form EXCEPT zombified piglin — its target on a
     * NORMAL-form zombie player is LEFT INTACT.
     */
    static void mobTraderLlamaAttacksNormalForm(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        TraderLlama llama = helper.spawn(EntityTypes.TRADER_LLAMA, new BlockPos(2, 2, 2));

        if (!clearedTarget(llama, player)) {
            helper.succeed();
        } else {
            helper.fail("the trader llama must keep its target on a NORMAL-form zombie player");
        }
    }

    /**
     * MOB-004 (form-aware, negative): the trader llama's spit list excludes piglins, so it does NOT target a
     * ZOMBIFIED_PIGLIN-form zombie player — its target is CLEARED.
     */
    static void mobTraderLlamaIgnoresZombifiedPiglinForm(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.ZOMBIFIED_PIGLIN, ZombieSize.ADULT);
        TraderLlama llama = helper.spawn(EntityTypes.TRADER_LLAMA, new BlockPos(2, 2, 2));

        if (clearedTarget(llama, player)) {
            helper.succeed();
        } else {
            helper.fail("the trader llama must NOT target a ZOMBIFIED_PIGLIN-form zombie player (its spit list excludes piglins)");
        }
    }

    /**
     * Drive the real central targeting seam: post {@code onLivingChangeTarget(mob, player, MOB_TARGET)} the way
     * vanilla fires it from {@code Mob.setTarget}, and report whether the mod's deny-list NULLED the target.
     */
    private static boolean clearedTarget(Mob mob, FakePlayer player) {
        LivingChangeTargetEvent event = CommonHooks.onLivingChangeTarget(
                mob, player, LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);
        return event.getNewAboutToBeSetTarget() == null;
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
        // MC 26.2 registers beds as a per-DyeColor ColorCollection (no flat Blocks.RED_BED); any colour is a BedBlock.
        Block bed = Blocks.BED.pick(DyeColor.RED);
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
            helper.fail("a zombie player's bed right-click should explode/destroy both bed halves");
            return;
        }
        helper.succeed();
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
            helper.fail("an empty-handed zombie should break a wooden door 3x faster (got " + boosted + ")");
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
            helper.fail("a zombie holding an item should get NO wooden-door break boost (got " + speed + ")");
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
}
