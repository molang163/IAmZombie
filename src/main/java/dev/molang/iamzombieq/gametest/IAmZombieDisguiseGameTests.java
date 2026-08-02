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

/** Connected-player GameTests for the disguise trade and villager-fear paths. */
public final class IAmZombieDisguiseGameTests {
    private static final String EMPTY_STRUCTURE = "empty_test";
    private static final String FEAR_STRUCTURE = "disguise_fear_test";

    private IAmZombieDisguiseGameTests() {
    }

    static void registerAll(
            RegisterGameTestsEvent event,
            Holder<TestEnvironmentDefinition<?>> defaultEnv,
            Holder<TestEnvironmentDefinition<?>> hardEnv) {
        register(event, "trade_undisguised_zombie_is_denied", EMPTY_STRUCTURE, defaultEnv, 100, 8,
                IAmZombieDisguiseGameTestBodies::undisguisedZombieIsDenied);
        register(event, "trade_disguised_zombie_opens_and_damages_mask", EMPTY_STRUCTURE, defaultEnv, 100, 8,
                IAmZombieDisguiseGameTestBodies::disguisedZombieOpensAndDamagesMask);
        register(event, "villager_fear_respects_disguise", FEAR_STRUCTURE, defaultEnv, 160, 24,
                IAmZombieDisguiseGameTestBodies::villagerFearRespectsDisguise);
        register(event, "wandering_trader_fear_respects_disguise", FEAR_STRUCTURE, defaultEnv, 100, 24,
                IAmZombieDisguiseGameTestBodies::wanderingTraderFearRespectsDisguise);
    }

    private static void register(
            RegisterGameTestsEvent event,
            String name,
            String structure,
            Holder<TestEnvironmentDefinition<?>> environment,
            int maxTicks,
            int padding,
            Consumer<GameTestHelper> body) {
        TestData<Holder<TestEnvironmentDefinition<?>>> info = new TestData<>(
                environment,
                modId(structure),
                maxTicks,
                0,            // setupTicks
                true,         // required
                Rotation.NONE,
                false,        // manualOnly
                1,            // maxAttempts
                1,            // requiredSuccesses
                false,        // skyAccess
                padding);
        Identifier id = modId(name);
        event.registerTest(id, new ConsumerGameTestInstance(id, info, body));
    }

    private static Identifier modId(String path) {
        return Identifier.fromNamespaceAndPath(IAmZombieMod.MOD_ID, path);
    }
}
