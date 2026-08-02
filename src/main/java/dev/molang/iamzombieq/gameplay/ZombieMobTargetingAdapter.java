package dev.molang.iamzombieq.gameplay;

import dev.molang.iamzombieq.IAmZombieItems;
import dev.molang.iamzombieq.rules.DisguiseRules;
import dev.molang.iamzombieq.rules.TargetingOverrides;
import dev.molang.iamzombieq.rules.ZombieMobTargetingRules;
import dev.molang.iamzombieq.rules.ZombieMobTargetingRules.MobKind;
import dev.molang.iamzombieq.state.PlayerZombieData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Live (Minecraft-typed) adapters that bridge real game objects to the registry-free decision cores in
 * {@link ZombieMobTargetingRules} and {@link DisguiseRules}. Keeping these adapters here in the {@code gameplay}
 * event-wiring layer lets both rules classes stay pure logic (no Minecraft runtime imports), so they remain directly
 * JUnit-testable without a mod bootstrap.
 *
 * <ul>
 *   <li>{@link #classify(LivingEntity)} maps a live mob to the {@link MobKind} the targeting matrix understands.</li>
 *   <li>{@link #shouldIgnoreZombiePlayer} / {@link #isInterDrownedFriendlyFire} / {@link #shouldRallyToAttackDrowned}
 *       are the {@link LivingEntity}/{@link Mob}-typed deny-list + drowned-social adapters.</li>
 *   <li>{@link #isDisguisedAsHuman(ItemStack)} compares a head stack against the registered disguise-mask item, so the
 *       adapter and the {@link DisguiseRules#DISGUISE_MASK_ID} id-string core can never drift.</li>
 * </ul>
 */
public final class ZombieMobTargetingAdapter {
    private ZombieMobTargetingAdapter() {
    }

    /**
     * Classify a live mob into the {@link MobKind} the decision core understands. Fast path: the registry-free
     * {@link ZombieMobTargetingRules#classifyByEntityTypeId(String)} maps the exact vanilla entity-type id (the single
     * source of truth for the known attacker types). Only when that returns {@link MobKind#IGNORED} (an unknown id) do
     * we fall back to the {@code instanceof} chain, so a mod entity that SUBCLASSES a vanilla attacker class — and thus
     * has an unknown registry id — still keeps its original classification instead of being silently narrowed.
     */
    public static MobKind classify(LivingEntity mob) {
        MobKind byId = ZombieMobTargetingRules.classifyByEntityTypeId(
                EntityType.getKey(mob.getType()).toString());
        if (byId != MobKind.IGNORED) {
            return byId;
        }
        return classifyBySubclass(mob);
    }

    /**
     * {@code instanceof} fallback used only for unknown entity-type ids (mod subclasses of the vanilla attacker
     * classes). The order and semantics mirror {@link ZombieMobTargetingRules#classifyByEntityTypeId(String)} exactly:
     * for a plain vanilla mob this returns the same MobKind the id map already returned, and it exists so a mod
     * subclass keeps the vanilla classification rather than collapsing to {@link MobKind#IGNORED}.
     */
    private static MobKind classifyBySubclass(LivingEntity mob) {
        if (mob instanceof IronGolem) {
            return MobKind.IRON_GOLEM;
        }
        if (mob instanceof SnowGolem) {
            return MobKind.SNOW_GOLEM;
        }
        if (mob instanceof Zoglin) {
            return MobKind.ZOGLIN;
        }
        if (mob instanceof Goat) {
            return MobKind.GOAT;
        }
        if (mob instanceof Creeper) {
            return MobKind.CREEPER;
        }
        // Endermite (spawned by chance from a thrown ender pearl) attacks every form; reuse the all-forms
        // CREEPER row rather than adding a dedicated enum constant.
        if (mob instanceof Endermite) {
            return MobKind.CREEPER;
        }
        // TraderLlama is a subclass of Llama; a plain Llama is NOT a trader llama and stays IGNORED.
        if (mob instanceof TraderLlama) {
            return MobKind.TRADER_LLAMA;
        }
        if (mob instanceof Axolotl) {
            return MobKind.AXOLOTL;
        }
        if (mob instanceof Warden || mob instanceof WitherBoss) {
            return MobKind.BOSS;
        }
        // Enderman (eye-contact) + polar bear (cub defense) target the player only when provoked, via a direct
        // anger-less target that the handler's angeredNeutral/isAngryAt check misses — so they must not be cancelled.
        if (mob instanceof EnderMan || mob instanceof PolarBear) {
            return MobKind.PROVOKED_SELF_TARGETING;
        }
        return MobKind.IGNORED;
    }

    /**
     * Live adapter: classify the mob and decide whether it must be stopped from targeting the zombie player.
     * {@code retaliating}/{@code angeredNeutral} are supplied by the handler (which has the server level needed to
     * evaluate neutral anger). The disguise mask no longer affects targeting (it is too crude to fool any mob); it
     * only gates villager trading elsewhere.
     */
    public static boolean shouldIgnoreZombiePlayer(
            LivingEntity mob,
            Player player,
            PlayerZombieData data,
            boolean retaliating,
            boolean angeredNeutral
    ) {
        return shouldIgnoreZombiePlayer(
                mob,
                player,
                data,
                new TargetingOverrides(retaliating, angeredNeutral)
        );
    }

    public static boolean shouldIgnoreZombiePlayer(
            LivingEntity mob,
            Player player,
            PlayerZombieData data,
            TargetingOverrides overrides
    ) {
        return ZombieMobTargetingRules.shouldIgnore(classify(mob), data.state().form(), overrides);
    }

    /**
     * N9: two Drowned must not start fighting each other from a Drowned's trident friendly-fire. Returns true
     * when both the targeting mob and the about-to-be-set target are Drowned, so the handler can clear the
     * target. {@code retaliating} is honoured: if one Drowned was genuinely struck by the other in melee (so it
     * is the target's last attacker) we let the fight stand rather than masking it.
     */
    public static boolean isInterDrownedFriendlyFire(LivingEntity mob, LivingEntity newTarget, boolean retaliating) {
        if (retaliating) {
            return false;
        }
        return mob instanceof Drowned && newTarget instanceof Drowned;
    }

    /**
     * N10: should this nearby Drowned be recruited to attack the offending Drowned? True when the candidate is a
     * living Drowned that currently has no target (so we never steal an in-progress fight) and is not the
     * offender itself.
     */
    public static boolean shouldRallyToAttackDrowned(Mob candidate, Drowned offender) {
        return candidate instanceof Drowned
                && candidate.isAlive()
                && candidate != offender
                && candidate.getTarget() == null;
    }

    /**
     * True when the stack worn on the head is the crude disguise mask. An empty stack (no headgear) is never a
     * disguise. Compares against the registered {@code IAmZombieItems.DISGUISE_MASK} item directly so this adapter and
     * the id-string core in {@link DisguiseRules} can never drift.
     */
    public static boolean isDisguisedAsHuman(ItemStack head) {
        return head != null && !head.isEmpty() && head.is(IAmZombieItems.DISGUISE_MASK.get());
    }
}
