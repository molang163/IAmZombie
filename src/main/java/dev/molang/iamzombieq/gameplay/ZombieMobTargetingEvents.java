package dev.molang.iamzombieq.gameplay;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
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

    // RC4-sweep (Option B): per-converted-kin grace window. Keyed by the converted mob's UUID -> the converting
    // player + the game-tick the window expires. Populated at conversion (ZombieInfectionEvents.recordConversionGrace)
    // so the deny-list below neutralises the SAME swing's Sweeping-Edge sweep -- which clips the freshly-spawned kin
    // in the same Player.attack call and seeds lastHurtByMob=player. Bounded LinkedHashMap (markers live ~10 ticks,
    // so the cap is never realistically reached) with insertion-order eviction, so a kin that despawns before its
    // target is ever changed cannot leak its marker. Server-thread-only access, like the mod's other transient maps
    // (mirrors the ZombieMountEvents bounded-LinkedHashMap idiom).
    private static final int CONVERSION_GRACE_CAP = 256;
    private static final Map<UUID, ConversionGrace> CONVERSION_GRACE =
            new LinkedHashMap<>(16, 0.75F, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, ConversionGrace> eldest) {
                    return size() > CONVERSION_GRACE_CAP;
                }
            };

    // ~10 ticks (0.5s). Long enough to cover the same-swing sweep (seeded the SAME tick as conversion) plus the
    // 1-2 tick latency before the kin's HurtByTargetGoal fires its setTarget; short enough to expire before the
    // converting player could land a DELIBERATE second strike -- a Sweeping-Edge sweep needs a full-strength attack
    // (attackStrengthScale > 0.9F) and a sword's attack-strength ticker takes ~12.5 ticks to recharge that far.
    private static final int CONVERSION_GRACE_TICKS = 10;

    /** A conversion grace marker: who converted the kin, and the game-tick at which the window expires. */
    private record ConversionGrace(UUID convertingPlayer, long expiryGameTime) {
    }

    // A2 player-grudge (sticky retaliation): per-mob transient "grudge" so a monster the player GENUINELY attacked
    // keeps being ALLOWED to target the player for a bounded window. Fixes the bug where an IGNORED-table mob
    // (zombie/skeleton/spider) gave up ~100t after the player's last hit -- when vanilla LivingEntity.baseTick() nulls
    // lastHurtByMob, `retaliating` flips false and the every-tick deny-list re-assert cancels the target -- and could
    // then never re-aggro (the same deny-list also cancelled its NearestAttackableTargetGoal re-acquisition). Keyed by
    // the mob's UUID -> the grudged player + the game-tick the grudge expires. Bounded LinkedHashMap (cap 256,
    // insertion-order eviction); self-expires by game-tick, dropped lazily on read in hasLiveGrudge, force-cleared on
    // logout/stop. Server-thread-only, exactly like CONVERSION_GRACE above.
    private static final int GRUDGE_CAP = 256;
    private static final Map<UUID, Grudge> PLAYER_GRUDGE =
            new LinkedHashMap<>(16, 0.75F, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Grudge> eldest) {
                    return size() > GRUDGE_CAP;
                }
            };

    // FORGIVE-TAIL length (ticks): how long a grudge OUTLIVES the mob's goal still targeting the player. The grudge
    // self-refreshes every tick the goal re-asserts setTarget(player) (see the RECORD/REFRESH block in onChangeTarget),
    // so while the mob is ENGAGED it never expires -- this value takes effect ONLY after vanilla's own TargetGoal drops
    // the player (escape / past follow-range / ~300t unseen). During the tail the mob stays WILLING to re-aggro if it
    // re-acquires the player; once the tail lapses with no re-acquisition it FORGIVES (resumes ignoring). 200t (10s)
    // sits within vanilla's own ~300t unseen-memory ballpark and bridges a brief out-of-sight gap; vanilla hard-caps
    // any over-stick regardless (the grudge only ALLOWS a target, it can't force a goal to keep one).
    private static final int GRUDGE_TICKS = 200;

    /** A player-grudge marker: which player the mob bears a grudge against, and the game-tick the grudge expires. */
    private record Grudge(UUID grudgePlayer, long expiryGameTime) {
    }

    /**
     * RC4-sweep (Option B): record the conversion grace window for a freshly-converted kin. Called by
     * {@link ZombieInfectionEvents} right after the {@code convertTo}, so {@link #onChangeTarget} can neutralise the
     * conversion swing's Sweeping-Edge sweep (which clips the new kin in the same {@code Player.attack} call, seeding
     * {@code lastHurtByMob = converter}) instead of letting it provoke retaliation. No-op off-server.
     */
    public static void recordConversionGrace(Mob convertedKin, Player convertingPlayer) {
        if (!(convertedKin.level() instanceof ServerLevel level)) {
            return;
        }
        CONVERSION_GRACE.put(
                convertedKin.getUUID(),
                new ConversionGrace(convertingPlayer.getUUID(), level.getGameTime() + CONVERSION_GRACE_TICKS));
    }

    /**
     * A2 player-grudge reader with LAZY expiry: true iff {@code mob} currently bears a LIVE grudge against this exact
     * {@code player}. Mirrors the grace branch's drop-on-expiry (player-match gate, then a {@code <= expiry} live
     * check; on lapse the marker is removed in-line). Checked by UUID (not getTarget()), so it works for goal- and
     * brain-driven mobs alike. Server-thread-only, like CONVERSION_GRACE.
     */
    private static boolean hasLiveGrudge(LivingEntity mob, Player player, ServerLevel serverLevel) {
        Grudge grudge = PLAYER_GRUDGE.get(mob.getUUID());
        if (grudge != null && grudge.grudgePlayer().equals(player.getUUID())) {
            if (serverLevel.getGameTime() <= grudge.expiryGameTime()) {
                return true;
            }
            PLAYER_GRUDGE.remove(mob.getUUID());
        }
        return false;
    }

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

        // RC4-sweep (Option B): if this mob is a freshly-converted kin still inside its conversion grace window and
        // the target about to be set is its converting player, treat it as the SAME swing's Sweeping-Edge sweep
        // (retaliation the player never intended) and neutralise it. NON-CONSUMING: the marker survives the whole
        // window (removed only on expiry below) because the kin's artifact-independent NearestAttackableTargetGoal
        // can acquire the player a tick BEFORE the sweep seeds lastHurtByMob -- consuming on first use would burn the
        // marker with no artifact present and let the real sweep retaliation slip through. Clearing lastHurtByMob,
        // GUARDED to the converter (so a third party's revenge record / sweep-derived anger is never erased), flips
        // `retaliating` false so once the window lapses the IGNORED deny-list permanently denies the unconditional
        // re-acquisition; for a NeutralMob the sweep-derived persistent anger is cleared too. Genuine later
        // retaliation is preserved -- a real post-window strike re-seeds lastHurtByMob.
        ConversionGrace grace = CONVERSION_GRACE.get(mob.getUUID());
        if (grace != null && grace.convertingPlayer().equals(player.getUUID())) {
            if (serverLevel.getGameTime() <= grace.expiryGameTime()) {
                if (mob.getLastHurtByMob() == player) {
                    mob.setLastHurtByMob(null);
                    if (mob instanceof NeutralMob neutral) {
                        neutral.setPersistentAngerTarget(null);
                        neutral.setPersistentAngerEndTime(NeutralMob.NO_ANGER_END_TIME);
                    }
                }
                event.setNewAboutToBeSetTarget(null);
                return;
            }
            CONVERSION_GRACE.remove(mob.getUUID());
        }

        // A2 player-grudge RECORD/REFRESH (sticky retaliation) -- the ONLY place a grudge is created. A grudge is
        // BORN only on a genuine hit (trueHit: getLastHurtByMob()==player) and then SELF-REFRESHES while still live:
        // every tick the mob's vanilla TargetGoal.canContinueToUse re-asserts setTarget(player), onChangeTarget fires
        // again, and an already-live grudge (grudged) re-arms the window. So the grudge survives for as long as the
        // mob actually keeps the player as its target and expires only GRUDGE_TICKS AFTER its goal stops targeting the
        // player (vanilla drops the target on escape / past follow-range / ~300t unseen) -- a forgive-tail, NOT a
        // fixed timer from the last hit. This is the vanilla-faithful "chase until you escape, then forgive" feel.
        // Read grudged BEFORE the put: the refresh must never bootstrap a grudge from nothing, so the FIRST put on any
        // mob necessarily has grudged=false and therefore REQUIRES trueHit. Placed AFTER the CONVERSION_GRACE
        // early-return above: inside a live grace window that branch already returned AND nulled lastHurtByMob, so a
        // grace-suppressed conversion-swing sweep can never reach here (trueHit=false) and a freshly-converted kin has
        // no prior grudge (grudged=false) -- it can never start one (RC4-safe by construction). NOT gated on
        // newTarget==player. Bounded by vanilla's own target-drop: the grudge only ALLOWS a target, it cannot force a
        // goal to keep one, so the every-tick refresh loop ends the moment vanilla drops the player.
        boolean trueHit = mob.getLastHurtByMob() == player;
        boolean grudged = hasLiveGrudge(mob, player, serverLevel);
        if (trueHit || grudged) {
            PLAYER_GRUDGE.put(
                    mob.getUUID(),
                    new Grudge(player.getUUID(), serverLevel.getGameTime() + GRUDGE_TICKS));
        }

        // Preserve retaliation: a mob the player just struck may fight back -- now also a mob bearing a LIVE
        // player-grudge (it genuinely attacked the player and is still engaged), so it stays engaged for as long as
        // its own goal keeps the player, plus the GRUDGE_TICKS forgive-tail after it loses the player. Reuse the
        // trueHit/grudged reads above (grudged captured PRE-refresh) -- do not re-read hasLiveGrudge here, which would
        // observe the just-written refresh and is redundant.
        boolean retaliating = trueHit || grudged;
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
     * Cancelling the interaction stops the merchant's {@code mobInteract} -- {@code Villager.mobInteract}
     * (-> startTrading -> openTradingScreen) and {@code WanderingTrader.mobInteract} (-> openTradingScreen) --
     * from running. {@link AbstractVillager} is the common supertype matched here, covering both {@code Villager}
     * and {@code WanderingTrader}.
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

    // Transient-map cleanup: both CONVERSION_GRACE (RC4-sweep) and PLAYER_GRUDGE (A2 sticky retaliation) self-expire by
    // game-tick (dropped lazily in onChangeTarget / hasLiveGrudge when expired, evicted by their caps), but a player
    // who logs out, or a server stop, must not strand entries naming that player. Mirrors the
    // ZombieFoodEvents/ZombieMountEvents transient-map cleanup.
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        CONVERSION_GRACE.values().removeIf(grace -> grace.convertingPlayer().equals(playerId));
        PLAYER_GRUDGE.values().removeIf(grudge -> grudge.grudgePlayer().equals(playerId));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CONVERSION_GRACE.clear();
        PLAYER_GRUDGE.clear();
    }

    private static boolean isZombiePlayer(Player player) {
        // N6: creative zombie players are still zombies for targeting purposes, so undead/monsters ignore them
        // too. Only spectators (non-interacting) are excluded.
        return !player.isSpectator();
    }
}
