package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import org.jetbrains.annotations.Nullable;

/**
 * One sample of a flowing channel — the reference {@link HydrologicalUnit}, the only type with a full
 * cross-section.
 *
 * <p>{@code coord} and {@code normal} are handed by reference (no boxing); treat both as immutable. A
 * null {@code normal} means no tangent — the unit averages into zones but cuts no cross-section.
 *
 * <p>A null {@code rosgenType} means never classified (spring/mouth, nothing to measure) and coalesces
 * to {@link RosgenType#A} so the unit still carves.
 */
public record RiverUnit(
        double[] coord,
        double radius,
        RosgenType rosgenType,
        double @Nullable [] normal,
        double curvature,
        double width,
        double elevation)
        implements HydrologicalUnit {

    static final RiverUnit PROTOTYPE = new RiverUnit(new double[] {0.0, 0.0}, 0, null, null, 0,0, 0);

    @Override
    public double[] getCoords() {
        return coord;
    }

    @Override
    public double[] getCenter() {
        return coord;
    }

    @Override
    public double getRadius() {
        return radius;
    }

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.RIVER;
    }

    /** Channel-membership test driving {@code HydrologyProfilePainter.insideChannel}. */
    @Override
    public boolean channelContains(double distSqFromCentre) {
        final double bedHalfWidth = ChannelGeometry.bedHalfWidth(width);
        return distSqFromCentre <= bedHalfWidth * bedHalfWidth;
    }

    @Override
    public float waterLine() {
        if(width <= 1.5 ) return -1;
        if(width <= 2.5  ) return -2;
        return -3;
    }

    @Override
    public HydrologyProfile getProfile() {
        return RosgenProfile.of(rosgenType == null ? RosgenType.A : rosgenType);
    }

    /** Cuts the unit's cross-section, faded over an elliptical footprint so full strength lands across
     *  the floodplain but tapers along the channel. Neighbouring units' ellipses cover the stretch
     *  between them, which is why the {@code dx <= width/2} spacing matters. */
    @Override
    public double carveFineGrained(double[] pt, double elevAtPixel) {
        if (normal == null) return elevAtPixel;
        final RosgenProfile profile = (RosgenProfile) getProfile();

        final double nx = normal[0], nz = normal[1];
        final double dx = pt[0] - coord[0], dz = pt[1] - coord[1];
        final double floodPlainLength = profile.floodPlainLength(width);
        final double radiusSq = floodPlainLength * floodPlainLength;
        if (dx * dx + dz * dz >= radiusSq) return elevAtPixel;
        if (Math.abs(nx) < 1e-6 || Math.abs(nz) < 1e-6) {
            return elevAtPixel;
        }

        final double signedPerpDist = nx * dx + nz * dz;
        final double alongDist = Math.abs(nz * dx - nx * dz);

        final double uninterpolatedDelta =
                profile.riverAreaDelta(Arrays.hashCode(coord), signedPerpDist, alongDist, width, curvature);

        if (radiusSq - signedPerpDist * signedPerpDist < 1e-6) return elevAtPixel;
        final double eccentricity =
                Math.sqrt(Math.abs(1 - (alongDist * alongDist) / (radiusSq - signedPerpDist * signedPerpDist)));
        final double t = Math.clamp(0.5 * (Math.tanh(8 * (eccentricity - HydrologyTuning.MAX_ECCENTRICITY)) + 1), 0, 1);
        //  final double t = Math.clamp(eccentricity / HydrologyTuning.MAX_ECCENTRICITY, 0, 1);
        return elevAtPixel + t * uninterpolatedDelta;
    }

    @Override
    public long unitByteSize() {
        // rosgen tag + coord + normal + radius + width + elevation
        return Integer.BYTES + UnitCodec.coordByteSize(coord) + UnitCodec.coordByteSize(normal) + 4L * Double.BYTES;
    }

    @Override
    public byte[] serializeUnit() {
        final ByteBuffer buf = ByteBuffer.allocate((int) unitByteSize()).order(ByteOrder.LITTLE_ENDIAN);
        // An unclassified reach stamps -1; every other value is a RosgenType ordinal.
        buf.putInt(rosgenType == null ? -1 : rosgenType.ordinal());
        UnitCodec.putCoord(buf, coord);
        UnitCodec.putCoord(buf, normal);
        buf.putDouble(curvature);
        buf.putDouble(radius);
        buf.putDouble(width);
        buf.putDouble(elevation);
        return buf.array();
    }

    @Override
    public HydrologicalUnit deserializeUnit(byte[] rawBytes) {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final int rosgenOrdinal = buf.getInt();
        final RosgenType rosgen = rosgenOrdinal < 0 ? null : RosgenType.values()[rosgenOrdinal];
        final double[] coords = UnitCodec.getCoord(buf);
        final double[] normalVec = UnitCodec.getCoord(buf);
        final double curvature = buf.getDouble();
        final double r = buf.getDouble();
        final double w = buf.getDouble();
        final double e = buf.getDouble();
        return new RiverUnit(coords, r, rosgen, normalVec, curvature , w, e);
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RiverUnit other)) return false;
        return rosgenType == other.rosgenType
                && Arrays.equals(coord, other.coord)
                && Arrays.equals(normal, other.normal)
                && Double.compare(radius, other.radius) == 0
                && Double.compare(width, other.width) == 0
                && Double.compare(elevation, other.elevation) == 0;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(rosgenType, radius, width, elevation);
        result = 31 * result + Arrays.hashCode(coord);
        result = 31 * result + Arrays.hashCode(normal);
        return result;
    }

    @Override
    public String toString() {
        return "River[coord=" + Arrays.toString(coord) + ", radius=" + radius + ", rosgenType=" + rosgenType
                + ", normal=" + Arrays.toString(normal) + ", width=" + width + ", elevation=" + elevation + "]";
    }

    /** Rosgen stream classification (A–G); selects the unit's {@link RosgenProfile}. */
    public enum RosgenType {
        A,
        Aa,
        B,
        C,
        D,
        DA,
        E,
        F,
        G
    }
}
