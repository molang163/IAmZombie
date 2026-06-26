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
 * FakePlayer-driven NeoForge GameTest harness for the FORM (§2.1) + ATTR (§2.2) acceptance rows of {@code iamzombieq}
 * (MC 26.2 / NeoForge 26.2.0.6-beta). Sibling to {@link IAmZombieGameTests}; it mirrors that harness's proven
 * registration path (MOD-bus {@link RegisterGameTestsEvent}, inline {@link ConsumerGameTestInstance} bodies, the
 * shared {@code empty_test} 1x1x1 air structure) exactly, and only differs in the test bodies it registers.
 *
 * <p><b>Distinct environment ids.</b> This harness registers its OWN environments — {@code env_default_form} and
 * {@code env_hard_form} — and never reuses {@link IAmZombieGameTests}'s {@code env_default}/{@code env_hard}.
 * Re-registering an already-registered environment id would crash the test server, so the suffix keeps the two
 * harnesses independent. The HARD-difficulty environment is used so any difficulty-dependent handler branch is
 * deterministic; the FORM/ATTR rows here are difficulty-independent, but HARD keeps them robust either way.
 */
@EventBusSubscriber(modid = IAmZombieMod.MOD_ID)
public final class IAmZombieFormGameTests {

    private static final String STRUCTURE = "empty_test";

    private IAmZombieFormGameTests() {
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        // A no-op environment (empty AllOf) for tests that don't need a specific world setup. Registered under a
        // FORM-unique id so it never collides with the sibling harness's env_default.
        Holder<TestEnvironmentDefinition<?>> defaultEnv =
                event.registerEnvironment(modId("env_default_form"));
        // HARD difficulty so any difficulty-gated handler branch is deterministic (FORM/ATTR here are
        // difficulty-independent, but this keeps the harness robust). FORM-unique id to avoid an env_hard collision.
        Holder<TestEnvironmentDefinition<?>> hardEnv =
                event.registerEnvironment(modId("env_hard_form"), new TestEnvironmentDefinition.SetDifficulty(Difficulty.HARD));

        register(event, "form_default_state", defaultEnv, false, 100, IAmZombieFormGameTestBodies::formDefaultState);
        register(event, "form_creative_giant_kill_becomes_giant", hardEnv, false, 100, IAmZombieFormGameTestBodies::formCreativeGiantKillBecomesGiant);
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
