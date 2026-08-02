package dev.molang.iamzombieq.rules;
import dev.molang.iamzombieq.rules.core.ZombieForm;

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
 * unit-testable; the {@code LivingEntity}-typed {@code classify}/{@code shouldIgnoreZombiePlayer} adapters (which
 * bridge to live mobs) live in {@code gameplay.ZombieMobTargetingAdapter} so this class stays Minecraft-free.
 * {@link #needsActiveSeeding} marks the attackers that will NOT naturally target a {@code Player}
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
     * Registry-free classification of a mob by its vanilla entity-type id string (e.g. {@code "minecraft:creeper"}).
     * Mirrors the {@code instanceof} chain in {@code gameplay.ZombieMobTargetingAdapter#classify} for every EXACT
     * vanilla type in the ① attacker table; any unknown / near-miss / other-namespace / null id maps to
     * {@link MobKind#IGNORED}. The adapter delegates here for the fast path and only falls back to {@code instanceof}
     * for unknown ids (mod subclasses of these vanilla types), so the two can never diverge on the known types.
     */
    public static MobKind classifyByEntityTypeId(String entityTypeId) {
        if (entityTypeId == null) {
            return MobKind.IGNORED;
        }
        return switch (entityTypeId) {
            case "minecraft:iron_golem" -> MobKind.IRON_GOLEM;
            case "minecraft:snow_golem" -> MobKind.SNOW_GOLEM;
            case "minecraft:zoglin" -> MobKind.ZOGLIN;
            case "minecraft:goat" -> MobKind.GOAT;
            // Endermite (from a thrown ender pearl) attacks every form; it reuses the all-forms CREEPER row.
            case "minecraft:creeper", "minecraft:endermite" -> MobKind.CREEPER;
            // A plain llama is NOT a trader llama and stays IGNORED (only the trader_llama id maps here).
            case "minecraft:trader_llama" -> MobKind.TRADER_LLAMA;
            case "minecraft:axolotl" -> MobKind.AXOLOTL;
            case "minecraft:warden", "minecraft:wither" -> MobKind.BOSS;
            case "minecraft:enderman", "minecraft:polar_bear" -> MobKind.PROVOKED_SELF_TARGETING;
            default -> MobKind.IGNORED;
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
        return shouldIgnore(
                kind,
                form,
                new TargetingOverrides(retaliating, angeredNeutral)
        );
    }

    public static boolean shouldIgnore(
            MobKind kind,
            ZombieForm form,
            TargetingOverrides overrides
    ) {
        if (overrides.retaliating() || overrides.angeredNeutral()) {
            return false;
        }
        return !attacksZombiePlayer(kind, form);
    }
}
