package dev.molang.iamzombieq.gameplay;

import dev.molang.iamzombieq.IAmZombieItems;
import dev.molang.iamzombieq.rules.mount.BigZombieTargetRules;
import dev.molang.iamzombieq.rules.mount.MountKind;
import dev.molang.iamzombieq.rules.food.ZombieFoodRules;
import dev.molang.iamzombieq.rules.mount.ZombieMountRules;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import dev.molang.iamzombieq.state.SpiderMountData;
import dev.molang.iamzombieq.util.MountCapability;
import dev.molang.iamzombieq.util.RideHelper;
import dev.molang.iamzombieq.util.ZombiePlayerGates;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilus;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.List;

public final class ZombieMountEvents {
    private ZombieMountEvents() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Player player = event.getEntity();
        if (!isZombiePlayer(player)) {
            return;
        }

        // Only VANILLA (living) horses are refused. ZombieHorse/SkeletonHorse extend AbstractHorse (siblings of
        // Horse, not subclasses), so isNormalHorse's instanceof Horse is already false for them; this early block
        // therefore never fires for undead horses and the ZombieHorse feed handler below stays reachable.
        if (isNormalHorse(event.getTarget()) && !ZombieMountRules.canMount(true, MountKind.NORMAL_HORSE, false)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
            if (!player.level().isClientSide()) {
                player.sendSystemMessage(Component.translatable("iamzombieq.message.mount.horse_refused"));
            }
            return;
        }

        if (event.getTarget() instanceof ZombieHorse zombieHorse) {
            ItemStack stack = player.getItemInHand(event.getHand());
            if (isZombieHorseFood(stack)) {
                if (zombieHorse.getHealth() < zombieHorse.getMaxHealth()) {
                    if (!player.level().isClientSide()) {
                        String itemId = stack.is(IAmZombieItems.SUPER_ROTTEN_FLESH.get())
                                ? ZombieFoodRules.SUPER_ROTTEN_FLESH_ID
                                : "minecraft:rotten_flesh";
                        zombieHorse.heal(ZombieMountRules.zombieHorseHealAmount(itemId));
                        stack.consume(1, player);
                    }
                } else if (!player.level().isClientSide()) {
                    // At full health the feed used to silently do nothing and not cancel. Acknowledge the
                    // interaction (don't waste the food) so it isn't silently dropped.
                    player.sendSystemMessage(Component.translatable("iamzombieq.message.mount.horse_full_health"));
                }
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
            }
        }

        // Undead horses (zombie/skeleton) are a zombie player's natural mounts. Vanilla gates BOTH riding and the
        // inventory screen on isTamed(), but wild/spawned undead horses are untamed and can't be tamed by normal
        // means -- so a zombie player could neither ride one nor open its bags (canMount=true was necessary but
        // not sufficient). Mark it tamed+owned for the zombie player, then let VANILLA do the actual mount /
        // inventory open (do NOT cancel). Empty/non-food hand only; the rotten-flesh heal path above already
        // handled + cancelled food. (ZombieHorse/SkeletonHorse are siblings of Horse under AbstractHorse in 26.2.)
        if (!player.level().isClientSide()
                && (event.getTarget() instanceof ZombieHorse || event.getTarget() instanceof SkeletonHorse)
                && event.getTarget() instanceof AbstractHorse undeadHorse
                && !undeadHorse.isTamed()) {
            undeadHorse.setTamed(true);
            undeadHorse.setOwner(player);
        }

        if (event.getTarget() instanceof Spider spider) {
            handleSpiderInteract(event, player, spider);
            return;
        }

        if (event.getTarget() instanceof Zombie zombie) {
            handleBigZombieInteract(event, player, zombie);
            return;
        }

        if (event.getTarget() instanceof Chicken chicken) {
            handleChickenInteract(event, player, chicken);
        }
    }

    @SubscribeEvent
    public static void onEntityMount(EntityMountEvent event) {
        if (!event.isMounting()
                || !(event.getEntityMounting() instanceof Player player)
                || player.level().isClientSide()
                || !isZombiePlayer(player)) {
            return;
        }

        Entity mounted = event.getEntityBeingMounted();
        if (mounted == null) {
            return;
        }

        MountKind mountKind = mountKindFor(mounted);
        if (!ZombieMountRules.canMount(true, zombieSize(player), mountKind, spiderOwnedBy(mounted, player))) {
            event.setCanceled(true);
            return;
        }
        // Defensive backstop for "provoked big zombie is not a mount" on any mount path other than the interact
        // handler (which already returns early before startRiding).
        if (mountKind == MountKind.BIG_ZOMBIE && mounted instanceof Zombie bigZombie && isBigZombieProvokedBy(bigZombie, player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Horse horse) || horse.level().isClientSide()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player player) || !isZombiePlayer(player)) {
            return;
        }

        float healthAfterDamage = Math.max(0.0F, horse.getHealth() - event.getAmount());
        if (healthAfterDamage <= 0.0F) {
            // The snapshot map (PENDING_HORSE_HEALTH_RATIOS) and the whole horse/nautilus death-conversion
            // flow -- including the difficulty roll via configuredInfectionChance(gameDifficulty(level.getDifficulty()))
            // -- live in ZombieInfectionEvents with the other infection paths. Only this pre-death capture stays
            // here, because it is an attribute of the incoming-damage event; it feeds the map through the
            // package-level accessor on ZombieInfectionEvents (same package).
            ZombieInfectionEvents.recordPendingHorseHealthRatio(horse.getUUID(), preDamageHorseHealthRatio(horse));
        }
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Spider spider) || !(event.getNewAboutToBeSetTarget() instanceof Player player)) {
            if (event.getEntity() instanceof Zombie zombie
                    && event.getNewAboutToBeSetTarget() instanceof Player target
                    && isMountedBigZombieRider(zombie, target)) {
                event.setNewAboutToBeSetTarget(null);
            }
            return;
        }
        if (MountCapability.isOwnedSpider(spider, player.getUUID())) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        // Tamed-spider steering is handled by the vanilla riding flow (MobMixin.getControllingPassenger makes the
        // spider report the owner as its controlling passenger so the client sends rider input; LivingEntityMixin
        // .getRiddenInput/getRiddenSpeed drive it). The actual climb motion now comes from SpiderMixin, which
        // makes a ridden owner-spider's onClimbable() track its local horizontalCollision on the controlling
        // client. Here we keep the SERVER-side synced climbing flag in sync with collision so the climb
        // animation shows for observers; track the flag in both directions (true when colliding, false
        // otherwise) instead of only ever poking it true.
        if (event.getEntity() instanceof Spider spider
                && spider.getFirstPassenger() instanceof Player rider
                && MountCapability.isOwnedSpider(spider, rider.getUUID())) {
            spider.setClimbing(spider.horizontalCollision);
            return;
        }

        // Chicken + big-zombie steering now goes through the vanilla controlling-passenger riding flow
        // (MobMixin.getControllingPassenger reports the baby-player rider; LivingEntityMixin.getRiddenInput/
        // getRiddenSpeed/tickRidden drive + rotate them), so no per-tick driveMount is needed here. We keep only
        // the big-zombie auto-attack acquisition, which is not part of the movement flow.
        if (event.getEntity() instanceof Zombie zombie && zombie.level() instanceof ServerLevel level && isRideableBigZombie(zombie)
                && zombie.getFirstPassenger() instanceof Player player && RideHelper.isBabyZombieRider(player)) {
            maybeAutoTargetForMountedBigZombie(level, zombie, player);
        }
    }

    @SubscribeEvent
    public static void onMobDespawn(MobDespawnEvent event) {
        // Defensive backstop moved from MobMixin#removeWhenFarAway: an actively serving mod mount
        // must never despawn. The event gates Mob.checkDespawn ahead of every distance/no-action branch, so the
        // coverage is a superset of the old single-method injection.
        if (MountCapability.activeFor(event.getEntity()).isPresent()) {
            event.setResult(MobDespawnEvent.Result.DENY);
        }
    }

    private static boolean isZombiePlayer(Player player) {
        // Creative players follow zombie mount rules too (flight and invulnerability stay inherent). Only spectators are excluded.
        return ZombiePlayerGates.isZombiePlayer(player);
    }

    private static boolean isZombieHorseFood(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH) || stack.is(IAmZombieItems.SUPER_ROTTEN_FLESH.get());
    }

    // A vanilla (living) horse: the blocked mount kind. ZombieHorse/SkeletonHorse extend AbstractHorse (siblings of
    // Horse, not subclasses), so instanceof Horse already excludes them; the explicit !ZombieHorse/!SkeletonHorse
    // guards are defensive, keeping these undead, zombie-rideable mounts out of the "normal horse" check.
    private static boolean isNormalHorse(Entity target) {
        return target instanceof Horse && !(target instanceof ZombieHorse) && !(target instanceof SkeletonHorse);
    }

    private static void handleBigZombieInteract(PlayerInteractEvent.EntityInteract event, Player player, Zombie zombie) {
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!stack.isEmpty() || !isRideableBigZombie(zombie)) {
            return;
        }

        // "If I hit it, I can't ride it": a big zombie the player has provoked (it is hunting the player, or the
        // player recently struck it) is hostile, not a mount. Don't mount it and don't swallow the click.
        if (isBigZombieProvokedBy(zombie, player)) {
            return;
        }

        completeSimpleMountInteraction(event, player, zombie, MountKind.BIG_ZOMBIE, true);
    }

    private static void handleChickenInteract(PlayerInteractEvent.EntityInteract event, Player player, Chicken chicken) {
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!stack.isEmpty()) {
            return;
        }

        completeSimpleMountInteraction(event, player, chicken, MountKind.CHICKEN, false);
    }

    private static void completeSimpleMountInteraction(
            PlayerInteractEvent.EntityInteract event,
            Player player,
            Mob mount,
            MountKind mountKind,
            boolean clearTargetBeforeRiding
    ) {
        if (!ZombieMountRules.canMount(true, zombieSize(player), mountKind, false)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
            return;
        }

        if (!player.level().isClientSide()) {
            if (clearTargetBeforeRiding) {
                mount.setTarget(null);
            }
            // Forced ride (rule already approved) so sneaking does not veto Entity.canRide; see handleSpiderInteract.
            player.startRiding(mount, true, true);
            // Keep the mount from despawning while it serves as the player's ride (spiders and horses already
            // do this). The onMobDespawn MobDespawnEvent handler is the defensive backstop.
            mount.setPersistenceRequired();
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
    }

    private static void handleSpiderInteract(PlayerInteractEvent.EntityInteract event, Player player, Spider spider) {
        ItemStack stack = player.getItemInHand(event.getHand());
        SpiderMountData data = spider.getData(IAmZombieAttachments.SPIDER_MOUNT);
        if (isSpiderFood(stack)) {
            if (!player.level().isClientSide()) {
                handleSpiderFood(player, spider, stack, data);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
            return;
        }

        if (stack.isEmpty() && ZombieMountRules.canMount(true, MountKind.SPIDER, data.isOwnedBy(player.getUUID()))) {
            if (!player.level().isClientSide()) {
                spider.setTarget(null);
                // Force the ride: our canMount rule already approved it. The plain startRiding overload
                // routes through Entity.canRide, which refuses to mount while the rider is sneaking
                // (isShiftKeyDown) -- and players commonly sneak when carefully approaching a hostile
                // spider, which previously made a tamed spider impossible to ride. The forced overload
                // still fires EntityMountEvent (-> onEntityMount), so the canMount rule remains the gate.
                player.startRiding(spider, true, true);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
        }
    }

    private static void handleSpiderFood(Player player, Spider spider, ItemStack stack, SpiderMountData data) {
        if (!data.hasOwner()) {
            // Taming is no longer instant. Each feed adds food-dependent progress, and the
            // spider only becomes owned once progress reaches the threshold. Always consume the food + give
            // per-feed feedback so the interaction is never silently dropped.
            String foodId = spiderFoodId(stack);
            int nextProgress = ZombieMountRules.spiderTameProgressAfterFeed(data.tameProgress(), foodId);
            stack.consume(1, player);
            spider.playSound(SoundEvents.GENERIC_EAT.value(), 1.0F, 1.0F);
            spider.setTarget(null);

            if (ZombieMountRules.spiderIsTamed(nextProgress)) {
                spider.setData(IAmZombieAttachments.SPIDER_MOUNT, SpiderMountData.ownedBy(player.getUUID()));
                spider.setPersistenceRequired();
                player.sendSystemMessage(Component.translatable("iamzombieq.message.mount.spider_tamed"));
            } else {
                spider.setData(IAmZombieAttachments.SPIDER_MOUNT, data.withProgress(nextProgress));
                int percent = nextProgress * 100 / ZombieMountRules.SPIDER_TAME_PROGRESS_THRESHOLD;
                player.sendSystemMessage(Component.translatable("iamzombieq.message.mount.spider_taming", percent));
            }
            return;
        }

        if (!data.isOwnedBy(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("iamzombieq.message.mount.spider_owned"));
            return;
        }

        float heal = ZombieMountRules.spiderHealAmount(spiderFoodId(stack));
        if (heal > 0.0F && spider.getHealth() < spider.getMaxHealth()) {
            spider.heal(heal);
            stack.consume(1, player);
        }
    }

    private static boolean isSpiderFood(ItemStack stack) {
        return ZombieMountRules.isSpiderTamingFood(spiderFoodId(stack));
    }

    private static String spiderFoodId(ItemStack stack) {
        if (stack.is(Items.ROTTEN_FLESH)) {
            return "minecraft:rotten_flesh";
        }
        if (stack.is(Items.SPIDER_EYE)) {
            return "minecraft:spider_eye";
        }
        if (stack.is(IAmZombieItems.SUPER_ROTTEN_FLESH.get())) {
            return ZombieFoodRules.SUPER_ROTTEN_FLESH_ID;
        }
        return "";
    }

    private static float preDamageHorseHealthRatio(Horse horse) {
        return Math.max(0.0F, horse.getHealth() / horse.getMaxHealth());
    }

    private static ZombieSize zombieSize(Player player) {
        return player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state().size();
    }

    private static MountKind mountKindFor(Entity mounted) {
        if (mounted instanceof Spider) {
            return MountKind.SPIDER;
        }
        if (mounted instanceof ZombieHorse) {
            return MountKind.ZOMBIE_HORSE;
        }
        if (mounted instanceof SkeletonHorse) {
            return MountKind.SKELETON_HORSE;
        }
        if (mounted instanceof Horse) {
            return MountKind.NORMAL_HORSE;
        }
        if (mounted instanceof Chicken) {
            return MountKind.CHICKEN;
        }
        if (mounted instanceof ZombieNautilus) {
            return MountKind.ZOMBIE_NAUTILUS;
        }
        if (mounted instanceof Strider) {
            return MountKind.STRIDER;
        }
        if (mounted instanceof Zombie zombie) {
            return isRideableBigZombie(zombie) ? MountKind.BIG_ZOMBIE : MountKind.OTHER;
        }
        return MountKind.OTHER;
    }

    private static boolean spiderOwnedBy(Entity mounted, Player player) {
        return MountCapability.isOwnedSpider(mounted, player.getUUID());
    }

    private static boolean isRideableBigZombie(Zombie zombie) {
        // Delegate to the shared classifier so the events layer and the MountCapability/mixin layer agree on
        // exactly which zombies are BIG_ZOMBIE mounts (was a byte-identical copy of RideHelper's predicate).
        return RideHelper.isRideableBigZombie(zombie);
    }

    private static boolean isMountedBigZombieRider(Zombie zombie, Player target) {
        return isRideableBigZombie(zombie) && zombie.getFirstPassenger() == target;
    }

    /**
     * A big zombie the player has provoked is no longer mountable ("if I hit it, I can't ride it"): it is either
     * already hunting the player or the player recently struck it (so it is retaliating). Both signals are
     * transient combat memory, so a zombie the player has left alone becomes rideable again once it calms down.
     */
    private static boolean isBigZombieProvokedBy(Zombie zombie, Player player) {
        return zombie.getTarget() == player || zombie.getLastHurtByMob() == player;
    }

    private static void maybeAutoTargetForMountedBigZombie(ServerLevel level, Zombie zombie, Player rider) {
        if (zombie.tickCount % 10 != 0) {
            return;
        }
        LivingEntity current = zombie.getTarget();
        boolean haveValidTarget = current != null
                && current.isAlive()
                && ZombieMountRules.bigZombieShouldAutoAttack(Math.sqrt(zombie.distanceToSqr(current)));
        if (!haveValidTarget) {
            current = selectMountedBigZombieTarget(level, zombie, rider);
            if (current != null) {
                zombie.setTarget(current);
            }
        }

        // While a player controls the mount, the zombie's own melee AI goal is suppressed, so setting a target
        // is not enough -- it would never swing. Actively swing + hurt when a valid target is in melee reach,
        // throttled to ~once per second so it is a normal attack cadence rather than a per-tick blender.
        if (current != null
                && current.isAlive()
                && current != rider
                && zombie.tickCount % 20 == 0
                && zombie.isWithinMeleeAttackRange(current)) {
            zombie.swing(InteractionHand.MAIN_HAND);
            zombie.doHurtTarget(level, current);
        }
    }

    /**
     * Target priority for a ridden big zombie (design): (1) whoever the rider just attacked, (2) whoever just
     * attacked the rider, then (3) the nearest creature zombies naturally aggro (villager > iron golem > other
     * monster). (1)/(2) come from the rider's own recent combat so the mount fights alongside the player; (3) is
     * the proximity scan.
     */
    private static LivingEntity selectMountedBigZombieTarget(ServerLevel level, Zombie zombie, Player rider) {
        return BigZombieTargetRules.pickTarget(
                () -> riderCombatTarget(zombie, rider, rider.getLastHurtMob(),
                        rider.tickCount - rider.getLastHurtMobTimestamp()),
                () -> riderCombatTarget(zombie, rider, rider.getLastHurtByMob(),
                        rider.tickCount - rider.getLastHurtByMobTimestamp()),
                () -> scanMountedBigZombieTargets(level, zombie, rider));
    }

    private static BigZombieTargetRules.RiderCombatTarget<LivingEntity> riderCombatTarget(
            Zombie zombie, Player rider, LivingEntity candidate, int ageTicks) {
        return new BigZombieTargetRules.RiderCombatTarget<>(
                candidate, isMountAttackable(zombie, rider, candidate), ageTicks);
    }

    /** A target the ridden mount may attack: alive, not the rider, not the mount itself, not the rider's own
     *  tamed spider. (Rider-driven targets intentionally allow fellow zombies -- if the rider hits one, the mount
     *  helps -- unlike the proximity scan, which excludes fellow zombies.) */
    private static boolean isMountAttackable(Zombie zombie, Player rider, LivingEntity candidate) {
        return candidate != null
                && candidate != rider
                && candidate != zombie
                && candidate.isAlive()
                && !MountCapability.isOwnedSpider(candidate, rider.getUUID());
    }

    private static List<BigZombieTargetRules.Candidate<LivingEntity>> scanMountedBigZombieTargets(
            ServerLevel level, Zombie zombie, Player rider) {
        AABB area = zombie.getBoundingBox().inflate(ZombieMountRules.BIG_ZOMBIE_AUTO_ATTACK_RANGE);
        List<BigZombieTargetRules.Candidate<LivingEntity>> candidates = new ArrayList<>();

        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area, candidate ->
                candidate != rider && candidate.isAlive() && zombie.canAttack(candidate))) {
            boolean riderOwnedSpider = candidate instanceof Monster
                    && !(candidate instanceof Zombie)
                    && MountCapability.isOwnedSpider(candidate, rider.getUUID());
            BigZombieTargetRules.TargetTier tier = BigZombieTargetRules.classify(
                    candidate instanceof AbstractVillager,
                    candidate instanceof IronGolem,
                    candidate instanceof Monster,
                    candidate instanceof Zombie,
                    riderOwnedSpider);
            candidates.add(new BigZombieTargetRules.Candidate<>(
                    candidate, tier, zombie.distanceToSqr(candidate)));
        }
        return candidates;
    }
}
