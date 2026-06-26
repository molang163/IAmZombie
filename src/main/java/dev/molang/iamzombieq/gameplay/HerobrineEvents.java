package dev.molang.iamzombieq.gameplay;
import dev.molang.iamzombieq.util.ModIds;

import dev.molang.iamzombieq.IAmZombieConfig;
import dev.molang.iamzombieq.IAmZombieEntities;
import dev.molang.iamzombieq.entity.HerobrineEntity;
import dev.molang.iamzombieq.rules.herobrine.HerobrineEncounter;
import dev.molang.iamzombieq.rules.herobrine.HerobrineRules;
import dev.molang.iamzombieq.state.HerobrineEncounterState;
import dev.molang.iamzombieq.state.HerobrineRespawnSnapshot;
import dev.molang.iamzombieq.state.IAmZombieAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class HerobrineEvents {
    private static final double NEARBY_HEROBRINE_RANGE = 64.0;
    private static final double OMEN_LIGHT_RADIUS = 16.0;

    // Custom damage type backing the Herobrine kill so the death screen + chat show the
    // `death.attack.iamzombieq.herobrine` message (mirrors the sunlight damage type in
    // ZombiePlayerEvents). Defined in data/iamzombieq/damage_type/herobrine.json.
    private static final ResourceKey<DamageType> HEROBRINE_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ModIds.id("herobrine")
    );

    // Snapshots for a real-death-then-respawn-in-place flow, keyed by player UUID. Written just
    // before the lethal hit (so the actual death drops nothing), consumed across the death/clone/
    // respawn lifecycle. NOT cleared on logout so a player who quits at the death screen is still
    // restored on relogin + respawn. Server-thread only.
    private static final Map<UUID, PendingRespawn> PENDING_RESPAWNS = new HashMap<>();

    // Per-player Herobrine dread state (OBSERVATION → ESCALATION → LETHAL arc) and reversible omen
    // restorations were formerly in-memory maps here; both are now DURABLE — dread as the per-player
    // HEROBRINE_ENCOUNTER attachment (survives logout/restart + the player's own death), omen as the
    // per-level OmenLightsSavedData (extinguished blocks restore after a server restart).

    // Number of live HerobrineEntity instances currently in the world. Incremented from
    // EntityJoinLevelEvent (so ANY spawn — natural cave spawn, /summon, or otherwise — arms the
    // per-tick gaze scan) and decremented via EntityLeaveLevelEvent so discard/death/chunk-unload
    // are all covered robustly. Reset on server stop. Server-thread only.
    private static int liveHerobrineCount = 0;

    private HerobrineEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) {
            return;
        }

        ServerLevel level = player.level();
        if (liveHerobrineCount > 0) {
            handleGaze(player, level);
        }
        maybeSpawnNear(player, level);
        restoreExpiredOmenLights(level);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof HerobrineEntity herobrine) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        event.setCanceled(true);
        handleEncounter(player, herobrine);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        // Any projectile striking Herobrine — arrow, trident, snowball, egg, AND thrown splash /
        // lingering potions (ThrownPotion is a Projectile) — counts as an interaction if its owner
        // is a server player. Resolve the entity hit, confirm it's Herobrine, resolve the owner,
        // then run the encounter and cancel so the projectile's normal impact never processes.
        HitResult ray = event.getRayTraceResult();
        if (ray.getType() != HitResult.Type.ENTITY || !(ray instanceof EntityHitResult entityHit)) {
            return;
        }
        if (!(entityHit.getEntity() instanceof HerobrineEntity herobrine)) {
            return;
        }
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide()) {
            return;
        }
        if (!(projectile.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        event.setCanceled(true);
        handleEncounter(player, herobrine);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof HerobrineEntity) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget() instanceof HerobrineEntity) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // On a real death the server builds a brand-new ServerPlayer; this is where we move the
        // snapshotted inventory + XP onto the NEW player so the encounter death "keeps inventory"
        // (the old player's inventory was already cleared before the kill, so vanilla drops
        // nothing). Restore here rather than on respawn so the data is present the instant the new
        // player exists.
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }
        // Carry the per-player dread state across the player's OWN death so "veteran forever"
        // (escalatedBefore) and accumulated sightings/timings survive the respawn. The attachment is
        // not .copyOnDeath(), so read it off the original (dead) player and re-set it on the new one,
        // matching the PLAYER_ZOMBIE/HEROBRINE_PENDING_RESPAWN manual-carry pattern.
        HerobrineEncounterState carried = event.getOriginal().getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER);
        newPlayer.setData(IAmZombieAttachments.HEROBRINE_ENCOUNTER, new HerobrineEncounterState(
                carried.sightings, carried.lastSightingTick, carried.lastLethalTick, carried.escalatedBefore));
        PendingRespawn pending = PENDING_RESPAWNS.get(newPlayer.getUUID());
        if (pending == null) {
            // Fallback: the in-memory map was cleared by a server stop while the player was at the
            // death screen. The durable attachment rode along on the ORIGINAL (dead) player's NBT, so
            // recover it from there. EMPTY means there is genuinely no pending snapshot → no-op.
            HerobrineRespawnSnapshot snapshot =
                    event.getOriginal().getData(IAmZombieAttachments.HEROBRINE_PENDING_RESPAWN);
            if (!snapshot.isPresent()) {
                return;
            }
            pending = fromSnapshot(snapshot);
            // Carry the snapshot onto the NEW player so onPlayerRespawn can read the death position
            // after the original player is gone (the in-memory entry is still absent on this path).
            newPlayer.setData(IAmZombieAttachments.HEROBRINE_PENDING_RESPAWN, snapshot);
        }
        restoreInventory(newPlayer, pending);
        restoreExperience(newPlayer, pending);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // After the death screen, teleport the (new) player back to exactly where they died,
        // facing the same way, so the encounter death reads as "respawn in place" without ever
        // touching the player's bed/world spawn. Consume the pending entry only here, once the
        // whole death → clone → respawn lifecycle is complete.
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PendingRespawn pending = PENDING_RESPAWNS.remove(player.getUUID());
        if (pending == null) {
            // Fallback: the in-memory entry was lost to a server stop; the durable snapshot was copied
            // onto the new player in onPlayerClone, so read the death position from there instead.
            HerobrineRespawnSnapshot snapshot = player.getData(IAmZombieAttachments.HEROBRINE_PENDING_RESPAWN);
            if (!snapshot.isPresent()) {
                return;
            }
            pending = fromSnapshot(snapshot);
        }
        player.teleportTo(player.level(), pending.position().x, pending.position().y, pending.position().z,
                Set.of(), pending.yRot(), pending.xRot(), true);
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
        player.resetFallDistance();
        // Clear the durable mirror so no stale snapshot persists once the whole death → clone →
        // respawn lifecycle has completed (covers both the normal and the post-server-stop path).
        player.setData(IAmZombieAttachments.HEROBRINE_PENDING_RESPAWN, HerobrineRespawnSnapshot.EMPTY);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // Increment for ANY HerobrineEntity entering a server level — natural cave spawn, /summon,
        // or chunk-load — so a live Herobrine ALWAYS arms the per-tick gaze scan (the root cause of
        // "looking does nothing, only attack works" was that the count was bumped only at the
        // natural-spawn site). EntityLeaveLevelEvent decrements symmetrically.
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof HerobrineEntity) {
            liveHerobrineCount++;
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        // Robustly covers discard (gaze/attack/projectile death), entity death, and chunk-unload.
        // Filter to the server level so the count stays a pure server-side gate matching the join
        // increment.
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof HerobrineEntity && liveHerobrineCount > 0) {
            liveHerobrineCount--;
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        liveHerobrineCount = 0;
        // Dread (HEROBRINE_ENCOUNTER attachment) and omen restorations (OmenLightsSavedData) are now
        // durable, so they are NOT cleared here — they persist across restart by design. Only the
        // transient in-memory PENDING_RESPAWNS map is cleared (its durable mirror rides player NBT).
        PENDING_RESPAWNS.clear();
    }

    private static void handleGaze(ServerPlayer player, ServerLevel level) {
        if (player.tickCount % 4 != 0) {
            return;
        }
        AABB area = player.getBoundingBox().inflate(HerobrineRules.GAZE_DISTANCE);
        for (HerobrineEntity herobrine : level.getEntitiesOfClass(HerobrineEntity.class, area, Entity::isAlive)) {
            double distance = player.distanceTo(herobrine);
            double lookDot = lookDot(player, herobrine);
            boolean hasLineOfSight = player.hasLineOfSight(herobrine);
            if (HerobrineRules.isGazingAtHerobrine(lookDot, hasLineOfSight, distance)) {
                handleEncounter(player, herobrine);
                return;
            }
        }
    }

    private static double lookDot(Player player, HerobrineEntity herobrine) {
        Vec3 toHerobrine = herobrine.getEyePosition().subtract(player.getEyePosition());
        if (toHerobrine.lengthSqr() <= 0.0001) {
            return 1.0;
        }
        return player.getViewVector(1.0F).normalize().dot(toHerobrine.normalize());
    }

    /**
     * Resolve a gaze/attack/projectile against the per-player dread arc: in non-lethal phases
     * Herobrine vanishes and a sighting is recorded (possibly upgrading the phase + emitting a
     * cue); in the LETHAL phase the real encounter death runs. With the default config thresholds
     * (escalation = lethal = 0) every interaction is lethal on the first encounter.
     */
    private static void handleEncounter(ServerPlayer player, HerobrineEntity herobrine) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        long now = level.getGameTime();
        int escalationSightings = IAmZombieConfig.HEROBRINE_ESCALATION_SIGHTINGS.get();
        int lethalSightings = IAmZombieConfig.HEROBRINE_LETHAL_SIGHTINGS.get();
        long memoryWindow = IAmZombieConfig.HEROBRINE_MEMORY_WINDOW_TICKS.get();
        long lethalCooldown = IAmZombieConfig.HEROBRINE_LETHAL_COOLDOWN_TICKS.get();

        HerobrineEncounterState state = player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER);
        // Memory decay: a sighting that aged out of the window resets the accumulated sightings,
        // but NOT escalatedBefore — once Herobrine has killed you it stays lethal to you (a veteran
        // is marked for good), matching the documented "veteran immediately lethal again" rule.
        if (state.sightings > 0 && HerobrineEncounter.isSightingExpired(now, state.lastSightingTick, memoryWindow)) {
            state.sightings = 0;
        }

        HerobrineEncounter.Phase phase =
                HerobrineEncounter.phaseFor(state.sightings, state.escalatedBefore, escalationSightings, lethalSightings);

        boolean lethal = HerobrineEncounter.isLethal(phase)
                && !HerobrineEncounter.isOnLethalCooldown(now, state.lastLethalTick, lethalCooldown);

        if (lethal) {
            triggerEncounterDeath(player, herobrine);
            state.lastLethalTick = now;
            state.escalatedBefore = true;
            // Re-set the mutated attachment so the change is persisted (the attachment stores a
            // reference, but setData marks the holder dirty for serialization).
            player.setData(IAmZombieAttachments.HEROBRINE_ENCOUNTER, state);
            return;
        }

        // Non-lethal sighting: record it, vanish, and announce any phase upgrade.
        HerobrineEncounter.Phase before = phase;
        state.sightings++;
        state.lastSightingTick = now;
        // Re-set the mutated attachment so the change is persisted (see lethal branch above).
        player.setData(IAmZombieAttachments.HEROBRINE_ENCOUNTER, state);
        HerobrineEncounter.Phase after =
                HerobrineEncounter.phaseFor(state.sightings, state.escalatedBefore, escalationSightings, lethalSightings);
        herobrine.discard();

        HerobrineEncounter.TransitionCue cue = HerobrineEncounter.phaseTransitionCue(before, after);
        if (cue != null) {
            // Deliver to the action-bar overlay (overlay = true) so the "threat is escalating"
            // cue is always visible — overlay messages bypass chat-visibility HIDDEN, unlike a
            // plain system chat message which a player who disabled chat would never see.
            player.sendSystemMessage(Component.translatable(cue.subtitleKey()), true);
        }
    }

    private static void maybeSpawnNear(ServerPlayer player, ServerLevel level) {
        int interval = IAmZombieConfig.HEROBRINE_CAVE_CHECK_INTERVAL_TICKS.get();
        if (interval <= 0 || player.tickCount % interval != 0) {
            return;
        }

        boolean playerInCave = isEligibleCavePlayer(player, level);
        boolean noNearbyHerobrine = level.getEntitiesOfClass(
                HerobrineEntity.class,
                player.getBoundingBox().inflate(NEARBY_HEROBRINE_RANGE),
                Entity::isAlive
        ).isEmpty();
        if (!HerobrineRules.shouldAttemptCaveSpawn(
                player.getRandom().nextDouble(),
                IAmZombieConfig.HEROBRINE_CAVE_SPAWN_CHANCE.get(),
                playerInCave,
                noNearbyHerobrine
        )) {
            return;
        }

        findSpawnPosition(player, level).ifPresent(pos -> spawnHerobrine(level, player, pos));
    }

    private static boolean isEligibleCavePlayer(ServerPlayer player, ServerLevel level) {
        BlockPos pos = player.blockPosition();
        return level.dimension() == Level.OVERWORLD
                && pos.getY() < level.getSeaLevel() - 8
                && !level.canSeeSky(pos)
                && !player.isSpectator();
    }

    private static Optional<BlockPos> findSpawnPosition(ServerPlayer player, ServerLevel level) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
            int distance = 12 + player.getRandom().nextInt(12);
            int dx = (int) Math.round(Math.cos(angle) * distance);
            int dz = (int) Math.round(Math.sin(angle) * distance);
            int dy = player.getRandom().nextInt(7) - 3;
            BlockPos base = player.blockPosition().offset(dx, dy, dz);
            for (int y = -4; y <= 4; y++) {
                BlockPos candidate = base.offset(0, y, 0);
                if (canStandAt(level, candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean canStandAt(ServerLevel level, BlockPos pos) {
        return level.isInWorldBounds(pos)
                && level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && !level.getBlockState(pos.below()).isAir();
    }

    private static void spawnHerobrine(ServerLevel level, ServerPlayer player, BlockPos pos) {
        HerobrineEntity herobrine = IAmZombieEntities.HEROBRINE.get().create(level, EntitySpawnReason.EVENT);
        if (herobrine == null) {
            return;
        }
        herobrine.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        herobrine.lookAt(player, 180.0F, 180.0F);

        // Publish the player's current phase onto the spawned entity so the client can gate the
        // phase-scaled heartbeat (HB-AUDIO-HEARTBEAT) with zero new packets.
        HerobrineEncounter.Phase phase = currentPhase(player, level);
        herobrine.setEncounterPhase(phase);

        // The live-count gate is armed by onEntityJoinLevel when this entity enters the level, so
        // any spawn path (cave spawn here, /summon, etc.) consistently arms the gaze scan.
        level.addFreshEntity(herobrine);

        // HB-OMEN: phase-scaled, reversible environmental dread on spawn.
        playOmen(level, player, phase);
    }

    private static HerobrineEncounter.Phase currentPhase(ServerPlayer player, ServerLevel level) {
        HerobrineEncounterState state = player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER);
        int escalationSightings = IAmZombieConfig.HEROBRINE_ESCALATION_SIGHTINGS.get();
        int lethalSightings = IAmZombieConfig.HEROBRINE_LETHAL_SIGHTINGS.get();
        long memoryWindow = IAmZombieConfig.HEROBRINE_MEMORY_WINDOW_TICKS.get();
        long now = level.getGameTime();
        int sightings = state.sightings;
        boolean escalatedBefore = state.escalatedBefore;
        if (sightings > 0 && HerobrineEncounter.isSightingExpired(now, state.lastSightingTick, memoryWindow)) {
            sightings = 0;
        }
        return HerobrineEncounter.phaseFor(sightings, escalatedBefore, escalationSightings, lethalSightings);
    }

    /**
     * HB-OMEN: phase-scaled, reversible omen. Extinguishes up to {@code litBlocks} blocks that
     * carry the vanilla {@code LIT} blockstate (candles, campfires, furnaces, redstone lamps, …)
     * within {@link #OMEN_LIGHT_RADIUS} — recorded in {@link OmenLightsSavedData} and restored after
     * the omen duration — and
     * plays a small number of phantom footstep sounds toward the player.
     */
    private static void playOmen(ServerLevel level, ServerPlayer player, HerobrineEncounter.Phase phase) {
        if (!IAmZombieConfig.HEROBRINE_OMEN_ENABLED.get()) {
            return;
        }

        HerobrineEncounter.OmenIntensity intensity = HerobrineEncounter.omenIntensityFor(phase);
        int maxDuration = IAmZombieConfig.HEROBRINE_OMEN_DURATION_TICKS.get();
        long restoreAt = level.getGameTime() + Math.min(intensity.durationTicks(), maxDuration);

        extinguishLitBlocks(level, player.blockPosition(), intensity.litBlocks(), restoreAt);

        // Phantom footsteps: one-off vanilla step sounds at the player's position, no source.
        int footsteps = Math.max(0, intensity.footsteps());
        for (int i = 0; i < footsteps; i++) {
            double offset = 0.6 + i * 0.4;
            Vec3 toward = player.position().add((player.getRandom().nextDouble() - 0.5) * offset, 0.0,
                    (player.getRandom().nextDouble() - 0.5) * offset);
            float pitch = 0.7F + player.getRandom().nextFloat() * 0.2F;
            level.playSound(null, toward.x, toward.y, toward.z,
                    SoundEvents.STONE_STEP, SoundSource.HOSTILE, 0.8F, pitch);
        }
    }

    private static void extinguishLitBlocks(ServerLevel level, BlockPos origin, int max, long restoreAt) {
        if (max <= 0) {
            return;
        }
        OmenLightsSavedData data = level.getDataStorage().computeIfAbsent(OmenLightsSavedData.TYPE);
        int radius = (int) OMEN_LIGHT_RADIUS;
        int extinguished = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        // Deterministic spiral-ish scan from the origin outward; cheap and reversible.
        for (int r = 1; r <= radius && extinguished < max; r++) {
            for (int dx = -r; dx <= r && extinguished < max; dx++) {
                for (int dy = -r; dy <= r && extinguished < max; dy++) {
                    for (int dz = -r; dz <= r && extinguished < max; dz++) {
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) {
                            continue; // shell only — avoids rescanning inner positions
                        }
                        cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        BlockPos pos = cursor.immutable();
                        if (data.getMap().containsKey(pos)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(pos);
                        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
                            data.put(pos, state, restoreAt);
                            level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, false));
                            extinguished++;
                        }
                    }
                }
            }
        }
    }

    private static void restoreExpiredOmenLights(ServerLevel level) {
        // Per-level SavedData: an Overworld omen lives only in the Overworld's data storage, so the
        // old per-dimension filtering is now automatic (this runs on the ticking player's level, and
        // computeIfAbsent lazily loads the SavedData from disk on the first post-restart tick).
        OmenLightsSavedData data = level.getDataStorage().computeIfAbsent(OmenLightsSavedData.TYPE);
        if (data.getMap().isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        List<BlockPos> due = new ArrayList<>();
        for (Map.Entry<BlockPos, OmenLightsSavedData.OmenLight> entry : data.getMap().entrySet()) {
            if (now >= entry.getValue().restoreAt()) {
                due.add(entry.getKey());
            }
        }
        for (BlockPos pos : due) {
            OmenLightsSavedData.OmenLight light = data.getMap().get(pos);
            if (light == null) {
                continue;
            }
            BlockState current = level.getBlockState(pos);
            // Only relight if it's still the same unlit block we dimmed (player may have changed it).
            if (current.getBlock() == light.original().getBlock()
                    && current.hasProperty(BlockStateProperties.LIT)
                    && !current.getValue(BlockStateProperties.LIT)) {
                level.setBlockAndUpdate(pos, current.setValue(BlockStateProperties.LIT, true));
            }
            // Drop the entry whether or not we relit (restored or expired/stale) so it isn't retried.
            data.remove(pos);
        }
    }

    /**
     * Run the lethal encounter as a REAL death with respawn-in-place and kept inventory:
     * <ol>
     *   <li>play the WARDEN_ROAR stinger;</li>
     *   <li>snapshot death position/rotation, a deep copy of the full inventory (main + armor +
     *       offhand), and XP into {@link #PENDING_RESPAWNS};</li>
     *   <li>CLEAR the live inventory so the impending vanilla death drops nothing;</li>
     *   <li>deal lethal {@code iamzombieq:herobrine} damage and let the player actually die — the
     *       death screen + chat show {@code death.attack.iamzombieq.herobrine} from the custom
     *       damage type (exactly like the sunlight death message).</li>
     * </ol>
     * {@link #onPlayerClone} restores the inventory + XP onto the new player and
     * {@link #onPlayerRespawn} teleports them back to the death spot.
     */
    private static void triggerEncounterDeath(ServerPlayer player, HerobrineEntity herobrine) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        // HB-JOLT: a vanilla stinger right before the lethal kill (the client vignette/shake is
        // driven by IAmZombieClient reacting to this same sound + proximity).
        if (IAmZombieConfig.HEROBRINE_JOLT_ENABLED.get()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.0F, 0.6F);
        }

        PendingRespawn pending = new PendingRespawn(
                player.position(),
                player.getYRot(),
                player.getXRot(),
                snapshotInventory(player),
                player.experienceLevel,
                player.experienceProgress,
                player.totalExperience
        );
        PENDING_RESPAWNS.put(player.getUUID(), pending);

        // Durable mirror of the SAME pre-clear snapshot, written onto the dying player's attachment so
        // it serializes with the player NBT. Read ONLY as a fallback in onPlayerClone/onPlayerRespawn
        // when the in-memory PENDING_RESPAWNS entry is gone after a clean server stop at the death
        // screen. Built from `pending` (already deep-copied via snapshotInventory above), so it reuses
        // the exact pre-clear data — do NOT read the inventory after the clear below.
        player.setData(IAmZombieAttachments.HEROBRINE_PENDING_RESPAWN, toSnapshot(pending));

        // Clear the live inventory BEFORE the kill so the real death drops nothing; the snapshot
        // above is restored onto the respawned player.
        player.getInventory().clearContent();

        DamageSource source = level.damageSources().source(HEROBRINE_DAMAGE);
        player.hurtServer(level, source, Float.MAX_VALUE);

        // The iamzombieq:herobrine damage type is tagged bypasses_invulnerability, so this kills
        // even in Creative and through a totem. Safety net: if the player somehow still survived
        // (e.g. Resistance V via command), undo the pre-kill inventory clear so nothing is lost
        // (the live inventory menu re-syncs to the client on the next server tick).
        if (player.isAlive()) {
            PendingRespawn aborted = PENDING_RESPAWNS.remove(player.getUUID());
            if (aborted != null) {
                restoreInventory(player, aborted);
            }
            // Drop the durable mirror too so a survived kill leaves no stale snapshot to recover from.
            player.setData(IAmZombieAttachments.HEROBRINE_PENDING_RESPAWN, HerobrineRespawnSnapshot.EMPTY);
        }
        herobrine.discard();
    }

    /** Deep copy of every inventory slot (main + armor + offhand) so it survives the death. */
    private static List<ItemStack> snapshotInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> copy = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            copy.add(inventory.getItem(slot).copy());
        }
        return copy;
    }

    private static void restoreInventory(ServerPlayer player, PendingRespawn pending) {
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        List<ItemStack> snapshot = pending.inventory();
        for (int slot = 0; slot < inventory.getContainerSize() && slot < snapshot.size(); slot++) {
            inventory.setItem(slot, snapshot.get(slot).copy());
        }
    }

    private static void restoreExperience(ServerPlayer player, PendingRespawn pending) {
        // Mirror vanilla ServerPlayer#restoreFrom: assign the XP fields directly. The respawn
        // packet resyncs the XP bar to the client, so no explicit resend is needed.
        player.experienceLevel = pending.experienceLevel();
        player.experienceProgress = pending.experienceProgress();
        player.totalExperience = pending.totalExperience();
    }

    /**
     * Convert the in-memory {@link PendingRespawn} into the durable {@link HerobrineRespawnSnapshot}
     * stored in the attachment. Reuses the already deep-copied inventory list as-is (the snapshot
     * serializer copies on restore), preserving the exact slot mapping.
     */
    private static HerobrineRespawnSnapshot toSnapshot(PendingRespawn pending) {
        return new HerobrineRespawnSnapshot(
                true,
                pending.position().x,
                pending.position().y,
                pending.position().z,
                pending.yRot(),
                pending.xRot(),
                pending.inventory(),
                pending.experienceLevel(),
                pending.experienceProgress(),
                pending.totalExperience()
        );
    }

    /**
     * Rebuild a {@link PendingRespawn} from a recovered durable {@link HerobrineRespawnSnapshot} so
     * the existing restoreInventory/restoreExperience/teleport paths can consume it unchanged.
     */
    private static PendingRespawn fromSnapshot(HerobrineRespawnSnapshot snapshot) {
        return new PendingRespawn(
                new Vec3(snapshot.x(), snapshot.y(), snapshot.z()),
                snapshot.yRot(),
                snapshot.xRot(),
                snapshot.inventory(),
                snapshot.experienceLevel(),
                snapshot.experienceProgress(),
                snapshot.totalExperience()
        );
    }

    private record PendingRespawn(Vec3 position, float yRot, float xRot, List<ItemStack> inventory,
                                  int experienceLevel, float experienceProgress, int totalExperience) {
    }
}
