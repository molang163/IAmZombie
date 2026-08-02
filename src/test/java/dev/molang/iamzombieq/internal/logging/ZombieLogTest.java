package dev.molang.iamzombieq.internal.logging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZombieLogTest {
    @Test
    void disabledSkipsBothTheSupplierAndTheSink() {
        int[] supplierCalls = {0};
        List<String> sunk = new ArrayList<>();

        ZombieLog.debug(() -> false, () -> {
            supplierCalls[0]++;
            return "should never be built";
        }, sunk::add);

        assertEquals(0, supplierCalls[0], "the supplier must not be evaluated when disabled");
        assertEquals(0, sunk.size(), "the sink must not be called when disabled");
    }

    @Test
    void enabledEvaluatesTheSupplierExactlyOnceAndPassesTheMessageThrough() {
        int[] supplierCalls = {0};
        List<String> sunk = new ArrayList<>();

        ZombieLog.debug(() -> true, () -> {
            supplierCalls[0]++;
            return "state.example uuid=abc";
        }, sunk::add);

        assertEquals(1, supplierCalls[0], "the supplier must be evaluated exactly once when enabled");
        assertEquals(List.of("state.example uuid=abc"), sunk, "the sink must receive the supplier's message unchanged");
    }

    @Test
    void enabledSupplierThrowingIsSwallowedAndSkipsMessageAndSink() {
        int[] messageCalls = {0};
        List<String> sunk = new ArrayList<>();

        assertDoesNotThrow(() -> ZombieLog.debug(() -> {
            throw new RuntimeException("config read blew up");
        }, () -> {
            messageCalls[0]++;
            return "should never be built";
        }, sunk::add));

        assertEquals(0, messageCalls[0], "the message supplier must not run when the enabled check itself throws");
        assertEquals(0, sunk.size(), "the sink must not run when the enabled check itself throws");
    }

    @Test
    void messageSupplierThrowingIsSwallowedAndSkipsSink() {
        List<String> sunk = new ArrayList<>();

        assertDoesNotThrow(() -> ZombieLog.debug(() -> true, () -> {
            throw new RuntimeException("message build blew up");
        }, sunk::add));

        assertEquals(0, sunk.size(), "the sink must not run when the message supplier throws");
    }

    @Test
    void sinkThrowingIsSwallowedButEnabledAndMessageEachRanExactlyOnce() {
        int[] enabledCalls = {0};
        int[] messageCalls = {0};

        assertDoesNotThrow(() -> ZombieLog.debug(() -> {
            enabledCalls[0]++;
            return true;
        }, () -> {
            messageCalls[0]++;
            return "state.example uuid=abc";
        }, message -> {
            throw new RuntimeException("sink blew up");
        }));

        assertEquals(1, enabledCalls[0], "the enabled check must still have run exactly once");
        assertEquals(1, messageCalls[0], "the message supplier must still have run exactly once before the sink threw");
    }
}
