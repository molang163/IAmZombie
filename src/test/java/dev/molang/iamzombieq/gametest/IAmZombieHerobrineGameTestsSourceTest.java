package dev.molang.iamzombieq.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

class IAmZombieHerobrineGameTestsSourceTest {
    private static final Path REGISTRY = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/IAmZombieGameTestRegistry.java");
    private static final Path SUITE = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/IAmZombieHerobrineGameTests.java");
    private static final Path BODIES = Path.of(
            "src/main/java/dev/molang/iamzombieq/gametest/IAmZombieHerobrineGameTestBodies.java");
    private static final Path CAVE_STRUCTURE = Path.of(
            "src/main/resources/data/iamzombieq/structure/herobrine_cave_test.nbt");

    @Test
    void registersTheFiveRequiredTestsInIsolatedBatches() throws IOException {
        assertTrue(Files.exists(SUITE), "the Herobrine GameTest suite must exist");
        assertTrue(Files.exists(BODIES), "the Herobrine GameTest bodies must exist");
        assertTrue(Files.exists(CAVE_STRUCTURE), "the pre-baked cave structure must exist");

        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        assertTrue(Set.of("26.2.x", "26.1.x", "1.21.11", "1.21.10", "1.21.8").contains(executingNode),
                "unknown Stonecutter test node: " + executingNode);
        boolean nativeDifficultyEnvironment = executingNode.equals("26.2.x");

        String registry = SourceScan.stripComments(Files.readString(REGISTRY));
        String suite = SourceScan.stripComments(Files.readString(SUITE));
        String functionRegistration = SourceScan.methodBody(registry, "public static void onRegisterTestFunctions");
        String testRegistration = SourceScan.methodBody(registry, "public static void onRegisterGameTests");
        String compactFunctionRegistration = SourceScan.compact(functionRegistration);
        String compactTestRegistration = SourceScan.compact(testRegistration);
        String compactSuite = SourceScan.compact(suite);
        String register = SourceScan.compact(SourceScan.stripComments(
                SourceScan.methodBody(suite, "private static void register(")));

        assertTrue(registry.contains("IAmZombieHerobrineGameTests.registerAll("));
        assertTrue(registry.contains("env_herobrine_interact"));
        assertEquals(1, SourceScan.countOccurrences(
                compactFunctionRegistration,
                "event.register(Registries.TEST_ENVIRONMENT_DEFINITION_TYPE,"
                        + "modId(\"herobrine_cave_sea_level\"),()->HerobrineCaveSeaLevelEnvironment.CODEC)"));
        String nativeProvider =
                "newTestEnvironmentDefinition." + "SetDifficulty(Difficulty.HARD)";
        String restoringProvider = "newRestoringHardDifficulty" + "Environment()";
        String expectedProvider = nativeDifficultyEnvironment ? nativeProvider : restoringProvider;
        assertEquals(1, SourceScan.countOccurrences(
                compactTestRegistration,
                "event.registerEnvironment(modId(\"env_herobrine_cave\"),"
                        + "HerobrineCaveSeaLevelEnvironment.INSTANCE,"
                        + expectedProvider + ")"));
        assertEquals(1, SourceScan.countOccurrences(
                suite, "register(event, \"herobrine_lethal_attack_respawns_in_place\""));
        assertEquals(1, SourceScan.countOccurrences(
                suite, "register(event, \"herobrine_gaze_records_nonlethal_sighting\""));
        assertEquals(1, SourceScan.countOccurrences(
                suite, "register(event, \"herobrine_right_click_is_cancelled\""));
        assertTrue(compactSuite.contains(
                "register(event,\"herobrine_right_click_is_cancelled\",interactEnv,"));
        assertEquals(1, SourceScan.countOccurrences(
                suite, "register(event, \"herobrine_natural_cave_spawn_sets_phase\""));
        assertEquals(1, SourceScan.countOccurrences(
                suite, "register(event, \"herobrine_discards_after_max_lifetime\""));
        assertTrue(suite.contains("herobrine_cave_test"));
        assertTrue(compactSuite.contains(
                "register(event,\"herobrine_natural_cave_spawn_sets_phase\",caveEnv,"
                        + "CAVE_STRUCTURE,40,20,true,8,"));
        boolean nativePadding = java.util.Set.of("26.2.x", "26.1.x")
                .contains(System.getProperty("iamzombieq.test.nodeId"));
        String expectedTestData =
                "newTestData<>(environment,modId(structure),maxTicks,setupTicks,true,"
                        + "Rotation.NONE,false,1,1,skyAccess" + (nativePadding ? ",padding" : "") + ")";
        assertTrue(register.contains(expectedTestData),
                "Herobrine tests must remain required, non-manual, single-attempt tests");
    }

    @Test
    void rightClickUsesTheRealPacketPathAndProvesSingleCancellationPerEntry() throws IOException {
        String source = Files.readString(BODIES);
        String rightClick = SourceScan.methodBody(source, "static void herobrineRightClickIsCancelled");
        String compact = SourceScan.compact(SourceScan.stripComments(rightClick));
        String generalDispatch = SourceScan.methodBody(
                source, "private static void dispatchNodeNativeGeneralInteraction");
        String compactGeneralDispatch = SourceScan.compact(SourceScan.stripComments(generalDispatch));
        String activeSource = SourceScan.stripComments(source);
        String generalObserver = SourceScan.methodBody(activeSource, "public void onEntityInteract(");
        String compactGeneralObserver = SourceScan.compact(generalObserver);
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        boolean recordPacket = Set.of("26.2.x", "26.1.x").contains(executingNode);
        boolean splitEvents = !executingNode.equals("26.2.x");
        String recordLocationPacket =
                "newServerboundInteractPacket(herobrine.getId(),InteractionHand.MAIN_HAND,Vec3.ZERO,false);";
        String legacyLocationPacket =
                "ServerboundInteractPacket.createInteractionPacket(herobrine,false,InteractionHand.MAIN_HAND,Vec3.ZERO);";
        String directGeneral =
                "player.interactOn(target,InteractionHand.MAIN_HAND,Vec3.ZERO);";
        String packetGeneral =
                "player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket("
                        + "target,false,InteractionHand.MAIN_HAND));";

        assertTrue(rightClick.contains("spawnConnectedZombiePlayer"));
        assertEquals(recordPacket ? 1 : 0, SourceScan.countOccurrences(compact, recordLocationPacket));
        assertEquals(recordPacket ? 0 : 1, SourceScan.countOccurrences(compact, legacyLocationPacket));
        assertEquals(1, SourceScan.countOccurrences(compact, "player.connection.handleInteract(locationPacket);"),
                "the location gate must deliver exactly one real protocol packet");
        assertEquals(0, SourceScan.countOccurrences(compact, "player.interactOn("),
                "Player.interactOn must not masquerade as the location-bearing packet proof");
        assertEquals(1, SourceScan.countOccurrences(compact, "dispatchNodeNativeGeneralInteraction("),
                "the reverse general-entry guard must run exactly once");

        assertEquals(recordPacket ? 1 : 0,
                SourceScan.countOccurrences(compactGeneralDispatch, directGeneral));
        assertEquals(recordPacket ? 0 : 1,
                SourceScan.countOccurrences(compactGeneralDispatch, packetGeneral));
        assertEquals(1, SourceScan.countOccurrences(
                compactGeneralDispatch, recordPacket ? "player.interactOn(" : "player.connection.handleInteract("),
                "the general-entry guard must dispatch exactly once");

        assertTrue(rightClick.contains("observer.generalEvents == 1"));
        assertTrue(rightClick.contains("observer.specificEvents == 1"));
        assertTrue(rightClick.contains("observer.specificEvents == 0"));
        assertTrue(rightClick.contains("observer.lastGeneralCanceled"));
        assertTrue(rightClick.contains("observer.lastSpecificCanceled"));
        assertTrue(rightClick.contains("InteractionResult.SUCCESS_SERVER"));
        assertTrue(rightClick.contains("HEROBRINE_ENCOUNTER"));
        assertTrue(rightClick.contains("new HerobrineEncounterState(2, 123L, 456L, true)"));
        assertEquals(2, SourceScan.countOccurrences(compact,
                "player.getData(IAmZombieAttachments.HEROBRINE_ENCOUNTER).equals(originalState)"),
                "both entry points must prove that cancellation does not advance encounter state");
        assertTrue(source.contains(
                "@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)"));
        assertEquals(1, SourceScan.countOccurrences(compactGeneralObserver, "generalEvents++;"));
        assertEquals(1, SourceScan.countOccurrences(
                compactGeneralObserver, "lastGeneralCanceled=event.isCanceled();"));
        String specificSignature =
                "public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event)";
        assertEquals(splitEvents ? 1 : 0,
                SourceScan.countOccurrences(activeSource, specificSignature));
        if (splitEvents) {
            String specificObserver = SourceScan.compact(
                    SourceScan.methodBody(activeSource, specificSignature));
            assertEquals(1, SourceScan.countOccurrences(specificObserver, "specificEvents++;"));
            assertEquals(1, SourceScan.countOccurrences(
                    specificObserver, "lastSpecificCanceled=event.isCanceled();"));
        }
        assertTrue(rightClick.contains("disconnectConnectedPlayer"));
        assertFalse(rightClick.contains("NeoForge.EVENT_BUS.post"));
        assertFalse(rightClick.contains("new PlayerInteractEvent."));
    }

    @Test
    void bakedCaveHasCompleteFloorAndRoofAcrossTheSpawnRing() throws IOException {
        CompoundTag structure = NbtIo.readCompressed(CAVE_STRUCTURE, NbtAccounter.unlimitedHeap());
        ListTag size = structure.getListOrEmpty("size");
        assertEquals(48, size.getIntOr(0, -1));
        assertEquals(17, size.getIntOr(1, -1));
        assertEquals(48, size.getIntOr(2, -1));

        ListTag palette = structure.getListOrEmpty("palette");
        assertEquals(1, palette.size());
        assertEquals("minecraft:stone", palette.getCompoundOrEmpty(0).getStringOr("Name", ""));

        boolean[][] floor = new boolean[48][48];
        boolean[][] roof = new boolean[48][48];
        ListTag blocks = structure.getListOrEmpty("blocks");
        assertEquals(48 * 48 * 2, blocks.size());
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompoundOrEmpty(index);
            assertEquals(0, block.getIntOr("state", -1));
            ListTag pos = block.getListOrEmpty("pos");
            int x = pos.getIntOr(0, -1);
            int y = pos.getIntOr(1, -1);
            int z = pos.getIntOr(2, -1);
            assertTrue(x >= 0 && x < 48 && z >= 0 && z < 48);
            assertTrue(y == 7 || y == 16, "unexpected cave block Y: " + y);
            (y == 7 ? floor : roof)[x][z] = true;
        }
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                assertTrue(floor[x][z], "missing cave floor at " + x + ",7," + z);
                assertTrue(roof[x][z], "missing cave roof at " + x + ",16," + z);
            }
        }
    }

    @Test
    void gazeUsesARealJoinAndFakePlayerTickAndAssertsTheWholeSnapshot() throws IOException {
        assertTrue(Files.exists(BODIES), "the Herobrine GameTest bodies must exist");
        String source = Files.readString(BODIES);
        String gaze = SourceScan.methodBody(source, "static void herobrineGazeRecordsNonlethalSighting");

        assertTrue(gaze.contains("level.addFreshEntity(herobrine)"));
        assertTrue(gaze.contains("player.doTick()"));
        assertTrue(gaze.contains("sightings()"));
        assertTrue(gaze.contains("lastSightingTick()"));
        assertTrue(gaze.contains("lastLethalTick()"));
        assertTrue(gaze.contains("escalatedBefore()"));
        assertTrue(SourceScan.compact(gaze).contains(
                "if(herobrine!=null&&!herobrine.isRemoved()){herobrine.discard();}"));
        assertFalse(gaze.contains("PlayerTickEvent.Post"));
        assertFalse(gaze.contains("HerobrineEvents."));
    }

    @Test
    void caveSpawnUsesTheBakedRoofAndAssertsUniquePositionAndPhase() throws IOException {
        assertTrue(Files.exists(BODIES), "the Herobrine GameTest bodies must exist");
        String source = Files.readString(BODIES);
        String cave = SourceScan.methodBody(source, "static void herobrineNaturalCaveSpawnSetsPhase");
        String caveCode = SourceScan.compact(SourceScan.stripComments(cave));

        assertTrue(cave.contains("HEROBRINE_CAVE_SPAWN_CHANCE.set(1.0)"));
        assertTrue(cave.contains("HEROBRINE_CAVE_CHECK_INTERVAL_TICKS.set(1)"));
        assertTrue(cave.contains("HEROBRINE_OMEN_ENABLED.set(false)"));
        assertTrue(caveCode.contains(
                "BlockPosfloorBlock=helper.absolutePos(newBlockPos(24,7,24));"));
        assertTrue(caveCode.contains(
                "BlockPosroofBlock=helper.absolutePos(newBlockPos(24,16,24));"));
        assertTrue(cave.contains("level.getBlockState(floorBlock).is(Blocks.STONE)"));
        assertTrue(cave.contains("level.getBlockState(roofBlock).is(Blocks.STONE)"));
        assertTrue(cave.contains("helper.startSequence()"));
        assertEquals(1, SourceScan.countOccurrences(caveCode, ".thenWaitUntil("));
        assertTrue(caveCode.contains(
                "GameTestAssertions.assertFalse(helper,level.canSeeSky(playerBlock),"
                        + "\"waitingforbakedcaveskylighttosettle\");"));
        assertTrue(cave.contains("helper.runBeforeTestEnd("));
        assertTrue(caveCode.contains("boolean[]skylightTimedOut={false};"));
        assertTrue(caveCode.contains("skylightTimedOut[0]=true;"));
        assertTrue(caveCode.contains(
                "GameTestAssertions.assertFalse(helper,skylightTimedOut[0],"
                        + "\"bakedcaveskylightreadinesstimedout\");"));
        assertTrue(cave.contains("LightLayer.SKY"));
        assertTrue(SourceScan.containsInOrder(
                        caveCode,
                        "BlockPosfloorBlock=",
                        "BlockPosroofBlock=",
                        "level.getBlockState(floorBlock).is(Blocks.STONE)",
                        "level.getBlockState(roofBlock).is(Blocks.STONE)",
                        ".thenWaitUntil(",
                        ".thenExecute(()->",
                        "GameTestPlayers.spawnZombieFakePlayer(",
                        "player.doTick();",
                        "getEntitiesOfClass(HerobrineEntity.class",
                        "config.restore();",
                        ".thenSucceed();"),
                "structure checks and skylight readiness must precede the one production player tick");
        assertEquals(1, SourceScan.countOccurrences(caveCode, "player.doTick();"),
                "one cave readiness event must exercise exactly one production player tick");
        assertFalse(caveCode.contains("helper.succeed();"),
                "the sequence may succeed only after fixture cleanup returns");
        assertFalse(caveCode.contains("Thread.sleep("));
        assertFalse(caveCode.contains(".thenIdle("));
        assertFalse(caveCode.contains(".thenExecuteAfter("));
        assertFalse(caveCode.contains("runAfterDelay("));
        assertTrue(SourceScan.compact(cave).contains("getEntitiesOfClass(HerobrineEntity.class"));
        assertTrue(cave.contains("CAVE_SPAWN_HORIZONTAL_DISTANCE"));
        assertTrue(cave.contains("getEncounterPhase()"));
        assertTrue(cave.contains("HerobrineEncounter.Phase.ESCALATION"));
        assertFalse(cave.contains("PlayerTickEvent.Post"));
        assertFalse(cave.contains("HerobrineEvents."));
    }

    @Test
    void caveEnvironmentMakesTheProductionHeightGateReachableAndRestoresTheGenerator() throws IOException {
        String executingNode = System.getProperty("iamzombieq.test.nodeId");
        Set<String> genericNodes = Set.of("26.2.x", "26.1.x");
        Set<String> legacyNodes = Set.of("1.21.11", "1.21.10", "1.21.8");
        assertTrue(genericNodes.contains(executingNode) || legacyNodes.contains(executingNode),
                "unknown Stonecutter test node: " + executingNode);

        String registry = SourceScan.stripComments(Files.readString(REGISTRY));
        String environment = SourceScan.methodBody(registry, "private static final class HerobrineCaveSeaLevelEnvironment");
        String compactEnvironment = SourceScan.compact(environment);
        String setup = SourceScan.methodBody(
                environment, genericNodes.contains(executingNode) ? "public SavedContext setup" : "public void setup");

        assertTrue(environment.contains("VANILLA_OVERWORLD_SEA_LEVEL"));
        assertTrue(environment.contains("new FlatLevelSource(flatGenerator.settings())"));
        assertTrue(environment.contains("return VANILLA_OVERWORLD_SEA_LEVEL"));
        assertTrue(SourceScan.containsInOrder(
                SourceScan.compact(setup),
                "WorldGenContextoriginal=readWorldGenContext(worldGenContextField,chunkMap);",
                "writeWorldGenContext(worldGenContextField,chunkMap,replacement);",
                "if(level.getSeaLevel()!=VANILLA_OVERWORLD_SEA_LEVEL)"));
        String teardown = SourceScan.compact(
                SourceScan.methodBody(environment, "public void teardown"));
        String restore = "writeWorldGenContext(savedContext.worldGenContextField(),"
                + "savedContext.chunkMap(),savedContext.worldGenContext());";
        assertTrue(teardown.contains(restore));

        if (genericNodes.contains(executingNode)) {
            assertTrue(compactEnvironment.contains(
                    "implementsTestEnvironmentDefinition<HerobrineCaveSeaLevelEnvironment.SavedContext>{"));
            assertTrue(SourceScan.compact(setup).contains(
                    "returnnewSavedContext(worldGenContextField,chunkMap,original);"));
            assertTrue(compactEnvironment.contains(
                    "publicvoidteardown(ServerLevellevel,SavedContextsavedContext)"));
            assertFalse(compactEnvironment.contains("savedContexts"));
        } else {
            assertTrue(compactEnvironment.contains("implementsTestEnvironmentDefinition{"));
            assertTrue(compactEnvironment.contains(
                    "privatefinalMap<ServerLevel,ArrayDeque<SavedContext>>savedContexts=newIdentityHashMap<>();"));
            assertTrue(SourceScan.compact(setup).contains(
                    "savedContexts.computeIfAbsent(level,ignored->newArrayDeque<>()).push("
                            + "newSavedContext(worldGenContextField,chunkMap,original));"));
            assertTrue(SourceScan.containsInOrder(
                    teardown,
                    "if(stack==null||stack.isEmpty())",
                    "SavedContextsavedContext=stack.peek();",
                    restore,
                    "stack.pop();",
                    "savedContexts.remove(level);"));
            assertTrue(teardown.contains("thrownewIllegalStateException("));
        }

        assertTrue(compactEnvironment.contains(
                "MapCodec.unit(INSTANCE)"));
        assertFalse(environment.contains("static final Field"),
                "private ChunkMap access must stay lazy and GameTest-only");
        assertFalse(environment.contains("tickCount"));
    }

    @Test
    void lifetimeUsesNaturalServerTicksAtTheExactBoundary() throws IOException {
        assertTrue(Files.exists(BODIES), "the Herobrine GameTest bodies must exist");
        String source = Files.readString(BODIES);
        String lifetime = SourceScan.methodBody(source, "static void herobrineDiscardsAfterMaxLifetime");

        assertTrue(lifetime.contains("level.addFreshEntity(herobrine)"));
        assertTrue(lifetime.contains("runAtTickTime(900L"));
        assertTrue(lifetime.contains("runAtTickTime(901L"));
        assertTrue(SourceScan.compact(lifetime).contains(
                "Vec3.atBottomCenterOf(helper.absolutePos(BlockPos.ZERO)).add(0.0,2.0,0.0)"));
        assertEquals(2, SourceScan.countOccurrences(
                lifetime, "level.isPositionEntityTicking(herobrine.blockPosition())"));
        assertTrue(lifetime.contains("tickCount != 900"));
        assertTrue(lifetime.contains("tickCount != 901"));
        assertTrue(lifetime.contains("isRemoved()"));
        assertTrue(SourceScan.compact(lifetime).contains(
                "if(!level.addFreshEntity(herobrine)){cleanup.run();GameTestAssertions.fail(helper,"));
        assertFalse(lifetime.contains("herobrine.tick()"));
        assertFalse(lifetime.contains("GAZE_HEROBRINE_POS"));
        assertFalse(lifetime.contains("for ("));
        assertFalse(lifetime.contains("while ("));
    }

    @Test
    void snapshotsAndRestoresEveryTouchedConfigWithoutReflectionOrDirectHandlers() throws IOException {
        assertTrue(Files.exists(BODIES), "the Herobrine GameTest bodies must exist");
        String source = Files.readString(BODIES);

        for (String config : new String[] {
                "HEROBRINE_ESCALATION_SIGHTINGS",
                "HEROBRINE_LETHAL_SIGHTINGS",
                "HEROBRINE_MEMORY_WINDOW_TICKS",
                "HEROBRINE_LETHAL_COOLDOWN_TICKS",
                "HEROBRINE_CAVE_CHECK_INTERVAL_TICKS",
                "HEROBRINE_CAVE_SPAWN_CHANCE",
                "HEROBRINE_OMEN_ENABLED",
                "HEROBRINE_JOLT_ENABLED"
        }) {
            assertTrue(source.contains(config), "missing config snapshot/restore for " + config);
        }

        assertTrue(SourceScan.countOccurrences(source, "finally") >= 5,
                "all five tests need failure-safe cleanup");
        assertTrue(source.contains("config.restore()"));
        assertTrue(source.contains("herobrine.discard()"));
        assertFalse(source.contains("setAccessible("));
        assertFalse(source.contains("getDeclaredField("));
        assertFalse(source.contains("HerobrineEvents."));
        assertFalse(source.contains("NeoForge.EVENT_BUS.post"));
    }
}
