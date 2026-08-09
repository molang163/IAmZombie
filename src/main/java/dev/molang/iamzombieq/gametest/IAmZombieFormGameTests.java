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
 * FakePlayer- and connected-ServerPlayer-driven NeoForge GameTest harness for the FORM and ATTR
 * test cases of {@code iamzombieq} (MC 26.2 / NeoForge 26.2.0.25-beta). Sibling to
 * {@link IAmZombieGameTests}; it mirrors that harness's proven registration path (MOD-bus
 * {@link RegisterGameTestsEvent}, serializable {@link ConsumerGameTestInstance} function bridge, and the shared
 * {@code empty_test} 1x1x1 air structure) exactly, and only differs in the test bodies it registers.
 *
 * <p><b>Environments.</b> This suite receives the two shared environments ({@code env_default} no-op /
 * {@code env_hard}) from {@link IAmZombieGameTestRegistry}. The HARD-difficulty environment is used so any
 * difficulty-dependent handler branch is deterministic; the FORM/ATTR rows here are difficulty-independent, but HARD
 * keeps them robust either way.
 */
public final class IAmZombieFormGameTests {

    private static final String STRUCTURE = "empty_test";

    private IAmZombieFormGameTests() {
    }

    static void registerAll(
            RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> defaultEnv,
            Holder<TestEnvironmentDefinition<?>> hardEnv) {
        register(event, "form_default_state", defaultEnv, false, 100, IAmZombieFormGameTestBodies::formDefaultState);
        register(event, "form_creative_giant_kill_becomes_giant", hardEnv, false, 100, IAmZombieFormGameTestBodies::formCreativeGiantKillBecomesGiant);
        register(event, "form_starvation_adult_becomes_baby_in_place", hardEnv, false, 100, IAmZombieFormGameTestBodies::starvationAdultBecomesBabyInPlace);
        register(event, "form_drowning_normal_becomes_drowned_in_place", hardEnv, false, 100, IAmZombieFormGameTestBodies::drowningNormalBecomesDrownedInPlace);
        register(event, "s1_transform_pre_giant_kill_veto", hardEnv, false, 100, IAmZombieFormGameTestBodies::s1TransformPreGiantKillVeto);
        register(event, "s1_transform_pre_giant_kill_pass", hardEnv, false, 100, IAmZombieFormGameTestBodies::s1TransformPreGiantKillPass);
        register(event, "s1_transform_pre_clone_reset_veto_preserves_state", hardEnv, false, 100, IAmZombieFormGameTestBodies::s1TransformPreCloneResetVetoPreservesState);
        register(event, "s1_transform_pre_clone_reset_pass", hardEnv, false, 100, IAmZombieFormGameTestBodies::s1TransformPreCloneResetPass);
        register(event, "s1_evolve_pre_drowning_veto_real_death", hardEnv, false, 100, IAmZombieFormGameTestBodies::s1EvolvePreDrowningVetoRealDeath);
        register(event, "s1_evolve_pre_drowning_pass_once", hardEnv, false, 100, IAmZombieFormGameTestBodies::s1EvolvePreDrowningPassOnce);
        register(event, "s1_evolve_pre_starvation_veto_real_death", hardEnv, false, 100, IAmZombieFormGameTestBodies::s1EvolvePreStarvationVetoRealDeath);
        register(event, "s1_evolve_pre_starvation_pass", hardEnv, false, 100, IAmZombieFormGameTestBodies::s1EvolvePreStarvationPass);
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
