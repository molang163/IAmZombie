package dev.molang.iamzombieq.internal.mount;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans continuous swept-AABB samples at every voxel-face event interval.
 *
 * <p>For a translating box, the overlapped half-open voxel set can change only
 * when one of its six faces crosses an integer plane. Sampling both each event
 * and every nonempty interval between adjacent events therefore covers every
 * voxel touched by the sweep without scanning the full enclosing prism.
 */
final class SweptAabbVoxelSupercover {
    private static final double MAX_EXACT_INTEGER = 9_007_199_254_740_991.0;

    private SweptAabbVoxelSupercover() {}

    static double[] sampleFractions(
            Box origin,
            double deltaX,
            double deltaY,
            double deltaZ) {
        if (origin == null) {
            throw new NullPointerException("origin");
        }
        finite(deltaX, "deltaX");
        finite(deltaY, "deltaY");
        finite(deltaZ, "deltaZ");
        if (deltaX == 0.0 && deltaY == 0.0 && deltaZ == 0.0) {
            return new double[] {0.0};
        }

        FaceEvents[] faces = {
            new FaceEvents(origin.minX, deltaX),
            new FaceEvents(origin.maxX, deltaX),
            new FaceEvents(origin.minY, deltaY),
            new FaceEvents(origin.maxY, deltaY),
            new FaceEvents(origin.minZ, deltaZ),
            new FaceEvents(origin.maxZ, deltaZ)
        };
        List<Double> samples = new ArrayList<>();
        append(samples, 0.0);
        double previous = 0.0;
        while (true) {
            double next = Double.POSITIVE_INFINITY;
            for (FaceEvents face : faces) {
                if (face.hasNext()) {
                    next = Math.min(next, face.fraction());
                }
            }
            if (!Double.isFinite(next)) {
                break;
            }
            appendInterior(samples, previous, next);
            append(samples, next);
            previous = next;
            for (FaceEvents face : faces) {
                if (face.hasNext()
                        && Double.compare(face.fraction(), next) == 0) {
                    face.advance();
                }
            }
        }
        appendInterior(samples, previous, 1.0);
        append(samples, 1.0);

        double[] fractions = new double[samples.size()];
        for (int index = 0; index < samples.size(); index++) {
            fractions[index] = samples.get(index);
        }
        return fractions;
    }

    private static void appendInterior(
            List<Double> samples, double left, double right) {
        if (!(left < right)) {
            return;
        }
        double midpoint = left + (right - left) * 0.5;
        if (!(midpoint > left && midpoint < right)) {
            midpoint = Math.nextUp(left);
        }
        if (midpoint > left && midpoint < right) {
            append(samples, midpoint);
        }
    }

    private static void append(List<Double> samples, double fraction) {
        if (samples.isEmpty()
                || Double.compare(
                                samples.get(samples.size() - 1),
                                fraction)
                        != 0) {
            samples.add(fraction);
        }
    }

    private static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    name + " must be finite: " + value);
        }
        return value;
    }

    record Box(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ) {
        Box {
            minX = finite(minX, "minX");
            minY = finite(minY, "minY");
            minZ = finite(minZ, "minZ");
            maxX = finite(maxX, "maxX");
            maxY = finite(maxY, "maxY");
            maxZ = finite(maxZ, "maxZ");
            if (!(minX < maxX)
                    || !(minY < maxY)
                    || !(minZ < maxZ)) {
                throw new IllegalArgumentException(
                        "Swept AABB must have positive extent");
            }
        }
    }

    private static final class FaceEvents {
        private final double start;
        private final double end;
        private final double delta;
        private final int direction;
        private long plane;
        private double fraction;
        private boolean hasNext;

        private FaceEvents(double start, double delta) {
            this.start = start;
            this.delta = delta;
            this.end = finite(start + delta, "face endpoint");
            requireExactIntegerRange(start);
            requireExactIntegerRange(end);
            if (delta > 0.0) {
                direction = 1;
                plane =
                        Math.addExact(
                                (long) Math.floor(start), 1L);
            } else if (delta < 0.0) {
                direction = -1;
                plane =
                        Math.subtractExact(
                                (long) Math.ceil(start), 1L);
            } else {
                direction = 0;
            }
            seekNext();
        }

        private boolean hasNext() {
            return hasNext;
        }

        private double fraction() {
            if (!hasNext) {
                throw new IllegalStateException(
                        "Voxel-face event stream is exhausted");
            }
            return fraction;
        }

        private void advance() {
            if (!hasNext) {
                throw new IllegalStateException(
                        "Voxel-face event stream is exhausted");
            }
            plane = Math.addExact(plane, direction);
            seekNext();
        }

        private void seekNext() {
            hasNext = false;
            while (direction != 0 && planeIsInterior()) {
                double candidate = (plane - start) / delta;
                if (candidate > 0.0 && candidate < 1.0) {
                    fraction = candidate;
                    hasNext = true;
                    return;
                }
                plane = Math.addExact(plane, direction);
            }
        }

        private boolean planeIsInterior() {
            return direction > 0 ? plane < end : plane > end;
        }

        private static void requireExactIntegerRange(double coordinate) {
            if (coordinate < -MAX_EXACT_INTEGER
                    || coordinate > MAX_EXACT_INTEGER) {
                throw new IllegalArgumentException(
                        "Swept face exceeds exact integer-plane range: "
                                + coordinate);
            }
        }
    }
}
