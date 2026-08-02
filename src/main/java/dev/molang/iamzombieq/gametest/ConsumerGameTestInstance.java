package dev.molang.iamzombieq.gametest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import dev.molang.iamzombieq.IAmZombieMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Bridges programmatic test bodies onto Minecraft's serializable {@link FunctionGameTestInstance} contract.
 *
 * <p>All mod tests point at one real {@code TEST_FUNCTION} entry. Its dispatcher uses the current test holder ID to
 * invoke the original body registered during {@code RegisterGameTestsEvent}. The inherited vanilla codec therefore
 * round-trips the dispatcher key and the complete {@link TestData}; a missing body fails loudly instead of decoding
 * to an empty or different test.
 */
final class ConsumerGameTestInstance extends FunctionGameTestInstance {
    static final Identifier DISPATCHER_ID =
            Identifier.fromNamespaceAndPath(IAmZombieMod.MOD_ID, "programmatic_dispatch");
    private static final ResourceKey<Consumer<GameTestHelper>> DISPATCHER_KEY =
            ResourceKey.create(Registries.TEST_FUNCTION, DISPATCHER_ID);
    private static final Map<Identifier, Consumer<GameTestHelper>> TEST_BODIES = new ConcurrentHashMap<>();

    ConsumerGameTestInstance(
            Identifier id,
            TestData<Holder<TestEnvironmentDefinition<?>>> info,
            Consumer<GameTestHelper> body) {
        super(DISPATCHER_KEY, info);
        TEST_BODIES.put(id, body);
    }

    static void dispatch(GameTestHelper helper) {
        Identifier id = helper.testInfo.id();
        Consumer<GameTestHelper> body = TEST_BODIES.get(id);
        if (body == null) {
            throw new IllegalStateException("No programmatic GameTest body registered for " + id);
        }
        body.accept(helper);
    }
}
