package dev.molang.iamzombieq.gameplay;
import dev.molang.iamzombieq.rules.herobrine.HerobrineEncounter;
import dev.molang.iamzombieq.rules.herobrine.HerobrineRules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HerobrineEventsSourceTest {
    private static final Path SOURCE = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/HerobrineEvents.java");
    private static final Path CONFIG = Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieConfig.java");
    private static final Path ATTACHMENTS = Path.of("src/main/java/dev/molang/iamzombieq/state/IAmZombieAttachments.java");
    private static final Path ENCOUNTER_STATE = Path.of("src/main/java/dev/molang/iamzombieq/state/HerobrineEncounterState.java");
    private static final Path OMEN_SAVED_DATA = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/OmenLightsSavedData.java");

    private static String source() throws IOException {
        return Files.readString(SOURCE);
    }

    @Test
    void subscribesToTheCorrectServerEvents() throws IOException {
        String source = source();
        assertTrue(source.contains("@SubscribeEvent"), "the events class should subscribe to NeoForge events");
        assertTrue(source.contains("PlayerTickEvent.Post"), "the spawn/gaze driver should run on PlayerTickEvent.Post");
        assertTrue(source.contains("AttackEntityEvent"), "attacking Herobrine should be intercepted");
        assertTrue(source.contains("ProjectileImpactEvent"), "projectiles hitting Herobrine should be intercepted");
        assertTrue(source.contains("EntityJoinLevelEvent"), "the live-count gate should increment on EntityJoinLevelEvent (any spawn arms the gaze)");
        assertTrue(source.contains("EntityLeaveLevelEvent"), "the live-count gate should decrement on EntityLeaveLevelEvent");
        assertTrue(source.contains("PlayerEvent.Clone"), "inventory/XP restore should hook PlayerEvent.Clone");
        assertTrue(source.contains("PlayerEvent.PlayerRespawnEvent"), "respawn teleport should hook PlayerEvent.PlayerRespawnEvent");
        assertTrue(source.contains("ServerStoppedEvent"), "server stop should reset state");
        assertTrue(source.contains("instanceof ServerPlayer player"), "drivers should only run server-side for players");
    }

    @Test
    void broadensTriggersToProjectilesAndPotions() throws IOException {
        String source = source();
        assertTrue(source.contains("onProjectileImpact"), "a projectile-impact handler should exist");
        assertTrue(source.contains("getRayTraceResult"), "the projectile handler should inspect the ray trace result");
        assertTrue(source.contains("EntityHitResult"), "the projectile handler should resolve an EntityHitResult");
        assertTrue(source.contains("getProjectile"), "the projectile handler should read the projectile");
        assertTrue(source.contains("getOwner()"), "the projectile owner should be resolved to a ServerPlayer");
        assertTrue(source.contains("event.setCanceled(true)"), "a handled projectile impact should be cancelled");
        assertTrue(source.contains("handleEncounter("), "projectile/melee/gaze should all route through handleEncounter");
    }

    @Test
    void armsTheGazeScanForAnySpawnViaEntityJoin() throws IOException {
        String source = source();
        assertTrue(source.contains("onEntityJoinLevel"), "any HerobrineEntity join should arm the gaze gate");
        assertTrue(source.contains("liveHerobrineCount++"), "the join handler should increment the live count");
        assertTrue(source.contains("liveHerobrineCount--"), "the leave handler should decrement the live count");
        // The manual spawn-site increment must be gone to avoid double-counting.
        assertFalse(source.contains("liveHerobrineCount++;\n\n        // HB-OMEN"),
                "the manual spawn-site increment should be removed");
    }

    @Test
    void performsRealDeathKeepInventoryAndRespawnInPlace() throws IOException {
        String source = source();
        assertTrue(source.contains("PENDING_RESPAWNS"), "real death should track pending respawns per UUID");
        assertTrue(source.contains("PENDING_RESPAWNS.put(player.getUUID()"), "a respawn snapshot should be recorded before the kill");
        assertTrue(source.contains("snapshotInventory"), "the full inventory should be deep-copied into the snapshot");
        assertTrue(source.contains(".copy()"), "inventory snapshot/restore should deep-copy each ItemStack");
        assertTrue(source.contains("getInventory().clearContent()"), "the live inventory should be cleared so the real death drops nothing");
        assertTrue(source.contains("damageSources().source(HEROBRINE_DAMAGE)"), "the kill should use the custom Herobrine damage source");
        assertTrue(source.contains("HEROBRINE_DAMAGE"), "a Herobrine DamageType ResourceKey should exist");
        assertTrue(source.contains("Registries.DAMAGE_TYPE"), "the Herobrine damage source should be keyed in the damage-type registry");
        assertTrue(source.contains("hurtServer(level, source, Float.MAX_VALUE)"), "the kill should deal lethal max damage");
        assertFalse(source.contains("LivingDeathEvent"), "the death must proceed — the LivingDeathEvent cancel must be removed");
        assertTrue(source.contains("restoreInventory"), "the snapshot inventory should be restored onto the new player");
        assertTrue(source.contains("restoreExperience"), "the snapshot XP should be restored onto the new player");
        assertTrue(source.contains("isWasDeath()"), "the clone restore should only run on a real death");
        assertTrue(source.contains("teleportTo("), "the new player should be teleported back to the death position");
    }

    @Test
    void mirrorsTheRespawnSnapshotIntoADurableServerOnlyAttachment() throws IOException {
        String source = source();
        // (a) The death path writes the durable attachment alongside the in-memory snapshot.
        assertTrue(source.contains("HEROBRINE_PENDING_RESPAWN"),
                "the death path should mirror the snapshot into the durable attachment");
        assertTrue(source.contains("setData(IAmZombieAttachments.HEROBRINE_PENDING_RESPAWN, toSnapshot(pending))"),
                "triggerEncounterDeath should setData the durable snapshot from the pre-clear data");
        // (b) Clone + respawn fall back to the attachment when the in-memory map entry is absent.
        assertTrue(source.contains("event.getOriginal().getData(IAmZombieAttachments.HEROBRINE_PENDING_RESPAWN)"),
                "onPlayerClone should fall back to the original player's durable snapshot");
        assertTrue(source.contains("player.getData(IAmZombieAttachments.HEROBRINE_PENDING_RESPAWN)"),
                "onPlayerRespawn should fall back to the new player's durable snapshot");
        assertTrue(source.contains("snapshot.isPresent()"),
                "an EMPTY/absent snapshot should be treated as no pending respawn (no-op)");
        // (c) The attachment is cleared (set EMPTY) so no stale snapshot persists after respawn/survive.
        assertTrue(source.contains("HerobrineRespawnSnapshot.EMPTY"),
                "the durable snapshot should be cleared to EMPTY once consumed");
    }

    @Test
    void keepsDreadDurableAcrossLogout() throws IOException {
        String source = source();
        // Dread is now a durable per-player attachment, so the old logout handler that removed the
        // in-memory encounter state is GONE — dread survives logout by design (veteran forever).
        assertFalse(source.contains("onPlayerLoggedOut"), "the logout handler should be removed (dread now persists)");
        assertFalse(source.contains("ENCOUNTERS.remove"), "logout should no longer clear the per-player encounter state");
        assertTrue(source.contains("HEROBRINE_ENCOUNTER"), "dread should be stored in the durable HEROBRINE_ENCOUNTER attachment");
        // Dread (esp. escalatedBefore) is carried across the player's OWN death via the clone handler.
        assertTrue(source.contains("event.getOriginal().getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER)"),
                "onPlayerClone should carry dread from the original (dead) player");
    }

    @Test
    void preservesGazeAndCaveSpawnGating() throws IOException {
        String source = source();
        assertTrue(source.contains("HerobrineRules.isGazingAtHerobrine"), "gaze should reuse the pure gaze rule");
        assertTrue(source.contains("hasLineOfSight"), "gaze should require line of sight");
        assertTrue(source.contains("liveHerobrineCount"), "a live-count performance gate should exist");
        assertTrue(source.contains("HerobrineRules.shouldAttemptCaveSpawn"), "cave spawn should reuse the pure spawn rule");
        assertTrue(source.contains("isEligibleCavePlayer"), "cave spawn should be gated by the cave eligibility check");
    }

    @Test
    void maintainsThePerPlayerEscalationStateMachine() throws IOException {
        String source = source();
        assertTrue(source.contains("HEROBRINE_ENCOUNTER"), "per-player dread should live in the durable HEROBRINE_ENCOUNTER attachment");
        assertTrue(source.contains("HerobrineEncounterState"), "an encounter state class should track sightings/phase");
        assertTrue(source.contains("player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER)"),
                "the encounter state should be read from the player's attachment");
        assertTrue(source.contains("HerobrineEncounter.phaseFor"), "phase should be computed via the pure phaseFor function");
        assertTrue(source.contains("HerobrineEncounter.isLethal"), "lethality should be decided via the pure isLethal function");
        assertTrue(source.contains("HerobrineEncounter.isOnLethalCooldown"), "lethal cooldown should be enforced");
        assertTrue(source.contains("HerobrineEncounter.isSightingExpired"), "sighting memory decay should be enforced");
        assertTrue(source.contains("herobrine.discard()"), "non-lethal sightings should make Herobrine vanish");
        assertTrue(source.contains("phaseTransitionCue"), "phase upgrades should emit a perceptible cue");
        assertTrue(source.contains("sendSystemMessage"), "phase upgrades should message the affected player");
    }

    @Test
    void drivesThePhaseScaledReversibleOmen() throws IOException {
        String source = source();
        assertTrue(source.contains("playOmen"), "spawning should trigger an omen");
        assertTrue(source.contains("omenIntensityFor"), "omen strength should scale with the encounter phase");
        assertTrue(source.contains("BlockStateProperties.LIT"), "the omen should extinguish lit blocks via blockstate (reversible)");
        assertTrue(source.contains("OmenLightsSavedData"), "extinguished lights should be recorded in the durable per-level SavedData");
        assertTrue(source.contains("getDataStorage()"), "the omen SavedData should be obtained from the level's data storage");
        assertTrue(source.contains("restoreExpiredOmenLights"), "extinguished lights should be restored after their duration");
        assertTrue(source.contains("setEncounterPhase"), "the spawned Herobrine should publish the player's phase for the client heartbeat");
        assertTrue(source.contains("SoundEvents.STONE_STEP"), "the omen should play phantom footsteps");
    }

    @Test
    void playsTheLethalJoltStinger() throws IOException {
        String source = source();
        assertTrue(source.contains("HEROBRINE_JOLT_ENABLED"), "the jolt should be config-gated");
        assertTrue(source.contains("SoundEvents.WARDEN_ROAR"), "a vanilla stinger should play before the lethal kill");
    }

    @Test
    void cleansUpTransientStateOnServerStop() throws IOException {
        String source = source();
        // Dread + omen are now durable, so server stop must NOT clear them; only the transient
        // in-memory PENDING_RESPAWNS map and the live-count gate are reset.
        assertFalse(source.contains("ENCOUNTERS.clear()"), "server stop should no longer clear the (now-durable) dread state");
        assertFalse(source.contains("OMEN_LIGHTS.clear()"), "server stop should no longer clear the (now-durable) omen restorations");
        assertTrue(source.contains("PENDING_RESPAWNS.clear()"), "server stop should clear the transient pending-respawn map");
        assertTrue(source.contains("liveHerobrineCount = 0"), "server stop should reset the live count");
    }

    @Test
    void persistsDreadAndOmenViaDurableStorage() throws IOException {
        String source = source();
        // Dread: read-mutate-re-set the per-player attachment so the change serializes onto player NBT.
        assertTrue(source.contains("player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER)"),
                "dread should be read from the durable per-player attachment");
        assertTrue(source.contains("player.setData(IAmZombieAttachments.HEROBRINE_ENCOUNTER, state)"),
                "the mutated dread state must be re-set so the change persists");
        assertTrue(source.contains("event.getOriginal().getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER)"),
                "dread should be carried across the player's own death in onPlayerClone");

        // The attachment is registered (server-only: no .sync, no .copyOnDeath) with a 4-field serializer.
        String attachments = Files.readString(ATTACHMENTS);
        assertTrue(attachments.contains("HEROBRINE_ENCOUNTER"), "the dread attachment should be registered");
        assertTrue(attachments.contains("HerobrineEncounterStateSerializer"), "the dread attachment should have a serializer");
        assertTrue(attachments.contains("getLongOr") && attachments.contains("putLong"),
                "the dread serializer should persist the long sighting/lethal ticks");

        // The encounter-state holder exposes the four mutable dread fields + a copy constructor.
        String encounterState = Files.readString(ENCOUNTER_STATE);
        assertTrue(encounterState.contains("int sightings"), "the dread state should hold the sightings count");
        assertTrue(encounterState.contains("boolean escalatedBefore"), "the dread state should hold the veteran flag");

        // Omen: a per-level codec-based SavedData, obtained via the level's data storage.
        assertTrue(source.contains("level.getDataStorage().computeIfAbsent(OmenLightsSavedData.TYPE)"),
                "the omen records should live in the per-level OmenLightsSavedData");
        String omen = Files.readString(OMEN_SAVED_DATA);
        assertTrue(omen.contains("extends SavedData"), "the omen storage should be a SavedData");
        assertTrue(omen.contains("SavedDataType<OmenLightsSavedData> TYPE"), "the omen SavedData should declare a SavedDataType");
        assertTrue(omen.contains("BlockState.CODEC") && omen.contains("BlockPos.CODEC"),
                "the omen codec should round-trip the blockstate + position losslessly");
        assertTrue(omen.contains("setDirty()"), "mutating the omen SavedData should mark it dirty for saving");
    }

    @Test
    void exposesTheNewDreadKnobsInConfig() throws IOException {
        String config = Files.readString(CONFIG);
        assertTrue(config.contains("HEROBRINE_ESCALATION_SIGHTINGS"), "escalation threshold should be configurable");
        assertTrue(config.contains("HEROBRINE_LETHAL_SIGHTINGS"), "lethal threshold should be configurable");
        assertTrue(config.contains("HEROBRINE_MEMORY_WINDOW_TICKS"), "memory window should be configurable");
        assertTrue(config.contains("HEROBRINE_LETHAL_COOLDOWN_TICKS"), "lethal cooldown should be configurable");
        assertTrue(config.contains("HEROBRINE_OMEN_ENABLED"), "omen toggle should be configurable");
        assertTrue(config.contains("HEROBRINE_HEARTBEAT_ENABLED"), "heartbeat toggle should be configurable");
        assertTrue(config.contains("HEROBRINE_JOLT_ENABLED"), "jolt toggle should be configurable");
    }
}
