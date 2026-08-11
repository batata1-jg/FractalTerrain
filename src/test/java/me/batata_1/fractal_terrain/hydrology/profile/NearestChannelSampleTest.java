package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;

/** Unit tests for the one-distance-per-channel sampler. */
class NearestChannelSampleTest {

    /**
     * A knot on a channel running along +x, so the spline normal is perpendicular(+x) = (0,-1)
     * and a point at +z reads as a negative signedPerpDist.
     */
    private static RiverPrimitive knot(int channelId, int knotIndex, double x, double width, double bedElev) {
        return new RiverPrimitive(
                new double[] {x, 0.0},
                4.0,
                RosgenType.C,
                new double[] {0.0, -1.0},
                0.0,
                width,
                bedElev,
                knotIndex | (((long) channelId) << 32));
    }

    /** Three knots of channel 1 at x = 0, 4, 8 — the straight reference reach. */
    private static List<HydrologicalPrimitive> straightReach() {
        final List<HydrologicalPrimitive> primitives = new ArrayList<>();
        primitives.add(knot(1, 0, 0.0, 2.0, 60.0));
        primitives.add(knot(1, 1, 4.0, 6.0, 50.0));
        primitives.add(knot(1, 2, 8.0, 10.0, 40.0));
        return primitives;
    }

    @Test
    void returnsNullWhenNoPrimitiveWasNearest() {
        assertNull(HydrologyProfileInprinter.sampleNearestChannel(straightReach(), -1, new double[] {0.0, 0.0}));
    }

    @Test
    void straightReachGivesTheExactPerpendicularDistance() {
        // Point 3 above the reach, nearest knot is the middle one at x=4.
        final NearestChannelSample sample =
                HydrologyProfileInprinter.sampleNearestChannel(straightReach(), 1, new double[] {4.0, 3.0});
        assertNotNull(sample);
        assertEquals(3.0, Math.abs(sample.signedPerpDist()), 1e-9);
    }

    @Test
    void signMatchesTheLegacyTangentLineProjection() {
        // The sign convention the F/G profiles were tuned against is dot(normal, pt - coord).
        final List<HydrologicalPrimitive> reach = straightReach();
        final RiverPrimitive middle = (RiverPrimitive) reach.get(1);
        for (double side : new double[] {3.0, -3.0}) {
            final double[] point = {4.0, side};
            final NearestChannelSample sample = HydrologyProfileInprinter.sampleNearestChannel(reach, 1, point);
            assertEquals(Math.signum(middle.d(point)), Math.signum(sample.signedPerpDist()), 1e-12);
        }
    }

    @Test
    void attributesAreInterpolatedAtTheFootPointNotReadOffTheKnot() {
        // Point above x=6, the midpoint between knot 1 (width 6, bed 50) and knot 2 (width 10, bed 40).
        final NearestChannelSample sample =
                HydrologyProfileInprinter.sampleNearestChannel(straightReach(), 1, new double[] {6.0, 2.0});
        assertNotNull(sample);
        assertEquals(8.0, sample.channelWidth(), 1e-9);
        assertEquals(45.0, sample.bedElevation(), 1e-9);
    }

    @Test
    void interpolationRunsDownstreamRegardlessOfWhichNeighbourWins() {
        // Nearest knot is 2 (x=8) but the foot lands back at x=6, between knots 1 and 2.
        // If the segment were oriented from the nearest knot outward, width would read 8 the
        // other way round and bed elevation would come out 45 vs 45 — so pin the asymmetric pair.
        final NearestChannelSample sample =
                HydrologyProfileInprinter.sampleNearestChannel(straightReach(), 2, new double[] {6.5, 2.0});
        assertNotNull(sample);
        assertEquals(6.0 + 4.0 * 0.625, sample.channelWidth(), 1e-9);
        assertEquals(50.0 - 10.0 * 0.625, sample.bedElevation(), 1e-9);
    }

    @Test
    void aNeighbourFromAnotherChannelIsIgnored() {
        // Channel 2's knot sits closer in list order but must not become a segment endpoint.
        final List<HydrologicalPrimitive> primitives = new ArrayList<>();
        primitives.add(knot(2, 0, 4.0, 6.0, 99.0));
        primitives.add(knot(1, 0, 0.0, 2.0, 60.0));
        primitives.add(knot(2, 5, 8.0, 6.0, 99.0));
        // Index 1 is the lone knot of channel 1; both neighbours belong to channel 2.
        final double[] point = {0.0, 3.0};
        final NearestChannelSample sample = HydrologyProfileInprinter.sampleNearestChannel(primitives, 1, point);
        assertNotNull(sample);
        assertEquals(1, sample.channelId());
        assertEquals(2.0, sample.channelWidth(), 1e-9);
        assertEquals(((RiverPrimitive) primitives.get(1)).d(point), sample.signedPerpDist(), 1e-9);
    }

    @Test
    void nonConsecutiveKnotsOfOneChannelFallBackToTheKnotTangentLine() {
        // The meander loopback: same channel, but knots 0 and 57 are not a real segment.
        final List<HydrologicalPrimitive> primitives = new ArrayList<>();
        primitives.add(knot(1, 0, 0.0, 2.0, 60.0));
        primitives.add(knot(1, 57, 4.0, 6.0, 30.0));
        primitives.add(knot(1, 58, 8.0, 6.0, 30.0));
        final double[] point = {0.0, 3.0};
        final NearestChannelSample sample = HydrologyProfileInprinter.sampleNearestChannel(primitives, 0, point);
        assertNotNull(sample);
        // Knot 0's only list neighbour is knot 57 — not adjacent, so no segment exists.
        assertEquals(((RiverPrimitive) primitives.get(0)).d(point), sample.signedPerpDist(), 1e-9);
        assertEquals(2.0, sample.channelWidth(), 1e-9);
    }

    @Test
    void theNearerOfTheTwoCandidateSegmentsWins() {
        // Point sits above x=1, far nearer the (0,4) segment than anything past knot 1.
        final NearestChannelSample sample =
                HydrologyProfileInprinter.sampleNearestChannel(straightReach(), 1, new double[] {1.0, 2.0});
        assertNotNull(sample);
        assertEquals(2.0, Math.abs(sample.signedPerpDist()), 1e-9);
        assertEquals(2.0 + 4.0 * 0.25, sample.channelWidth(), 1e-9);
    }
}
