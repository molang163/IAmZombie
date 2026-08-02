package dev.molang.iamzombieq.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/**
 * Shared FakePlayer-driven test seams reused across the {@code iamzombieq} GameTest suites (sibling to
 * {@link GameTestPlayers}). Every method here is a byte-for-byte extraction of an identical block that previously
 * lived duplicated inside the per-domain body files — the driving technique is unchanged, so behaviour is identical.
 *
 * <p>Each helper documents WHY it is the real vanilla/NeoForge seam it drives (so the "why" lives in one place).
 */
final class GameTestSeams {

    private GameTestSeams() {
    }

    /**
     * Runs the real eat: start the use (fires the Start hook), then finish it (fires the Finish event). Drives the exact
     * server seam a real eat takes so the mod's {@code ZombieFoodEvents} handler consumes the finish event.
     */
    static void feed(FakePlayer player, ItemStack food) {
        player.setItemInHand(InteractionHand.MAIN_HAND, food);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        // Mirror LivingEntity.completeUsingItem's middle step: this posts LivingEntityUseItemEvent.Finish to the
        // game bus, which the mod's ZombieFoodEvents handler consumes.
        EventHooks.onItemUseFinish(player, food.copy(), player.getUseItemRemainingTicks(), food.finishUsingItem(player.level(), player));
        player.stopUsingItem();
    }

    /**
     * Drive the real central targeting seam: post {@code onLivingChangeTarget(mob, player, MOB_TARGET)} the way vanilla
     * fires it from {@code Mob.setTarget}, and report whether the mod's deny-list NULLED the target (i.e. the mob was
     * denied from targeting the zombie player).
     */
    static boolean targetDenied(Mob mob, FakePlayer player) {
        LivingChangeTargetEvent event = CommonHooks.onLivingChangeTarget(
                mob, player, LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);
        return event.getNewAboutToBeSetTarget() == null;
    }

    /**
     * Deal lethal damage to {@code victim} through the real player-attack pipeline (a {@code playerAttack} source +
     * {@code hurtServer}) so VANILLA fires the real {@code LivingDeathEvent} the mod's handlers convert on — the same
     * path an in-game kill takes — rather than synthesizing the event. The caller passes the in-scope {@link ServerLevel}
     * (never {@code helper.getLevel()} here, to avoid any level != getLevel() surprise).
     */
    static void killByPlayerAttack(ServerLevel level, FakePlayer player, Entity victim) {
        DamageSource killedByPlayer = level.damageSources().playerAttack(player);
        victim.hurtServer(level, killedByPlayer, Float.MAX_VALUE);
    }

    /**
     * Poll (via {@code helper.succeedWhen}) until {@code source} has been removed by conversion AND a {@code convertedType}
     * entity has registered in a tight radius around the victim's spawn. The {@code new BlockPos(1, 2, 1)} local position
     * and the {@code 1.5} radius are the per-test local structure origin (batched tests share one level, so a tight radius
     * excludes sibling tests' converted entities). The messages are only visible on failure and do not affect the pass
     * decision.
     */
    static void awaitConverted(GameTestHelper helper, Entity source, EntityType<?> convertedType,
            String notConvertedMessage, String expectedMessage) {
        helper.succeedWhen(() -> {
            if (source.isAlive() && !source.isRemoved()) {
                throw helper.assertionException(notConvertedMessage);
            }
            if (helper.getEntities(convertedType, new BlockPos(1, 2, 1), 1.5).isEmpty()) {
                throw helper.assertionException(expectedMessage);
            }
        });
    }
}
