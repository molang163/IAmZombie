package dev.molang.iamzombieq.internal.mount;

import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.Connection;

/**
 * Connection/vehicle-bound one-shot marker for carrying a vanilla entity
 * impulse across the level-tracker/connection-tick boundary.
 *
 * <p>Keys are weak so a disconnected listener cannot be retained. Values are
 * vehicle UUIDs rather than entities or players, avoiding a value-to-key
 * reference cycle. Every read removes the marker, including a vehicle mismatch.
 */
final class SpiderVehicleImpulseRelay {
    private static final WeakHashMap<Connection, Marker> MARKERS =
            new WeakHashMap<>();

    private SpiderVehicleImpulseRelay() {}

    static synchronized void mark(
            Connection connection,
            UUID vehicleId,
            double x,
            double z,
            double deltaX,
            double deltaZ) {
        Connection key = Objects.requireNonNull(connection, "connection");
        UUID id = Objects.requireNonNull(vehicleId, "vehicleId");
        Marker current = MARKERS.get(key);
        if (current != null
                && current.suppressNextMark
                && id.equals(current.vehicleId)
                && current.suppression.covers(x, z, deltaX, deltaZ)) {
            MARKERS.remove(key);
            return;
        }
        MARKERS.put(key, new Marker(id, false, null));
    }

    static synchronized boolean consume(
            Connection connection, UUID currentVehicleId) {
        Marker marker =
                MARKERS.remove(
                        Objects.requireNonNull(connection, "connection"));
        return Objects.requireNonNull(
                                currentVehicleId, "currentVehicleId")
                        .equals(marker == null ? null : marker.vehicleId)
                && !marker.suppressNextMark;
    }

    static synchronized void suppressNextMark(
            Connection connection,
            UUID vehicleId,
            double x,
            double z,
            double deltaX,
            double deltaZ) {
        MARKERS.put(
                Objects.requireNonNull(connection, "connection"),
                new Marker(
                        Objects.requireNonNull(vehicleId, "vehicleId"),
                        true,
                        Suppression.at(x, z, deltaX, deltaZ)));
    }

    static synchronized void clear(Connection connection) {
        MARKERS.remove(Objects.requireNonNull(connection, "connection"));
    }

    static synchronized int markerCount() {
        return MARKERS.size();
    }

    private record Marker(
            UUID vehicleId,
            boolean suppressNextMark,
            Suppression suppression) {}

    private record Suppression(
            double x,
            double z,
            double horizontalVelocityBound) {
        private static Suppression at(
                double x, double z, double deltaX, double deltaZ) {
            double magnitude = Math.hypot(deltaX, deltaZ);
            return new Suppression(
                    x,
                    z,
                    Double.isFinite(x)
                                    && Double.isFinite(z)
                                    && Double.isFinite(magnitude)
                            ? Math.nextUp(magnitude)
                            : Double.NaN);
        }

        private boolean covers(
                double currentX,
                double currentZ,
                double currentDeltaX,
                double currentDeltaZ) {
            double currentMagnitude =
                    Math.hypot(currentDeltaX, currentDeltaZ);
            return Double.isFinite(currentX)
                    && Double.isFinite(currentZ)
                    && currentX == x
                    && currentZ == z
                    && Double.isFinite(horizontalVelocityBound)
                    && Double.isFinite(currentMagnitude)
                    && currentMagnitude <= horizontalVelocityBound;
        }
    }
}
