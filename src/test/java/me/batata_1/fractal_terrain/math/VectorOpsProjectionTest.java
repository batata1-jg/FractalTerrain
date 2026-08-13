package me.batata_1.fractal_terrain.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for the point-to-segment projection kernel. */
class VectorOpsProjectionTest {

    private final double[] projection = new double[2];

    @Test
    void projectsOntoSegmentInterior() {
        // Segment along +x from (0,0) to (4,0); the point sits directly above its midpoint.
        // perpendicular(+x) = (0,-1), so +z is the negative bank.
        VectorOps.projectPointOntoSegment(
                new double[] {2.0, 3.0}, new double[] {0.0, 0.0}, new double[] {4.0, 0.0}, projection);
        assertEquals(0.5, projection[0], 1e-12);
        assertEquals(-3.0, projection[1], 1e-12);
    }

    @Test
    void clampsBeforeSegmentStart() {
        // Foot would land at segParam = -0.75; clamping makes the start point the closest point (3-4-5).
        VectorOps.projectPointOntoSegment(
                new double[] {-3.0, 4.0}, new double[] {0.0, 0.0}, new double[] {4.0, 0.0}, projection);
        assertEquals(0.0, projection[0], 1e-12);
        assertEquals(-5.0, projection[1], 1e-12);
    }

    @Test
    void clampsPastSegmentEnd() {
        VectorOps.projectPointOntoSegment(
                new double[] {7.0, 4.0}, new double[] {0.0, 0.0}, new double[] {4.0, 0.0}, projection);
        assertEquals(1.0, projection[0], 1e-12);
        assertEquals(-5.0, projection[1], 1e-12);
    }

    @Test
    void degenerateSegmentReportsDistanceToItsStart() {
        // Two coincident knots must not divide by zero; the segment collapses to a point. With no
        // tangent there is no bank either, so the distance takes the positive sign.
        VectorOps.projectPointOntoSegment(
                new double[] {3.0, 4.0}, new double[] {1.0, 1.0}, new double[] {1.0, 1.0}, projection);
        assertEquals(0.0, projection[0], 1e-12);
        assertEquals(Math.sqrt(13.0), projection[1], 1e-12);
    }

    @Test
    void diagonalSegmentProjectsCorrectly() {
        // Segment (0,0)->(2,2); point (2,0) projects to the midpoint (1,1), dist = sqrt(2).
        // perpendicular((2,2)) = (2,-2), which points at (2,0): the positive bank.
        VectorOps.projectPointOntoSegment(
                new double[] {2.0, 0.0}, new double[] {0.0, 0.0}, new double[] {2.0, 2.0}, projection);
        assertEquals(0.5, projection[0], 1e-12);
        assertEquals(Math.sqrt(2.0), projection[1], 1e-12);
    }

    @Test
    void pointOnTheSegmentHasZeroDistance() {
        VectorOps.projectPointOntoSegment(
                new double[] {3.0, 0.0}, new double[] {0.0, 0.0}, new double[] {4.0, 0.0}, projection);
        assertEquals(0.75, projection[0], 1e-12);
        assertEquals(0.0, projection[1], 1e-12);
    }

    @Test
    void mirroredPointsGetOppositeSigns() {
        // The whole point of signing: two points straddling the centreline must not be conflated.
        VectorOps.projectPointOntoSegment(
                new double[] {2.0, 3.0}, new double[] {0.0, 0.0}, new double[] {4.0, 0.0}, projection);
        final double below = projection[1];
        VectorOps.projectPointOntoSegment(
                new double[] {2.0, -3.0}, new double[] {0.0, 0.0}, new double[] {4.0, 0.0}, projection);
        assertEquals(-below, projection[1], 1e-12);
        assertEquals(3.0, projection[1], 1e-12);
    }

    @Test
    void reversingTheSegmentFlipsTheSign() {
        // The sign is a property of the directed segment, so callers must orient it consistently
        // (HydrologyProfileInprinter always orients its two-segment polyline downstream).
        VectorOps.projectPointOntoSegment(
                new double[] {2.0, 3.0}, new double[] {0.0, 0.0}, new double[] {4.0, 0.0}, projection);
        final double forward = projection[1];
        VectorOps.projectPointOntoSegment(
                new double[] {2.0, 3.0}, new double[] {4.0, 0.0}, new double[] {0.0, 0.0}, projection);
        assertEquals(-forward, projection[1], 1e-12);
        assertEquals(0.5, projection[0], 1e-12);
    }
}
