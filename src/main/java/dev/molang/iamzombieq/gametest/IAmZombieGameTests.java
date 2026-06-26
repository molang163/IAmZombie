package dev.molang.iamzombieq.gametest;

import java.util.function.Consumer;

import dev.molang.iamzombieq.IAmZombieMod;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * FakePlayer-driven NeoForge GameTest harness for {@code iamzombieq} (MC 26.2 / NeoForge 26.2.0.6-beta).
 *
 * <p><b>Registration.</b> MC 26.2 dropped the old {@code @GameTest}/{@code @GameTestHolder} annotations. Tests are
 * registered on the MOD-bus {@link RegisterGameTestsEvent} (auto-subscribed via {@link EventBusSubscriber} — the
 * event implements {@code IModBusEvent}, so it routes to the mod bus without touching {@code IAmZombieMod}). Each
 * test is a {@link ConsumerGameTestInstance} holding its body inline; we never use the {@code TEST_FUNCTION}
 * loader path (that registry is frozen by the time this event fires).
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
@EventBusSubscriber(modid = IAmZombieMod.MOD_ID)
public final class IAmZombieGameTests {

    private static final String STRUCTURE = "empty_test";

    private IAmZombieGameTests() {
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        // A no-op environment (empty AllOf) for tests that don't need a specific world setup.
        Holder<TestEnvironmentDefinition<?>> defaultEnv =
                event.registerEnvironment(modId("env_default"));
        // HARD difficulty so the villager-infection chance is at its maximum (deterministic conversion).
        Holder<TestEnvironmentDefinition<?>> hardEnv =
                event.registerEnvironment(modId("env_hard"), new TestEnvironmentDefinition.SetDifficulty(Difficulty.HARD));

        register(event, "smoke", defaultEnv, false, 100, IAmZombieGameTestBodies::smoke);

        register(event, "food_human_hunger", hardEnv, false, 100, IAmZombieGameTestBodies::foodHumanHunger);
        register(event, "baby_grow", hardEnv, false, 100, IAmZombieGameTestBodies::babyGrow);
        register(event, "infection_villager", hardEnv, false, 100, IAmZombieGameTestBodies::infectionVillager);
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
                skyAccess,
                8);           // padding
        event.registerTest(modId(name), new ConsumerGameTestInstance(info, body));
    }

    private static Identifier modId(String path) {
        return Identifier.fromNamespaceAndPath(IAmZombieMod.MOD_ID, path);
    }
}
