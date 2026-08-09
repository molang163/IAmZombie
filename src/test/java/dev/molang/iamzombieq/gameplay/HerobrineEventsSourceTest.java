package dev.molang.iamzombieq.gameplay;
import dev.molang.iamzombieq.rules.herobrine.HerobrineEncounter;
import dev.molang.iamzombieq.rules.herobrine.HerobrineRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class HerobrineEventsSourceTest {
    private static final String SOURCE = "dev/molang/iamzombieq/gameplay/HerobrineEvents.java";
    private static final Path CONFIG = Path.of("src/main/java/dev/molang/iamzombieq/IAmZombieConfig.java");
    private static final Path ATTACHMENTS = Path.of("src/main/java/dev/molang/iamzombieq/state/IAmZombieAttachments.java");
    private static final Path ENCOUNTER_STATE = Path.of("src/main/java/dev/molang/iamzombieq/state/HerobrineEncounterState.java");
    private static final Path OMEN_SAVED_DATA = Path.of("src/main/java/dev/molang/iamzombieq/gameplay/OmenLightsSavedData.java");
    private static final Path MOUNT_GAMETEST =
            Path.of("src/main/java/dev/molang/iamzombieq/gametest/IAmZombieMountGameTestBodies.java");

    @Test
    void generatedProductionUsesTheNodeNativeEntityInteractionAbi() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "unknown Stonecutter test node: " + executingNode);
        String source = SourceScan.mainJava(SOURCE);
        String activeSource = SourceScan.stripComments(source);
        boolean splitEvents = !executingNode.equals("26.2.x");
        String generalSignature =
                "public static void onEntityInteract(PlayerInteractEvent.EntityInteract event)";
        String specificSignature =
                "public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event)";
        String generalHandler = SourceScan.compact(SourceScan.methodBody(activeSource, generalSignature));
        assertEquals(1, SourceScan.countOccurrences(activeSource, generalSignature),
                "every node must retain exactly one general interaction handler");
        assertEquals(1, subscriberCount(activeSource, "EntityInteract"),
                "the general interaction handler must remain the only general-event subscriber");
        assertEquals(
                SourceScan.compact(generalSignature
                        + " { cancelHerobrineInteraction("
                        + "event.getTarget(), event, event::setCancellationResult); }"),
                generalHandler);
        assertEquals(splitEvents ? 1 : 0,
                SourceScan.countOccurrences(activeSource, specificSignature),
                "only pre-26.2 nodes may retain the split specific-event subscriber");
        if (splitEvents) {
            assertEquals(1, subscriberCount(activeSource, "EntityInteractSpecific"),
                    "pre-26.2 must expose exactly one specific-event subscriber");
            assertEquals(
                    SourceScan.compact(specificSignature
                            + " { cancelHerobrineInteraction("
                            + "event.getTarget(), event, event::setCancellationResult); }"),
                    SourceScan.compact(SourceScan.methodBody(activeSource, specificSignature)));
        } else {
            try (var paths = Files.walk(Path.of("src/main/java"))) {
                for (Path path : paths.filter(Files::isRegularFile)
                        .filter(candidate -> candidate.toString().endsWith(".java"))
                        .toList()) {
                    assertFalse(SourceScan.stripComments(Files.readString(path))
                                    .contains("PlayerInteractEvent.EntityInteractSpecific"),
                            () -> "26.2 production source links the removed split event type in " + path);
                }
            }
        }
        String cancellation = SourceScan.compact(SourceScan.methodBody(
                activeSource, "private static void cancelHerobrineInteraction"));
        assertTrue(cancellation.contains("targetinstanceofHerobrineEntity"));
        assertTrue(cancellation.contains("event.setCanceled(true)"));
        assertTrue(cancellation.contains(
                "setCancellationResult.accept(InteractionResult.SUCCESS_SERVER)"));

        String mountGameTest = Files.readString(MOUNT_GAMETEST);
        assertFalse(mountGameTest.contains("new PlayerInteractEvent.EntityInteract("),
                "GameTests must not hand-construct a version-specific event ABI");
        String interaction = SourceScan.methodBody(
                mountGameTest, "private static PlayerInteractEvent.EntityInteract interact");
        assertTrue(interaction.contains("//? if >=26.1"),
                "the optional interaction location must stay behind the local Stonecutter seam");
        assertTrue(interaction.contains("Vec3.ZERO"),
                "the location-aware nodes must retain an exact local hit position");
        String activeInteraction = SourceScan.compact(SourceScan.stripComments(interaction));
        boolean locationApi = Set.of("26.2.x", "26.1.x").contains(executingNode);
        String locationCall = "player.interactOn(target,InteractionHand.MAIN_HAND,Vec3.ZERO);";
        String legacyCall = "player.interactOn(target,InteractionHand.MAIN_HAND);";
        assertEquals(locationApi ? 1 : 0, SourceScan.countOccurrences(activeInteraction, locationCall));
        assertEquals(locationApi ? 0 : 1, SourceScan.countOccurrences(activeInteraction, legacyCall));
        assertEquals(1, SourceScan.countOccurrences(activeInteraction, "player.interactOn("),
                "the helper must issue exactly one real node-native interaction");
    }

    @Test
    void compiledHandlerLinksExactlyTheNodeNativeEventTypes() throws IOException {
        String resource = "/" + HerobrineEvents.class.getName().replace('.', '/') + ".class";
        try (InputStream input = HerobrineEvents.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new AssertionError("missing class bytes for " + HerobrineEvents.class.getName());
            }
            String constantPool = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
            String generalDescriptor =
                    "(Lnet/neoforged/neoforge/event/entity/player/PlayerInteractEvent$EntityInteract;)V";
            String specificDescriptor =
                    "(Lnet/neoforged/neoforge/event/entity/player/PlayerInteractEvent$EntityInteractSpecific;)V";
            assertTrue(constantPool.contains(generalDescriptor),
                    "compiled production bytecode must retain the general handler descriptor");
            boolean splitEvents = !System.getProperty("iamzombieq.test.nodeId").equals("26.2.x");
            assertEquals(splitEvents,
                    constantPool.contains(specificDescriptor),
                    "compiled production bytecode must use the node-native specific handler descriptor");
        }
    }

    private static int subscriberCount(String source, String eventType) {
        Matcher matcher = Pattern.compile(
                        "@SubscribeEvent\\s+public\\s+static\\s+void\\s+\\w+\\s*\\(\\s*"
                                + "PlayerInteractEvent\\."
                                + Pattern.quote(eventType)
                                + "\\s+\\w+\\s*\\)")
                .matcher(source);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    @Test
    void subscribesToTheCorrectServerEvents() throws IOException {
        String source = SourceScan.mainJava(SOURCE);
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
        String source = SourceScan.mainJava(SOURCE);
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
        String source = SourceScan.mainJava(SOURCE);
        assertTrue(source.contains("onEntityJoinLevel"), "any HerobrineEntity join should arm the gaze gate");
        assertTrue(source.contains("liveHerobrineCount++"), "the join handler should increment the live count");
        assertTrue(source.contains("liveHerobrineCount--"), "the leave handler should decrement the live count");
        // The manual spawn-site increment must be gone to avoid double-counting.
        assertFalse(source.contains("liveHerobrineCount++;\n\n        // HB-OMEN"),
                "the manual spawn-site increment should be removed");
    }

    @Test
    void performsRealDeathKeepInventoryAndRespawnInPlace() throws IOException {
        String source = SourceScan.mainJava(SOURCE);
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
        String source = SourceScan.mainJava(SOURCE);
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
        String source = SourceScan.mainJava(SOURCE);
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
        String source = SourceScan.mainJava(SOURCE);
        String playerTick = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(source, "public static void onPlayerTick")));
        assertTrue(playerTick.contains(
                        "if(!(event.getEntity()instanceofServerPlayerplayer)"
                                + "||!ZombiePlayerGates.isZombiePlayer(player)||!player.isAlive()){return;}"),
                "the tick gate should preserve ServerPlayer, shared admission, and alive short-circuit order");
        assertFalse(playerTick.contains(".isSpectator("),
                "the tick gate should not duplicate the canonical spectator check");
        assertTrue(source.contains("HerobrineRules.isGazingAtHerobrine"), "gaze should reuse the pure gaze rule");
        assertTrue(source.contains("hasLineOfSight"), "gaze should require line of sight");
        assertTrue(source.contains("liveHerobrineCount"), "a live-count performance gate should exist");
        assertTrue(source.contains("HerobrineRules.shouldAttemptCaveSpawn"), "cave spawn should reuse the pure spawn rule");
        assertTrue(source.contains("isEligibleCavePlayer"), "cave spawn should be gated by the cave eligibility check");
    }

    @Test
    void caveSpawnSearchUsesNamedParametersWithoutChangingRandomOrderOrBounds() throws IOException {
        String source = SourceScan.mainJava(SOURCE);

        String eligible = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(source, "private static boolean isEligibleCavePlayer")));
        assertTrue(eligible.contains(
                        "returnlevel.dimension()==Level.OVERWORLD"
                                + "&&pos.getY()<level.getSeaLevel()-HerobrineRules.CAVE_SPAWN_SEA_LEVEL_OFFSET"
                                + "&&!level.canSeeSky(pos)&&ZombiePlayerGates.isZombiePlayer(player);"),
                "cave eligibility should preserve dimension, height, sky, and positive shared-admission order");
        assertFalse(eligible.contains("!ZombiePlayerGates.isZombiePlayer(player)"),
                "eligible cave players should use the shared gate with positive polarity");
        assertFalse(eligible.contains(".isSpectator("),
                "cave eligibility should not duplicate the canonical spectator check");
        assertFalse(eligible.contains("level.getSeaLevel()-8"),
                "cave eligibility must not fall back to the raw sea-level offset");

        String find = SourceScan.methodBody(source, "private static Optional<BlockPos> findSpawnPosition");
        String compactFind = SourceScan.compact(SourceScan.stripComments(find));
        assertTrue(compactFind.contains(
                "for(intattempt=0;attempt<HerobrineRules.CAVE_SPAWN_ATTEMPTS;attempt++)"));
        assertTrue(compactFind.contains(
                "intdistance=HerobrineRules.CAVE_SPAWN_HORIZONTAL_DISTANCE+player.getRandom().nextInt(HerobrineRules.CAVE_SPAWN_HORIZONTAL_DISTANCE)"));
        assertTrue(compactFind.contains(
                "intdy=player.getRandom().nextInt(HerobrineRules.CAVE_SPAWN_VERTICAL_OFFSET_RADIUS*2+1)-HerobrineRules.CAVE_SPAWN_VERTICAL_OFFSET_RADIUS"));
        assertTrue(compactFind.contains(
                "for(inty=-HerobrineRules.CAVE_SPAWN_VERTICAL_SEARCH_RADIUS;y<=HerobrineRules.CAVE_SPAWN_VERTICAL_SEARCH_RADIUS;y++)"));
        assertFalse(compactFind.contains("for(intattempt=0;attempt<16;attempt++)"),
                "spawn attempts must not fall back to the raw limit");
        assertFalse(compactFind.contains("intdistance=12+player.getRandom().nextInt(12)"),
                "horizontal distance must not fall back to raw bounds");
        assertFalse(compactFind.contains("intdy=player.getRandom().nextInt(7)-3"),
                "vertical offset must not fall back to raw bounds");
        assertFalse(compactFind.contains("for(inty=-4;y<=4;y++)"),
                "vertical search must not fall back to raw bounds");

        assertEquals(3, SourceScan.countOccurrences(find, "player.getRandom()"),
                "each position attempt should retain exactly three RNG calls");
        int angleRoll = find.indexOf("player.getRandom().nextDouble()");
        int distanceRoll = find.indexOf("player.getRandom().nextInt(HerobrineRules.CAVE_SPAWN_HORIZONTAL_DISTANCE)");
        int verticalRoll = find.indexOf("player.getRandom().nextInt(HerobrineRules.CAVE_SPAWN_VERTICAL_OFFSET_RADIUS");
        assertTrue(angleRoll >= 0 && angleRoll < distanceRoll && distanceRoll < verticalRoll,
                "RNG order must stay angle, horizontal distance, then vertical offset");
    }

    @Test
    void maintainsThePerPlayerEscalationStateMachine() throws IOException {
        String source = SourceScan.mainJava(SOURCE);
        assertTrue(source.contains("HEROBRINE_ENCOUNTER"), "per-player dread should live in the durable HEROBRINE_ENCOUNTER attachment");
        assertTrue(source.contains("HerobrineEncounterState"), "an encounter state class should track sightings/phase");
        assertTrue(source.contains("player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER)"),
                "the encounter state should be read from the player's attachment");
        assertTrue(source.contains("HerobrineEncounter.resolveEncounter("),
                "decay → phase → lethal/cooldown → cue should run through the single rules-layer resolveEncounter");
        assertTrue(source.contains("HerobrineEncounter.phaseAfterDecay("),
                "the read-only phase query should go through the pure phaseAfterDecay function");
        assertTrue(source.contains("HerobrineEncounter.Snapshot("),
                "the attachment state should be handed to the rules layer as a primitive snapshot");
        assertTrue(source.contains("HerobrineEncounter.Action.LETHAL"),
                "lethality should be decided by the resolution's action");
        assertTrue(source.contains("resolution.nextSnapshot()"),
                "the resolved next snapshot should be what gets persisted");
        assertTrue(source.contains("herobrine.discard()"), "non-lethal sightings should make Herobrine vanish");
        assertTrue(source.contains("resolution.cue()"), "phase upgrades should emit the cue carried by the resolution");
        assertTrue(source.contains(
                        "player.sendSystemMessage(Component.translatable(cue.subtitleKey()), true);"),
                "phase upgrades must remain action-bar messages that bypass hidden chat");
    }

    @Test
    void drivesThePhaseScaledReversibleOmen() throws IOException {
        String source = SourceScan.mainJava(SOURCE);
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
        String source = SourceScan.mainJava(SOURCE);
        assertTrue(source.contains("HEROBRINE_JOLT_ENABLED"), "the jolt should be config-gated");
        assertTrue(source.contains("SoundEvents.WARDEN_ROAR"), "a vanilla stinger should play before the lethal kill");
    }

    @Test
    void cleansUpTransientStateOnServerStop() throws IOException {
        String source = SourceScan.mainJava(SOURCE);
        // Dread + omen are now durable, so server stop must NOT clear them; only the transient
        // in-memory PENDING_RESPAWNS map and the live-count gate are reset.
        assertFalse(source.contains("ENCOUNTERS.clear()"), "server stop should no longer clear the (now-durable) dread state");
        assertFalse(source.contains("OMEN_LIGHTS.clear()"), "server stop should no longer clear the (now-durable) omen restorations");
        assertTrue(source.contains("PENDING_RESPAWNS.clear()"), "server stop should clear the transient pending-respawn map");
        assertTrue(source.contains("liveHerobrineCount = 0"), "server stop should reset the live count");
    }

    @Test
    void persistsDreadAndOmenViaDurableStorage() throws IOException {
        String source = SourceScan.mainJava(SOURCE);
        // Dread: read → resolve → re-set the per-player attachment so the change serializes onto player NBT.
        assertTrue(source.contains("player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER)"),
                "dread should be read from the durable per-player attachment");
        assertTrue(source.contains("player.setData(IAmZombieAttachments.HEROBRINE_ENCOUNTER, nextState)"),
                "the resolved dread state must be re-set so the change persists");
        assertTrue(source.contains("event.getOriginal().getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER)"),
                "dread should be carried across the player's own death in onPlayerClone");

        // The attachment is registered (server-only: no .sync, no .copyOnDeath) with a 4-field serializer
        // whose NBT keys must stay verbatim for old saves.
        String attachments = Files.readString(ATTACHMENTS);
        assertTrue(attachments.contains("HEROBRINE_ENCOUNTER"), "the dread attachment should be registered");
        assertTrue(attachments.contains("HerobrineEncounterStateSerializer"), "the dread attachment should have a serializer");
        assertTrue(attachments.contains("getLongOr") && attachments.contains("putLong"),
                "the dread serializer should persist the long sighting/lethal ticks");
        assertTrue(attachments.contains("\"sightings\"") && attachments.contains("\"lastSightingTick\"")
                        && attachments.contains("\"lastLethalTick\"") && attachments.contains("\"escalatedBefore\""),
                "the dread serializer must keep the four NBT keys verbatim (old-save compatibility)");
        assertTrue(attachments.contains("builder(HerobrineEncounterState::new)"),
                "the attachment default supplier should stay the no-arg constructor reference");

        // The encounter-state holder is an immutable record with the four dread components + with* copies.
        String encounterState = Files.readString(ENCOUNTER_STATE);
        assertTrue(encounterState.contains("record HerobrineEncounterState"), "the dread state should be an immutable record");
        assertTrue(encounterState.contains("int sightings"), "the dread state should hold the sightings count");
        assertTrue(encounterState.contains("boolean escalatedBefore"), "the dread state should hold the veteran flag");
        assertTrue(encounterState.contains("this(0, Long.MIN_VALUE, -1L, false)"),
                "the no-arg constructor must keep the legacy defaults for the attachment supplier");
        assertTrue(encounterState.contains("withSightingsReset")
                        && encounterState.contains("withSightingRecorded")
                        && encounterState.contains("withLethalTriggered"),
                "the record should expose the with* copy helpers");

        // Omen: a per-level codec-based SavedData, obtained via the level's data storage.
        assertTrue(source.contains("level.getDataStorage().computeIfAbsent(OmenLightsSavedData.TYPE)"),
                "the omen records should live in the per-level OmenLightsSavedData");
        String omen = Files.readString(OMEN_SAVED_DATA);
        assertTrue(omen.contains("extends SavedData"), "the omen storage should be a SavedData");
        assertTrue(omen.contains("SavedDataType<OmenLightsSavedData> TYPE"), "the omen SavedData should declare a SavedDataType");
        assertTrue(omen.contains("BlockState.CODEC") && omen.contains("BlockPos.CODEC"),
                "the omen codec should round-trip the blockstate + position losslessly");
        assertTrue(omen.contains("setDirty()"), "mutating the omen SavedData should mark it dirty for saving");

        String rawOmen = SourceScan.compact(omen);
        String activeOmen = SourceScan.compact(SourceScan.stripComments(omen));
        String identifierArgument = "ModIds.id(\"herobrine_omen_lights\"),";
        String stringArgument = "\"iamzombieq/herobrine_omen_lights\",";
        String identifierType = "newSavedDataType<>(" + identifierArgument
                + "OmenLightsSavedData::new,CODEC)";
        String stringType = "newSavedDataType<>(" + stringArgument
                + "OmenLightsSavedData::new,CODEC)";
        assertEquals(1, SourceScan.countOccurrences(rawOmen, identifierArgument),
                "canonical source must retain the namespaced Identifier form");
        assertEquals(1, SourceScan.countOccurrences(rawOmen, stringArgument),
                "canonical source must retain the same namespaced path for legacy String IDs");
        assertFalse(omen.contains("iamzombieq_herobrine_omen_lights"),
                "legacy storage must not collapse the namespace/path into a different underscore ID");

        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "test must run under one of the five frozen Stonecutter nodes");
        boolean identifierApi = Set.of("26.2.x", "26.1.x").contains(executingNode);
        assertEquals(identifierApi ? 1 : 0,
                SourceScan.countOccurrences(activeOmen, identifierType));
        assertEquals(identifierApi ? 0 : 1,
                SourceScan.countOccurrences(activeOmen, stringType));
        assertTrue(rawOmen.contains("optionalFieldOf(\"lights\",List.of())"));
        assertTrue(rawOmen.contains("fieldOf(\"pos\")"));
        assertTrue(rawOmen.contains("fieldOf(\"state\")"));
        assertTrue(rawOmen.contains("fieldOf(\"restoreAt\")"));
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
