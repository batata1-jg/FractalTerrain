package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;

/**
 * Where two or more channels meet — the influence-circle primitive covering the junction's local
 * cross-section.
 *
 * <p>{@code coord} is the junction point; {@code junctionElevation} its bed elevation. {@code angles},
 * {@code curvatures}, {@code widths}, {@code rimElevations} and {@code rosgenTypes} are parallel arrays
 * over the incident arms, handed by reference — treat all as immutable — ordered by incident channel,
 * NOT by angle. {@code angles[k]} is the outward bearing of arm k from {@code coord}; {@code
 * rimElevations[k]} its bed elevation at radius {@code influence}. A null {@code rosgenTypes[k]}
 * coalesces to {@link RiverPrimitive.RosgenType#A}, matching {@link RiverPrimitive#getProfile()}.
 */
public record ConfluencePrimitive(
        double[] coord,
        double influence,
        double junctionElevation,
        double[] angles,
        double[] curvatures,
        double[] widths,
        double[] rimElevations,
        RiverPrimitive.RosgenType[] rosgenTypes)
        implements SpatialIndexCircle, HydrologicalPrimitive {

    static final ConfluencePrimitive PROTOTYPE = new ConfluencePrimitive(
            new double[2],
            0,
            0,
            new double[0],
            new double[0],
            new double[0],
            new double[0],
            new RiverPrimitive.RosgenType[0]);

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.CONFLUENCE;
    }

    @Override
    public double[] getCenter() {
        return coord;
    }

    @Override
    public double getRadius() {
        return influence;
    }

    /** The widest arm's {@link RosgenProfile}, coalescing a null type to {@link
     *  RiverPrimitive.RosgenType#A} so the junction still carves; {@link RosgenProfile#A} when no arm
     *  is attached yet. */
    @Override
    public HydrologyProfile getProfile() {
        final int widest = widestArm();
        if (widest < 0) return RosgenProfile.A;
        final RiverPrimitive.RosgenType type = RiverPrimitive.RosgenType.orDefault(rosgenTypes[widest]);
        return RosgenProfile.of(type);
    }

    /** Angular bracket + inverse-distance blend across the two arms straddling {@code pt}, so the
     *  surface interpolates smoothly between confluent channels' cross-sections. */
    public double h(double[] pt) {
        if (angles.length == 0) return junctionElevation;
        final BracketSample bracket = bracketSample(pt);
        final double hI = armElevation(bracket.i(), bracket.r(), bracket.dI());
        if (bracket.i() == bracket.j()) return hI;
        final double hJ = armElevation(bracket.j(), bracket.r(), bracket.dJ());
        final double absI = Math.abs(bracket.dI());
        final double absJ = Math.abs(bracket.dJ());
        final double sum = absI + absJ;
        if (sum < 1e-12) return (hI + hJ) / 2;
        return (absJ * hI + absI * hJ) / sum;
    }

    /** Wetted-channel test against the widest arm's bed half-width, matching {@link
     *  RiverPrimitive#channelContains}. */
    @Override
    public boolean channelContains(double distSqFromCentre) {
        final int widest = widestArm();
        if (widest < 0) return false;
        final double bedHalfWidth = ChannelGeometry.bedHalfWidth(widths[widest]);
        return distSqFromCentre <= bedHalfWidth * bedHalfWidth;
    }

    /** Delegates to the widest arm's width; the interface default when no arm is attached yet. */
    @Override
    public float waterLine() {
        final int widest = widestArm();
        if (widest < 0) return HydrologicalPrimitive.super.waterLine();
        return HydrologicalPrimitive.waterLine(widths[widest]);
    }

    /** Index of the widest incident arm, or -1 when none exist; shared by {@link #getProfile},
     *  {@link #channelContains} and {@link #waterLine}. */
    private int widestArm() {
        if (widths.length == 0) return -1;
        int widest = 0;
        for (int k = 1; k < widths.length; k++) {
            if (widths[k] > widths[widest]) widest = k;
        }
        return widest;
    }

    /** One arm's bed elevation at {@code pt}: junction-to-rim lerp by radial fraction, plus that arm's
     *  Rosgen bed delta. */
    private double armElevation(int k, double r, double signedPerpDist) {
        final double t = influence > 0 ? Math.clamp(r / influence, 0, 1) : 1;
        final double rimLerp = junctionElevation + (rimElevations[k] - junctionElevation) * t;
        final RiverPrimitive.RosgenType type = RiverPrimitive.RosgenType.orDefault(rosgenTypes[k]);
        RosgenProfile profile = RosgenProfile.of(type);
        if (Math.abs(signedPerpDist) > profile.floodPlainLength(widths[k]))
            return rimLerp + profile.delta(hashCode(), profile.floodPlainLength(widths[k]), widths[k], curvatures[k]);
        return rimLerp + profile.delta(hashCode(), signedPerpDist, widths[k], curvatures[k]);
    }

    /** The CW/CCW arm pair bracketing {@code pt}'s bearing from the junction, wrapping across ±π; the
     *  lone arm serves as both when only one channel is incident. */
    private BracketSample bracketSample(double[] pt) {
        final double dx = pt[0] - coord[0], dz = pt[1] - coord[1];
        final double r = Math.hypot(dx, dz);
        final double theta = Math.atan2(dz, dx);
        final int n = angles.length;
        int i, j;
        if (n <= 1) {
            i = 0;
            j = 0;
        } else {
            int cw = -1, ccw = -1, farthest = 0;
            double cwMagnitude = Double.POSITIVE_INFINITY;
            double ccwDelta = Double.POSITIVE_INFINITY;
            double farthestMagnitude = -1;
            for (int k = 0; k < n; k++) {
                final double delta = wrap(theta - angles[k]);
                final double magnitude = Math.abs(delta);
                if (magnitude > farthestMagnitude) {
                    farthestMagnitude = magnitude;
                    farthest = k;
                }
                if (delta > 0) {
                    if (delta < ccwDelta) {
                        ccwDelta = delta;
                        ccw = k;
                    }
                } else if (magnitude < cwMagnitude) {
                    cwMagnitude = magnitude;
                    cw = k;
                }
            }
            // Every arm on one side means the bearing sits in a gap wider than π, so the far
            // bracket is the arm reached by wrapping past ±π — not a second copy of the near one.
            if (ccw == -1) ccw = farthest;
            if (cw == -1) cw = farthest;
            i = cw;
            j = ccw;
        }
        final double dI = r * Math.sin(wrap(theta - angles[i]));
        final double dJ = r * Math.sin(wrap(theta - angles[j]));
        return new BracketSample(i, j, r, dI, dJ);
    }

    /** The two arms bracketing a bearing and each one's signed perpendicular distance to it. */
    private record BracketSample(int i, int j, double r, double dI, double dJ) {}

    /** Wraps an angle into (−π, π]. */
    private static double wrap(double angle) {
        double wrapped = angle % (2 * Math.PI);
        if (wrapped <= -Math.PI) wrapped += 2 * Math.PI;
        if (wrapped > Math.PI) wrapped -= 2 * Math.PI;
        return wrapped;
    }

    @Override
    public long primitiveByteSize() {
        // coord + influence + junctionElevation + arm count + four per-arm double arrays + rosgen tags
        return PrimitiveCodec.coordByteSize(coord)
                + 2L * Double.BYTES
                + Integer.BYTES
                + 4L * angles.length * Double.BYTES
                + (long) angles.length * Integer.BYTES;
    }

    @Override
    public byte[] serializePrimitive() {
        final ByteBuffer buf = ByteBuffer.allocate((int) primitiveByteSize()).order(ByteOrder.LITTLE_ENDIAN);
        PrimitiveCodec.putCoord(buf, coord);
        buf.putDouble(influence);
        buf.putDouble(junctionElevation);
        buf.putInt(angles.length);
        for (final double a : angles) buf.putDouble(a);
        for (final double c : curvatures) buf.putDouble(c);
        for (final double w : widths) buf.putDouble(w);
        for (final double e : rimElevations) buf.putDouble(e);
        // An unclassified arm stamps -1; every other value is a RosgenType ordinal.
        for (final RiverPrimitive.RosgenType type : rosgenTypes) buf.putInt(type == null ? -1 : type.ordinal());
        return buf.array();
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final double[] coords = PrimitiveCodec.getCoord(buf);
        final double r = buf.getDouble();
        final double junctionElev = buf.getDouble();
        final int n = buf.getInt();
        final double[] armAngles = new double[n];
        for (int k = 0; k < n; k++) armAngles[k] = buf.getDouble();
        final double[] armCurvatures = new double[n];
        for (int k = 0; k < n; k++) armCurvatures[k] = buf.getDouble();
        final double[] armWidths = new double[n];
        for (int k = 0; k < n; k++) armWidths[k] = buf.getDouble();
        final double[] armRimElevations = new double[n];
        for (int k = 0; k < n; k++) armRimElevations[k] = buf.getDouble();
        final RiverPrimitive.RosgenType[] armRosgenTypes = new RiverPrimitive.RosgenType[n];
        for (int k = 0; k < n; k++) {
            final int ordinal = buf.getInt();
            armRosgenTypes[k] = ordinal < 0 ? null : RiverPrimitive.RosgenType.values()[ordinal];
        }
        return new ConfluencePrimitive(
                coords, r, junctionElev, armAngles, armCurvatures, armWidths, armRimElevations, armRosgenTypes);
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfluencePrimitive other)) return false;
        return Double.compare(influence, other.influence) == 0
                && Double.compare(junctionElevation, other.junctionElevation) == 0
                && Arrays.equals(coord, other.coord)
                && Arrays.equals(angles, other.angles)
                && Arrays.equals(curvatures, other.curvatures)
                && Arrays.equals(widths, other.widths)
                && Arrays.equals(rimElevations, other.rimElevations)
                && Arrays.equals(rosgenTypes, other.rosgenTypes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(influence, junctionElevation);
        result = 31 * result + Arrays.hashCode(coord);
        result = 31 * result + Arrays.hashCode(angles);
        result = 31 * result + Arrays.hashCode(curvatures);
        result = 31 * result + Arrays.hashCode(widths);
        result = 31 * result + Arrays.hashCode(rimElevations);
        result = 31 * result + Arrays.hashCode(rosgenTypes);
        return result;
    }

    @Override
    public String toString() {
        return "Confluence[coord=" + Arrays.toString(coord) + ", influence=" + influence + ", junctionElevation="
                + junctionElevation + ", angles=" + Arrays.toString(angles) + ", curvatures="
                + Arrays.toString(curvatures) + ", widths=" + Arrays.toString(widths) + ", rimElevations="
                + Arrays.toString(rimElevations) + ", rosgenTypes=" + Arrays.toString(rosgenTypes) + "]";
    }
}
