package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieForm;

import dev.molang.iamzombieq.state.PlayerZombieData;
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

/**
 * "Who attacks the zombie player?" — the unconditional-attacker (①) matrix from the undead-four relationship table
 * (亡灵四生物关系 · 无条件攻击版), treating the player as its current form. The closed attacker set is:
 *
 * <ul>
 *   <li><b>Iron golem</b> — all forms. The disguise mask is a crude rag and does NOT fool it (per user); the mask
 *       still only enables villager trades.</li>
 *   <li><b>Snow golem</b> — all forms (knockback-only snowballs).</li>
 *   <li><b>Zoglin</b> — all forms (it hates almost everything).</li>
 *   <li><b>Goat</b> — all forms (occasional ram; vanilla goats already ram nearby players).</li>
 *   <li><b>Creeper</b> — all forms. <i>(Extra rule requested by the user; not in the source wiki table.)</i></li>
 *   <li><b>Trader llama</b> — all forms EXCEPT zombified piglin (its spit target list excludes piglins).</li>
 *   <li><b>Axolotl</b> — the DROWNED form only (it hunts drowned).</li>
 *   <li><b>Warden / Wither (bosses)</b> — left to their own vanilla targeting and never force-seeded; the deny-list
 *       just does not cancel a target they acquire themselves, so they attack the zombie player normally (the Wither
 *       on sight, the blind Warden via vibration/sense). Per the user, both attack the zombie player as usual.</li>
 *   <li><b>Enderman / Polar bear</b> — like the bosses, never force-seeded and never cancelled: each targets a
 *       player ONLY when provoked (Enderman eye-contact, polar bear cub-defense) or retaliating, via a direct target
 *       that does NOT set persistent anger (so the handler's {@code angeredNeutral}/isAngryAt check misses it); the
 *       deny-list must leave their self-acquired target alone.</li>
 * </ul>
 *
 * Everything else (fellow monsters — zombie/skeleton/spider/…, and passive animals) is
 * {@link MobKind#IGNORED}: it does not attack the zombie player. Retaliation (the player struck the mob) and
 * neutral anger always OVERRIDE the ignore so genuine fights still resolve.
 *
 * <p>The {@link MobKind}-keyed {@link #attacksZombiePlayer}/{@link #shouldIgnore} core is registry-free and fully
 * unit-testable; the {@link LivingEntity}-typed {@link #classify}/{@link #shouldIgnoreZombiePlayer} adapters bridge
 * to live mobs. {@link #needsActiveSeeding} marks the attackers that will NOT naturally target a {@code Player}
 * (iron/snow golem, trader llama, axolotl) and so must be actively pointed at the player by the targeting handler;
 * creeper/zoglin acquire the player on their own, and the goat rams via its brain.
 */
public final class ZombieMobTargetingRules {
    /** Classification of a targeting mob against the ① attacker table. */
    public enum MobKind {
        /** Iron golem — attacks every form (the crude disguise mask does not fool it). */
        IRON_GOLEM,
        /** Snow golem — attacks every form (knockback-only). */
        SNOW_GOLEM,
        /** Zoglin — attacks every form. */
        ZOGLIN,
        /** Goat — attacks (rams) every form. */
        GOAT,
        /** Creeper — attacks every form (user-added rule). */
        CREEPER,
        /** Trader llama — attacks every form except zombified piglin. */
        TRADER_LLAMA,
        /** Axolotl — attacks the drowned form only. */
        AXOLOTL,
        /**
         * Warden + Wither (bosses) — left to their own vanilla targeting and never force-seeded; the deny-list just
         * does not cancel a target they acquire themselves, so they attack the zombie player normally (the Wither on
         * sight, the blind Warden via vibration/sense).
         */
        BOSS,
        /**
         * Enderman + polar bear — never force-seeded and never cancelled. Each targets a player ONLY when PROVOKED
         * (Enderman = eye contact, polar bear = defending cubs) or retaliating, never unprompted; and that provoked
         * target is set DIRECTLY without registering persistent anger, so the handler's {@code angeredNeutral}
         * (isAngryAt) check misses it. The deny-list must therefore leave their self-acquired target alone, exactly
         * like a boss. (Bee/Wolf/ZombifiedPiglin provoke via persistent anger instead, so angeredNeutral covers them.)
         */
        PROVOKED_SELF_TARGETING,
        /** Every other mob (fellow monsters, passive animals) — does not attack the zombie player. */
        IGNORED
    }

    private ZombieMobTargetingRules() {
    }

    /**
     * Registry-free attacker matrix: does a mob of {@code kind} unconditionally attack a zombie player of
     * {@code form}? (GIANT behaves like the zombie row; baby is tracked separately in size and does not change
     * this answer.)
     */
    public static boolean attacksZombiePlayer(MobKind kind, ZombieForm form) {
        return switch (kind) {
            case IRON_GOLEM, SNOW_GOLEM, ZOGLIN, GOAT, CREEPER, BOSS, PROVOKED_SELF_TARGETING -> true;
            case TRADER_LLAMA -> form != ZombieForm.ZOMBIFIED_PIGLIN;
            case AXOLOTL -> form == ZombieForm.DROWNED;
            case IGNORED -> false;
        };
    }

    /**
     * Whether an attacker of this kind must be ACTIVELY pointed at the zombie player by the targeting handler
     * because it does not naturally target a {@code Player}. Iron/snow golems target only mobs/angry-players,
     * trader llamas target zombie ENTITIES, and the axolotl hunts drowned ENTITIES — none would otherwise notice
     * the player. Creeper/zoglin acquire the player through their own AI, and the goat rams via its brain.
     */
    public static boolean needsActiveSeeding(MobKind kind) {
        return switch (kind) {
            case IRON_GOLEM, SNOW_GOLEM, TRADER_LLAMA, AXOLOTL -> true;
            default -> false;
        };
    }

    /**
     * Registry-free deny-list core: should this mob be stopped from targeting the zombie player? Retaliation and
     * neutral anger always override (allow the fight); otherwise the mob is ignored unless it is in the attacker
     * matrix for this form.
     */
    public static boolean shouldIgnore(
            MobKind kind,
            ZombieForm form,
            boolean retaliating,
            boolean angeredNeutral
    ) {
        if (retaliating || angeredNeutral) {
            return false;
        }
        return !attacksZombiePlayer(kind, form);
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
        return shouldIgnore(classify(mob), data.state().form(), retaliating, angeredNeutral);
    }

    /** Classify a live mob into the {@link MobKind} the decision core understands. */
    public static MobKind classify(LivingEntity mob) {
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

    // ---------------------------------------------------------------------------------------------------------
    // Drowned trident friendly-fire (N9 / N10)
    // ---------------------------------------------------------------------------------------------------------

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
}
