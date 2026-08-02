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
 * FakePlayer- and connected-ServerPlayer-driven NeoForge GameTest harness for the {@code iamzombieq} MOUNT system
 * (catalog &sect;2.12 MNT). Sibling to {@link IAmZombieGameTests}, driven by the shared
 * {@link IAmZombieGameTestRegistry} on the MOD-bus {@link RegisterGameTestsEvent}, using the two shared environments
 * ({@code env_default} no-op / {@code env_hard}).
 *
 * <p>Registration mirrors {@link IAmZombieGameTests}: MC 26.2 dropped the {@code @GameTest} annotations, so each test
 * uses the serializable {@link ConsumerGameTestInstance} function bridge; the shared {@code empty_test} structure (a
 * 1x1x1 air template) is reused. The mount interaction bodies drive the production {@code ZombieMountEvents.onEntityInteract}
 * handler by posting the real
 * {@link net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract} to the game bus
 * (see {@link IAmZombieMountGameTestBodies}).
 *
 * <p>The mount tests do not depend on difficulty, but they run under the shared HARD environment ({@code env_hard})
 * for parity with the gameplay harness.
 */
public final class IAmZombieMountGameTests {

    private static final String STRUCTURE = "empty_test";

    private IAmZombieMountGameTests() {
    }

    static void registerAll(
            RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> defaultEnv,
            Holder<TestEnvironmentDefinition<?>> hardEnv) {
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

        // MNT-003: the connected player covers the positive owned-spider ride; FakePlayer remains suitable for the
        // untamed refusal side, where no passenger relation should be established.
        register(event, "mnt_owned_spider_ride_allowed", hardEnv, 100, IAmZombieMountGameTestBodies::ownedSpiderRideAllowed);
        register(event, "mnt_untamed_spider_ride_refused", hardEnv, 100, IAmZombieMountGameTestBodies::untamedSpiderRideRefused);

        // MNT-011: the connected player covers the positive baby ride; FakePlayer covers the adult refusal side.
        register(event, "mnt_baby_can_ride_chicken", hardEnv, 100, IAmZombieMountGameTestBodies::babyCanRideChicken);
        register(event, "mnt_adult_cannot_ride_chicken", hardEnv, 100, IAmZombieMountGameTestBodies::adultCannotRideChicken);

        // MNT-017 (A2): a horse killed by a zombie player converts to a tamed, player-owned zombie horse via the
        // shared infection pipeline, restoring the pre-death health ratio (HARD => infection chance 1.0, deterministic).
        register(event, "mnt_zombie_horse_infection_on_death", hardEnv, 100, IAmZombieMountGameTestBodies::zombieHorseInfectionOnDeath);

        // LOOT1-FIX1: a validly ridden big zombie keeps a mob/direct attack while crediting its player rider.
        register(event, "mnt_big_zombie_rider_kill_attribution", hardEnv, 100, MountedZombieKillCreditGameTest::run);
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
        Identifier id = modId(name);
        event.registerTest(id, new ConsumerGameTestInstance(id, info, body));
    }

    private static Identifier modId(String path) {
        return Identifier.fromNamespaceAndPath(IAmZombieMod.MOD_ID, path);
    }
}
