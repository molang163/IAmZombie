package dev.molang.iamzombieq.gametest;

import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;

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
        GameTestSeams.feed(player, food);

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
        GameTestSeams.feed(player, food);

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
        GameTestSeams.killByPlayerAttack(level, player, villager);

        GameTestSeams.awaitConverted(helper, villager, EntityTypes.ZOMBIE_VILLAGER,
                "villager has not been converted yet",
                "expected a ZombieVillager after the zombie player killed the villager");
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
        GameTestSeams.killByPlayerAttack(level, player, pig);

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
        GameTestSeams.killByPlayerAttack(level, player, pig);

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
     * T-infection-villager-no-kin-aggro: a freshly infected ZombieVillager must not target the kin zombie
     * player that infected it. The old bug seeded the player as the converted mob's lastHurtByMob, so its
     * HurtByTargetGoal turned the new zombie on its own creator. After the conversion registers and the target goals
     * get several ticks, the new ZombieVillager's target must not be the player. (Genuine retaliation is unaffected:
     * the fix only deletes the spawn-time attacker seed and touches no retaliation code.) HARD env -> infection
     * chance 1.0 (deterministic conversion).
     */
    static void infectionVillagerNoKinAggro(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        ServerLevel level = helper.getLevel();

        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        GameTestSeams.killByPlayerAttack(level, player, villager);

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
            // (No positive-control re-strike here: the fix only deletes the spawn-time attacker seed and touches
            // no retaliation code, so it cannot disable genuine retaliation by construction; and a re-strike this
            // soon after conversion is absorbed by the mob's hurt-immunity frames, making such a check flaky.)
            helper.succeed();
        });
    }

    /**
     * T-infection-villager-sweep-grace: the conversion swing's Sweeping-Edge AoE clips the
     * freshly-converted ZombieVillager in the SAME Player.attack call, seeding it with the player as its last
     * attacker; the conversion grace window must NEUTRALISE that same-swing sweep so the kin does NOT hunt its
     * converting player. DETERMINISTIC seam-driven proof (no AI-goal timing -- the old fixed-delay getTarget() checks
     * were flaky because they waited on the kin's HurtByTargetGoal / NearestAttackableTargetGoal to fire): poll for
     * the kin to register, then -- still INSIDE the 10t grace window -- seed the sweep clip and drive the
     * onChangeTarget seam OURSELVES; the grace branch must SUPPRESS (deny) it AND CLEAR the seeded lastHurtByMob. Past
     * the window it must stay denied because the grace-suppressed sweep created no player grudge, and a deliberate
     * post-window strike must retaliate (allowed). The kill drives the real LivingDeathEvent (records the grace
     * marker); the sweep's targeting effect is reproduced by seeding lastHurtByMob (targeting depends only on it, not
     * the damage). HARD env -> infection chance 1.0 (deterministic conversion).
     */
    static void infectionVillagerSweepGrace(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        ServerLevel level = helper.getLevel();

        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        GameTestSeams.killByPlayerAttack(level, player, villager);

        ZombieVillager[] kinBox = new ZombieVillager[1];
        helper.startSequence()
                // Poll for the converted kin to register (HARD chance-1.0 conversion; ~1-2 ticks). thenWaitUntil
                // retries every tick until the runnable stops throwing -- no fixed-delay registration race.
                .thenWaitUntil(() -> {
                    ZombieVillager kin = helper.getEntities(EntityTypes.ZOMBIE_VILLAGER, new BlockPos(1, 2, 1), 4.0)
                            .stream().findFirst().orElse(null);
                    if (kin == null) {
                        throw helper.assertionException("waiting for the converted ZombieVillager to register");
                    }
                    kinBox[0] = kin;
                })
                // IN-WINDOW (same step as detection, ~T0+2, well inside the 10t grace window): seed the sweep clip,
                // then drive the seam ourselves -> the grace branch must DENY it AND CLEAR lastHurtByMob. No AI wait.
                .thenExecute(() -> {
                    ZombieVillager kin = kinBox[0];
                    kin.setLastHurtByMob(player);
                    if (!GameTestSeams.targetDenied(kin, player)) {
                        helper.fail("in-window: the conversion-grace branch must SUPPRESS (deny) the sweep-seeded target");
                        return;
                    }
                    if (kin.getLastHurtByMob() != null) {
                        helper.fail("in-window: the grace branch must CLEAR the sweep-seeded lastHurtByMob");
                    }
                })
                // POST-WINDOW (+28 -> ~T0+30, past the 10t window): no grudge was created, so re-posting
                // the seam (lastHurtByMob was nulled) must stay DENIED; then the positive control: a deliberate strike
                // re-seeds lastHurtByMob -> retaliation is ALLOWED.
                .thenExecuteAfter(28, () -> {
                    ZombieVillager kin = kinBox[0];
                    if (!GameTestSeams.targetDenied(kin, player)) {
                        helper.fail("post-window: a grace-suppressed conversion sweep must not create a player-grudge; the kin must stay DENIED");
                        return;
                    }
                    kin.setLastHurtByMob(player);
                    if (GameTestSeams.targetDenied(kin, player)) {
                        helper.fail("post-window: a deliberate strike must make the kin retaliate (target allowed, not denied)");
                    }
                })
                .thenSucceed();
    }

    /**
     * T-infection-piglin-sweep-grace (NeutralMob path): like the villager case but the kin is a
     * ZombifiedPiglin, so the grace branch must clear the sweep-derived persistent ANGER as well as the target.
     * DETERMINISTIC seam-driven (no AI timing): poll for the kin, then IN-WINDOW seed the sweep's full signal
     * (lastHurtByMob + the persistent anger a real converting hit induces on a NeutralMob) and drive the seam -> the
     * grace branch must DENY it AND CLEAR both lastHurtByMob AND the anger (seeding the anger is what makes the
     * anger-clear assertion non-vacuous). Past the window it stays DENIED (no grudge) and a deliberate strike
     * retaliates. Form-gated path -> ZOMBIFIED_PIGLIN-form player + Pig victim; HARD env.
     */
    static void infectionPiglinSweepGrace(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.ZOMBIFIED_PIGLIN, ZombieSize.ADULT);
        ServerLevel level = helper.getLevel();

        Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(1, 2, 1));
        GameTestSeams.killByPlayerAttack(level, player, pig);

        ZombifiedPiglin[] kinBox = new ZombifiedPiglin[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    ZombifiedPiglin kin = helper.getEntities(EntityTypes.ZOMBIFIED_PIGLIN, new BlockPos(1, 2, 1), 4.0)
                            .stream().findFirst().orElse(null);
                    if (kin == null) {
                        throw helper.assertionException("waiting for the converted ZombifiedPiglin to register");
                    }
                    kinBox[0] = kin;
                })
                // IN-WINDOW: seed the sweep's FULL signal (last-attacker + the persistent anger a real converting hit
                // induces on a NeutralMob), then drive the seam -> the grace branch must DENY it AND clear
                // lastHurtByMob AND the anger. No AI wait.
                .thenExecute(() -> {
                    ZombifiedPiglin kin = kinBox[0];
                    kin.setLastHurtByMob(player);
                    kin.setPersistentAngerTarget(EntityReference.of(player));
                    kin.startPersistentAngerTimer();
                    if (!GameTestSeams.targetDenied(kin, player)) {
                        helper.fail("in-window: the conversion-grace branch must SUPPRESS (deny) the sweep-seeded target");
                        return;
                    }
                    if (kin.getLastHurtByMob() != null) {
                        helper.fail("in-window: the grace branch must CLEAR the sweep-seeded lastHurtByMob");
                        return;
                    }
                    if (kin.isAngryAt(player, level)) {
                        helper.fail("in-window: the grace branch must CLEAR the sweep-derived persistent anger");
                    }
                })
                .thenExecuteAfter(28, () -> {
                    ZombifiedPiglin kin = kinBox[0];
                    if (!GameTestSeams.targetDenied(kin, player)) {
                        helper.fail("post-window: a grace-suppressed conversion sweep must not create a player-grudge; the zombified-piglin kin must stay DENIED");
                        return;
                    }
                    kin.setLastHurtByMob(player);
                    if (GameTestSeams.targetDenied(kin, player)) {
                        helper.fail("post-window: a deliberate strike must make the zombified-piglin kin retaliate (allowed)");
                    }
                })
                .thenSucceed();
    }

}
