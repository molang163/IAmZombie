package dev.molang.iamzombieq.internal.mount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.junit.jupiter.api.Test;

class SpiderVehicleImpulseRelayTest {
    @Test
    void markerIsConsumedExactlyOnceByTheMatchingConnectionAndVehicle() {
        Connection connection = connection();
        UUID spider = UUID.randomUUID();

        mark(connection, spider, 1.0, 3.0, 0.25, 0.75);
        assertTrue(SpiderVehicleImpulseRelay.consume(connection, spider));
        assertFalse(
                SpiderVehicleImpulseRelay.consume(connection, spider),
                "consume removes the one-shot marker");
    }

    @Test
    void vehicleMismatchConsumesStaleMarkerWithoutCrossScopeReuse() {
        Connection connection = connection();
        UUID previousSpider = UUID.randomUUID();
        UUID currentSpider = UUID.randomUUID();

        mark(connection, previousSpider, 1.0, 3.0, 0.25, 0.75);
        assertFalse(
                SpiderVehicleImpulseRelay.consume(
                        connection, currentSpider));
        assertFalse(
                SpiderVehicleImpulseRelay.consume(
                        connection, previousSpider));
    }

    @Test
    void connectionsAreIndependentAndExplicitClearRemovesPendingState() {
        Connection first = connection();
        Connection second = connection();
        UUID firstSpider = UUID.randomUUID();
        UUID secondSpider = UUID.randomUUID();

        mark(first, firstSpider, 1.0, 3.0, 0.25, 0.75);
        mark(second, secondSpider, 2.0, 4.0, 0.5, 1.0);
        assertEquals(2, SpiderVehicleImpulseRelay.markerCount());

        SpiderVehicleImpulseRelay.clear(first);
        assertFalse(
                SpiderVehicleImpulseRelay.consume(first, firstSpider));
        assertTrue(
                SpiderVehicleImpulseRelay.consume(second, secondSpider));
        assertEquals(0, SpiderVehicleImpulseRelay.markerCount());
    }

    @Test
    void directlyObservedFlagSuppressesItsLaterTrackerMarkExactlyOnce() {
        Connection connection = connection();
        UUID spider = UUID.randomUUID();

        suppress(connection, spider, 1.0, 3.0, 0.25, 0.75);
        mark(connection, spider, 1.0, 3.0, 0.25, 0.75);
        assertFalse(SpiderVehicleImpulseRelay.consume(connection, spider));

        mark(connection, spider, 1.0, 3.0, 0.25, 0.75);
        assertTrue(SpiderVehicleImpulseRelay.consume(connection, spider));
    }

    @Test
    void decayedOrRedirectedHorizontalVelocityIsTheSameCoveredImpulse() {
        Connection decayedToZero = connection();
        Connection sameMagnitude = connection();
        Connection lowerMagnitude = connection();
        UUID spider = UUID.randomUUID();

        suppress(decayedToZero, spider, 1.0, 3.0, 3.0, 4.0);
        mark(decayedToZero, spider, 1.0, 3.0, 0.0, 0.0);
        assertFalse(
                SpiderVehicleImpulseRelay.consume(
                        decayedToZero, spider),
                "normal server travel may consume the direct impulse before tracker observation");

        suppress(sameMagnitude, spider, 1.0, 3.0, 3.0, 4.0);
        mark(sameMagnitude, spider, 1.0, 3.0, -4.0, 3.0);
        assertFalse(
                SpiderVehicleImpulseRelay.consume(
                        sameMagnitude, spider),
                "a direction change with the same horizontal norm remains within the recorded impulse");

        suppress(lowerMagnitude, spider, 1.0, 3.0, 3.0, 4.0);
        mark(lowerMagnitude, spider, 1.0, 3.0, -2.0, 1.0);
        assertFalse(
                SpiderVehicleImpulseRelay.consume(
                        lowerMagnitude, spider),
                "a smaller horizontal norm remains within the recorded impulse");
    }

    @Test
    void increasedHorizontalVelocityOrHorizontalPositionPublishesANewImpulse() {
        Connection xChanged = connection();
        Connection zChanged = connection();
        Connection movementChanged = connection();
        UUID spider = UUID.randomUUID();

        suppress(xChanged, spider, 1.0, 3.0, 3.0, 4.0);
        mark(xChanged, spider, 1.5, 3.0, 3.0, 4.0);
        assertTrue(
                SpiderVehicleImpulseRelay.consume(
                        xChanged, spider),
                "a changed authoritative x is a new impulse generation");

        suppress(zChanged, spider, 1.0, 3.0, 3.0, 4.0);
        mark(zChanged, spider, 1.0, 3.5, 3.0, 4.0);
        assertTrue(
                SpiderVehicleImpulseRelay.consume(
                        zChanged, spider),
                "a changed authoritative z is a new impulse generation");

        suppress(movementChanged, spider, 1.0, 3.0, 3.0, 4.0);
        mark(movementChanged, spider, 1.0, 3.0, 6.0, 8.0);
        assertTrue(
                SpiderVehicleImpulseRelay.consume(
                        movementChanged, spider),
                "a larger horizontal velocity bound is a new impulse generation");
    }

    private static void mark(
            Connection connection,
            UUID spider,
            double x,
            double z,
            double deltaX,
            double deltaZ) {
        SpiderVehicleImpulseRelay.mark(
                connection,
                spider,
                x,
                z,
                deltaX,
                deltaZ);
    }

    private static void suppress(
            Connection connection,
            UUID spider,
            double x,
            double z,
            double deltaX,
            double deltaZ) {
        SpiderVehicleImpulseRelay.suppressNextMark(
                connection,
                spider,
                x,
                z,
                deltaX,
                deltaZ);
    }

    private static Connection connection() {
        return new Connection(PacketFlow.CLIENTBOUND);
    }
}
