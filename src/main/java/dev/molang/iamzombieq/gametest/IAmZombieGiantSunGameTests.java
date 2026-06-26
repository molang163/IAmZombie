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
 * FakePlayer-driven NeoForge GameTests for the cleanly-automatable GIANT + PIGLIN + POTION subset of the
 * {@code iamzombieq} gameplay rules. A sibling registrar to {@link IAmZombieGameTests}: same harness, same empty
 * {@code empty_test} structure, same {@link ConsumerGameTestInstance} inline-body registration path on the MOD-bus
 * {@link RegisterGameTestsEvent} (the annotation-driven loader was dropped in MC 26.2). The bodies live in
 * {@link IAmZombieGiantSunGameTestBodies}.
 *
 * <p>Uses its OWN environment id ({@code env_hard_giantsun}) so it never collides with the sibling registrar's
 * {@code env_hard} / {@code env_default}. HARD difficulty keeps the run deterministic and matches the sibling
 * gameplay tests; none of these bodies actually depend on the difficulty value (the gates they exercise are
 * form-driven), so HARD is simply a safe, consistent choice.
 *
 * <p>SUN and DROWN integration cases are intentionally NOT registered here: every remaining SUN seam is per-tick
 * (sun-burn ignition) or gated on a private sun-fire window that only the per-tick path opens, and the drowned
 * underwater-mining seam needs a submersion state a no-op-tick FakePlayer cannot set cleanly. Those are deferred to
 * their existing L0 rule coverage ({@code ZombieSunlightRulesTest} / {@code ZombieDamageRulesTest}) per the
 * green-or-defer policy.
 */
@EventBusSubscriber(modid = IAmZombieMod.MOD_ID)
public final class IAmZombieGiantSunGameTests {

    private static final String STRUCTURE = "empty_test";

    private IAmZombieGiantSunGameTests() {
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> hardEnv =
                event.registerEnvironment(modId("env_hard_giantsun"), new TestEnvironmentDefinition.SetDifficulty(Difficulty.HARD));

        register(event, "giant_swing_destroys_block_within_reach", hardEnv, 100, IAmZombieGiantSunGameTestBodies::giantSwingDestroysBlockWithinReach);
        register(event, "giant_swing_ignores_block_beyond_reach", hardEnv, 100, IAmZombieGiantSunGameTestBodies::giantSwingIgnoresBlockBeyondReach);
        register(event, "giant_swing_cooldown_blocks_second_swing", hardEnv, 100, IAmZombieGiantSunGameTestBodies::giantSwingSecondSwingBlockedByCooldown);
        register(event, "zombified_piglin_gold_durability_quarter_rate", hardEnv, 100, IAmZombieGiantSunGameTestBodies::zombifiedPiglinConsumesGoldDurabilityAtQuarterRate);
        register(event, "zombie_inverts_instant_damage_to_healing", hardEnv, 100, IAmZombieGiantSunGameTestBodies::zombiePlayerInvertsInstantDamageToHealing);
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
                false,        // skyAccess (not needed; no SUN per-tick case here)
                8);           // padding
        event.registerTest(modId(name), new ConsumerGameTestInstance(info, body));
    }

    private static Identifier modId(String path) {
        return Identifier.fromNamespaceAndPath(IAmZombieMod.MOD_ID, path);
    }
}
