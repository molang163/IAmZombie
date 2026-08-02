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

/** Registers the runtime regression GameTests driven by the shared {@link IAmZombieGameTestRegistry}. */
public final class IAmZombieFixRegressionGameTests {

    private static final String STRUCTURE = "empty_test";

    private IAmZombieFixRegressionGameTests() {
    }

    static void registerAll(
            RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> defaultEnv,
            Holder<TestEnvironmentDefinition<?>> hardEnv) {
        register(event, "reg_nautilus_saddle_not_fabricated", hardEnv,
                IAmZombieFixRegressionGameTestBodies::nautilusSaddleNotFabricated);
        register(event, "reg_piglin_conversion_not_baby_and_armed", hardEnv,
                IAmZombieFixRegressionGameTestBodies::piglinConversionNotBabyAndArmed);
        register(event, "reg_cake_candle_place_not_punished", hardEnv,
                IAmZombieFixRegressionGameTestBodies::cakeCandlePlaceNotPunished);
        register(event, "reg_cake_normal_bite_still_punished", hardEnv,
                IAmZombieFixRegressionGameTestBodies::cakeNormalBiteStillPunished);
        register(event, "reg_cake_candle_on_bitten_cake_still_punished", hardEnv,
                IAmZombieFixRegressionGameTestBodies::cakeCandleOnBittenCakeStillPunished);
        register(event, "reg_lit_candlecake_body_eat_still_punished", hardEnv,
                IAmZombieFixRegressionGameTestBodies::litCandleCakeBodyEatStillPunished);
        register(event, "reg_lit_candlecake_extinguish_not_punished", hardEnv,
                IAmZombieFixRegressionGameTestBodies::litCandleCakeExtinguishNotPunished);
        // #10: giant stomp aura excludes the player's OWN tamed mounts (self-validating — an in-radius WILD mount
        // is stomped in the same tick, proving the aura fired, so the owned-unchanged assertion can't pass vacuously).
        register(event, "reg_giant_aura_spares_owned_horse_stomps_wild", hardEnv,
                IAmZombieFixRegressionGameTestBodies::giantAuraSparesOwnedHorseStompsWild);
        register(event, "reg_giant_aura_spares_owned_nautilus_stomps_wild", hardEnv,
                IAmZombieFixRegressionGameTestBodies::giantAuraSparesOwnedNautilusStompsWild);
        // #1: the passive walk-destruction sweep clamps its delta so a stale GIANT_LAST_POS (teleport) can't raze a
        // far-away block. Extra padding so the in-test teleport does not enter a neighbouring test's region.
        registerPadded(event, "reg_giant_sweep_clamp_bounds_teleport", hardEnv, 48,
                IAmZombieFixRegressionGameTestBodies::giantSweepClampBoundsTeleport);
    }

    private static void register(RegisterGameTestsEvent event, String name,
            Holder<TestEnvironmentDefinition<?>> environment, Consumer<GameTestHelper> body) {
        registerPadded(event, name, environment, 8, body);
    }

    // Named registerPadded (not an overloaded register) to avoid a same-shape int overload colliding across the
    // gametest files (MobSleep/Mount/GiantSun have a register(..., int maxTicks, ...) that means something different).
    private static void registerPadded(RegisterGameTestsEvent event, String name,
            Holder<TestEnvironmentDefinition<?>> environment, int padding, Consumer<GameTestHelper> body) {
        TestData<Holder<TestEnvironmentDefinition<?>>> info = new TestData<>(
                environment, modId(STRUCTURE), 200, 0, true, Rotation.NONE, false, 1, 1, false, padding);
        Identifier id = modId(name);
        event.registerTest(id, new ConsumerGameTestInstance(id, info, body));
    }

    private static Identifier modId(String path) {
        return Identifier.fromNamespaceAndPath(IAmZombieMod.MOD_ID, path);
    }
}
