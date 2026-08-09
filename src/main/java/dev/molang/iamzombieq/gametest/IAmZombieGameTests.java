package dev.molang.iamzombieq.gametest;

import java.util.function.Consumer;

import dev.molang.iamzombieq.IAmZombieMod;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * FakePlayer-driven NeoForge GameTest harness for {@code iamzombieq} (MC 26.2 / NeoForge 26.2.0.25-beta).
 *
 * <p><b>Registration.</b> MC 26.2 dropped the old {@code @GameTest}/{@code @GameTestHolder} annotations. Tests are
 * registered on the MOD-bus {@link RegisterGameTestsEvent}; this suite is driven by the shared
 * {@link IAmZombieGameTestRegistry} (the sole {@code @EventBusSubscriber}), which registers the two shared
 * environments once and calls {@link #registerAll} here. Each test keeps its original body behind the shared
 * {@link ConsumerGameTestInstance} function dispatcher, which is registered before the built-in
 * {@code TEST_FUNCTION} registry freezes.
 *
 * <p><b>Structure.</b> {@link TestData} requires a non-null structure {@link Identifier}; there is no built-in
 * empty structure, so this harness ships a minimal 1x1x1 all-air {@code StructureTemplate} NBT at
 * {@code data/iamzombieq/structure/empty_test.nbt} (DataVersion 4903), auto-loaded by the framework's
 * {@code StructureTemplateManager}.
 *
 * <p><b>Environments.</b> A no-op environment for the smoke test, plus a HARD-difficulty environment for the
 * gameplay tests. {@code SetDifficulty} matters for the infection chance (it scales with difficulty, and is 1.0 on
 * HARD so the conversions are deterministic). Each test is given generous {@code padding} so batched tests in the
 * shared level are spaced well apart and a tight entity-search radius only sees the test's own structure.
 */
public final class IAmZombieGameTests {

    private static final String STRUCTURE = "empty_test";

    private IAmZombieGameTests() {
    }

    static void registerAll(
            RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> defaultEnv,
            Holder<TestEnvironmentDefinition<?>> hardEnv) {
        register(event, "smoke", defaultEnv, false, 100, IAmZombieGameTestBodies::smoke);

        register(event, "food_human_hunger", hardEnv, false, 100, IAmZombieGameTestBodies::foodHumanHunger);
        register(event, "baby_grow", hardEnv, false, 100, IAmZombieGameTestBodies::babyGrow);
        register(event, "infection_villager", hardEnv, false, 100, IAmZombieGameTestBodies::infectionVillager);
        register(event, "infection_villager_no_kin_aggro", hardEnv, false, 100, IAmZombieGameTestBodies::infectionVillagerNoKinAggro);
        register(event, "infection_villager_sweep_grace", hardEnv, false, 100, IAmZombieGameTestBodies::infectionVillagerSweepGrace);
        register(event, "infection_piglin_sweep_grace", hardEnv, false, 100, IAmZombieGameTestBodies::infectionPiglinSweepGrace);
        register(event, "infection_pig_normal_form_blocked", hardEnv, false, 100, IAmZombieGameTestBodies::infectionPigNormalFormBlocked);
        register(event, "infection_pig_piglin_form_spreads", hardEnv, false, 100, IAmZombieGameTestBodies::infectionPigPiglinFormSpreads);
        register(event, "husk_hunger", hardEnv, false, 100, IAmZombieGameTestBodies::huskHunger);
    }

    private static void register(
            RegisterGameTestsEvent event,
            String name,
            Holder<TestEnvironmentDefinition<?>> environment,
            boolean skyAccess,
            int maxTicks,
            Consumer<GameTestHelper> body) {
        TestData<Holder<TestEnvironmentDefinition<?>>> info = new TestData<>(
                environment,
                modId(STRUCTURE),
                maxTicks,
                0,            // setupTicks
                true,         // required
                Rotation.NONE,
                false,        // manualOnly
                1,            // maxAttempts
                1,            // requiredSuccesses
                skyAccess
                //? if >=26.1
                , 8
                );            // padding
        Identifier id = modId(name);
        //? if <26.1
        //LegacyGameTestPadding.register(id, 8);
        event.registerTest(id, new ConsumerGameTestInstance(id, info, body));
    }

    private static Identifier modId(String path) {
        return Identifier.fromNamespaceAndPath(IAmZombieMod.MOD_ID, path);
    }
}
