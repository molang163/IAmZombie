package dev.molang.iamzombieq.gametest;

import com.mojang.authlib.GameProfile;
import dev.molang.iamzombieq.gameplay.GiantPlayerEvents;
import dev.molang.iamzombieq.gameplay.ZombieFormAttributes;
import dev.molang.iamzombieq.gameplay.ZombieSunlightEvents;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.core.ZombieState;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import dev.molang.iamzombieq.state.PlayerZombieData;
import dev.molang.iamzombieq.util.MountCapability;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
//? if >=26.2
import net.minecraft.world.entity.EntityTypes;
//? if <26.2
//import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
//? if >=26.1
import net.neoforged.neoforge.event.enchanting.EnchantedEntityLootEvent;
//? if <26.1
//import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Runtime regression coverage for the mounted-zombie damage-attribution boundary. */
final class MountedZombieKillCreditGameTest {

    private static final float EPSILON = 1.0E-4F;
    private static final Stat<?> MOB_KILLS = Stats.CUSTOM.get(Stats.MOB_KILLS);
    private static final Stat<?> ZOMBIE_KILLS = Stats.ENTITY_KILLED.get(EntityTypes.ZOMBIE);
    private static final Field VEHICLE_FIELD = privateField(Entity.class, "vehicle");
    private static final Method ADD_PASSENGER_METHOD = privateMethod(Entity.class, "addPassenger", Entity.class);

    private MountedZombieKillCreditGameTest() {
    }

    static void run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 base = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        RecordingFakePlayer rider = player(level, base, ZombieForm.NORMAL, ZombieSize.BABY);
        AttributionObserver observer = new AttributionObserver();
        List<String> failures = new ArrayList<>();

        NeoForge.EVENT_BUS.register(observer);
        try {
            verifyIndependentMountedKill(level, base, rider, observer, failures);
            verifyGuaranteedEquipmentDrop(level, base, rider, observer, failures);
            verifyUnmountedZombieControl(level, base, observer, failures);
            verifyGiantStompControl(level, base, observer, failures);
            verifyMountWeaponChain(level, base, rider, observer, failures);
            verifyPlayerDifficultyScaling(level, base, rider, failures);
            verifyPvpAndTeamRules(level, base, rider, failures);
        } finally {
            detach(rider);
            NeoForge.EVENT_BUS.unregister(observer);
        }

        if (!failures.isEmpty()) {
            GameTestAssertions.fail(helper, "LOOT1-FIX1: " + String.join(" | ", failures));
            return;
        }
        helper.succeed();
    }

    private static void verifyIndependentMountedKill(
            ServerLevel level,
            Vec3 base,
            RecordingFakePlayer rider,
            AttributionObserver observer,
            List<String> failures) {
        setState(rider, ZombieForm.NORMAL, ZombieSize.BABY);
        rider.setItemInHand(InteractionHand.MAIN_HAND, enchantedSword(level, Enchantments.LOOTING, 3));

        Zombie mount = activeBigZombieMount(level, base, rider);
        Zombie victim = zombie(level, base.add(0.75, 0.0, 0.0));
        check(failures, victim.getLastHurtByPlayer() == null,
                "precondition: rider must never have attacked the independent-kill victim");
        victim.setHealth(1.0F);

        int mobKillsBefore = rider.statCount(MOB_KILLS);
        int zombieKillsBefore = rider.statCount(ZOMBIE_KILLS);
        //? if >=26.1
        observer.begin(victim);
        //? if <26.1
        //observer.begin(victim, rider.getMainHandItem());
        try {
            mount.setTarget(victim);
            mount.tickCount = 20;
            check(failures, mount.getTarget() == victim, "mounted-kill target precondition was not retained");
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(mount));
            KillObservation result = observer.finish();

            check(failures, !victim.isAlive(), "ridden big zombie did not independently kill the victim");
            check(failures, result.source != null && result.source.is(DamageTypes.MOB_ATTACK),
                    "damage type must remain minecraft:mob_attack");
            check(failures, result.source != null && result.source.getDirectEntity() == mount,
                    "directEntity must be the attacking zombie mount");
            check(failures, result.source != null && result.source.getEntity() == rider,
                    "causingEntity must be the valid player rider");
            check(failures, result.lastHurtByPlayer == rider,
                    "victim.lastHurtByPlayer must be the rider even without a prior rider hit");
            check(failures, result.recentlyHit,
                    "killed_by_player/recentlyHit must be true for an independent mounted kill");
            check(failures, result.lootingQueries > 0 && result.minLootingLevel == 3 && result.maxLootingLevel == 3,
                    "all actual Looting queries must resolve rider Looting III");
            check(failures, result.xpSeen && result.xpPlayer == rider
                            && result.originalXp == 5 && result.droppedXp == 5,
                    "an unequipped adult vanilla Zombie must emit 5 XP attributed to the rider");
            check(failures, rider.statCount(MOB_KILLS) - mobKillsBefore == 1,
                    "rider MOB_KILLS must increase by one");
            check(failures, rider.statCount(ZOMBIE_KILLS) - zombieKillsBefore == 1,
                    "rider ENTITY_KILLED[zombie] must increase by one");
        } finally {
            observer.clear();
            discardMount(rider, mount);
            victim.discard();
        }
    }

    private static void verifyGuaranteedEquipmentDrop(
            ServerLevel level,
            Vec3 base,
            RecordingFakePlayer rider,
            AttributionObserver observer,
            List<String> failures) {
        setState(rider, ZombieForm.NORMAL, ZombieSize.BABY);
        rider.setItemInHand(InteractionHand.MAIN_HAND, enchantedSword(level, Enchantments.LOOTING, 3));

        Zombie mount = activeBigZombieMount(level, base, rider);
        Zombie victim = zombie(level, base.add(0.75, 0.0, 0.0));
        victim.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        victim.setDropChance(EquipmentSlot.HEAD, 1.0F);
        check(failures, Math.abs(victim.getDropChances().byEquipment(EquipmentSlot.HEAD) - 1.0F) < EPSILON,
                "equipment probe drop chance must be exactly 1.0");
        check(failures, !victim.getDropChances().isPreserved(EquipmentSlot.HEAD),
                "equipment probe at 1.0 must not be preserved");
        check(failures, victim.getLastHurtByPlayer() == null,
                "precondition: rider must never have attacked the equipment-drop victim");
        victim.setHealth(1.0F);

        observer.begin(victim);
        try {
            mount.setTarget(victim);
            mount.tickCount = 20;
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(mount));
            KillObservation result = observer.finish();

            check(failures, !victim.isAlive(), "ridden big zombie did not kill the equipment-drop victim");
            check(failures, result.source != null && result.source.getDirectEntity() == mount
                            && result.source.getEntity() == rider,
                    "equipment-drop kill must retain direct=mount and causing=rider");
            check(failures, result.recentlyHit,
                    "equipment-drop victim must pass the killed_by_player/recentlyHit gate");
            check(failures, result.markerEquipmentDropped,
                    "1.0 non-preserved zombie head equipment must pass the player-kill gate");
        } finally {
            observer.clear();
            discardMount(rider, mount);
            victim.discard();
        }
    }

    private static void verifyUnmountedZombieControl(
            ServerLevel level, Vec3 base, AttributionObserver observer, List<String> failures) {
        Zombie attacker = zombie(level, base);
        Zombie victim = zombie(level, base.add(0.75, 0.0, 0.0));
        victim.setHealth(1.0F);
        observer.begin(victim);
        try {
            attacker.doHurtTarget(level, victim);
            KillObservation result = observer.finish();
            check(failures, result.source != null && result.source.is(DamageTypes.MOB_ATTACK),
                    "unmounted Zombie control must retain mob_attack");
            check(failures, result.source != null && result.source.getDirectEntity() == attacker
                            && result.source.getEntity() == attacker,
                    "unmounted Zombie control must keep native direct/causing attribution");
        } finally {
            observer.clear();
            attacker.discard();
            victim.discard();
        }
    }

    private static void verifyGiantStompControl(
            ServerLevel level, Vec3 base, AttributionObserver observer, List<String> failures) {
        RecordingFakePlayer giant = player(level, base, ZombieForm.GIANT, ZombieSize.ADULT);
        Zombie victim = zombie(level, base.add(0.75, 0.0, 0.0));
        victim.setHealth(1.0F);
        observer.begin(victim);
        try {
            giant.tickCount = 20;
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(giant));
            KillObservation result = observer.finish();
            check(failures, result.source != null && result.source.is(DamageTypes.PLAYER_ATTACK),
                    "giant stomp control must retain player_attack");
            check(failures, result.source != null && result.source.getDirectEntity() == giant
                            && result.source.getEntity() == giant,
                    "giant stomp control must retain player direct/causing attribution");
        } finally {
            observer.clear();
            GiantPlayerEvents.cleanupOnFormLeave(giant.getUUID());
            ZombieFormAttributes.clearOnLogout(giant.getUUID());
            ZombieSunlightEvents.clearSunFireWindow(giant.getUUID());
            victim.discard();
        }
    }

    private static void verifyMountWeaponChain(
            ServerLevel level,
            Vec3 base,
            RecordingFakePlayer rider,
            AttributionObserver observer,
            List<String> failures) {
        setState(rider, ZombieForm.NORMAL, ZombieSize.BABY);
        rider.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Zombie nativeAttacker = zombie(level, base);
        Zombie nativeVictim = zombie(level, base.add(0.75, 0.0, 0.0));
        nativeAttacker.setItemSlot(EquipmentSlot.MAINHAND, enchantedSword(level, Enchantments.SMITE, 1));
        int nativeDurabilityBefore = nativeAttacker.getMainHandItem().getDamageValue();
        float nativeHealthBefore = nativeVictim.getHealth();
        observer.begin(nativeVictim);
        try {
            boolean nativeHit = nativeAttacker.doHurtTarget(level, nativeVictim);
            KillObservation nativeResult = observer.finish();
            int nativeDurabilityDelta = nativeAttacker.getMainHandItem().getDamageValue() - nativeDurabilityBefore;
            float nativeHealthLoss = nativeHealthBefore - nativeVictim.getHealth();
            check(failures, nativeHit, "native armed Zombie control attack must succeed");
            check(failures, Math.abs(nativeResult.incomingAmount - 5.5F) < EPSILON,
                    "native Smite I Zombie control must deal 5.5 before reductions");

            observer.clear();
            Zombie mount = activeBigZombieMount(level, base, rider);
            Zombie mountedVictim = zombie(level, base.add(0.75, 0.0, 0.0));
            mount.setItemSlot(EquipmentSlot.MAINHAND, enchantedSword(level, Enchantments.SMITE, 1));
            int mountedDurabilityBefore = mount.getMainHandItem().getDamageValue();
            float mountedHealthBefore = mountedVictim.getHealth();
            observer.begin(mountedVictim);
            try {
                boolean mountedHit = mount.doHurtTarget(level, mountedVictim);
                KillObservation mountedResult = observer.finish();
                int mountedDurabilityDelta = mount.getMainHandItem().getDamageValue() - mountedDurabilityBefore;
                check(failures, mountedHit, "armed mounted Zombie control attack must succeed");
                check(failures, Math.abs(mount.getAttributeValue(Attributes.ATTACK_DAMAGE) - 3.0) < EPSILON,
                        "mount must retain the native Zombie 3.0 attack attribute while armed");
                check(failures, Math.abs(mountedResult.incomingAmount - nativeResult.incomingAmount) < EPSILON,
                        "mounted Smite damage must match the native Mob#doHurtTarget weapon chain");
                check(failures, Math.abs((mountedHealthBefore - mountedVictim.getHealth())
                                - nativeHealthLoss) < EPSILON,
                        "mounted Smite hit must preserve the native inflicted damage");
                check(failures, mountedDurabilityDelta == nativeDurabilityDelta,
                        "mounted weapon durability handling must match native Mob#doHurtTarget");
                check(failures, mount.getLastHurtMob() == mountedVictim,
                        "mounted attack must retain Mob#doHurtTarget post-attack bookkeeping");
                mount.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                check(failures, Math.abs(mount.getAttributeValue(Attributes.ATTACK_DAMAGE) - 3.0) < EPSILON,
                        "removing the mount weapon must retain the native 3.0 attack attribute");
            } finally {
                observer.clear();
                discardMount(rider, mount);
                mountedVictim.discard();
            }
        } finally {
            observer.clear();
            nativeAttacker.discard();
            nativeVictim.discard();
        }
    }

    private static void verifyPlayerDifficultyScaling(
            ServerLevel level, Vec3 base, RecordingFakePlayer rider, List<String> failures) {
        Difficulty originalDifficulty = level.getDifficulty();
        boolean originalPvp = level.getGameRules().get(GameRules.PVP);
        Zombie mount = null;
        try {
            setPvp(level, true);
            setState(rider, ZombieForm.NORMAL, ZombieSize.BABY);
            rider.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            mount = activeBigZombieMount(level, base, rider);
            check(failures, Math.abs(mount.getAttributeValue(Attributes.ATTACK_DAMAGE) - 3.0) < EPSILON,
                    "difficulty control requires an empty-hand 3.0 mount attack");

            Difficulty[] difficulties = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};
            float[] expected = {2.5F, 3.0F, 4.5F};
            for (int i = 0; i < difficulties.length; i++) {
                level.getServer().setDifficulty(difficulties[i], true);
                RecordingFakePlayer target = player(level, base.add(0.75, 0.0, 0.0), ZombieForm.NORMAL, ZombieSize.ADULT);
                float before = target.getHealth();
                boolean hit = mount.doHurtTarget(level, target);
                float actual = before - target.getHealth();
                check(failures, hit, "mount attack against player must succeed on " + difficulties[i]);
                check(failures, Math.abs(actual - expected[i]) < EPSILON,
                        difficulties[i] + " mounted attack must remain " + expected[i] + ", was " + actual);
            }
        } finally {
            if (mount != null) {
                discardMount(rider, mount);
            }
            level.getServer().setDifficulty(originalDifficulty, true);
            setPvp(level, originalPvp);
        }
    }

    private static void verifyPvpAndTeamRules(
            ServerLevel level, Vec3 base, RecordingFakePlayer rider, List<String> failures) {
        boolean originalPvp = level.getGameRules().get(GameRules.PVP);
        Zombie mount = null;
        PlayerTeam team = null;
        try {
            setState(rider, ZombieForm.NORMAL, ZombieSize.BABY);
            rider.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            mount = activeBigZombieMount(level, base, rider);

            setPvp(level, false);
            RecordingFakePlayer pvpTarget = player(level, base.add(0.75, 0.0, 0.0), ZombieForm.NORMAL, ZombieSize.ADULT);
            float pvpHealth = pvpTarget.getHealth();
            boolean pvpHit = mount.doHurtTarget(level, pvpTarget);
            check(failures, !pvpHit && Math.abs(pvpTarget.getHealth() - pvpHealth) < EPSILON,
                    "player-caused mount damage must obey the disabled PvP gamerule");

            setPvp(level, true);
            RecordingFakePlayer teamTarget = player(level, base.add(0.75, 0.0, 0.0), ZombieForm.NORMAL, ZombieSize.ADULT);
            team = level.getScoreboard().addPlayerTeam("loot1" + UUID.randomUUID().toString().substring(0, 8));
            team.setAllowFriendlyFire(false);
            level.getScoreboard().addPlayerToTeam(rider.getScoreboardName(), team);
            level.getScoreboard().addPlayerToTeam(teamTarget.getScoreboardName(), team);
            float teamHealth = teamTarget.getHealth();
            boolean teamHit = mount.doHurtTarget(level, teamTarget);
            check(failures, !teamHit && Math.abs(teamTarget.getHealth() - teamHealth) < EPSILON,
                    "player-caused mount damage must obey same-team friendly-fire=false");
        } finally {
            if (team != null) {
                level.getScoreboard().removePlayerTeam(team);
            }
            if (mount != null) {
                discardMount(rider, mount);
            }
            setPvp(level, originalPvp);
        }
    }

    private static void setPvp(ServerLevel level, boolean enabled) {
        //? if >=1.21.11 {
        level.getGameRules().set(GameRules.PVP, enabled, level.getServer());
        //?}
        //? if >=1.21.10 && <1.21.11 {
        /*level.getServer().getGameRules().getRule(GameRules.RULE_PVP).set(enabled, level.getServer());
        *///?}
        //? if <1.21.10 {
        /*level.getServer().setPvpAllowed(enabled);
        *///?}
    }

    private static Zombie activeBigZombieMount(ServerLevel level, Vec3 position, RecordingFakePlayer rider) {
        detach(rider);
        Zombie mount = zombie(level, position);
        forcePassenger(rider, mount);
        if (MountCapability.activeFor(mount).orElse(null) != MountCapability.BIG_ZOMBIE) {
            throw new IllegalStateException("test did not establish a valid BIG_ZOMBIE riding state");
        }
        return mount;
    }

    private static Zombie zombie(ServerLevel level, Vec3 position) {
        Zombie zombie = new Zombie(level);
        zombie.setNoAi(true);
        zombie.setBaby(false);
        zombie.snapTo(position.x, position.y, position.z, 0.0F, 0.0F);
        if (!level.addFreshEntity(zombie)) {
            throw new IllegalStateException("could not add GameTest Zombie");
        }
        return zombie;
    }

    private static RecordingFakePlayer player(
            ServerLevel level, Vec3 position, ZombieForm form, ZombieSize size) {
        String name = "loot1-" + UUID.randomUUID().toString().substring(0, 8);
        RecordingFakePlayer player = new RecordingFakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerable(false);
        player.getAbilities().invulnerable = false;
        player.snapTo(position.x, position.y, position.z, 0.0F, 0.0F);
        setState(player, form, size);
        return player;
    }

    private static void setState(RecordingFakePlayer player, ZombieForm form, ZombieSize size) {
        player.setData(IAmZombieAttachments.PLAYER_ZOMBIE,
                PlayerZombieData.DEFAULT.withState(new ZombieState(form, size)));
    }

    private static ItemStack enchantedSword(
            ServerLevel level,
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> enchantment,
            int levelValue) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), levelValue);
        return sword;
    }

    private static void forcePassenger(RecordingFakePlayer rider, Zombie mount) {
        try {
            VEHICLE_FIELD.set(rider, mount);
            ADD_PASSENGER_METHOD.invoke(mount, rider);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not establish the GameTest passenger relation", exception);
        }
    }

    private static void detach(RecordingFakePlayer rider) {
        if (rider.isPassenger()) {
            rider.stopRiding();
        }
    }

    private static void discardMount(RecordingFakePlayer rider, Zombie mount) {
        detach(rider);
        mount.discard();
    }

    private static Field privateField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Method privateMethod(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void check(List<String> failures, boolean condition, String message) {
        if (!condition) {
            failures.add(message);
        }
    }

    private static final class RecordingFakePlayer extends FakePlayer {
        private final Map<Stat<?>, Integer> awardedStats = new HashMap<>();

        RecordingFakePlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public void awardStat(Stat<?> stat, int amount) {
            awardedStats.merge(stat, amount, Integer::sum);
        }

        @Override
        public boolean canHarmPlayer(Player attacker) {
            if (!level().isPvpAllowed()) {
                return false;
            }
            Team team = getTeam();
            Team attackerTeam = attacker.getTeam();
            return team == null || !team.isAlliedTo(attackerTeam) || team.isAllowFriendlyFire();
        }

        @Override
        public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
            return false;
        }

        int statCount(Stat<?> stat) {
            return awardedStats.getOrDefault(stat, 0);
        }
    }

    private static final class AttributionObserver {
        private UUID victimId;
        private KillObservation active;

        void begin(Entity victim) {
            this.victimId = victim.getUUID();
            this.active = new KillObservation();
        }

        //? if <26.1 {
        /*void begin(Entity victim, ItemStack lootingStack) {
            begin(victim);
            active.lootingStack = lootingStack;
        }
        *///?}

        KillObservation finish() {
            if (active == null) {
                throw new IllegalStateException("attribution observer was not active");
            }
            return active;
        }

        void clear() {
            victimId = null;
            active = null;
        }

        @SubscribeEvent
        public void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (matches(event.getEntity())) {
                active.source = event.getSource();
                active.incomingAmount = event.getOriginalAmount();
            }
        }

        @SubscribeEvent
        public void onDeath(LivingDeathEvent event) {
            if (matches(event.getEntity())) {
                active.source = event.getSource();
                active.lastHurtByPlayer = event.getEntity().getLastHurtByPlayer();
            }
        }

        @SubscribeEvent
        public void onDrops(LivingDropsEvent event) {
            if (matches(event.getEntity())) {
                active.recentlyHit = event.isRecentlyHit();
                active.markerEquipmentDropped = event.getDrops().stream()
                        .anyMatch(drop -> drop.getItem().is(Items.CARVED_PUMPKIN));
            }
        }

        @SubscribeEvent
        public void onExperience(LivingExperienceDropEvent event) {
            if (matches(event.getEntity())) {
                active.xpSeen = true;
                active.xpPlayer = event.getAttackingPlayer();
                active.originalXp = event.getOriginalExperience();
                active.droppedXp = event.getDroppedExperience();
            }
        }

        //? if >=26.1 {
        @SubscribeEvent
        public void onEnchantedLoot(EnchantedEntityLootEvent event) {
            if (matches(event.getEntity()) && event.getEnchantment().is(Enchantments.LOOTING)) {
                active.lootingQueries++;
                active.minLootingLevel = Math.min(active.minLootingLevel, event.getEnchantmentLevel());
                active.maxLootingLevel = Math.max(active.maxLootingLevel, event.getEnchantmentLevel());
            }
        }
        //?} else {
        /*@SubscribeEvent
        public void onLegacyEnchantmentLevel(GetEnchantmentLevelEvent event) {
            var target = event.getTargetEnchant();
            if (active != null
                    && event.getStack() == active.lootingStack
                    && target != null
                    && target.is(Enchantments.LOOTING)) {
                int level = event.getEnchantments().getLevel(target);
                active.lootingQueries++;
                active.minLootingLevel = Math.min(active.minLootingLevel, level);
                active.maxLootingLevel = Math.max(active.maxLootingLevel, level);
            }
        }
        *///?}

        private boolean matches(Entity entity) {
            return active != null && victimId != null && victimId.equals(entity.getUUID());
        }
    }

    private static final class KillObservation {
        DamageSource source;
        Player lastHurtByPlayer;
        Player xpPlayer;
        float incomingAmount = Float.NaN;
        boolean recentlyHit;
        boolean markerEquipmentDropped;
        boolean xpSeen;
        int originalXp;
        int droppedXp;
        int lootingQueries;
        int minLootingLevel = Integer.MAX_VALUE;
        int maxLootingLevel = Integer.MIN_VALUE;
        //? if <26.1
        //ItemStack lootingStack;
    }
}
