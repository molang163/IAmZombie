package dev.molang.iamzombieq.gameplay;

import dev.molang.iamzombieq.IAmZombieConfig;
import dev.molang.iamzombieq.rules.DisguiseRules;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.ZombieMobTargetingRules;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import dev.molang.iamzombieq.state.PlayerZombieData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * "Who attacks the zombie player?" — enforces the undead-four attacker table (亡灵四生物关系 · 无条件攻击版) on a
 * (non-spectator, creative included per N6) zombie player. Two halves, both driven by the same
 * {@link ZombieMobTargetingRules#attacksZombiePlayer} matrix so they always agree:
 *
 * <ul>
 *   <li><b>Deny-list</b> ({@link #onChangeTarget}): a target a mob is about to set on the zombie player is cleared
 *       UNLESS that mob is a table attacker for the player's form (or is retaliating / an angered neutral). This
 *       stops every other mob — fellow monsters and the Wither — from hunting the player, and now ALLOWS the
 *       attackers that hunt players on their own (creeper, zoglin) plus the Warden's own sense-based targeting.</li>
 *   <li><b>Active seeding</b> ({@link #seedAttackersOntoZombiePlayer}): the attackers that would never naturally
 *       notice a {@code Player} (iron/snow golem, trader llama, axolotl) are pointed at a nearby zombie player.</li>
 * </ul>
 *
 * Retaliation (a mob the player just hit) and neutral anger (an angry zombified-piglin pack / provoked golem) are
 * always preserved. The disguise mask (iron golem stands down) + the trade gate + the N9/N10 drowned-trident
 * social rules are unchanged.
 *
 * <p>Uses NeoForge's {@link LivingChangeTargetEvent} (fired centrally from setTarget, including the brain-based
 * BEHAVIOR_TARGET path used by piglins/brutes/hoglins), so a single server-side hook covers both goal- and
 * brain-based targeting.
 *
 * <p>Also hosts the disguise-mask trade behaviour (G19 + G12 durability): a zombie player may only open
 * villager / wandering-trader trades while wearing the disguise mask, and each successful trade spends one point
 * of mask durability.
 */
public final class ZombieMobTargetingEvents {
    private ZombieMobTargetingEvents() {
    }

    /** N9/N10: how far a hurt zombie reaches out for nearby targetless Drowned to retaliate against an offender. */
    private static final double DROWNED_RALLY_RADIUS = 24.0;

    // How often (ticks) the attacker-seeding scan runs per zombie player. 10 (twice/second) keeps a seeded golem
    // engaged without a visible gap if its own target goal clears the (non-mob) player between cycles, while still
    // being far cheaper than per-tick. Re-seeding only touches mobs whose target is currently null.
    private static final int ATTACKER_SEED_INTERVAL_TICKS = 10;
    /** Radius of the attacker-seeding scan around a zombie player. */
    private static final double ATTACKER_SEED_RADIUS = 24.0;
    /** How long (ticks) a seeded brain ATTACK_TARGET memory lasts; re-applied each scan so it stays fresh. */
    private static final long ATTACKER_SEED_MEMORY_TICKS = 200L;

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity mob = event.getEntity();
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();

        // N9: a Drowned's thrown trident can clip another Drowned; do not let drowned start fighting each other
        // from that friendly fire. Genuine melee retaliation (the target is this mob's last attacker) is kept.
        boolean drownedRetaliating = mob.getLastHurtByMob() == newTarget;
        if (ZombieMobTargetingRules.isInterDrownedFriendlyFire(mob, newTarget, drownedRetaliating)) {
            event.setNewAboutToBeSetTarget(null);
            return;
        }

        if (!IAmZombieConfig.UNDEAD_IGNORE_ZOMBIE_PLAYER.get()) {
            return;
        }
        if (!(newTarget instanceof Player player) || !isZombiePlayer(player)) {
            return;
        }

        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Preserve retaliation: a mob the player just struck may fight back.
        boolean retaliating = mob.getLastHurtByMob() == player;
        // Preserve neutral-mob anger (an angered zombified piglin pack, a provoked iron golem, …).
        boolean angeredNeutral = mob instanceof NeutralMob neutral && neutral.isAngryAt(player, serverLevel);

        PlayerZombieData data = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE);
        if (ZombieMobTargetingRules.shouldIgnoreZombiePlayer(mob, player, data, retaliating, angeredNeutral)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    /**
     * The active half of the attacker table: the mobs that UNCONDITIONALLY attack the four undead but would never
     * naturally notice a {@code Player} — iron golem, snow golem, trader llama (all target mobs/angry-players, not
     * players) and the axolotl (hunts drowned ENTITIES) — are pointed at a nearby zombie player here. Creeper and
     * zoglin acquire the player through their own AI (the {@link #onChangeTarget} deny-list now allows them), and
     * the goat rams via its brain, so none of those need seeding. Throttled + bounded like the mod's other scans;
     * only seeds a mob with no current target so it never steals an in-progress fight, and the form/disguise gate
     * is the same {@link ZombieMobTargetingRules#attacksZombiePlayer} predicate the deny-list uses (so the seeded
     * target is never cancelled).
     */
    @SubscribeEvent
    public static void seedAttackersOntoZombiePlayer(PlayerTickEvent.Post event) {
        if (!IAmZombieConfig.UNDEAD_IGNORE_ZOMBIE_PLAYER.get()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !isZombiePlayer(player)
                || player.tickCount % ATTACKER_SEED_INTERVAL_TICKS != 0
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ZombieForm form = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state().form();
        AABB area = player.getBoundingBox().inflate(ATTACKER_SEED_RADIUS);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, candidate -> shouldSeedAttacker(candidate, form))) {
            if (mob instanceof Axolotl) {
                // The axolotl is brain-driven; setTarget alone won't fight, so seed its ATTACK_TARGET memory.
                mob.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, player, ATTACKER_SEED_MEMORY_TICKS);
            } else {
                mob.setTarget(player);
            }
        }
    }

    private static boolean shouldSeedAttacker(Mob mob, ZombieForm form) {
        if (!mob.isAlive() || mob.getTarget() != null) {
            return false;
        }
        ZombieMobTargetingRules.MobKind kind = ZombieMobTargetingRules.classify(mob);
        return ZombieMobTargetingRules.needsActiveSeeding(kind)
                && ZombieMobTargetingRules.attacksZombiePlayer(kind, form);
    }

    /**
     * N10: when a non-drowned {@link net.minecraft.world.entity.monster.zombie.Zombie} is wounded by a Drowned's
     * thrown trident, nearby Drowned that currently have no target rally to attack the offending Drowned.
     *
     * <p>The trident's {@link net.minecraft.world.damagesource.DamageSource} carries the projectile as the direct
     * entity and the firing Drowned as the causing entity ({@code getEntity()}). We only react when the victim is
     * a plain {@code Zombie} that is NOT itself a Drowned (Drowned-vs-Drowned friendly fire is handled by N9), and
     * the offender is a live Drowned. The search is bounded to {@link #DROWNED_RALLY_RADIUS} blocks and only
     * recruits targetless Drowned, so it never steals an in-progress fight and stays cheap.
     */
    @SubscribeEvent
    public static void onZombieHurtByDrownedTrident(LivingIncomingDamageEvent event) {
        // Victim must be a plain Zombie but NOT a Drowned (N9 owns inter-drowned cases).
        if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie)
                || zombie instanceof Drowned) {
            return;
        }
        if (!(zombie.level() instanceof ServerLevel level)) {
            return;
        }
        // The trident's owner (causing entity) must be a Drowned other than the victim.
        Entity offendingEntity = event.getSource().getEntity();
        if (!(offendingEntity instanceof Drowned offender) || offender == zombie) {
            return;
        }

        AABB area = zombie.getBoundingBox().inflate(DROWNED_RALLY_RADIUS);
        for (Drowned ally : level.getEntitiesOfClass(Drowned.class, area,
                ally -> ZombieMobTargetingRules.shouldRallyToAttackDrowned(ally, offender))) {
            ally.setTarget(offender);
        }
    }

    /**
     * G19 trade gate: block a zombie player from OPENING villager / wandering-trader trades unless disguised.
     * Cancelling the interaction stops vanilla {@code AbstractVillager.mobInteract} from running
     * {@code startTrading}/{@code openTradingScreen}. {@link AbstractVillager} covers both {@code Villager} and
     * {@code WanderingTrader}.
     */
    @SubscribeEvent
    public static void onMerchantInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getTarget() instanceof AbstractVillager)) {
            return;
        }
        Player player = event.getEntity();
        if (!isZombiePlayer(player)) {
            return;
        }
        if (DisguiseRules.isDisguisedAsHuman(player.getItemBySlot(EquipmentSlot.HEAD))) {
            return;
        }

        // Not disguised: a zombie cannot trade. Swallow the interaction so the trade screen never opens.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
    }

    /**
     * G12 durability: spend one point of mask durability per SUCCESSFUL trade while the mask is worn. NeoForge's
     * {@link TradeWithVillagerEvent} fires server-side from {@code AbstractVillager.notifyTrade} once a trade
     * result is taken, and carries the trading player, so it is the precise "successful trade" hook.
     */
    @SubscribeEvent
    public static void onTradeWithVillager(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isZombiePlayer(player)) {
            return;
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!DisguiseRules.isDisguisedAsHuman(head) || !head.isDamageableItem()) {
            return;
        }
        // hurtAndBreak no-ops off-server and fires the proper head-slot break hook; spend exactly one point.
        head.hurtAndBreak(1, player, EquipmentSlot.HEAD);
    }

    private static boolean isZombiePlayer(Player player) {
        // N6: creative zombie players are still zombies for targeting purposes, so undead/monsters ignore them
        // too. Only spectators (non-interacting) are excluded.
        return !player.isSpectator();
    }
}
