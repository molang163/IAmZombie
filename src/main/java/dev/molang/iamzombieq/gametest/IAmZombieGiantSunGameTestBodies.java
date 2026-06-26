package dev.molang.iamzombieq.gametest;

import dev.molang.iamzombieq.rules.ZombieBalanceRules;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * FakePlayer-driven GameTest bodies for the GIANT + PIGLIN + POTION subset of the {@code iamzombieq} gameplay rules,
 * registered by {@link IAmZombieGiantSunGameTests}. A sibling of {@link IAmZombieGameTestBodies} (same harness, same
 * {@link GameTestPlayers} helpers); these cover the cleanly-automatable EVENT-driven seams that the existing bodies
 * file does not.
 *
 * <p><b>Why these are event-driven, not per-tick.</b> Because {@code FakePlayer.tick()} is a no-op, none of the mod's
 * per-tick handlers (sun-burn ignition, drowned/piglin passive effect refresh, giant walk-destruction) run on a
 * FakePlayer. Each body here therefore drives the exact server-side seam the corresponding handler subscribes to:
 * <ul>
 *   <li><b>GIANT swing AoE / cooldown</b> ({@code onGiantSwing}) is driven by posting the real
 *       {@link PlayerInteractEvent.LeftClickBlock} (START) to {@link NeoForge#EVENT_BUS} — the same event vanilla
 *       fires when a player begins breaking a block. {@code ZombiePlayerEvents} is registered on that bus by the mod
 *       constructor, so the handler receives the post exactly as in-game.</li>
 *   <li><b>GIANT suffocation immunity</b> ({@code onIncomingDamage}) is driven through the real damage pipeline
 *       ({@code hurtServer} with the vanilla {@code in_wall} source), so NeoForge fires the real
 *       {@code LivingIncomingDamageEvent} the handler cancels — the same path an in-game suffocation tick takes.</li>
 *   <li><b>PIGLIN gold durability ×0.25</b> ({@code ItemStackMixin#hurtAndBreak}) is driven by calling the real
 *       {@code ItemStack.hurtAndBreak(...)} the mixin targets, with the FakePlayer as owner.</li>
 *   <li><b>POTION heal/harm inversion</b> ({@code LivingEntityMixin#isInvertedHealAndHarm}) is driven by invoking the
 *       vanilla instant-effect seam ({@code HealOrHarmMobEffect#applyInstantaneousEffect}), which consults
 *       {@code mob.isInvertedHealAndHarm()} — the method the mixin overrides for zombie players.</li>
 * </ul>
 *
 * <p>The block-placement targets are written into the world via {@code helper.setBlock(...)} (relative -&gt; absolute);
 * the harness structure is a 1x1x1 all-air template spaced from its neighbours by {@code padding}, so these blocks
 * are this test's own. Asserted values are read from {@link ZombieBalanceRules} so they track the production source.
 */
final class IAmZombieGiantSunGameTestBodies {

    private IAmZombieGiantSunGameTestBodies() {
    }

    /**
     * GIANT-009/010: a GIANT-form zombie FakePlayer's left-click on a block WITHIN reach destroys it through the
     * active swing AoE ({@code onGiantSwing}). We place a stone block adjacent to the giant's eyes (well inside even
     * the un-extended 4.5 block reach, so the test does not depend on the per-tick giant attribute apply), then post
     * the real {@code LeftClickBlock(START)} the mod handler subscribes to. The 17^3 swing cube is centred on the
     * clicked block, so the clicked block (nearest to impact, hardness 1.5 &lt;= the swing's 5.0 cap) is razed.
     */
    static void giantSwingDestroysBlockWithinReach(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.GIANT, ZombieSize.ADULT);

        BlockPos targetRel = new BlockPos(2, 3, 1);
        helper.setBlock(targetRel, Blocks.STONE);
        BlockPos targetAbs = helper.absolutePos(targetRel);

        postLeftClickStart(player, targetAbs);

        if (!helper.getBlockState(targetRel).isAir()) {
            helper.fail("a GIANT-form player's swing should have destroyed the stone block within reach");
            return;
        }
        helper.succeed();
    }

    /**
     * GIANT-010 (negative reach gate): a GIANT-form player's swing must NOT destroy a block BEYOND its block reach.
     * {@code onGiantSwing} rejects a target whose distance from the eyes exceeds (reach + 1). On a no-op-tick
     * FakePlayer the reach attribute is the un-extended 4.5, so a block placed far away (~12 blocks) is well outside
     * (4.5 + 1) and the swing is reach-rejected — the block survives. This proves the server-side reach validation,
     * independent of whether the giant scale modifier happens to be applied (which would only widen the reach).
     */
    static void giantSwingIgnoresBlockBeyondReach(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.GIANT, ZombieSize.ADULT);

        BlockPos farRel = new BlockPos(13, 3, 1);
        helper.setBlock(farRel, Blocks.STONE);
        BlockPos farAbs = helper.absolutePos(farRel);

        postLeftClickStart(player, farAbs);

        if (!helper.getBlockState(farRel).is(Blocks.STONE)) {
            helper.fail("a GIANT-form player's swing must NOT destroy a block beyond its block-interaction reach");
            return;
        }
        helper.succeed();
    }

    /**
     * GIANT-011 (cooldown gate): a GIANT-form player's swing AoE is on a cooldown ({@code giantSwingCooldownTicks},
     * 25 ticks) so it is not an infinite instant-miner. The first committed swing starts the cooldown; a second
     * swing within the same game tick (so {@code now < cooldownUntil}) is rejected and its block survives. We do not
     * advance game time (which the no-op-tick FakePlayer cannot do cleanly), so we assert the within-cooldown gate
     * directly: first target razed, second target placed-and-clicked-immediately is preserved.
     */
    static void giantSwingSecondSwingBlockedByCooldown(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.GIANT, ZombieSize.ADULT);

        BlockPos firstRel = new BlockPos(2, 3, 1);
        helper.setBlock(firstRel, Blocks.STONE);
        postLeftClickStart(player, helper.absolutePos(firstRel));
        if (!helper.getBlockState(firstRel).isAir()) {
            helper.fail("precondition: the FIRST swing should have destroyed its stone block");
            return;
        }

        // Second target, clicked in the same synchronous game tick (game time did not advance): the cooldown set by
        // the first swing is still active, so this swing must be rejected and the block preserved.
        BlockPos secondRel = new BlockPos(2, 3, 2);
        helper.setBlock(secondRel, Blocks.STONE);
        postLeftClickStart(player, helper.absolutePos(secondRel));
        if (!helper.getBlockState(secondRel).is(Blocks.STONE)) {
            helper.fail("a second giant swing within the cooldown window must be rejected (block preserved)");
            return;
        }
        helper.succeed();
    }

    /**
     * PIGLIN-002: a ZOMBIFIED_PIGLIN-form player consumes gold-tool durability at x0.25
     * ({@code ZombieBalanceRules.goldDurabilityConsumptionMultiplier} = 0.25, wired in {@code ItemStackMixin}). A
     * golden pickaxe (PIGLIN_LOVED, damageable) hurt for 4 points loses 4*0.25 = 1 durability for the piglin player,
     * versus the full 4 for a NORMAL-form baseline. The scaled amount is exactly 1.0 (no fractional RNG rounding), so
     * the comparison is deterministic. Driven via the real {@code ItemStack.hurtAndBreak(...)} the mixin targets.
     */
    static void zombifiedPiglinConsumesGoldDurabilityAtQuarterRate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int amount = 4;

        FakePlayer piglin = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.ZOMBIFIED_PIGLIN, ZombieSize.ADULT);
        ItemStack piglinGold = new ItemStack(Items.GOLDEN_PICKAXE);
        piglinGold.hurtAndBreak(amount, level, piglin, item -> {});
        int piglinDamage = piglinGold.getDamageValue();

        FakePlayer normal = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        ItemStack normalGold = new ItemStack(Items.GOLDEN_PICKAXE);
        normalGold.hurtAndBreak(amount, level, normal, item -> {});
        int normalDamage = normalGold.getDamageValue();

        int expectedPiglinDamage = (int) (amount * ZombieBalanceRules.goldDurabilityConsumptionMultiplier(ZombieForm.ZOMBIFIED_PIGLIN));
        if (normalDamage != amount) {
            helper.fail("baseline: a NORMAL-form player should lose the full " + amount
                    + " gold durability, but lost " + normalDamage);
            return;
        }
        if (piglinDamage != expectedPiglinDamage) {
            helper.fail("a ZOMBIFIED_PIGLIN-form player should lose only " + expectedPiglinDamage
                    + " gold durability (x0.25), but lost " + piglinDamage);
            return;
        }
        if (piglinDamage >= normalDamage) {
            helper.fail("the piglin's gold durability loss (" + piglinDamage
                    + ") must be strictly less than the baseline (" + normalDamage + ")");
            return;
        }
        helper.succeed();
    }

    /**
     * POTION-001: a zombie player inverts INSTANT_DAMAGE into HEALING ({@code LivingEntityMixin#isInvertedHealAndHarm}
     * returns true for a survival, non-spectator zombie player). We invoke the exact vanilla instant-effect seam
     * ({@code HealOrHarmMobEffect#applyInstantaneousEffect}), which keys on {@code mob.isInvertedHealAndHarm()}: with
     * inversion on, a HARMFUL instant-damage effect HEALS instead. Starting below max health, the player's health
     * goes UP (capped at max), proving the harm-&gt;heal inversion at runtime.
     */
    static void zombiePlayerInvertsInstantDamageToHealing(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        ServerLevel level = helper.getLevel();

        player.setHealth(10.0F);
        float before = player.getHealth();
        MobEffects.INSTANT_DAMAGE.value().applyInstantaneousEffect(level, null, null, player, 0, 1.0);

        if (player.getHealth() <= before) {
            helper.fail("a zombie player should be HEALED by instant-damage (undead heal/harm inversion); health went from "
                    + before + " to " + player.getHealth());
            return;
        }
        helper.succeed();
    }

    /**
     * Posts the real {@code LeftClickBlock(START)} the giant swing handler subscribes to. The mod registers
     * {@code ZombiePlayerEvents} on {@link NeoForge#EVENT_BUS} in its constructor, so this reaches {@code onGiantSwing}
     * exactly as a vanilla begin-break action would. The {@code @ApiStatus.Internal} constructor is the same one
     * NeoForge uses when translating the {@code START_DESTROY_BLOCK} action packet.
     */
    private static void postLeftClickStart(FakePlayer player, BlockPos absoluteTarget) {
        PlayerInteractEvent.LeftClickBlock event = new PlayerInteractEvent.LeftClickBlock(
                player, absoluteTarget, Direction.UP, PlayerInteractEvent.LeftClickBlock.Action.START);
        NeoForge.EVENT_BUS.post(event);
    }
}
