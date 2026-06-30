package dev.molang.iamzombieq.gameplay;
import dev.molang.iamzombieq.util.Difficulties;

import dev.molang.iamzombieq.IAmZombieConfig;
import dev.molang.iamzombieq.api.event.ZombieInfectPreEvent;
import dev.molang.iamzombieq.api.event.ZombieInfectedEvent;
import dev.molang.iamzombieq.internal.event.ZombieEventPublisher;
import dev.molang.iamzombieq.rules.difficulty.GameDifficulty;
import dev.molang.iamzombieq.rules.ZombieInfectionRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class ZombieInfectionEvents {
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
        }
    }

    private static void tryInfectVillager(LivingDeathEvent event, ServerLevel level, Villager villager, Player player) {
        if (!ZombieInfectionRules.shouldInfect(IAmZombieConfig.configuredInfectionChance(gameDifficulty(level.getDifficulty())), villager.getRandom().nextDouble())) {
            return;
        }

        if (!EventHooks.canLivingConvert(villager, EntityTypes.ZOMBIE_VILLAGER, timer -> {})) {
            return;
        }

        // Phase-1 API: cancellable PRE fire AFTER the RNG + canLivingConvert gates but BEFORE the conversion;
        // cancelling it aborts this infection. Server-side only; isolated via ZombieEventPublisher.
        if (player instanceof ServerPlayer serverPlayer
                && ZombieEventPublisher.postCancelable(
                        new ZombieInfectPreEvent(serverPlayer, villager, EntityTypes.ZOMBIE_VILLAGER))) {
            return;
        }

        if (convertVillagerToZombieVillager(level, villager, player)) {
            awardInfection(player);
            // Phase-1 API: observer POST fire AFTER the successful conversion. Server-side only; isolated.
            if (player instanceof ServerPlayer serverPlayer) {
                ZombieEventPublisher.post(new ZombieInfectedEvent(serverPlayer, villager, EntityTypes.ZOMBIE_VILLAGER));
            }
            event.setCanceled(true);
        }
    }

    // N1: a zombie player that kills a Pig OR any Piglin/AbstractPiglin can infect it into a zombified piglin, mirroring
    // the villager infection (difficulty-scaled chance, EventHooks.canLivingConvert gate, INFECTION advancement). Both
    // source types convert to ZOMBIFIED_PIGLIN, matching vanilla's pig+lightning zombification. Form-gated (see the
    // call site): ONLY a ZOMBIFIED_PIGLIN-form zombie player infects pigs/piglins into zombified piglins (the form is
    // the "kin" of what it spreads); NORMAL/DROWNED/HUSK/GIANT cannot.
    private static void tryInfectIntoZombifiedPiglin(LivingDeathEvent event, ServerLevel level, Mob victim, Player player) {
        if (!ZombieInfectionRules.shouldInfect(IAmZombieConfig.configuredInfectionChance(gameDifficulty(level.getDifficulty())), victim.getRandom().nextDouble())) {
            return;
        }

        if (!EventHooks.canLivingConvert(victim, EntityTypes.ZOMBIFIED_PIGLIN, timer -> {})) {
            return;
        }

        // Phase-1 API: cancellable PRE fire AFTER the RNG + canLivingConvert gates but BEFORE the conversion;
        // cancelling it aborts this infection. Server-side only; isolated via ZombieEventPublisher.
        if (player instanceof ServerPlayer serverPlayer
                && ZombieEventPublisher.postCancelable(
                        new ZombieInfectPreEvent(serverPlayer, victim, EntityTypes.ZOMBIFIED_PIGLIN))) {
            return;
        }

        if (convertToZombifiedPiglin(level, victim, player)) {
            awardInfection(player);
            // Phase-1 API: observer POST fire AFTER the successful conversion. Server-side only; isolated.
            if (player instanceof ServerPlayer serverPlayer) {
                ZombieEventPublisher.post(new ZombieInfectedEvent(serverPlayer, victim, EntityTypes.ZOMBIFIED_PIGLIN));
            }
            event.setCanceled(true);
        }
    }

    private static void awardInfection(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            IAmZombieAdvancements.award(serverPlayer, IAmZombieAdvancements.INFECTION);
        }
    }

    // N6: creative players are full zombies, so the infection no longer requires survival mode. Only spectators are
    // excluded (they cannot be the killing entity in practice, but keep the guard for parity with the other gates).
    private static boolean isZombiePlayer(Player player) {
        return !player.isSpectator();
    }

    private static GameDifficulty gameDifficulty(Difficulty difficulty) {
        return Difficulties.toGameDifficulty(difficulty);
    }

    private static boolean convertVillagerToZombieVillager(ServerLevel level, Villager villager, Player player) {
        ZombieVillager zombieVillager = villager.convertTo(
                EntityTypes.ZOMBIE_VILLAGER,
                ConversionParams.single(villager, true, true),
                zombie -> {
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
            // RC4-sweep (Option B): the SAME swing's Sweeping-Edge AoE will clip this freshly-spawned kin a moment
            // later (Player.attack -> doSweepAttack, same tick), seeding it with the player as its last attacker.
            // Record a short grace window so the deny-list treats that conversion-swing sweep as non-provoking;
            // genuine later retaliation (a deliberate strike after the window) is preserved.
            ZombieMobTargetingEvents.recordConversionGrace(zombieVillager, player);
        }
        return zombieVillager != null;
    }

    // Mirrors vanilla Pig#thunderHit's pig -> zombified piglin conversion (ConversionParams.single(victim, false, true):
    // keepEquipment=false, preserveCanPickUpLoot=true; populateDefaultEquipmentSlots + setPersistenceRequired) and the villager pattern above
    // (conversion levelEvent only). The converted mob is seeded with NO attacker, so the kin zombie player stays
    // IGNORED from tick one (RC4). The one residual provoker is the SAME swing's Sweeping-Edge sweep, which clips
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
                    piglin.populateDefaultEquipmentSlots(victim.getRandom(), level.getCurrentDifficultyAt(piglin.blockPosition()));
                    piglin.finalizeSpawn(
                            level,
                            level.getCurrentDifficultyAt(piglin.blockPosition()),
                            EntitySpawnReason.CONVERSION,
                            null
                    );
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
}
