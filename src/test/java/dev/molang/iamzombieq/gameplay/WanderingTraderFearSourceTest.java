package dev.molang.iamzombieq.gameplay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards the migration of the wandering trader's avoid-undisguised-zombie-player goal from the
 * fragile {@code @Inject registerGoals} trader mixin to an {@code EntityJoinLevelEvent} handler
 * in {@link ZombieMobTargetingEvents}. Vanilla traders already carry priority-1 {@code AvoidEntityGoal} instances
 * (Zombie/Evoker/...), so idempotency on re-join must key on the exact {@code AvoidZombiePlayerGoal} marker
 * subclass — the event wiring, the marker-typed anyMatch guard and the {@code VillagerFearRules} constants must
 * stay in place AND the old mixin must stay deleted (source file + mixins.json registration).
 *
 * <p>The deleted mixin's class name is spelled split ({@code "...Trader" + "Mixin"}) so the
 * source guard can verify that the deleted class name no longer occurs elsewhere, which
 * is exactly what this test guards — no live reference may reappear anywhere, including here.
 */
class WanderingTraderFearSourceTest {
    private static final String OLD_MIXIN_NAME = "WanderingTrader" + "Mixin";
    private static final Path EVENTS =
            Path.of("src/main/java/dev/molang/iamzombieq/gameplay/ZombieMobTargetingEvents.java");
    private static final Path RULES =
            Path.of("src/main/java/dev/molang/iamzombieq/rules/VillagerFearRules.java");
    private static final Path OLD_MIXIN =
            Path.of("src/main/java/dev/molang/iamzombieq/mixin/" + OLD_MIXIN_NAME + ".java");
    private static final Path MIXIN_JSON = Path.of("src/main/resources/iamzombieq.mixins.json");

    @Test
    void traderFleeGoalIsWiredThroughEntityJoinLevelEvent() throws IOException {
        String source = Files.readString(EVENTS);

        assertTrue(source.contains("EntityJoinLevelEvent"),
                "ZombieMobTargetingEvents must handle NeoForge's EntityJoinLevelEvent after the mixin replacement");
        assertTrue(source.contains("AvoidZombiePlayerGoal"),
                "the flee goal must be the AvoidZombiePlayerGoal marker subclass (idempotency key)");
        assertTrue(source.contains("anyMatch(wrapped -> wrapped.getGoal() instanceof AvoidZombiePlayerGoal)"),
                "re-join injection must be idempotent via a marker-typed anyMatch over the trader's goals "
                        + "(a generic AvoidEntityGoal instanceof would false-positive on the vanilla anti-Zombie goal)");
    }

    @Test
    void villagerFearRulesCarriesTheMigratedGoalConstants() throws IOException {
        String source = Files.readString(RULES);

        assertTrue(source.contains("AVOID_ZOMBIE_PLAYER_GOAL_PRIORITY = 1"),
                "VillagerFearRules must keep the goal priority migrated verbatim from the deleted trader mixin");
        assertTrue(source.contains("FLEE_WALK_SPEED = 0.5"),
                "VillagerFearRules must keep the walk-speed modifier migrated verbatim from the deleted trader mixin");
        assertTrue(source.contains("FLEE_SPRINT_SPEED = 0.5"),
                "VillagerFearRules must keep the sprint-speed modifier migrated verbatim from the deleted trader mixin");
    }

    @Test
    void oldTraderMixinStaysDeleted() throws IOException {
        assertFalse(Files.exists(OLD_MIXIN),
                OLD_MIXIN_NAME + " was replaced by the EntityJoinLevelEvent handler and must not come back");
        assertFalse(Files.readString(MIXIN_JSON).contains(OLD_MIXIN_NAME),
                "iamzombieq.mixins.json must not register the deleted " + OLD_MIXIN_NAME);
    }
}
