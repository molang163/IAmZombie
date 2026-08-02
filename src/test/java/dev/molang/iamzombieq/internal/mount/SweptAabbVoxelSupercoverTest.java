package dev.molang.iamzombieq.internal.mount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SweptAabbVoxelSupercoverTest {
    @Test
    void faceCrossingIsPlannedEvenWhenTheEndpointRemainsInTheSameFloorCell() {
        SweptAabbVoxelSupercover.Box box =
                new SweptAabbVoxelSupercover.Box(
                        0.1, 0.0, 0.0, 0.9, 1.0, 1.0);

        double[] fractions =
                SweptAabbVoxelSupercover.sampleFractions(
                        box, 0.2, 0.0, 0.0);
        Set<Cell> cells = covered(box, 0.2, 0.0, 0.0);

        assertTrue(
                fractions.length >= 5,
                "the max-face event splits the sweep into two sampled intervals");
        assertTrue(cells.contains(new Cell(0, 0, 0)));
        assertTrue(cells.contains(new Cell(1, 0, 0)));
    }

    @Test
    void diagonalCornerIntervalIncludesVoxelMissedByEndpointSampling() {
        SweptAabbVoxelSupercover.Box box =
                new SweptAabbVoxelSupercover.Box(
                        -0.6, 0.0, 0.1, 0.8, 1.0, 1.5);

        Set<Cell> cells = covered(box, 1.0, 0.0, 1.0);

        assertTrue(
                cells.contains(new Cell(-1, 0, 2)),
                "x=-1 and z=2 overlap only between the z-max and x-min face events");
    }

    @Test
    void reverseSweepCoversTheSameDiagonalCornerVoxel() {
        SweptAabbVoxelSupercover.Box destination =
                new SweptAabbVoxelSupercover.Box(
                        0.4, 0.0, 1.1, 1.8, 1.0, 2.5);

        Set<Cell> reverse =
                covered(destination, -1.0, 0.0, -1.0);

        assertTrue(reverse.contains(new Cell(-1, 0, 2)));
    }

    @Test
    void exactGridBoundariesUseTheSameHalfOpenVoxelConventionAsScanBox() {
        SweptAabbVoxelSupercover.Box box =
                new SweptAabbVoxelSupercover.Box(
                        0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

        Set<Cell> cells = covered(box, 1.0, 0.0, 0.0);

        assertTrue(cells.contains(new Cell(0, 0, 0)));
        assertTrue(cells.contains(new Cell(1, 0, 0)));
        assertFalse(cells.contains(new Cell(-1, 0, 0)));
        assertFalse(cells.contains(new Cell(2, 0, 0)));
    }

    @Test
    void threeAxisCornerIntervalIsCovered() {
        SweptAabbVoxelSupercover.Box box =
                new SweptAabbVoxelSupercover.Box(
                        -0.6, -0.6, -0.6, 0.8, 0.8, 0.8);

        Set<Cell> cells = covered(box, 1.0, 1.0, 1.0);

        assertTrue(
                cells.contains(new Cell(-1, 1, 1)),
                "the event interval must combine old x coverage with new y/z coverage");
    }

    @Test
    void zeroSweepUsesOneEndpointSample() {
        SweptAabbVoxelSupercover.Box box =
                new SweptAabbVoxelSupercover.Box(
                        -0.2, 1.0, 2.0, 0.2, 2.0, 3.0);

        assertEquals(
                1,
                SweptAabbVoxelSupercover.sampleFractions(
                                box, 0.0, 0.0, 0.0)
                        .length);
        assertEquals(
                Set.of(new Cell(-1, 1, 2), new Cell(0, 1, 2)),
                covered(box, 0.0, 0.0, 0.0));
    }

    private static Set<Cell> covered(
            SweptAabbVoxelSupercover.Box box,
            double deltaX,
            double deltaY,
            double deltaZ) {
        Set<Cell> cells = new HashSet<>();
        for (double fraction :
                SweptAabbVoxelSupercover.sampleFractions(
                        box, deltaX, deltaY, deltaZ)) {
            int minX = floor(box.minX() + deltaX * fraction);
            int minY = floor(box.minY() + deltaY * fraction);
            int minZ = floor(box.minZ() + deltaZ * fraction);
            int maxX =
                    floor(
                            Math.nextDown(
                                    box.maxX()
                                            + deltaX * fraction));
            int maxY =
                    floor(
                            Math.nextDown(
                                    box.maxY()
                                            + deltaY * fraction));
            int maxZ =
                    floor(
                            Math.nextDown(
                                    box.maxZ()
                                            + deltaZ * fraction));
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        cells.add(new Cell(x, y, z));
                    }
                }
            }
        }
        return cells;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private record Cell(int x, int y, int z) {}
}
