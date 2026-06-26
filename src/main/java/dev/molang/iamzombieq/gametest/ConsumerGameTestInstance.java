package dev.molang.iamzombieq.gametest;

import java.util.function.Consumer;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A {@link GameTestInstance} that wraps an inline {@link Consumer Consumer&lt;GameTestHelper&gt;} body, registered
 * programmatically via {@link IAmZombieGameTests} on the MOD-bus {@code RegisterGameTestsEvent}.
 *
 * <p>This is the "direct subclass" registration path (per run7 plan EXECUTION ORDER step 1): it holds the test body
 * directly and overrides {@link #run}, avoiding the {@code Registries.TEST_FUNCTION} loader path entirely. That
 * registry is a built-in registry populated at bootstrap and is already frozen by the time the
 * {@code RegisterGameTestsEvent} fires (the event only exposes the {@code TEST_INSTANCE} / {@code TEST_ENVIRONMENT}
 * data registries), so a {@code FunctionGameTestInstance} would have no clean seam to inject a function into. A
 * direct subclass sidesteps that.
 *
 * <p>{@link #codec()} is intentionally unsupported: the headless {@code gameTestServer} run only iterates the
 * {@code TEST_INSTANCE} registry and calls {@link #run} on each instance (verified against
 * {@code GameTestServer#evaluateTestsToRun} / {@code GameTestInfo#startTest} in MC 26.2); it never serializes the
 * registered instances, so {@code codec()} is never invoked. Returning a registered codec here would require adding
 * an entry to the frozen {@code TEST_INSTANCE_TYPE} registry, which this harness deliberately avoids.
 */
final class ConsumerGameTestInstance extends GameTestInstance {

    private final Consumer<GameTestHelper> body;

    ConsumerGameTestInstance(TestData<Holder<TestEnvironmentDefinition<?>>> info, Consumer<GameTestHelper> body) {
        super(info);
        this.body = body;
    }

    @Override
    public void run(GameTestHelper helper) {
        this.body.accept(helper);
    }

    @Override
    public MapCodec<? extends GameTestInstance> codec() {
        throw new UnsupportedOperationException(
                "ConsumerGameTestInstance is registered programmatically and never serialized; codec() is unused.");
    }

    @Override
    protected MutableComponent typeDescription() {
        return Component.literal("iamzombieq fake-player gametest");
    }
}
