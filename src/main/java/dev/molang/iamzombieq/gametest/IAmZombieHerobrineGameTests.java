package dev.molang.iamzombieq.gametest;

import dev.molang.iamzombieq.IAmZombieMod;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Required runtime coverage for Herobrine encounters, respawn, natural cave spawning, and maximum lifetime. */
public final class IAmZombieHerobrineGameTests {
    private static final String EMPTY_STRUCTURE = "empty_test";
    private static final String CAVE_STRUCTURE = "herobrine_cave_test";

    private IAmZombieHerobrineGameTests() {
    }

    static void registerAll(
            RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> lethalEnv,
            Holder<TestEnvironmentDefinition<?>> gazeEnv,
            Holder<TestEnvironmentDefinition<?>> interactEnv,
            Holder<TestEnvironmentDefinition<?>> caveEnv,
            Holder<TestEnvironmentDefinition<?>> lifetimeEnv) {
        register(event, "herobrine_lethal_attack_respawns_in_place", lethalEnv, EMPTY_STRUCTURE, 40, 0, false, 8,
                IAmZombieHerobrineGameTestBodies::herobrineLethalAttackRespawnsInPlace);
        register(event, "herobrine_gaze_records_nonlethal_sighting", gazeEnv, EMPTY_STRUCTURE, 40, 0, false, 8,
                IAmZombieHerobrineGameTestBodies::herobrineGazeRecordsNonlethalSighting);
        register(event, "herobrine_right_click_is_cancelled", interactEnv, EMPTY_STRUCTURE, 40, 0, false, 8,
                IAmZombieHerobrineGameTestBodies::herobrineRightClickIsCancelled);
        register(event, "herobrine_natural_cave_spawn_sets_phase", caveEnv, CAVE_STRUCTURE, 40, 20, true, 8,
                IAmZombieHerobrineGameTestBodies::herobrineNaturalCaveSpawnSetsPhase);
        register(event, "herobrine_discards_after_max_lifetime", lifetimeEnv, EMPTY_STRUCTURE, 930, 0, false, 8,
                IAmZombieHerobrineGameTestBodies::herobrineDiscardsAfterMaxLifetime);
    }

    private static void register(
            RegisterGameTestsEvent event,
            String name,
            Holder<TestEnvironmentDefinition<?>> environment,
            String structure,
            int maxTicks,
            int setupTicks,
            boolean skyAccess,
            int padding,
            Consumer<GameTestHelper> body) {
        TestData<Holder<TestEnvironmentDefinition<?>>> info = new TestData<>(
                environment,
                modId(structure),
                maxTicks,
                setupTicks,
                true,         // required
                Rotation.NONE,
                false,        // manualOnly
                1,            // maxAttempts
                1,            // requiredSuccesses
                skyAccess
                //? if >=26.1
                , padding
                );
        Identifier id = modId(name);
        //? if <26.1
        //LegacyGameTestPadding.register(id, padding);
        event.registerTest(id, new ConsumerGameTestInstance(id, info, body));
    }

    private static Identifier modId(String path) {
        return Identifier.fromNamespaceAndPath(IAmZombieMod.MOD_ID, path);
    }
}
