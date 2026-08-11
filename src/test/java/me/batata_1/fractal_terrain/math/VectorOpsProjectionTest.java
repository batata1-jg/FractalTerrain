package me.batata_1.fractal_terrain.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for the point-to-segment projection kernel. */
class VectorOpsProjectionTest {

    private final double[] projection = new double[2];

    @Test
    void projectsOntoSegmentInterior() {
        // Segment along +x from (0,0) to (4,0); the point sits directly above its midpoint.
        VectorOps.projectPointOntoSegment(
                new double[] {2.0, 3.0}, new double[] {0.0, 0.0}, new double[] {4.0, 0.0}, projection);
        assertEquals(0.5, projection[0], 1e-12);
        assertEquals(9.0, projection[1], 1e-12);
    }

    @Test
    void clampsBeforeSegmentStart() {
        // Foot would land at segParam = -0.75; clamping makes the start point the closest point (3-4-5).
        VectorOps.projectPointOntoSegment(
                new double[] {-3.0, 4.0}, new double[] {0.0, 0.0}, new double[] {4.0, 0.0}, projection);
        assertEquals(0.0, projection[0], 1e-12);
        assertEquals(25.0, projection[1], 1e-12);
    }

    @Test
    void clampsPastSegmentEnd() {
        VectorOps.projectPointOntoSegment(
                new double[] {7.0, 4.0}, new double[] {0.0, 0.0}, new double[] {4.0, 0.0}, projection);
        assertEquals(1.0, projection[0], 1e-12);
        assertEquals(25.0, projection[1], 1e-12);
    }

    @Test
    void degenerateSegmentReportsDistanceToItsStart() {
        // Two coincident knots must not divide by zero; the segment collapses to a point.
        VectorOps.projectPointOntoSegment(
                new double[] {3.0, 4.0}, new double[] {1.0, 1.0}, new double[] {1.0, 1.0}, projection);
        assertEquals(0.0, projection[0], 1e-12);
        assertEquals(13.0, projection[1], 1e-12);
    }

    @Test
    void diagonalSegmentProjectsCorrectly() {
        // Segment (0,0)->(2,2); point (2,0) projects to the midpoint (1,1), distSq = 2.
        VectorOps.projectPointOntoSegment(
                new double[] {2.0, 0.0}, new double[] {0.0, 0.0}, new double[] {2.0, 2.0}, projection);
        assertEquals(0.5, projection[0], 1e-12);
        assertEquals(2.0, projection[1], 1e-12);
    }

    @Test
    void pointOnTheSegmentHasZeroDistance() {
        VectorOps.projectPointOntoSegment(
                new double[] {3.0, 0.0}, new double[] {0.0, 0.0}, new double[] {4.0, 0.0}, projection);
        assertEquals(0.75, projection[0], 1e-12);
        assertEquals(0.0, projection[1], 1e-12);
    }
}
