package dev.molang.iamzombieq.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.molang.iamzombieq.util.SourceScan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void registersTheFourRequiredTestsInIsolatedBatches() throws IOException {
        assertTrue(Files.exists(SUITE), "the Herobrine GameTest suite must exist");
        assertTrue(Files.exists(BODIES), "the Herobrine GameTest bodies must exist");
        assertTrue(Files.exists(CAVE_STRUCTURE), "the pre-baked cave structure must exist");

        String registry = Files.readString(REGISTRY);
        String suite = Files.readString(SUITE);
        String functionRegistration = SourceScan.methodBody(registry, "public static void onRegisterTestFunctions");
        String testRegistration = SourceScan.methodBody(registry, "public static void onRegisterGameTests");

        assertTrue(registry.contains("IAmZombieHerobrineGameTests.registerAll("));
        assertTrue(registry.contains("env_herobrine_lethal"));
        assertTrue(registry.contains("env_herobrine_gaze"));
        assertTrue(registry.contains("env_herobrine_cave"));
        assertTrue(registry.contains("env_herobrine_lifetime"));
        assertTrue(SourceScan.compact(functionRegistration).contains(
                "event.register(Registries.TEST_ENVIRONMENT_DEFINITION_TYPE,"
                        + "modId(\"herobrine_cave_sea_level\"),()->HerobrineCaveSeaLevelEnvironment.CODEC)"));
        assertTrue(SourceScan.compact(testRegistration).contains(
                "event.registerEnvironment(modId(\"env_herobrine_cave\"),"
                        + "HerobrineCaveSeaLevelEnvironment.INSTANCE,"
                        + "newTestEnvironmentDefinition.SetDifficulty(Difficulty.HARD))"));
        assertEquals(1, SourceScan.countOccurrences(
                suite, "register(event, \"herobrine_lethal_attack_respawns_in_place\""));
        assertEquals(1, SourceScan.countOccurrences(
                suite, "register(event, \"herobrine_gaze_records_nonlethal_sighting\""));
        assertEquals(1, SourceScan.countOccurrences(
                suite, "register(event, \"herobrine_natural_cave_spawn_sets_phase\""));
        assertEquals(1, SourceScan.countOccurrences(
                suite, "register(event, \"herobrine_discards_after_max_lifetime\""));
        assertTrue(suite.contains("herobrine_cave_test"));
        assertTrue(SourceScan.compact(suite).contains(
                "register(event,\"herobrine_natural_cave_spawn_sets_phase\",caveEnv,"
                        + "CAVE_STRUCTURE,40,20,true,8,"));
        assertTrue(suite.contains("true,         // required"));
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

        assertTrue(cave.contains("HEROBRINE_CAVE_SPAWN_CHANCE.set(1.0)"));
        assertTrue(cave.contains("HEROBRINE_CAVE_CHECK_INTERVAL_TICKS.set(1)"));
        assertTrue(cave.contains("HEROBRINE_OMEN_ENABLED.set(false)"));
        assertTrue(cave.contains("player.doTick()"));
        assertTrue(SourceScan.compact(cave).contains("getEntitiesOfClass(HerobrineEntity.class"));
        assertTrue(cave.contains("CAVE_SPAWN_HORIZONTAL_DISTANCE"));
        assertTrue(cave.contains("getEncounterPhase()"));
        assertTrue(cave.contains("HerobrineEncounter.Phase.ESCALATION"));
        assertFalse(cave.contains("PlayerTickEvent.Post"));
        assertFalse(cave.contains("HerobrineEvents."));
    }

    @Test
    void caveEnvironmentMakesTheProductionHeightGateReachableAndRestoresTheGenerator() throws IOException {
        String registry = Files.readString(REGISTRY);
        String environment = SourceScan.methodBody(registry, "private static final class HerobrineCaveSeaLevelEnvironment");

        assertTrue(environment.contains("VANILLA_OVERWORLD_SEA_LEVEL"));
        assertTrue(environment.contains("new FlatLevelSource(flatGenerator.settings())"));
        assertTrue(environment.contains("return VANILLA_OVERWORLD_SEA_LEVEL"));
        assertTrue(environment.contains("return new SavedContext(worldGenContextField, chunkMap, original)"));
        assertTrue(SourceScan.compact(environment).contains(
                "writeWorldGenContext(savedContext.worldGenContextField(),savedContext.chunkMap(),"
                        + "savedContext.worldGenContext())"));
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
                "if(!level.addFreshEntity(herobrine)){cleanup.run();helper.fail("));
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

        assertTrue(SourceScan.countOccurrences(source, "finally") >= 4,
                "all four tests need failure-safe cleanup");
        assertTrue(source.contains("config.restore()"));
        assertTrue(source.contains("herobrine.discard()"));
        assertFalse(source.contains("setAccessible("));
        assertFalse(source.contains("getDeclaredField("));
        assertFalse(source.contains("HerobrineEvents."));
        assertFalse(source.contains("NeoForge.EVENT_BUS.post"));
    }
}
