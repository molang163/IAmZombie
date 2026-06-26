package dev.molang.iamzombieq.gametest;

import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;

import dev.molang.iamzombieq.IAmZombieConfig;
import dev.molang.iamzombieq.IAmZombieItems;

/**
 * Additional FakePlayer-driven bodies for the {@code iamzombieq} FOOD + INF acceptance domains, registered by
 * {@link IAmZombieFoodInfGameTests}. These COMPLEMENT (never duplicate) the seven tests already shipped by
 * {@link IAmZombieGameTests}: those cover food_human_hunger (cooked_beef -> Hunger), baby_grow
 * (super_rotten_flesh -> ADULT), and the villager/pig-normal/pig-piglin infection triad. Here we add the rest of the
 * cleanly-assertable FOOD rows (golden apple, enchanted golden apple, pufferfish, spider eye, T3 Hunger amplifier,
 * sweet Slowness, super_rotten_flesh Strength, chorus fruit, honey bottle) and the horse-infection row (INF-005).
 *
 * <p>Eating is driven through the exact same real server-side seam {@link IAmZombieGameTestBodies} proved out: the
 * private {@link #feed} helper (copied verbatim from that file's pattern) runs {@code startUsingItem} (fires the
 * {@code LivingEntityUseItemEvent.Start} hook the mod snapshots on) + {@code EventHooks.onItemUseFinish} (posts the
 * {@code .Finish} event the mod's {@code ZombieFoodEvents} handler applies the buffs/debuffs on). We assert the
 * resulting {@code MobEffect}s the rule's data table applies in {@code applyZombieEffects} -- those run on every
 * successful zombie-food eat, independent of the (fragile) Start-snapshot restore path -- so the assertions are
 * robust effect-presence/amplifier checks rather than per-tick or snapshot-dependent ones.
 */
final class IAmZombieFoodInfGameTestBodies {

    private IAmZombieFoodInfGameTestBodies() {
    }

    /**
     * FOOD-011: a zombie player eating a Golden Apple gains ABSORPTION (the rule's buff) and HUNGER (the rule's
     * debuff). Both are static {@code EffectSpec}s applied by {@code applyZombieEffects}, so they appear regardless of
     * the snapshot path. We assert presence + amplifier (Absorption I = amp 0, Hunger I = amp 0) -- the durations are
     * config-derived and the snapshot-restore/suppress path is intentionally NOT asserted here (deferred as fragile).
     */
    static void foodGoldenApple(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.removeEffect(MobEffects.ABSORPTION);
        player.removeEffect(MobEffects.HUNGER);

        feed(player, new ItemStack(Items.GOLDEN_APPLE));

        if (!assertEffect(helper, player, MobEffects.ABSORPTION, 0, "golden_apple -> Absorption I")) {
            return;
        }
        if (!assertEffect(helper, player, MobEffects.HUNGER, 0, "golden_apple -> Hunger I")) {
            return;
        }
        helper.succeed();
    }

    /**
     * FOOD-012: an Enchanted Golden Apple gives ABSORPTION II (amp 1) + RESISTANCE I (amp 0) as buffs and HUNGER I
     * (amp 0) as its debuff. All three are static {@code EffectSpec}s in the rule, so they apply on the Finish path.
     */
    static void foodEnchantedGoldenApple(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.removeEffect(MobEffects.ABSORPTION);
        player.removeEffect(MobEffects.RESISTANCE);
        player.removeEffect(MobEffects.HUNGER);

        feed(player, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));

        if (!assertEffect(helper, player, MobEffects.ABSORPTION, 1, "enchanted_golden_apple -> Absorption II")) {
            return;
        }
        if (!assertEffect(helper, player, MobEffects.RESISTANCE, 0, "enchanted_golden_apple -> Resistance I")) {
            return;
        }
        if (!assertEffect(helper, player, MobEffects.HUNGER, 0, "enchanted_golden_apple -> Hunger I")) {
            return;
        }
        helper.succeed();
    }

    /**
     * FOOD-013: a Pufferfish grants ABSORPTION (amp 0) + REGENERATION II (amp 1) as pure buffs with NO human-food
     * punishment (it is a T4 SPECIAL, not HUMAN_COOKED) -- so the player must NOT come away with a Hunger debuff.
     */
    static void foodPufferfish(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.removeEffect(MobEffects.ABSORPTION);
        player.removeEffect(MobEffects.REGENERATION);
        player.removeEffect(MobEffects.HUNGER);

        feed(player, new ItemStack(Items.PUFFERFISH));

        if (!assertEffect(helper, player, MobEffects.ABSORPTION, 0, "pufferfish -> Absorption I")) {
            return;
        }
        if (!assertEffect(helper, player, MobEffects.REGENERATION, IAmZombieConfig.PUFFERFISH_REGENERATION_AMPLIFIER.get(), "pufferfish -> Regeneration at the configured amplifier")) {
            return;
        }
        if (player.getEffect(MobEffects.HUNGER) != null) {
            helper.fail("pufferfish (T4 SPECIAL) must NOT inflict the human-food Hunger punishment");
            return;
        }
        helper.succeed();
    }

    /**
     * FOOD-006: a Spider Eye (T1 CARRION) grants NIGHT_VISION (amp 0) as its buff, and -- being processed through the
     * zombie-food path -- does NOT leave a Hunger punishment (it is not HUMAN_COOKED). We assert the buff and the
     * absence of the human-food Hunger debuff. (Vanilla's own Poison side-effect strip is part of the snapshot path
     * and is deferred as fragile; we assert only the additive rule buff here.)
     */
    static void foodSpiderEye(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.removeEffect(MobEffects.NIGHT_VISION);
        player.removeEffect(MobEffects.HUNGER);

        feed(player, new ItemStack(Items.SPIDER_EYE));

        if (!assertEffect(helper, player, MobEffects.NIGHT_VISION, 0, "spider_eye -> Night Vision")) {
            return;
        }
        if (player.getEffect(MobEffects.HUNGER) != null) {
            helper.fail("spider_eye (T1 CARRION) must NOT inflict the human-food Hunger punishment");
            return;
        }
        helper.succeed();
    }

    /**
     * FOOD-009: a T3 HUMAN_COOKED food (cooked_porkchop, distinct from the existing cooked_beef test) applies the
     * human-food punishment as HUNGER at amplifier 2 (Hunger III) plus NAUSEA. This asserts the AMPLIFIER (the
     * existing food_human_hunger test only asserts Hunger presence), covering the "Hunger III" data point.
     */
    static void foodHumanHungerAmplifier(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.removeEffect(MobEffects.HUNGER);
        player.removeEffect(MobEffects.NAUSEA);

        feed(player, new ItemStack(Items.COOKED_PORKCHOP));

        if (!assertEffect(helper, player, MobEffects.HUNGER, IAmZombieConfig.HUMAN_FOOD_HUNGER_AMPLIFIER.get(), "cooked_porkchop -> Hunger at the configured human-food amplifier")) {
            return;
        }
        if (player.getEffect(MobEffects.NAUSEA) == null) {
            helper.fail("T3 HUMAN_COOKED should also inflict Nausea");
            return;
        }
        helper.succeed();
    }

    /**
     * FOOD-010: a SWEET T3 food (cookie) applies the same human-food punishment (Hunger III + Nausea) AND, uniquely,
     * an extra SLOWNESS (amp 0) carried in the rule's debuffs list. We assert the SWEET-specific Slowness plus the
     * shared Hunger to confirm the sweet branch resolved.
     */
    static void foodSweetSlowness(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.removeEffect(MobEffects.SLOWNESS);
        player.removeEffect(MobEffects.HUNGER);

        feed(player, new ItemStack(Items.COOKIE));

        if (!assertEffect(helper, player, MobEffects.SLOWNESS, 0, "cookie (SWEET) -> Slowness I")) {
            return;
        }
        if (player.getEffect(MobEffects.HUNGER) == null) {
            helper.fail("cookie is still T3 HUMAN_COOKED, so it should also inflict the human-food Hunger");
            return;
        }
        helper.succeed();
    }

    /**
     * FOOD-017 (effect angle; the BABY->ADULT angle is the existing baby_grow test): super_rotten_flesh grants
     * STRENGTH (amp 0) + SATURATION as an ADULT eater. We assert the Strength buff (the headline buff of the mod's
     * own item) on an ADULT so this is orthogonal to the existing baby-grow coverage.
     */
    static void foodSuperRottenFleshStrength(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.removeEffect(MobEffects.STRENGTH);

        feed(player, new ItemStack(IAmZombieItems.SUPER_ROTTEN_FLESH.get()));

        if (!assertEffect(helper, player, MobEffects.STRENGTH, IAmZombieConfig.SUPER_ROTTEN_FLESH_STRENGTH_AMPLIFIER.get(), "super_rotten_flesh -> Strength at the configured amplifier")) {
            return;
        }
        helper.succeed();
    }

    /**
     * FOOD-015: a Chorus Fruit grants SLOW_FALLING (amp 0) as its buff and NAUSEA (amp 0) as its debuff -- both static
     * {@code EffectSpec}s -- with the vanilla random teleport suppressed (the suppression itself is a per-eat
     * side-effect and not asserted here; we assert the two effects the rule applies).
     */
    static void foodChorusFruit(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.removeEffect(MobEffects.SLOW_FALLING);
        player.removeEffect(MobEffects.NAUSEA);

        feed(player, new ItemStack(Items.CHORUS_FRUIT));

        if (!assertEffect(helper, player, MobEffects.SLOW_FALLING, 0, "chorus_fruit -> Slow Falling")) {
            return;
        }
        if (!assertEffect(helper, player, MobEffects.NAUSEA, 0, "chorus_fruit -> Nausea")) {
            return;
        }
        helper.succeed();
    }

    /**
     * FOOD-016: a Honey Bottle inflicts NAUSEA (amp 0) as its only effect and grants NO buff. We assert the Nausea and
     * that no human-food Hunger punishment is applied (it is a T4 SPECIAL, not HUMAN_COOKED).
     */
    static void foodHoneyBottle(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        player.removeEffect(MobEffects.NAUSEA);
        player.removeEffect(MobEffects.HUNGER);

        feed(player, new ItemStack(Items.HONEY_BOTTLE));

        if (!assertEffect(helper, player, MobEffects.NAUSEA, 0, "honey_bottle -> Nausea")) {
            return;
        }
        if (player.getEffect(MobEffects.HUNGER) != null) {
            helper.fail("honey_bottle (T4 SPECIAL) must NOT inflict the human-food Hunger punishment");
            return;
        }
        helper.succeed();
    }

    /**
     * INF-005: a zombie player that kills a normal Horse turns it into a ZombieHorse (the horse-infection path in
     * {@code ZombieMountEvents#onLivingDeath}). HARD difficulty makes the infection chance 1.0 so the conversion is
     * deterministic; the path is form-agnostic (any zombie-player form works, unlike the form-gated pig/piglin path).
     * We kill the horse through the real player-attack damage pipeline so vanilla fires the real LivingDeathEvent the
     * handler converts on, then poll a tight radius around the victim until the converted ZombieHorse appears (it
     * registers with the level on the next tick), scoped like the villager/pig tests because batched tests share one
     * level.
     */
    static void infectionHorse(GameTestHelper helper) {
        FakePlayer player = GameTestPlayers.spawnZombieFakePlayer(helper, ZombieForm.NORMAL, ZombieSize.ADULT);
        ServerLevel level = helper.getLevel();

        Horse horse = helper.spawn(EntityTypes.HORSE, new BlockPos(1, 2, 1));
        DamageSource killedByPlayer = level.damageSources().playerAttack(player);
        horse.hurtServer(level, killedByPlayer, Float.MAX_VALUE);

        helper.succeedWhen(() -> {
            if (horse.isAlive() && !horse.isRemoved()) {
                throw helper.assertionException("horse has not been converted yet");
            }
            if (helper.getEntities(EntityTypes.ZOMBIE_HORSE, new BlockPos(1, 2, 1), 1.5).isEmpty()) {
                throw helper.assertionException("expected a ZombieHorse after the zombie player killed the horse");
            }
        });
    }

    // ---- helpers ----

    /**
     * Assert the player has {@code effect} at exactly {@code expectedAmplifier}; on failure, {@code helper.fail} with a
     * descriptive message and return false so the caller bails. Asserts presence + amplifier (the stable, data-table
     * facts) and intentionally NOT duration (config-derived) so the test stays robust.
     */
    private static boolean assertEffect(GameTestHelper helper, FakePlayer player, Holder<MobEffect> effect, int expectedAmplifier, String what) {
        MobEffectInstance instance = player.getEffect(effect);
        if (instance == null) {
            helper.fail("expected effect missing: " + what);
            return false;
        }
        if (instance.getAmplifier() != expectedAmplifier) {
            helper.fail("wrong amplifier for " + what + ": expected " + expectedAmplifier + " but was " + instance.getAmplifier());
            return false;
        }
        return true;
    }

    /** Runs the real eat: start the use (fires the Start hook), then finish it (fires the Finish event). Copied from
     * {@link IAmZombieGameTestBodies}'s proven {@code feed} so this domain's tests drive the exact same server seam. */
    private static void feed(FakePlayer player, ItemStack food) {
        player.setItemInHand(InteractionHand.MAIN_HAND, food);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        // Mirror LivingEntity.completeUsingItem's middle step: this posts LivingEntityUseItemEvent.Finish to the
        // game bus, which the mod's ZombieFoodEvents handler consumes.
        EventHooks.onItemUseFinish(player, food.copy(), player.getUseItemRemainingTicks(), food.finishUsingItem(player.level(), player));
        player.stopUsingItem();
    }
}
