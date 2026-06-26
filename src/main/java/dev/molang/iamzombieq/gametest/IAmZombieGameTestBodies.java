package dev.molang.iamzombieq.gametest;

import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;

import dev.molang.iamzombieq.IAmZombieItems;

/**
 * The FakePlayer-driven bodies for the {@code iamzombieq} GameTests, registered by {@link IAmZombieGameTests}.
 *
 * <p>Because {@code FakePlayer.tick()} is a no-op, these tests drive the mod's gameplay handlers the same way
 * vanilla would: by invoking the exact server-side seam the handlers subscribe to. Eating runs the real
 * {@code startUsingItem} (fires the Start hook) + {@code EventHooks.onItemUseFinish} (fires the Finish event — the
 * same call vanilla's {@code LivingEntity.completeUsingItem} makes). Kills run the real damage pipeline
 * ({@code hurtServer} with a player-attack source) so vanilla fires the real {@code LivingDeathEvent} — the same
 * path an in-game kill takes. Batched tests share one level, so entity assertions use a tight radius around the
 * test's own structure (which {@code padding} spaces well apart from its neighbours).
 */
final class IAmZombieGameTestBodies {

    private IAmZombieGameTestBodies() {
    }

    /** Smoke: a FakePlayer can be spawned and configured as a zombie, and the harness runs end to end. */
    static void smoke(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        if (!player.isAlive()) {
            helper.fail("FakePlayer should be alive after spawn");
            return;
        }
        if (GameTestPlayers.stateOf(player).form() != ZombieForm.NORMAL) {
            helper.fail("FakePlayer zombie form should be NORMAL");
            return;
        }
        helper.succeed();
    }

    /**
     * T-food-hunger: an adult zombie FakePlayer that finishes eating a HUMAN_COOKED food (cooked_beef) receives the
     * human-food-punishment Hunger effect. (Plain rotten flesh is CARRION and does NOT punish — cooked food does.)
     */
    static void foodHumanHunger(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.removeEffect(MobEffects.HUNGER);

        ItemStack food = new ItemStack(Items.COOKED_BEEF);
        feed(player, food);

        if (player.getEffect(MobEffects.HUNGER) == null) {
            helper.fail("Zombie player should have the Hunger debuff after eating cooked_beef (HUMAN_COOKED)");
            return;
        }
        helper.succeed();
    }

    /**
     * T-baby-grow: a BABY zombie FakePlayer that finishes eating super_rotten_flesh grows to size ADULT
     * (the rule's {@code restoresBabyState}).
     */
    static void babyGrow(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.BABY);
        if (GameTestPlayers.stateOf(player).size() != ZombieSize.BABY) {
            helper.fail("precondition: FakePlayer should start as a BABY");
            return;
        }

        ItemStack food = new ItemStack(IAmZombieItems.SUPER_ROTTEN_FLESH.get());
        feed(player, food);

        if (GameTestPlayers.stateOf(player).size() != ZombieSize.ADULT) {
            helper.fail("Baby zombie should have grown to ADULT after eating super_rotten_flesh");
            return;
        }
        helper.succeed();
    }

    /**
     * T-infection-villager: a zombie FakePlayer that kills a Villager turns it into a ZombieVillager. Uses the
     * FORM-AGNOSTIC villager path (a NORMAL zombie player), not the form-gated pig/piglin path. The environment is
     * HARD difficulty, where the infection chance is 1.0, so the conversion is deterministic.
     */
    static void infectionVillager(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        ServerLevel level = helper.getLevel();

        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));

        // Actually KILL the villager through the real damage pipeline (a player-attack source) so VANILLA fires the
        // real LivingDeathEvent the mod's ZombieInfectionEvents handler converts on — the same path an in-game kill
        // takes — rather than synthesizing the event. The converted entity registers on the next tick, so poll a
        // tight radius around the victim's spawn (scoped like the pig tests, since batched tests share one level).
        DamageSource killedByPlayer = level.damageSources().playerAttack(player);
        villager.hurtServer(level, killedByPlayer, Float.MAX_VALUE);

        helper.succeedWhen(() -> {
            if (villager.isAlive() && !villager.isRemoved()) {
                throw helper.assertionException("villager has not been converted yet");
            }
            if (helper.getEntities(EntityTypes.ZOMBIE_VILLAGER, new BlockPos(1, 2, 1), 1.5).isEmpty()) {
                throw helper.assertionException("expected a ZombieVillager after the zombie player killed the villager");
            }
        });
    }

    /**
     * T-infection-pig-form-gate (negative): a NORMAL-form zombie FakePlayer that kills a Pig must NOT produce a
     * ZombifiedPiglin — the pig/piglin path is FORM-GATED to a ZOMBIFIED_PIGLIN-form player (see the call-site form
     * check in {@code ZombieInfectionEvents#onLivingDeath}), unlike the form-agnostic villager path. HARD difficulty
     * (infection chance 1.0) so the ONLY thing that can suppress the conversion is the form gate, not the RNG. We
     * deal lethal damage through the real player-attack pipeline (so vanilla fires the real death event), then after
     * a short delay assert no ZombifiedPiglin exists near the victim.
     */
    static void infectionPigNormalFormBlocked(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        ServerLevel level = helper.getLevel();

        Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(1, 2, 1));
        DamageSource killedByPlayer = level.damageSources().playerAttack(player);
        pig.hurtServer(level, killedByPlayer, Float.MAX_VALUE);

        helper.runAfterDelay(5L, () -> {
            if (hasZombifiedPiglinNear(helper)) {
                helper.fail("a NORMAL-form zombie player must NOT convert a Pig into a ZombifiedPiglin (form-gated)");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * T-infection-pig-form-gate (positive): a ZOMBIFIED_PIGLIN-form zombie FakePlayer that kills a Pig DOES turn it
     * into a ZombifiedPiglin — the form is the "kin" of what it spreads, so the form gate passes. HARD difficulty
     * makes the infection chance 1.0, so the conversion is deterministic. Mirrors the villager-infection poll: the new
     * entity registers with the level on the next tick, so poll a tight radius around the victim until it appears.
     */
    static void infectionPigPiglinFormSpreads(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.ZOMBIFIED_PIGLIN, ZombieSize.ADULT);
        ServerLevel level = helper.getLevel();

        Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(1, 2, 1));
        DamageSource killedByPlayer = level.damageSources().playerAttack(player);
        pig.hurtServer(level, killedByPlayer, Float.MAX_VALUE);

        helper.succeedWhen(() -> {
            if (pig.isAlive() && !pig.isRemoved()) {
                throw helper.assertionException("pig has not been converted yet");
            }
            if (!hasZombifiedPiglinNear(helper)) {
                throw helper.assertionException("expected a ZombifiedPiglin after the zombified-piglin-form player killed the pig");
            }
        });
    }

    // Scope the search to THIS test's local structure (a small radius around the pig's spawn at local (1,2,1))
    // rather than the whole level: the GameTest framework runs all batched tests concurrently in one shared level,
    // so a level-wide scan would see the ZombifiedPiglin the sibling POSITIVE test legitimately produces. The
    // converted piglin replaces the pig in place, so a tight radius reliably finds only this test's own entity.
    private static boolean hasZombifiedPiglinNear(GameTestHelper helper) {
        return !helper.getEntities(EntityTypes.ZOMBIFIED_PIGLIN, new BlockPos(1, 2, 1), 1.5).isEmpty();
    }

    /**
     * T-husk-hunger: a HUSK-form zombie FakePlayer's melee inflicts Hunger on its target (the husk handler in
     * {@code ZombiePlayerEvents#onIncomingDamage}). The victim is an Iron Golem (100 HP) so a single small hit
     * never kills it and the applied effect persists for the assertion. Driven via the real
     * {@code hurtServer(playerAttack)} pipeline so the {@code LivingIncomingDamageEvent} fires as in-game.
     */
    static void huskHunger(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.HUSK, ZombieSize.ADULT);
        ServerLevel level = helper.getLevel();

        net.minecraft.world.entity.LivingEntity target = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(1, 2, 1));
        target.removeEffect(MobEffects.HUNGER);

        // playerAttack(player) has the player as both source and direct entity, which is what the husk handler keys
        // on. A tiny amount keeps the 100 HP golem alive so the freshly-applied Hunger isn't lost to its death.
        DamageSource attack = level.damageSources().playerAttack(player);
        target.hurtServer(level, attack, 1.0F);

        if (target.getEffect(MobEffects.HUNGER) == null) {
            helper.fail("A husk zombie's melee should inflict Hunger on its target");
            return;
        }
        helper.succeed();
    }

    /** Runs the real eat: start the use (fires the Start hook), then finish it (fires the Finish event). */
    private static void feed(FakePlayer player, ItemStack food) {
        player.setItemInHand(InteractionHand.MAIN_HAND, food);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        // Mirror LivingEntity.completeUsingItem's middle step: this posts LivingEntityUseItemEvent.Finish to the
        // game bus, which the mod's ZombieFoodEvents handler consumes.
        EventHooks.onItemUseFinish(player, food.copy(), player.getUseItemRemainingTicks(), food.finishUsingItem(player.level(), player));
        player.stopUsingItem();
    }
}
