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
 * FakePlayer-driven NeoForge GameTests for the MOB-targeting, SLEEP (bed), REINF (reinforcement alert) and DOOR
 * (break-speed) domains of {@code iamzombieq} (MC 26.2 / NeoForge 26.2.0.6-beta). A sibling of
 * {@link IAmZombieGameTests} that registers an INDEPENDENT set of tests on the same MOD-bus
 * {@link RegisterGameTestsEvent}, using its own uniquely-named HARD environment ({@code env_hard_mobsleep}) so it can
 * never collide with the food/infection suite's {@code env_hard}/{@code env_default}.
 *
 * <p>Registration / structure mechanics mirror {@link IAmZombieGameTests} exactly: each test is a
 * {@link ConsumerGameTestInstance} holding its body inline (the {@code TEST_FUNCTION} loader path is frozen by the
 * time this event fires), and all reuse the shipped 1x1x1 all-air {@code data/iamzombieq/structure/empty_test.nbt}
 * structure. Generous {@code padding} spaces batched tests apart so each test's tight entity-search radius / local
 * block positions only see its own structure.
 *
 * <p>HARD difficulty is used throughout: the REINF alert test runs the real reinforcement path (HARD enables the
 * spawn attempt alongside the always-on alert), and the MOB/SLEEP/DOOR tests are difficulty-agnostic so HARD is safe.
 */
@EventBusSubscriber(modid = IAmZombieMod.MOD_ID)
public final class IAmZombieMobSleepGameTests {

    private static final String STRUCTURE = "empty_test";

    private IAmZombieMobSleepGameTests() {
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        // HARD difficulty, isolated under a unique id so it never reuses the food suite's env_hard/env_default.
        Holder<TestEnvironmentDefinition<?>> hardEnv =
                event.registerEnvironment(modId("env_hard_mobsleep"), new TestEnvironmentDefinition.SetDifficulty(Difficulty.HARD));

        // MOB: who may target the zombie player (the LivingChangeTargetEvent deny-list).
        register(event, "mob_undead_ignores_zombie_player", hardEnv, IAmZombieMobSleepGameTestBodies::mobUndeadIgnoresZombiePlayer);
        register(event, "mob_iron_golem_not_fooled_by_mask", hardEnv, IAmZombieMobSleepGameTestBodies::mobIronGolemNotFooledByMask);
        register(event, "mob_axolotl_attacks_drowned_form", hardEnv, IAmZombieMobSleepGameTestBodies::mobAxolotlAttacksDrownedForm);
        register(event, "mob_axolotl_ignores_normal_form", hardEnv, IAmZombieMobSleepGameTestBodies::mobAxolotlIgnoresNormalForm);
        register(event, "mob_trader_llama_attacks_normal_form", hardEnv, IAmZombieMobSleepGameTestBodies::mobTraderLlamaAttacksNormalForm);
        register(event, "mob_trader_llama_ignores_zombified_piglin_form", hardEnv, IAmZombieMobSleepGameTestBodies::mobTraderLlamaIgnoresZombifiedPiglinForm);

        // SLEEP: a zombie player's bed right-click explodes the bed.
        register(event, "sleep_bed_explodes_on_right_click", hardEnv, IAmZombieMobSleepGameTestBodies::sleepBedExplodesOnRightClick);

        // DOOR: empty-hand wooden-door break-speed x3 / item-in-hand no boost.
        register(event, "door_empty_hand_boosts_wooden_door_break", hardEnv, IAmZombieMobSleepGameTestBodies::doorEmptyHandBoostsWoodenDoorBreak);
        register(event, "door_item_in_hand_no_boost", hardEnv, IAmZombieMobSleepGameTestBodies::doorItemInHandNoBoost);
    }

    private static void register(
            RegisterGameTestsEvent event,
            String name,
            Holder<TestEnvironmentDefinition<?>> environment,
            Consumer<GameTestHelper> body) {
        TestData<Holder<TestEnvironmentDefinition<?>>> info = new TestData<>(
                environment,
                modId(STRUCTURE),
                100,          // maxTicks
                0,            // setupTicks
                true,         // required
                Rotation.NONE,
                false,        // manualOnly
                1,            // maxAttempts
                1,            // requiredSuccesses
                false,        // skyAccess
                8);           // padding
        event.registerTest(modId(name), new ConsumerGameTestInstance(info, body));
    }

    private static Identifier modId(String path) {
        return Identifier.fromNamespaceAndPath(IAmZombieMod.MOD_ID, path);
    }
}
