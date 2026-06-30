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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

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

    /**
     * T-infection-villager-no-kin-aggro (RC4): a freshly-infected ZombieVillager must NOT target the kin zombie
     * player that infected it. The old bug seeded the player as the converted mob's lastHurtByMob, so its
     * HurtByTargetGoal turned the new zombie on its own creator. After the conversion registers and the target goals
     * get several ticks, the new ZombieVillager's target must not be the player. (Genuine retaliation is unaffected:
     * the RC4 fix only deletes the spawn-time attacker seed and touches no retaliation code.) HARD env -> infection
     * chance 1.0 (deterministic conversion).
     */
    static void infectionVillagerNoKinAggro(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        ServerLevel level = helper.getLevel();

        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        villager.hurtServer(level, level.damageSources().playerAttack(player), Float.MAX_VALUE);

        // Give the conversion AND the target goals (HurtByTargetGoal / NearestAttackableTargetGoal) a few ticks to
        // run — a leaked attacker-seed would have made the new zombie pick the player as its target by now — but
        // keep the delay short and the radius generous so the AI-driven ZombieVillager hasn't wandered out of range
        // (radius 4 stays well inside the 8-block test padding, so it never sees a sibling test's zombie villager).
        helper.runAfterDelay(8L, () -> {
            net.minecraft.world.entity.monster.zombie.ZombieVillager zombie =
                    helper.getEntities(EntityTypes.ZOMBIE_VILLAGER, new BlockPos(1, 2, 1), 4.0)
                            .stream().findFirst().orElse(null);
            if (zombie == null) {
                helper.fail("expected a ZombieVillager after the zombie player killed the villager");
                return;
            }
            if (zombie.getTarget() == player) {
                helper.fail("a freshly-infected ZombieVillager must NOT target the kin zombie player that infected it");
                return;
            }
            // (No positive-control re-strike here: the RC4 fix only DELETES the spawn-time attacker seed and touches
            // no retaliation code, so it cannot disable genuine retaliation by construction; and a re-strike this
            // soon after conversion is absorbed by the mob's hurt-immunity frames, making such a check flaky.)
            helper.succeed();
        });
    }

    /**
     * T-infection-villager-sweep-grace (RC4-sweep / Option B): the conversion swing's Sweeping-Edge AoE clips the
     * freshly-converted ZombieVillager in the SAME Player.attack call, seeding it with the player as its last
     * attacker -> HurtByTargetGoal -> the deny-list's retaliating branch. The conversion grace window must
     * NEUTRALISE that same-swing sweep so the kin does NOT hunt its converting player -- asserted AFTER the window
     * expires, because a window-only suppression that did not CLEAR the artifact would let the kin's unconditional
     * NearestAttackableTargetGoal re-acquire the player here. A DELIBERATE strike after the window must still make it
     * retaliate. The kill drives the real LivingDeathEvent (records the grace marker); the sweep's targeting effect
     * is reproduced by directly seeding the kin's last-attacker (a confound-free equivalent of the sweep's
     * hurtServer -- targeting depends only on lastHurtByMob, not the damage, so this dodges the converted mob's
     * hurt-immunity frames that make a re-strike flaky). HARD env -> infection chance 1.0 (deterministic conversion).
     */
    static void infectionVillagerSweepGrace(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        ServerLevel level = helper.getLevel();

        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        villager.hurtServer(level, level.damageSources().playerAttack(player), Float.MAX_VALUE);

        // +2: the ZombieVillager has registered; reproduce the conversion-swing sweep clip on the kin.
        helper.runAfterDelay(2L, () -> {
            net.minecraft.world.entity.monster.zombie.ZombieVillager kin =
                    helper.getEntities(EntityTypes.ZOMBIE_VILLAGER, new BlockPos(1, 2, 1), 4.0)
                            .stream().findFirst().orElse(null);
            if (kin == null) {
                helper.fail("expected a ZombieVillager after the zombie player killed the villager");
                return;
            }
            kin.setLastHurtByMob(player);

            // +30 (well past the 10-tick grace window AND the sweep-sim hurt-immunity frames): the sweep-seeded
            // retaliation must be CLEARED, not merely delayed -- the kin must not be hunting its converter.
            helper.runAfterDelay(28L, () -> {
                if (kin.getTarget() == player) {
                    helper.fail("after the conversion grace window, the kin must NOT target its converting player from the same-swing sweep");
                    return;
                }
                // Fix1/RC4 boundary: the grace-suppressed conversion sweep must NOT have created a player-grudge, or
                // the kin would be allowed to hunt its converter for GRUDGE_TICKS. Re-posting the real seam here (no
                // fresh hit; lastHurtByMob was nulled by the grace branch) must return DENIED -> no grudge exists.
                if (!deniedTarget(kin, player)) {
                    helper.fail("a grace-suppressed conversion sweep must not create a player-grudge: the kin must stay DENIED past the grace window");
                    return;
                }
                // Positive control: a DELIBERATE strike after the window must make genuine retaliation work.
                kin.setLastHurtByMob(player);
                helper.runAfterDelay(3L, () -> {
                    if (kin.getTarget() != player) {
                        helper.fail("after the grace window, a deliberate strike must make the kin retaliate against the player");
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    /**
     * T-infection-piglin-sweep-grace (RC4-sweep / Option B, NeutralMob path): same as the villager case but the kin
     * is a ZombifiedPiglin. The grace branch must clear the sweep-derived persistent ANGER as well as the target, so
     * an anger-driven scan cannot re-acquire the converting player after the window. A deliberate strike after the
     * window must still make it retaliate. Form-gated path -> ZOMBIFIED_PIGLIN-form player + Pig victim; HARD env.
     */
    static void infectionPiglinSweepGrace(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.ZOMBIFIED_PIGLIN, ZombieSize.ADULT);
        ServerLevel level = helper.getLevel();

        Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(1, 2, 1));
        pig.hurtServer(level, level.damageSources().playerAttack(player), Float.MAX_VALUE);

        helper.runAfterDelay(2L, () -> {
            net.minecraft.world.entity.monster.zombie.ZombifiedPiglin kin =
                    helper.getEntities(EntityTypes.ZOMBIFIED_PIGLIN, new BlockPos(1, 2, 1), 4.0)
                            .stream().findFirst().orElse(null);
            if (kin == null) {
                helper.fail("expected a ZombifiedPiglin after the zombified-piglin-form player killed the pig");
                return;
            }
            // Reproduce the sweep's FULL targeting signal on this NeutralMob kin: the last-attacker AND the
            // target-derived persistent anger a real sweep induces (the converting hit makes the piglin angry at its
            // converter, and its NearestAttackableTargetGoal<Player> is anger-gated). The grace branch must clear
            // BOTH -- else an anger-driven scan re-acquires the player after the window. Seeding the anger here is
            // what makes the post-window isAngryAt assertion actually EXERCISE the anger-clear branch (without it the
            // assert would be vacuous, since setLastHurtByMob alone latches no anger).
            kin.setLastHurtByMob(player);
            kin.setPersistentAngerTarget(net.minecraft.world.entity.EntityReference.<net.minecraft.world.entity.LivingEntity>of(player));
            kin.startPersistentAngerTimer();

            helper.runAfterDelay(28L, () -> {
                if (kin.getTarget() == player) {
                    helper.fail("after the conversion grace window, the zombified-piglin kin must NOT target its converting player from the same-swing sweep");
                    return;
                }
                if (kin.isAngryAt(player, level)) {
                    helper.fail("after the conversion grace window, the sweep-derived persistent anger toward the converting player must be cleared");
                    return;
                }
                if (!deniedTarget(kin, player)) {
                    helper.fail("a grace-suppressed conversion sweep must not create a player-grudge: the zombified-piglin kin must stay DENIED past the grace window");
                    return;
                }
                kin.setLastHurtByMob(player);
                helper.runAfterDelay(3L, () -> {
                    if (kin.getTarget() != player) {
                        helper.fail("after the grace window, a deliberate strike must make the zombified-piglin kin retaliate against the player");
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    /** Runs the real eat: start the use (fires the Start hook), then finish it (fires the Finish event). */
    // Drive the real central targeting seam (like the MOB tests) and report whether the deny-list NULLED the target.
    // A grace-suppressed sweep must leave the kin with NO player-grudge, so re-posting here returns DENIED past the
    // 10-tick grace window even though no fresh hit was dealt.
    private static boolean deniedTarget(Mob mob, FakePlayer player) {
        LivingChangeTargetEvent event = CommonHooks.onLivingChangeTarget(
                mob, player, LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);
        return event.getNewAboutToBeSetTarget() == null;
    }

    private static void feed(FakePlayer player, ItemStack food) {
        player.setItemInHand(InteractionHand.MAIN_HAND, food);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        // Mirror LivingEntity.completeUsingItem's middle step: this posts LivingEntityUseItemEvent.Finish to the
        // game bus, which the mod's ZombieFoodEvents handler consumes.
        EventHooks.onItemUseFinish(player, food.copy(), player.getUseItemRemainingTicks(), food.finishUsingItem(player.level(), player));
        player.stopUsingItem();
    }
}
