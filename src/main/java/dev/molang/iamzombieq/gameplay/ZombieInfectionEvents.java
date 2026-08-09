package dev.molang.iamzombieq.gameplay;
import dev.molang.iamzombieq.util.Difficulties;

import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import dev.molang.iamzombieq.IAmZombieServerConfig;
import dev.molang.iamzombieq.util.BoundedUuidMap;
import dev.molang.iamzombieq.api.event.ZombieInfectPreEvent;
import dev.molang.iamzombieq.api.event.ZombieInfectedEvent;
import dev.molang.iamzombieq.internal.event.ZombieEventPublisher;
import dev.molang.iamzombieq.internal.logging.ZombieLog;
import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;
import dev.molang.iamzombieq.rules.ZombieInfectionRules;
import dev.molang.iamzombieq.util.ZombiePlayerGates;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
//? if >=26.2
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
// CROSS_VERSION-NAUTILUS-CAPABILITY:infection-imports
//? if >=1.21.11 {
import net.minecraft.world.entity.animal.nautilus.Nautilus;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilus;
//?}
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
//? if <1.21.11 {
/*import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
*///?}
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

public final class ZombieInfectionEvents {
    // Keyed by horse UUID. Bounded LinkedHashMap with insertion-order eviction: entries are normally
    // removed when the horse dies to a zombie player, but horses that die from other sources (lava, fall,
    // etc.) would otherwise leak their snapshot until server stop. The cap prevents unbounded growth while
    // the eldest (least recently inserted) entry is dropped first; 256 pending dying-horse snapshots is far
    // more than can realistically be in flight, so eviction never disturbs a real in-progress conversion.
    private static final int PENDING_HORSE_HEALTH_RATIOS_CAP = 256;
    private static final Map<UUID, Float> PENDING_HORSE_HEALTH_RATIOS =
            BoundedUuidMap.newBounded(PENDING_HORSE_HEALTH_RATIOS_CAP);

    private ZombieInfectionEvents() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }

        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player player) || !isZombiePlayer(player)) {
            return;
        }

        if (victim instanceof Villager villager) {
            tryInfectVillager(event, level, villager, player);
        } else if (victim instanceof Mob mob
                // Only a ZOMBIFIED-PIGLIN-form zombie player turns pigs/piglins into zombified piglins; a normal
                // (or drowned/husk) zombie player cannot. The form is the "kin" of what it spreads.
                && player.getData(dev.molang.iamzombieq.state.IAmZombieAttachments.PLAYER_ZOMBIE).state().form() == dev.molang.iamzombieq.rules.core.ZombieForm.ZOMBIFIED_PIGLIN
                && ZombieInfectionRules.canInfectIntoZombifiedPiglin(victim instanceof Pig, victim instanceof AbstractPiglin)) {
            tryInfectIntoZombifiedPiglin(event, level, mob, player);
        } else if (victim instanceof Horse horse) {
            tryInfectHorse(event, level, horse, player);
        }
        // CROSS_VERSION-NAUTILUS-CAPABILITY:infection-dispatch
        //? if >=1.21.11 {
        else if (victim instanceof Nautilus nautilus) {
            tryInfectNautilus(event, level, nautilus, player);
        }
        //?}
    }

    private static void tryInfectVillager(LivingDeathEvent event, ServerLevel level, Villager villager, Player player) {
        runInfectionPipeline(event, level, villager, player, EntityTypes.ZOMBIE_VILLAGER,
                () -> convertVillagerToZombieVillager(level, villager, player),
                IAmZombieAdvancements.INFECTION);
    }

    // A zombie player that kills a Pig or any Piglin/AbstractPiglin can infect it into a zombified piglin, mirroring
    // the villager infection (difficulty-scaled chance, EventHooks.canLivingConvert gate, INFECTION advancement). Both
    // source types convert to ZOMBIFIED_PIGLIN, matching vanilla's pig+lightning zombification. Form-gated (see the
    // call site): ONLY a ZOMBIFIED_PIGLIN-form zombie player infects pigs/piglins into zombified piglins (the form is
    // the "kin" of what it spreads); NORMAL/DROWNED/HUSK/GIANT cannot.
    private static void tryInfectIntoZombifiedPiglin(LivingDeathEvent event, ServerLevel level, Mob victim, Player player) {
        runInfectionPipeline(event, level, victim, player, EntityTypes.ZOMBIFIED_PIGLIN,
                () -> convertToZombifiedPiglin(level, victim, player),
                IAmZombieAdvancements.INFECTION);
    }

    // Migrated from ZombieMountEvents: the pending pre-death health-ratio snapshot is consumed before the
    // infection roll (inside the pipeline), so a failed roll still clears the entry and cannot leak it into a
    // later, unrelated death of the same horse.
    private static void tryInfectHorse(LivingDeathEvent event, ServerLevel level, Horse horse, Player player) {
        Float pendingHorseHealthRatio = PENDING_HORSE_HEALTH_RATIOS.remove(horse.getUUID());
        runInfectionPipeline(event, level, horse, player, EntityTypes.ZOMBIE_HORSE,
                () -> convertHorseToZombieHorse(level, horse, player, pendingHorseHealthRatio),
                IAmZombieAdvancements.HORSE_INFECTION);
    }

    // Migrated from ZombieMountEvents. No advancement exists for the nautilus infection, so the pipeline's
    // advancement step is skipped (null).
    // CROSS_VERSION-NAUTILUS-CAPABILITY:infection-pipeline-adapter
    //? if >=1.21.11 {
    private static void tryInfectNautilus(LivingDeathEvent event, ServerLevel level, Nautilus nautilus, Player player) {
        runInfectionPipeline(event, level, nautilus, player, EntityTypes.ZOMBIE_NAUTILUS,
                () -> convertNautilusToZombieNautilus(level, nautilus, player),
                null);
    }
    //?}

    /**
     * Shared infection pipeline shell for all four infection paths (villager, pig/piglin, horse, nautilus), in
     * the exact order of the original villager path: difficulty-scaled RNG roll, then the
     * {@code EventHooks.canLivingConvert} gate, then the cancellable {@code ZombieInfectPreEvent}, then the
     * path's own conversion callback, then the advancement (nullable), then the {@code ZombieInfectedEvent}
     * observer, then the death-event cancel. The conversion methods stay path-specific and are passed in
     * unchanged.
     *
     * @param attacker    the killing zombie player; the API events and the advancement fire only when it is a
     *                    {@code ServerPlayer} (the same narrowing the villager path always used)
     * @param conversion  the path's existing conversion method; returns whether the victim converted
     * @param advancement the advancement to award on success, or {@code null} for a path without one
     */
    private static void runInfectionPipeline(LivingDeathEvent event, ServerLevel level, LivingEntity victim,
            Player attacker, EntityType<? extends LivingEntity> resultType, BooleanSupplier conversion,
            @Nullable Identifier advancement) {
        if (!ZombieInfectionRules.shouldInfect(
                configuredInfectionChance(gameDifficulty(level.getDifficulty())),
                victim.getRandom().nextDouble())) {
            return;
        }

        if (!EventHooks.canLivingConvert(victim, resultType, timer -> {})) {
            return;
        }

        // Cancellable pre-event after the RNG and canLivingConvert gates but before conversion;
        // cancelling it aborts this infection. Server-side only; isolated via ZombieEventPublisher.
        if (attacker instanceof ServerPlayer serverPlayer
                && ZombieEventPublisher.postCancelable(
                        new ZombieInfectPreEvent(serverPlayer, victim, resultType))) {
            return;
        }

        if (conversion.getAsBoolean()) {
            if (advancement != null && attacker instanceof ServerPlayer serverPlayer) {
                IAmZombieAdvancements.award(serverPlayer, advancement);
            }
            // Observer post-event after successful conversion. Server-side only and isolated.
            if (attacker instanceof ServerPlayer serverPlayer) {
                ZombieEventPublisher.post(new ZombieInfectedEvent(serverPlayer, victim, resultType));
            }
            event.setCanceled(true);
            ZombieLog.debug(() -> "state.infection source=" + BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType())
                    + " target=" + BuiltInRegistries.ENTITY_TYPE.getKey(resultType)
                    + " attacker=" + attacker.getUUID());
        }
    }

    private static double configuredInfectionChance(GameDifficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL ->
                    ZombieInfectionRules.infectionChance(
                            GameDifficulty.PEACEFUL);
            case EASY -> IAmZombieServerConfig.EASY_INFECTION_CHANCE.get();
            case NORMAL -> IAmZombieServerConfig.NORMAL_INFECTION_CHANCE.get();
            case HARD -> IAmZombieServerConfig.HARD_INFECTION_CHANCE.get();
        };
    }

    /**
     * Package-level bridge for {@code ZombieMountEvents.onIncomingDamage}: records the pre-death health-ratio
     * snapshot of a horse about to die to a zombie player. Consumed by {@code tryInfectHorse} above; the map
     * (and its server-stop cleanup) lives here with the rest of the horse infection path.
     */
    static void recordPendingHorseHealthRatio(UUID horseUuid, float preDamageHealthRatio) {
        PENDING_HORSE_HEALTH_RATIOS.put(horseUuid, preDamageHealthRatio);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_HORSE_HEALTH_RATIOS.clear();
    }

    // Creative players are full zombies, so infection does not require survival mode. Only spectators are
    // excluded (they cannot be the killing entity in practice, but keep the guard for parity with the other gates).
    private static boolean isZombiePlayer(Player player) {
        return ZombiePlayerGates.isZombiePlayer(player);
    }

    private static GameDifficulty gameDifficulty(Difficulty difficulty) {
        return Difficulties.toGameDifficulty(difficulty);
    }

    private static boolean convertVillagerToZombieVillager(ServerLevel level, Villager villager, Player player) {
        ZombieVillager zombieVillager = villager.convertTo(
                EntityTypes.ZOMBIE_VILLAGER,
                ConversionParams.single(villager, true, true),
                zombie -> {
                    //? if >=26.2
                    zombie.setVillagerDataFinalized(villager.getVillagerDataFinalized());
                    zombie.finalizeSpawn(
                            level,
                            level.getCurrentDifficultyAt(zombie.blockPosition()),
                            EntitySpawnReason.CONVERSION,
                            new Zombie.ZombieGroupData(false, true)
                    );
                    zombie.setVillagerData(villager.getVillagerData());
                    zombie.setGossips(villager.getGossips().copy());
                    zombie.setTradeOffers(villager.getOffers().copy());
                    zombie.setVillagerXp(villager.getVillagerXp());
                    EventHooks.onLivingConvert(villager, zombie);
                    if (!villager.isSilent()) {
                        level.levelEvent(null, 1026, villager.blockPosition(), 0);
                    }
                }
        );
        if (zombieVillager != null) {
            // The same swing's Sweeping-Edge AoE will clip this freshly spawned kin a moment
            // later (Player.attack -> doSweepAttack, same tick), seeding it with the player as its last attacker.
            // Record a short grace window so the deny-list treats that conversion-swing sweep as non-provoking;
            // genuine later retaliation (a deliberate strike after the window) is preserved.
            ZombieMobTargetingEvents.recordConversionGrace(zombieVillager, player);
        }
        return zombieVillager != null;
    }

    // Mirrors vanilla Pig#thunderHit's pig -> zombified piglin conversion (ConversionParams.single(victim, false, true):
    // keepEquipment=false, preserveCanPickUpLoot=true; node-native default equipment + setPersistenceRequired) and the villager pattern above
    // (conversion levelEvent only). The converted mob is seeded with NO attacker, so the kin zombie player stays
    // ignored from tick one. The one residual provoker is the same swing's Sweeping-Edge sweep, which clips
    // the freshly-spawned kin a moment later in the same Player.attack call; the conversion grace window recorded
    // below (honoured by ZombieMobTargetingEvents) neutralises that -- and because the kin is a NeutralMob, the
    // deny-list also clears the sweep-derived persistent anger so it cannot re-acquire the player after the window.
    // Genuine retaliation still works because a real later strike re-seeds the kin's last attacker (and anger).
    // Works for both Pig and any Piglin/AbstractPiglin victim.
    private static boolean convertToZombifiedPiglin(ServerLevel level, Mob victim, Player player) {
        ZombifiedPiglin zombifiedPiglin = victim.convertTo(
                EntityTypes.ZOMBIFIED_PIGLIN,
                ConversionParams.single(victim, false, true),
                piglin -> {
                    // CROSS_VERSION-ZOMBIFIED-PIGLIN-DEFAULT-EQUIPMENT-API
                    //? if <1.21.11 {
                    /*piglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
                    *///?} else {
                    piglin.populateDefaultEquipmentSlots(victim.getRandom(), level.getCurrentDifficultyAt(piglin.blockPosition()));
                    //?}
                    // Do NOT call finalizeSpawn here (mirrors vanilla Pig#thunderHit, which never does): with null group
                    // data it rolls a spurious ~5% baby (#3), and its unconditional handleAttributes (random
                    // reinforcement/FOLLOW_RANGE modifiers) + Halloween-head roll drift the converted piglin from the
                    // vanilla thunder-hit result (#4). The node-native equipment is already assigned by the seam above
                    // (finalizeSpawn skips equipment on CONVERSION anyway), so dropping the call loses nothing.
                    piglin.setPersistenceRequired();
                    EventHooks.onLivingConvert(victim, piglin);
                    if (!victim.isSilent()) {
                        level.levelEvent(null, 1026, victim.blockPosition(), 0);
                    }
                }
        );
        if (zombifiedPiglin != null) {
            ZombieMobTargetingEvents.recordConversionGrace(zombifiedPiglin, player);
        }
        return zombifiedPiglin != null;
    }

    private static boolean convertHorseToZombieHorse(ServerLevel level, Horse horse, Player owner, Float pendingHorseHealthRatio) {
        ZombieHorse zombieHorse = EntityTypes.ZOMBIE_HORSE.create(level, EntitySpawnReason.CONVERSION);
        if (zombieHorse == null) {
            return false;
        }

        zombieHorse.snapTo(horse.getX(), horse.getY(), horse.getZ(), horse.getYRot(), horse.getXRot());
        zombieHorse.finalizeSpawn(level, level.getCurrentDifficultyAt(horse.blockPosition()), EntitySpawnReason.CONVERSION, null);
        zombieHorse.setTamed(true);
        zombieHorse.setOwner(owner);
        zombieHorse.setPersistenceRequired();
        copyHorseStateToZombieHorse(horse, zombieHorse, pendingHorseHealthRatio);

        level.addFreshEntity(zombieHorse);
        horse.discard();
        level.levelEvent(null, 1026, horse.blockPosition(), 0);
        return true;
    }

    private static void copyHorseStateToZombieHorse(Horse horse, ZombieHorse zombieHorse, Float pendingHorseHealthRatio) {
        zombieHorse.setItemSlot(EquipmentSlot.SADDLE, horse.getItemBySlot(EquipmentSlot.SADDLE).copy());
        zombieHorse.setItemSlot(EquipmentSlot.BODY, horse.getItemBySlot(EquipmentSlot.BODY).copy());
        zombieHorse.setAge(horse.getAge());

        float healthRatio = pendingHorseHealthRatio != null ? pendingHorseHealthRatio : horse.getHealth() / horse.getMaxHealth();
        zombieHorse.setHealth(Math.max(1.0F, zombieHorse.getMaxHealth() * healthRatio));
        if (horse.hasCustomName()) {
            zombieHorse.setCustomName(horse.getCustomName());
            zombieHorse.setCustomNameVisible(horse.isCustomNameVisible());
        }
    }

    // CROSS_VERSION-NAUTILUS-CAPABILITY:infection-converter
    //? if >=1.21.11 {
    private static boolean convertNautilusToZombieNautilus(
            ServerLevel level, Nautilus nautilus, Player owner) {
        ZombieNautilus zombieNautilus = EntityTypes.ZOMBIE_NAUTILUS.create(level, EntitySpawnReason.CONVERSION);
        if (zombieNautilus == null) {
            return false;
        }

        zombieNautilus.snapTo(nautilus.getX(), nautilus.getY(), nautilus.getZ(), nautilus.getYRot(), nautilus.getXRot());
        zombieNautilus.finalizeSpawn(level, level.getCurrentDifficultyAt(nautilus.blockPosition()), EntitySpawnReason.CONVERSION, null);
        zombieNautilus.setTame(true, true);
        zombieNautilus.setOwner(owner);
        zombieNautilus.setPersistenceRequired();
        zombieNautilus.setHealth(zombieNautilus.getMaxHealth());
        // Copy the source's saddle + body armor (mirroring the horse conversion path) instead of minting a fresh
        // vanilla saddle: minting fabricates a saddle for an unsaddled nautilus and destroys any enchanted/renamed
        // saddle the source carried. copy() on an empty slot returns EMPTY, so an unsaddled source stays unsaddled (#2).
        zombieNautilus.setItemSlot(EquipmentSlot.SADDLE, nautilus.getItemBySlot(EquipmentSlot.SADDLE).copy());
        zombieNautilus.setItemSlot(EquipmentSlot.BODY, nautilus.getItemBySlot(EquipmentSlot.BODY).copy());
        if (nautilus.hasCustomName()) {
            zombieNautilus.setCustomName(nautilus.getCustomName());
            zombieNautilus.setCustomNameVisible(nautilus.isCustomNameVisible());
        }

        level.addFreshEntity(zombieNautilus);
        nautilus.discard();
        level.levelEvent(null, 1026, nautilus.blockPosition(), 0);
        return true;
    }
    //?}
}
