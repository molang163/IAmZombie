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
 * FakePlayer- and connected-player-driven NeoForge GameTests for the MOB-targeting, SLEEP (bed/coffin) and DOOR
 * (break-speed) domains of {@code iamzombieq} (MC 26.2 / NeoForge 26.2.0.25-beta). A sibling of
 * {@link IAmZombieGameTests} that registers an INDEPENDENT set of tests on the same MOD-bus
 * {@link RegisterGameTestsEvent} via the shared {@link IAmZombieGameTestRegistry}. Most tests use the shared HARD
 * environment ({@code env_hard}); the two connected coffin lifecycle tests use separate environments.
 *
 * <p>Registration / structure mechanics mirror {@link IAmZombieGameTests} exactly: each test uses the shared
 * serializable {@link ConsumerGameTestInstance} function bridge, and all reuse the shipped 1x1x1 all-air
 * {@code data/iamzombieq/structure/empty_test.nbt} structure. Generous {@code padding} spaces batched tests apart so
 * each test's tight entity-search radius / local block positions only see its own structure.
 *
 * <p>HARD difficulty is used throughout; the MOB / SLEEP / DOOR / grudge / swarm tests are difficulty-agnostic so
 * HARD is safe.
 */
public final class IAmZombieMobSleepGameTests {

    private static final String STRUCTURE = "empty_test";

    private IAmZombieMobSleepGameTests() {
    }

    static void registerAll(
            RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> defaultEnv,
            Holder<TestEnvironmentDefinition<?>> hardEnv,
            Holder<TestEnvironmentDefinition<?>> coffinVoteEnv,
            Holder<TestEnvironmentDefinition<?>> coffinTimeoutEnv) {
        // MOB: who may target the zombie player (the LivingChangeTargetEvent deny-list).
        register(event, "mob_undead_ignores_zombie_player", hardEnv, IAmZombieMobSleepGameTestBodies::mobUndeadIgnoresZombiePlayer);
        register(event, "mob_iron_golem_not_fooled_by_mask", hardEnv, IAmZombieMobSleepGameTestBodies::mobIronGolemNotFooledByMask);
        register(event, "mob_axolotl_attacks_drowned_form", hardEnv, IAmZombieMobSleepGameTestBodies::mobAxolotlAttacksDrownedForm);
        register(event, "mob_axolotl_ignores_normal_form", hardEnv, IAmZombieMobSleepGameTestBodies::mobAxolotlIgnoresNormalForm);
        register(event, "mob_trader_llama_attacks_normal_form", hardEnv, IAmZombieMobSleepGameTestBodies::mobTraderLlamaAttacksNormalForm);
        register(event, "mob_trader_llama_ignores_zombified_piglin_form", hardEnv, IAmZombieMobSleepGameTestBodies::mobTraderLlamaIgnoresZombifiedPiglinForm);

        // SLEEP: a zombie player's bed right-click explodes the bed.
        register(event, "sleep_bed_explodes_on_right_click", hardEnv, IAmZombieMobSleepGameTestBodies::sleepBedExplodesOnRightClick);
        register(event, "coffin_break_drops_one", hardEnv, IAmZombieMobSleepGameTestBodies::coffinBreakDropsExactlyOne);
        register(event, "coffin_sleep_vote_advances_and_wakes_all", coffinVoteEnv,
                IAmZombieMobSleepGameTestBodies::coffinSleepVoteAdvancesAndWakesAll);
        register(event, "coffin_sleep_timeout_wakes_without_skip", coffinTimeoutEnv,
                IAmZombieMobSleepGameTestBodies::coffinSleepTimeoutWakesWithoutSkip);

        // DOOR: empty-hand wooden-door break-speed x3 / item-in-hand no boost.
        register(event, "door_empty_hand_boosts_wooden_door_break", hardEnv, IAmZombieMobSleepGameTestBodies::doorEmptyHandBoostsWoodenDoorBreak);
        register(event, "door_item_in_hand_no_boost", hardEnv, IAmZombieMobSleepGameTestBodies::doorItemInHandNoBoost);

        // The mob-grudge test re-posts every 120 ticks to prove the grudge keeps the mob allowed
        // while engaged (past vanilla's 100t lastHurtByMob); then a 300t silent gap proves forgive-after-escape
        // (succeed at +660). Needs a long maxTicks (760 leaves ~100t headroom past the +660 succeed checkpoint).
        register(event, "mob_grudge_sticky_retaliation", hardEnv, 760, IAmZombieMobSleepGameTestBodies::mobGrudgeStickyRetaliation);

        // A zombified piglin group-angered at the zombie player is allowed to target it, restoring
        // the pack swarm). Deterministic seam test -- the headless harness does not drive mob target-goals against a
        // FakePlayer, so this proves the deny-list mechanism the group-anger mixin depends on (idle piglin denied ->
        // angered piglin allowed; guard skips table attackers). The mixin applying is confirmed by server boot.
        register(event, "mob_piglin_angered_kin_allowed", hardEnv, IAmZombieMobSleepGameTestBodies::mobPiglinAngeredKinAllowed);
    }

    private static void register(
            RegisterGameTestsEvent event,
            String name,
            Holder<TestEnvironmentDefinition<?>> environment,
            Consumer<GameTestHelper> body) {
        register(event, name, environment, 100, body);
    }

    private static void register(
            RegisterGameTestsEvent event,
            String name,
            Holder<TestEnvironmentDefinition<?>> environment,
            int maxTicks,
            Consumer<GameTestHelper> body) {
        TestData<Holder<TestEnvironmentDefinition<?>>> info = new TestData<>(
                environment,
                modId(STRUCTURE),
                maxTicks,     // maxTicks
                0,            // setupTicks
                true,         // required
                Rotation.NONE,
                false,        // manualOnly
                1,            // maxAttempts
                1,            // requiredSuccesses
                false,        // skyAccess
                8);           // padding
        Identifier id = modId(name);
        event.registerTest(id, new ConsumerGameTestInstance(id, info, body));
    }

    private static Identifier modId(String path) {
        return Identifier.fromNamespaceAndPath(IAmZombieMod.MOD_ID, path);
    }
}
