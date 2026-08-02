package dev.molang.iamzombieq.gametest;

import com.mojang.serialization.MapCodec;
import dev.molang.iamzombieq.IAmZombieMod;
import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * The single MOD-bus entry point that registers every {@code iamzombieq} GameTest suite.
 *
 * <p><b>Why one registrar.</b> MC 26.2 dropped the old {@code @GameTest}/{@code @GameTestHolder} annotations, so tests
 * are registered on the MOD-bus {@link RegisterGameTestsEvent} (it implements {@code IModBusEvent}, so it routes to the
 * mod bus without touching {@code IAmZombieMod}). Previously each of the original seven suites carried its own
 * {@link EventBusSubscriber}/{@code @SubscribeEvent} pair and each registered its own suffixed HARD/no-op environments
 * ({@code env_hard_form}, {@code env_default_mount}, …) to avoid an environment-id collision (a duplicate
 * {@code registerEnvironment} id crashes the server). Those suffixed environments were all behaviourally identical
 * (every HARD one is a bare {@link TestEnvironmentDefinition.SetDifficulty} HARD; every default one is a no-op empty
 * {@code AllOf}). This class collapses that boilerplate: it registers the two shared environments <b>exactly once</b>
 * and hands the resulting {@link Holder}s to the shared suites' {@code registerAll}. The Herobrine lifecycle suite
 * uses four additional HARD environments so its temporary global config overrides run in separate batches. The two
 * coffin lifecycle tests likewise use distinct no-op environments so their connected-player vote populations and
 * static nap state cannot share a batch.
 *
 * <p><b>Structure.</b> Most suites reuse the shipped 1x1x1 all-air
 * {@code data/iamzombieq/structure/empty_test.nbt} template. The natural Herobrine spawn test uses a pre-baked roofed
 * cave. The shared {@link ConsumerGameTestInstance} bridge uses one registered vanilla function codec path and retains
 * each existing body under its unchanged test ID.
 */
@EventBusSubscriber(modid = IAmZombieMod.MOD_ID)
public final class IAmZombieGameTestRegistry {
    private static final int VANILLA_OVERWORLD_SEA_LEVEL = 63;

    private IAmZombieGameTestRegistry() {
    }

    @SubscribeEvent
    public static void onRegisterTestFunctions(RegisterEvent event) {
        event.register(
                Registries.TEST_ENVIRONMENT_DEFINITION_TYPE,
                modId("herobrine_cave_sea_level"),
                () -> HerobrineCaveSeaLevelEnvironment.CODEC);
        event.register(
                Registries.TEST_FUNCTION,
                ConsumerGameTestInstance.DISPATCHER_ID,
                () -> ConsumerGameTestInstance::dispatch);
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        // The two shared environments, each registered EXACTLY ONCE (a duplicate registerEnvironment id crashes the
        // server). A no-op environment (empty AllOf) for tests that don't need a specific world setup, and a HARD
        // environment so the difficulty-scaled infection chance is 1.0 (deterministic conversions).
        Holder<TestEnvironmentDefinition<?>> defaultEnv =
                event.registerEnvironment(modId("env_default"));
        Holder<TestEnvironmentDefinition<?>> hardEnv =
                event.registerEnvironment(modId("env_hard"), new TestEnvironmentDefinition.SetDifficulty(Difficulty.HARD));
        Holder<TestEnvironmentDefinition<?>> herobrineLethalEnv =
                event.registerEnvironment(modId("env_herobrine_lethal"),
                        new TestEnvironmentDefinition.SetDifficulty(Difficulty.HARD));
        Holder<TestEnvironmentDefinition<?>> herobrineGazeEnv =
                event.registerEnvironment(modId("env_herobrine_gaze"),
                        new TestEnvironmentDefinition.SetDifficulty(Difficulty.HARD));
        Holder<TestEnvironmentDefinition<?>> herobrineCaveEnv =
                event.registerEnvironment(modId("env_herobrine_cave"),
                        HerobrineCaveSeaLevelEnvironment.INSTANCE,
                        new TestEnvironmentDefinition.SetDifficulty(Difficulty.HARD));
        Holder<TestEnvironmentDefinition<?>> herobrineLifetimeEnv =
                event.registerEnvironment(modId("env_herobrine_lifetime"),
                        new TestEnvironmentDefinition.SetDifficulty(Difficulty.HARD));
        Holder<TestEnvironmentDefinition<?>> coffinVoteEnv =
                event.registerEnvironment(modId("env_coffin_vote"));
        Holder<TestEnvironmentDefinition<?>> coffinTimeoutEnv =
                event.registerEnvironment(modId("env_coffin_timeout"));

        IAmZombieGameTests.registerAll(event, defaultEnv, hardEnv);
        IAmZombieDisguiseGameTests.registerAll(event, defaultEnv, hardEnv);
        IAmZombieFoodInfGameTests.registerAll(event, defaultEnv, hardEnv);
        IAmZombieFormGameTests.registerAll(event, defaultEnv, hardEnv);
        IAmZombieGiantSunGameTests.registerAll(event, defaultEnv, hardEnv);
        IAmZombieMobSleepGameTests.registerAll(
                event, defaultEnv, hardEnv, coffinVoteEnv, coffinTimeoutEnv);
        IAmZombieMountGameTests.registerAll(event, defaultEnv, hardEnv);
        IAmZombieFixRegressionGameTests.registerAll(event, defaultEnv, hardEnv);
        IAmZombieHerobrineGameTests.registerAll(
                event, herobrineLethalEnv, herobrineGazeEnv, herobrineCaveEnv, herobrineLifetimeEnv);
    }

    private static Identifier modId(String path) {
        return Identifier.fromNamespaceAndPath(IAmZombieMod.MOD_ID, path);
    }

    /**
     * GameTestServer always uses FlatLevelSource, whose sea level is -63. That makes the production cave gate
     * (player Y below sea level - 8) impossible in its Overworld, whose minimum Y is -64. This batch-local
     * environment preserves the flat generator settings while exposing the normal Overworld sea level, then restores
     * the exact original world-generation context when the batch ends.
     */
    private static final class HerobrineCaveSeaLevelEnvironment
            implements TestEnvironmentDefinition<HerobrineCaveSeaLevelEnvironment.SavedContext> {
        private static final HerobrineCaveSeaLevelEnvironment INSTANCE = new HerobrineCaveSeaLevelEnvironment();
        private static final MapCodec<HerobrineCaveSeaLevelEnvironment> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public SavedContext setup(ServerLevel level) {
            ChunkMap chunkMap = level.getChunkSource().chunkMap;
            Field worldGenContextField = findWorldGenContextField();
            WorldGenContext original = readWorldGenContext(worldGenContextField, chunkMap);
            if (!(original.generator() instanceof FlatLevelSource flatGenerator)) {
                throw new IllegalStateException("Herobrine cave GameTest requires the vanilla flat test generator");
            }

            FlatLevelSource seaLevelGenerator = new FlatLevelSource(flatGenerator.settings()) {
                @Override
                public int getSeaLevel() {
                    return VANILLA_OVERWORLD_SEA_LEVEL;
                }
            };
            WorldGenContext replacement = new WorldGenContext(
                    original.level(),
                    seaLevelGenerator,
                    original.structureManager(),
                    original.lightEngine(),
                    original.mainThreadExecutor(),
                    original.unsavedListener());
            writeWorldGenContext(worldGenContextField, chunkMap, replacement);
            if (level.getSeaLevel() != VANILLA_OVERWORLD_SEA_LEVEL) {
                writeWorldGenContext(worldGenContextField, chunkMap, original);
                throw new IllegalStateException("failed to install the Herobrine cave GameTest sea level");
            }
            return new SavedContext(worldGenContextField, chunkMap, original);
        }

        @Override
        public void teardown(ServerLevel level, SavedContext savedContext) {
            writeWorldGenContext(
                    savedContext.worldGenContextField(), savedContext.chunkMap(), savedContext.worldGenContext());
        }

        @Override
        public MapCodec<HerobrineCaveSeaLevelEnvironment> codec() {
            return CODEC;
        }

        private static Field findWorldGenContextField() {
            Field[] matches = Arrays.stream(ChunkMap.class.getDeclaredFields())
                    .filter(field -> field.getType() == WorldGenContext.class)
                    .toArray(Field[]::new);
            if (matches.length != 1 || !matches[0].trySetAccessible()) {
                throw new IllegalStateException("cannot access the GameTest ChunkMap world-generation context");
            }
            return matches[0];
        }

        private static WorldGenContext readWorldGenContext(Field field, ChunkMap chunkMap) {
            try {
                return (WorldGenContext) field.get(chunkMap);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("cannot read the GameTest world-generation context", exception);
            }
        }

        private static void writeWorldGenContext(Field field, ChunkMap chunkMap, WorldGenContext context) {
            try {
                field.set(chunkMap, context);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("cannot update the GameTest world-generation context", exception);
            }
        }

        private record SavedContext(Field worldGenContextField, ChunkMap chunkMap, WorldGenContext worldGenContext) {
        }
    }
}
