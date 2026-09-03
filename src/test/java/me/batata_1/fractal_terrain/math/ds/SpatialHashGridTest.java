package me.batata_1.fractal_terrain.math.ds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpatialHashGridTest {

    private record TestPoint(double[] coords) implements SpatialIndexPoint {
        @Override
        public double[] getCoords() {
            return coords;
        }
    }

    @Test
    void getPointsInCircleFindsAnInsertedPointWithinRadius() {
        SpatialHashGrid<TestPoint> grid = new SpatialHashGrid<>(4.0);
        TestPoint pt = new TestPoint(new double[] {1.0, 1.0});
        grid.insertPoint(pt);

        List<TestPoint> hits = grid.getPointsInCircle(new double[] {0.0, 0.0}, 2.0);

        assertEquals(1, hits.size());
        assertEquals(pt, hits.getFirst());
    }

    /** The correctness detail the design spec calls out: a candidate sharing the query's scanned cell
     *  must still be rejected by the exact distance test if it sits outside the true circle. */
    @Test
    void getPointsInCircleExcludesAPointInsideTheCoveringCellButOutsideTheCircle() {
        SpatialHashGrid<TestPoint> grid = new SpatialHashGrid<>(4.0);
        // cellSize=4: a query at (0,0) r=1 covers cells with index in [-1,0] on each axis, which
        // includes cell (0,0) spanning world [0,4)x[0,4). A point at (3,3) lives in that scanned cell
        // but is far outside r=1 — a bucket-only match (no exact test) would wrongly include it.
        TestPoint farCorner = new TestPoint(new double[] {3.0, 3.0});
        grid.insertPoint(farCorner);

        List<TestPoint> hits = grid.getPointsInCircle(new double[] {0.0, 0.0}, 1.0);

        assertTrue(hits.isEmpty(), "point in the covering cell but outside the circle must be excluded");
    }

    @Test
    void removePointDropsItFromSubsequentQueries() {
        SpatialHashGrid<TestPoint> grid = new SpatialHashGrid<>(4.0);
        TestPoint pt = new TestPoint(new double[] {1.0, 1.0});
        grid.insertPoint(pt);
        grid.removePoint(pt);

        assertFalse(grid.containsPoint(pt));
        assertTrue(grid.getPointsInCircle(new double[] {0.0, 0.0}, 5.0).isEmpty());
        assertEquals(0, grid.numEntries());
    }

    @Test
    void clearEmptiesTheGrid() {
        SpatialHashGrid<TestPoint> grid = new SpatialHashGrid<>(4.0);
        grid.insertPoint(new TestPoint(new double[] {1.0, 1.0}));
        grid.insertPoint(new TestPoint(new double[] {-5.0, 9.0}));

        grid.clear();

        assertEquals(0, grid.numEntries());
        assertTrue(grid.getAllEntries().isEmpty());
    }

    @Test
    void containsPointReflectsInsertAndRemoveAcrossNegativeCoordinates() {
        SpatialHashGrid<TestPoint> grid = new SpatialHashGrid<>(4.0);
        TestPoint pt = new TestPoint(new double[] {-6.5, -2.5});
        assertFalse(grid.containsPoint(pt));

        grid.insertPoint(pt);
        assertTrue(grid.containsPoint(pt));

        grid.removePoint(pt);
        assertFalse(grid.containsPoint(pt));
    }
}
