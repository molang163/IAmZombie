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
import net.minecraft.world.entity.monster.skeleton.Skeleton;
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

    /**
     * SLEEP/RC3: breaking ONE half of a 2-part coffin drops EXACTLY ONE coffin item (no dupe). The coffin is a
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
                helper.fail("breaking one half of a 2-part coffin must drop exactly 1 coffin (got " + coffins + ")");
                return;
            }
            helper.succeed();
        });
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

    /**
     * MOB-GRUDGE (Fix1, vanilla-faithful self-refresh): a monster the player GENUINELY struck stays ALLOWED to target
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
        // the long window deterministic. setInvulnerable also suppresses vanilla's ~100t lastHurtByMob auto-clear,
        // which is exactly why each engaged step below nulls lastHurtByMob EXPLICITLY instead of relying on that timer.
        // The ONLY seam posts are this test's explicit clearedTarget() calls -> the refresh schedule is fully
        // test-controlled (no AI re-post can perturb the timing).
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(2, 2, 2));
        skeleton.setNoAi(true);
        skeleton.setInvulnerable(true);

        // t0: a genuine hit. Posting now (trueHit==true) SEEDS the player-grudge (expiry t0+200) and is ALLOWED.
        skeleton.setLastHurtByMob(player);
        if (clearedTarget(skeleton, player)) {
            helper.fail("a freshly-struck IGNORED monster (retaliating) must be ALLOWED to target the zombie player at t0");
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
                helper.fail("precondition: the struck skeleton must still be alive at +120t");
                return;
            }
            skeleton.setLastHurtByMob(null);               // trueHit=false: only the self-refreshing grudge remains
            if (clearedTarget(skeleton, player)) {
                helper.fail("while engaged (+120t, lastHurtByMob cleared), the self-refreshing grudge must keep the monster ALLOWED to target the player");
                return;
            }
            helper.runAfterDelay(120L, () -> {             // +240: refresh -> expiry +440
                if (!skeleton.isAlive()) {
                    helper.fail("precondition: the struck skeleton must still be alive at +240t");
                    return;
                }
                skeleton.setLastHurtByMob(null);
                if (clearedTarget(skeleton, player)) {
                    helper.fail("while engaged (+240t), the self-refreshing grudge must keep the monster ALLOWED to target the player");
                    return;
                }
                helper.runAfterDelay(120L, () -> {         // +360: LAST engaged post, refresh -> expiry +560
                    if (!skeleton.isAlive()) {
                        helper.fail("precondition: the struck skeleton must still be alive at +360t");
                        return;
                    }
                    skeleton.setLastHurtByMob(null);
                    if (clearedTarget(skeleton, player)) {
                        helper.fail("while engaged (+360t), the self-refreshing grudge must keep the monster ALLOWED to target the player");
                        return;
                    }
                    // ESCAPE: STOP posting. The last engaged post (+360) set expiry +560, so the grudge is forgiven
                    // 200t after the last engagement. Wait 300t (GRUDGE_TICKS + 100t margin) WITHOUT re-posting, then
                    // post once more (still no fresh hit -> lastHurtByMob null): the grudge has lapsed, so the IGNORED
                    // deny-list must CLEAR the target again.
                    helper.runAfterDelay(300L, () -> {     // +660: expiry +560, 100t past -> lapsed
                        if (!skeleton.isAlive()) {
                            helper.fail("precondition: the struck skeleton must still be alive at +660t");
                            return;
                        }
                        skeleton.setLastHurtByMob(null);
                        if (!clearedTarget(skeleton, player)) {
                            helper.fail("after the mob loses the player (no re-post for GRUDGE_TICKS), the forgiven grudge must let the IGNORED deny-list CLEAR the target again");
                            return;
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }
}
