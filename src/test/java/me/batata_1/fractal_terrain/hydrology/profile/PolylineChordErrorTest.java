package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;

/**
 * Bounds the error the two-segment polyline introduces against a curve of known analytic distance.
 * Justifies not reconstructing the quintic — see the design doc's "out of scope" section.
 */
class PolylineChordErrorTest {

    /** A circular arc of this radius is the worst case a meander realistically reaches. */
    private static final double ARC_RADIUS = 24.0;

    private static List<HydrologicalPrimitive> arc(int knotCount, double knotSpacing) {
        final List<HydrologicalPrimitive> primitives = new ArrayList<>();
        final double angleStep = knotSpacing / ARC_RADIUS;
        for (int i = 0; i < knotCount; i++) {
            final double angle = i * angleStep;
            final double x = ARC_RADIUS * Math.cos(angle);
            final double z = ARC_RADIUS * Math.sin(angle);
            // Outward radial direction; perpendicular to the arc tangent, so it is the spline normal.
            primitives.add(new RiverPrimitive(
                    new double[] {x, z},
                    4.0,
                    RosgenType.C,
                    new double[] {Math.cos(angle), Math.sin(angle)},
                    1.0 / ARC_RADIUS,
                    2.0,
                    0.0,
                    i | (1L << 32)));
        }
        return primitives;
    }

    @Test
    void chordErrorStaysBelowAQuarterBlockAtRealisticKnotSpacing() {
        // Resampling holds spacing near width/2; width 2 in the relief-pixel frame gives spacing 1.
        final List<HydrologicalPrimitive> primitives = arc(9, 1.0);
        double worstError = 0.0;
        for (double offset = -6.0; offset <= 6.0; offset += 0.25) {
            final double angle = 4 * (1.0 / ARC_RADIUS); // sample around the middle knot
            final double radius = ARC_RADIUS + offset;
            final double[] point = {radius * Math.cos(angle), radius * Math.sin(angle)};
            final NearestChannelSample sample = HydrologyProfileInprinter.sampleNearestChannel(primitives, 4, point);
            // Analytic distance to the circle is exactly |offset|.
            worstError = Math.max(worstError, Math.abs(Math.abs(sample.signedPerpDist()) - Math.abs(offset)));
        }
        assertTrue(worstError < 0.25, "polyline chord error grew to " + worstError + " relief pixels");
    }

    @Test
    void signIsStableAcrossTheCentrelineOnACurve() {
        // The failure this whole change exists to prevent: neighbouring evaluations disagreeing
        // about which side of the channel a point is on.
        final List<HydrologicalPrimitive> primitives = arc(9, 1.0);
        final double angle = 4 * (1.0 / ARC_RADIUS);
        for (double offset = 0.5; offset <= 6.0; offset += 0.25) {
            final double[] outside = {(ARC_RADIUS + offset) * Math.cos(angle), (ARC_RADIUS + offset) * Math.sin(angle)};
            final double[] inside = {(ARC_RADIUS - offset) * Math.cos(angle), (ARC_RADIUS - offset) * Math.sin(angle)};
            final double outsideSign =
                    Math.signum(HydrologyProfileInprinter.sampleNearestChannel(primitives, 4, outside)
                            .signedPerpDist());
            final double insideSign = Math.signum(HydrologyProfileInprinter.sampleNearestChannel(primitives, 4, inside)
                    .signedPerpDist());
            assertTrue(outsideSign != insideSign, "banks share a sign at offset " + offset);
        }
    }
}
