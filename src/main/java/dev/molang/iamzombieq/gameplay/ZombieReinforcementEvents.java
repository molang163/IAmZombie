package dev.molang.iamzombieq.gameplay;

import dev.molang.iamzombieq.util.Difficulties;

import dev.molang.iamzombieq.IAmZombieServerConfig;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.ZombieReinforcementRules;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Official zombie-reinforcement wiring for the zombie player: form-matched-undead alert + matching-FORM reinforcement
 * spawn, driven by the core {@code onIncomingDamage} coordinator via {@link #reinforceZombiePlayer}. Owns the
 * per-player {@code REINFORCEMENT_CHANCE} map (stored off-entity because PlayerZombieData has no room), self-cleaning
 * it on logout / server stop.
 */
public final class ZombieReinforcementEvents {
    // Per-player reinforcement chance (vanilla SPAWN_REINFORCEMENTS_CHANCE), lazily rolled once (0-0.1, plus a
    // regional-difficulty-scaled leader bonus) and decayed by -0.05 per successful reinforcement spawn. Stored
    // off-entity (PlayerZombieData has no room). Server-side only; cleared on logout + server stop.
    private static final Map<UUID, Double> REINFORCEMENT_CHANCE = new HashMap<>();

    private ZombieReinforcementEvents() {
    }

    /**
     * Official zombie-reinforcement applied to the zombie PLAYER, fired on each damage-by-living-entity event. Combines
     * two distinct vanilla zombie behaviours: (1) ALERT nearby form-matched undead onto the attacker even without line
     * of sight -- vanilla's {@code HurtByTargetGoal#alertOthers} (HURT_BY_TARGETING.ignoreLineOfSight(), wired via
     * setAlertOthers); like vanilla this now recruits only idle kin (getTarget() == null), with two remaining
     * deviations -- a fixed ~111-block alert box rather than vanilla's live follow-range, and no !isAlliedTo
     * team-exclusion -- and (2) on HARD + {@code doMobSpawning}, vanilla {@code Zombie#hurtServer}'s spawn of
     * matching-FORM reinforcements (capped, mob-cap ignoring). The giant form has no vanilla counterpart and does neither.
     */
    public static void reinforceZombiePlayer(ServerPlayer player, LivingEntity attacker) {
        if (!(player.level() instanceof ServerLevel level) || attacker == player) {
            return;
        }
        if (!IAmZombieServerConfig.REINFORCEMENTS_ENABLED.get()) {
            return;
        }
        ZombieForm form = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state().form();
        if (!ZombieReinforcementRules.hasReinforcementForm(form)) {
            return;
        }
        EntityType<? extends Mob> reinforcementType = reinforcementTypeFor(form);
        if (reinforcementType == null) {
            return;
        }

        alertFormMatchedUndead(level, player, attacker, reinforcementType);
        attemptSpawnReinforcements(level, player, attacker, reinforcementType);
    }

    /**
     * Retarget every alive, form-matched, currently idle undead (getTarget() == null) in the ~111x21x111 alert box onto
     * the attacker, even without line of sight (modelled on vanilla {@code HurtByTargetGoal#alertOthers}, which uses
     * ignoreLineOfSight and likewise only recruits kin whose current target is null, so a kin already fighting is not
     * yanked off; the two remaining deviations are the fixed ~111x21x111 box vs vanilla's live follow-range and the
     * omitted !isAlliedTo team-exclusion). Uses a single class-filtered AABB scan (not a per-block sweep). The zombified
     * piglin (a neutral mob) needs persistent anger established before setTarget; the always-hostile zombie family is
     * retargeted directly.
     */
    private static void alertFormMatchedUndead(ServerLevel level, ServerPlayer player, LivingEntity attacker,
            EntityType<? extends Mob> reinforcementType) {
        AABB area = player.getBoundingBox().inflate(
                ZombieReinforcementRules.ALERT_BOX_INFLATE_XZ,
                ZombieReinforcementRules.ALERT_BOX_INFLATE_Y,
                ZombieReinforcementRules.ALERT_BOX_INFLATE_XZ);
        // Exclude the attacker itself: when a form-matched undead (e.g. a zombie) hurts the zombie player, the
        // attacker is also a reinforcement-type mob in range, and without this guard it would be told to
        // setTarget(itself) -> it attacks itself and dies ("the zombie suicides after hitting me"). Genuine kin
        // still rally onto the attacker; only the attacker is spared from targeting itself.
        for (Mob ally : level.getEntitiesOfClass(Mob.class, area, candidate ->
                candidate != attacker && candidate.getType() == reinforcementType && candidate.isAlive() && candidate.canAttack(attacker) && candidate.getTarget() == null)) {
            if (ally instanceof ZombifiedPiglin piglin) {
                // Establish anger BEFORE setTarget: setTarget fires LivingChangeTargetEvent, and the
                // undead-ignore-zombie-player handler would otherwise null a target that is a zombie player before
                // anger is recorded (group help would no-op).
                piglin.setPersistentAngerTarget(EntityReference.of(attacker));
                piglin.startPersistentAngerTimer();
            }
            ally.setTarget(attacker);
        }
    }

    /**
     * Try to spawn matching-FORM reinforcements for the zombie player, mirroring vanilla {@code Zombie#hurtServer}:
     * only on HARD difficulty with {@code doMobSpawning} enabled, gated by a per-player reinforcement chance
     * (0-0.1, with possible leader bonus), at offsets (0 or +-7..40 on X/Y/Z), requiring a viable surface (vanilla
     * checkSpawnRules(REINFORCEMENT): a dark-enough spot via the probabilistic uniform[0,7] test, not a flat light<=9;
     * solid top, no player within 7, no collision). One successful spawn per damage event; each costs a -0.05 penalty.
     * Reinforcements ignore the mob cap (direct {@code addFreshEntityWithPassengers}).
     */
    private static void attemptSpawnReinforcements(ServerLevel level, ServerPlayer player, LivingEntity attacker,
            EntityType<? extends Mob> reinforcementType) {
        if (!ZombieReinforcementRules.canSpawnReinforcements(gameDifficulty(level.getDifficulty()), level.isSpawningMonsters())) {
            return;
        }
        var random = player.getRandom();
        double chance = playerReinforcementChance(player, level, random);
        if (!ZombieReinforcementRules.reinforcementRollSucceeds(random.nextFloat(), chance)) {
            return;
        }

        Mob reinforcement = reinforcementType.create(level, EntitySpawnReason.REINFORCEMENT);
        if (reinforcement == null) {
            return;
        }
        // A player has no vanilla baby age, so a baby-FORM zombie player must be read from its size state.
        boolean baby = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state().size() == dev.molang.iamzombieq.rules.core.ZombieSize.BABY
                && reinforcement instanceof net.minecraft.world.entity.monster.zombie.Zombie;
        int originX = net.minecraft.util.Mth.floor(player.getX());
        int originY = net.minecraft.util.Mth.floor(player.getY());
        int originZ = net.minecraft.util.Mth.floor(player.getZ());

        int attempts = IAmZombieServerConfig.REINFORCEMENT_SPAWN_ATTEMPTS.get();
        for (int i = 0; i < attempts; i++) {
            int xt = originX + ZombieReinforcementRules.spawnOffset(reinforcementMagnitude(random), reinforcementSign(random));
            int yt = originY + ZombieReinforcementRules.spawnOffset(reinforcementMagnitude(random), reinforcementSign(random));
            int zt = originZ + ZombieReinforcementRules.spawnOffset(reinforcementMagnitude(random), reinforcementSign(random));
            BlockPos spawnPos = new BlockPos(xt, yt, zt);
            // Solid top surface + a dark-enough spot are enforced by the vanilla spawn-placement + spawn-rules checks
            // for the type (checkSpawnRules(REINFORCEMENT) -> the real probabilistic uniform[0,7] darkness test, NOT a
            // flat light ceiling): this live path is the sole spawn-viability gate.
            if (!net.minecraft.world.entity.SpawnPlacements.isSpawnPositionOk(reinforcementType, level, spawnPos)
                    || !net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(reinforcementType, level, EntitySpawnReason.REINFORCEMENT, spawnPos, level.getRandom())) {
                continue;
            }
            reinforcement.setPos(xt, yt, zt);
            if (level.hasNearbyAlivePlayer(xt, yt, zt, ZombieReinforcementRules.MIN_PLAYER_DISTANCE)
                    || !level.isUnobstructed(reinforcement)
                    || !level.noCollision(reinforcement)
                    // Vanilla liquid guard (aquatic-aware): checkSpawnObstruction() is the PUBLIC form of vanilla
                    // Zombie#hurtServer's `canSpawnInLiquids() || !containsAnyLiquid(box)` check (canSpawnInLiquids is
                    // protected on Zombie, inaccessible on the Mob-typed reinforcement). Mob's default rejects any
                    // liquid in the bounding box; Drowned overrides it to allow water — so a non-aquatic reinforcement
                    // is no longer placed partially submerged at a shoreline, while aquatic ones still may (#9).
                    || !reinforcement.checkSpawnObstruction(level)) {
                continue;
            }
            if (reinforcement instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie) {
                zombie.setBaby(baby);
            }
            reinforcement.finalizeSpawn(level, level.getCurrentDifficultyAt(reinforcement.blockPosition()), EntitySpawnReason.REINFORCEMENT, null);
            reinforcement.setTarget(attacker);
            // Reinforcements ignore the mob cap: a direct add (not a natural-spawn-gated one).
            level.addFreshEntityWithPassengers(reinforcement);
            // Apply the caller penalty (-0.05) so chained calls quickly stop spawning, matching vanilla decay.
            applyReinforcementPenalty(player.getUUID());
            break;
        }
    }

    private static int reinforcementMagnitude(net.minecraft.util.RandomSource random) {
        return net.minecraft.util.Mth.nextInt(random,
                ZombieReinforcementRules.REINFORCEMENT_RANGE_MIN,
                ZombieReinforcementRules.REINFORCEMENT_RANGE_MAX);
    }

    private static int reinforcementSign(net.minecraft.util.RandomSource random) {
        return net.minecraft.util.Mth.nextInt(random, -1, 1);
    }

    /**
     * The per-player reinforcement chance (vanilla SPAWN_REINFORCEMENTS_CHANCE). Lazily rolled once per player into a
     * tracked per-UUID map (0-0.1, plus a regional-difficulty-scaled leader bonus of +0.5..0.75 and 40-100 max HP),
     * then decayed by -0.05 per successful spawn. Stored off-entity because PlayerZombieData has no room for it.
     */
    private static double playerReinforcementChance(ServerPlayer player, ServerLevel level, net.minecraft.util.RandomSource random) {
        UUID uuid = player.getUUID();
        Double existing = REINFORCEMENT_CHANCE.get(uuid);
        if (existing != null) {
            return existing;
        }
        double chance = ZombieReinforcementRules.baseReinforcementChance(random.nextDouble());
        // Vanilla leader chance = specialMultiplier (0..1) * 0.05, capping leaders at ~5% on the hardest regional
        // difficulty — matching Zombie#handleAttributes (not the larger effective-difficulty value).
        double regionalDifficulty = level.getCurrentDifficultyAt(player.blockPosition()).getSpecialMultiplier();
        if (ZombieReinforcementRules.isLeader(regionalDifficulty, random.nextFloat())) {
            chance += ZombieReinforcementRules.leaderReinforcementBonus(random.nextDouble());
        }
        REINFORCEMENT_CHANCE.put(uuid, chance);
        return chance;
    }

    private static void applyReinforcementPenalty(UUID uuid) {
        Double current = REINFORCEMENT_CHANCE.get(uuid);
        double base = current != null ? current : 0.0;
        REINFORCEMENT_CHANCE.put(uuid, ZombieReinforcementRules.applyReinforcementPenalty(base));
    }

    private static EntityType<? extends Mob> reinforcementTypeFor(ZombieForm form) {
        return switch (form) {
            case NORMAL -> EntityTypes.ZOMBIE;
            case DROWNED -> EntityTypes.DROWNED;
            case HUSK -> EntityTypes.HUSK;
            case ZOMBIFIED_PIGLIN -> EntityTypes.ZOMBIFIED_PIGLIN;
            case GIANT -> null;
        };
    }

    private static dev.molang.iamzombieq.rules.difficulty.GameDifficulty gameDifficulty(Difficulty difficulty) {
        return Difficulties.toGameDifficulty(difficulty);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Drop the reinforcement chance so a reconnecting player re-rolls a fresh value (and so accumulated -0.05
        // penalties don't persist for the server's lifetime).
        REINFORCEMENT_CHANCE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        REINFORCEMENT_CHANCE.clear();
    }
}
