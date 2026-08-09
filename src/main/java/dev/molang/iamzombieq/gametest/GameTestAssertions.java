package dev.molang.iamzombieq.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * Typed compatibility seam for boolean GameTest assertions whose messages are authored as literal strings.
 */
final class GameTestAssertions {
    private GameTestAssertions() {
    }

    static void assertTrue(GameTestHelper delegate, boolean condition, String message) {
        delegate.assertTrue(condition, Component.literal(message));
    }

    static void assertFalse(GameTestHelper delegate, boolean condition, String message) {
        delegate.assertFalse(condition, Component.literal(message));
    }

    static void fail(GameTestHelper delegate, String message) {
        delegate.fail(Component.literal(message));
    }
}
