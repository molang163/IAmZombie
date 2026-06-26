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
 * A SECOND, self-contained FakePlayer-driven GameTest harness for {@code iamzombieq} covering the rest of the FOOD
 * (FOOD-001..020) and INF (INF-001..005) acceptance domains, complementing the seven tests in
 * {@link IAmZombieGameTests} (which already cover cooked_beef->Hunger, baby_grow, and the villager/pig infection
 * triad). It is registered exactly the way {@link IAmZombieGameTests} is -- on the MOD-bus
 * {@link RegisterGameTestsEvent} (auto-subscribed via {@link EventBusSubscriber}) -- and reuses the SAME shipped
 * {@code data/iamzombieq/structure/empty_test.nbt} structure and the SAME {@link ConsumerGameTestInstance} body
 * holder, so it adds no new resources or production code.
 *
 * <p><b>Environments.</b> This harness registers its OWN uniquely-named environments
 * ({@code env_hard_foodinf}, {@code env_default_foodinf}) rather than reusing {@code env_hard}/{@code env_default}
 * from the sibling harness: each {@code RegisterGameTestsEvent#registerEnvironment} call must use a unique id, and a
 * duplicate id would crash the server. {@code env_hard_foodinf} sets HARD difficulty so the horse-infection chance is
 * 1.0 (deterministic conversion), matching the sibling infection tests.
 */
@EventBusSubscriber(modid = IAmZombieMod.MOD_ID)
public final class IAmZombieFoodInfGameTests {

    private static final String STRUCTURE = "empty_test";

    private IAmZombieFoodInfGameTests() {
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        // Uniquely-named environments for THIS harness (never reuse env_hard/env_default -- a duplicate id crashes).
        Holder<TestEnvironmentDefinition<?>> defaultEnv =
                event.registerEnvironment(modId("env_default_foodinf"));
        Holder<TestEnvironmentDefinition<?>> hardEnv =
                event.registerEnvironment(modId("env_hard_foodinf"), new TestEnvironmentDefinition.SetDifficulty(Difficulty.HARD));

        // FOOD: assert the rule's data-table effects (robust presence + amplifier) applied via the eat seam.
        register(event, "food_golden_apple", defaultEnv, IAmZombieFoodInfGameTestBodies::foodGoldenApple);
        register(event, "food_enchanted_golden_apple", defaultEnv, IAmZombieFoodInfGameTestBodies::foodEnchantedGoldenApple);
        register(event, "food_pufferfish", defaultEnv, IAmZombieFoodInfGameTestBodies::foodPufferfish);
        register(event, "food_spider_eye", defaultEnv, IAmZombieFoodInfGameTestBodies::foodSpiderEye);
        register(event, "food_human_hunger_amplifier", defaultEnv, IAmZombieFoodInfGameTestBodies::foodHumanHungerAmplifier);
        register(event, "food_sweet_slowness", defaultEnv, IAmZombieFoodInfGameTestBodies::foodSweetSlowness);
        register(event, "food_super_rotten_flesh_strength", defaultEnv, IAmZombieFoodInfGameTestBodies::foodSuperRottenFleshStrength);
        register(event, "food_chorus_fruit", defaultEnv, IAmZombieFoodInfGameTestBodies::foodChorusFruit);
        register(event, "food_honey_bottle", defaultEnv, IAmZombieFoodInfGameTestBodies::foodHoneyBottle);

        // INF: horse infection needs HARD (chance 1.0) for a deterministic conversion.
        register(event, "infection_horse", hardEnv, IAmZombieFoodInfGameTestBodies::infectionHorse);
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
                8);           // padding (keeps batched tests spaced so the tight entity-search radius is local)
        event.registerTest(modId(name), new ConsumerGameTestInstance(info, body));
    }

    private static Identifier modId(String path) {
        return Identifier.fromNamespaceAndPath(IAmZombieMod.MOD_ID, path);
    }
}
