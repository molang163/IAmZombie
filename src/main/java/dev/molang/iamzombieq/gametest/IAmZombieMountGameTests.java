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
 * Self-registering FakePlayer-driven NeoForge GameTest harness for the {@code iamzombieq} MOUNT system
 * (catalog &sect;2.12 MNT). Independent of {@link IAmZombieGameTests}: it owns its own {@link EventBusSubscriber}
 * subscription and its own uniquely-named environments ({@code env_hard_mount} / {@code env_default_mount}) so the
 * two harnesses can coexist in the same headless {@code gameTestServer} run without colliding on environment ids.
 *
 * <p>Registration mirrors {@link IAmZombieGameTests}: MC 26.2 dropped the {@code @GameTest} annotations, so each test
 * is a {@link ConsumerGameTestInstance} holding its body inline, registered on the MOD-bus
 * {@link RegisterGameTestsEvent}; the shared {@code empty_test} structure (a 1x1x1 air template) is reused. The mount
 * interaction bodies drive the production {@code ZombieMountEvents.onEntityInteract} handler by posting the real
 * {@link net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract} to the game bus
 * (see {@link IAmZombieMountGameTestBodies}).
 *
 * <p>The mount tests do not depend on difficulty, but they run under a HARD environment ({@code env_hard_mount}) for
 * parity with the gameplay harness; a default (no-op) environment ({@code env_default_mount}) is registered as a
 * spare for any test that should not pin difficulty.
 */
@EventBusSubscriber(modid = IAmZombieMod.MOD_ID)
public final class IAmZombieMountGameTests {

    private static final String STRUCTURE = "empty_test";

    private IAmZombieMountGameTests() {
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        // Spare no-op environment (unique id so it never collides with IAmZombieGameTests#env_default).
        Holder<TestEnvironmentDefinition<?>> defaultEnv =
                event.registerEnvironment(modId("env_default_mount"));
        // HARD-difficulty environment (unique id; mirrors env_hard but kept separate to avoid duplicate-id crashes).
        Holder<TestEnvironmentDefinition<?>> hardEnv =
                event.registerEnvironment(modId("env_hard_mount"), new TestEnvironmentDefinition.SetDifficulty(Difficulty.HARD));

        // MNT-001: spider taming progress (graded per food; not instant; threshold binds ownership).
        register(event, "mnt_spider_tame_rotten_flesh", hardEnv, 100, IAmZombieMountGameTestBodies::spiderTameProgressRottenFlesh);
        register(event, "mnt_spider_tame_super_rotten_flesh", hardEnv, 100, IAmZombieMountGameTestBodies::spiderTameProgressSuperRottenFlesh);
        register(event, "mnt_spider_tame_threshold_binds_owner", hardEnv, 100, IAmZombieMountGameTestBodies::spiderTameReachesThresholdBindsOwner);

        // MNT-002: owned spider heal.
        register(event, "mnt_spider_heal", hardEnv, 100, IAmZombieMountGameTestBodies::ownedSpiderHealsWhenFedSuperRottenFlesh);

        // MNT-013: undead horse auto-tame on interact.
        register(event, "mnt_zombie_horse_auto_tame", hardEnv, 100, IAmZombieMountGameTestBodies::wildZombieHorseAutoTamesOnInteract);
        register(event, "mnt_skeleton_horse_auto_tame", hardEnv, 100, IAmZombieMountGameTestBodies::wildSkeletonHorseAutoTamesOnInteract);

        // MNT-014/015: undead horse feed heal / full-health refusal.
        register(event, "mnt_zombie_horse_heal", hardEnv, 100, IAmZombieMountGameTestBodies::damagedZombieHorseHealsWhenFed);
        register(event, "mnt_zombie_horse_full_health_refuses", hardEnv, 100, IAmZombieMountGameTestBodies::fullHealthZombieHorseFeedKeepsFoodAndCancels);

        // MNT-016: normal horse refused.
        register(event, "mnt_normal_horse_refused", hardEnv, 100, IAmZombieMountGameTestBodies::normalHorseInteractIsRefusedAndCancelled);

        // MNT-003: tamed-spider ride allowed / untamed refused.
        register(event, "mnt_untamed_spider_ride_refused", hardEnv, 100, IAmZombieMountGameTestBodies::untamedSpiderRideRefused);

        // MNT-011: baby rides chicken / adult refused.
        register(event, "mnt_adult_cannot_ride_chicken", hardEnv, 100, IAmZombieMountGameTestBodies::adultCannotRideChicken);
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
                maxTicks,
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
