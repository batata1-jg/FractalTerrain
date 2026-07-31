package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.hydrology.profile.ZoneCategory;
import me.batata_1.fractal_terrain.math.VectorOps;
import org.jetbrains.annotations.Nullable;

/**
 * One sample of a flowing channel — the only feature type with a full cross-section today, and the
 * reference implementation of {@link HydrologicalUnit}.
 *
 * <p>It carries the channel {@code normal} (unit perpendicular to the centreline here, used to project
 * query points across the channel), the local channel {@code width}, the reference (bank)
 * {@code elevation} the tile-level shell carve lerps toward, and the {@code radius} it is indexed by.
 * {@code coord} and {@code normal} are primitive {@code double[]} treated as immutable — never mutate
 * them after construction, since {@link #getCoords()} hands out the backing array directly so tree
 * construction and every query point-scan pay no boxing.
 *
 * <p>{@code normal} may be {@code null} when no meaningful tangent exists, in which case the unit still
 * takes part in the zone averages but contributes no cross-section of its own.
 *
 * <p>{@link RosgenType} selects the {@link RosgenProfile} that defines this unit's
 * {@link ZoneCategory#BED} and {@link ZoneCategory#FLOODPLAIN} zones. A {@code null} rosgenType means
 * <em>this reach was never classified</em> — Rosgen classifies reaches, and a spring, a river mouth or a
 * removed feature has nothing to measure over. {@link #getProfile()} coalesces it to
 * {@link RosgenType#A} so a typeless unit still carves. Because only {@code A} overrides any profile
 * method today, the choice is currently observable only as "A vs. not-A".
 */
public record River(
        double[] coord,
        double radius,
        RosgenType rosgenType,
        double @Nullable [] normal,
        double width,
        double elevation)
        implements HydrologicalUnit {

    static final River PROTOTYPE = new River(new double[] {0.0, 0.0}, 0, null, null, 0, 0);

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

    /**
     * Whether a query point at squared distance {@code distSqFromCentre} from this unit's centre lies
     * inside the channel's water span — i.e. within this unit's bed half-width
     * ({@link ChannelGeometry#bedHalfWidth}). This is the channel-membership test used by
     * {@code HydrologyProfilePainter.insideChannel}.
     */
    @Override
    public boolean channelContains(double distSqFromCentre) {
        final double bedHalfWidth = ChannelGeometry.bedHalfWidth(width);
        return distSqFromCentre <= bedHalfWidth * bedHalfWidth;
    }

    @Override
    public HydrologyProfile getProfile() {
        return RosgenProfile.of(rosgenType == null ? RosgenType.A : rosgenType);
    }

    /**
     * The bed/floodplain cross-section this unit cuts at {@code pt}: the profile's
     * {@link RosgenProfile#riverAreaDelta} faded in over an elliptical footprint centred on the unit.
     *
     * <p>The fade weight {@code t} is read off the point's position in the unit's own frame — {@code x}
     * along the channel (tangent), {@code y} across it (the {@code normal}) — via
     * {@code e = 1 − x²/(l² − y²)}, with {@code l} the floodplain half-extent. The level sets of {@code e}
     * are ellipses of semi-axis {@code l·sqrt(1 − e)} along the channel and {@code l} across it:
     * {@code e = 1} on the unit's own cross-section line (the {@code x = 0} segment), decaying to
     * {@code e = 0} on the circle of radius {@code l}. A smoothstep about
     * {@link HydrologyTuning#MAX_ECCENTRICITY} turns that into the weight, and outside radius {@code l}
     * the pixel is returned untouched.
     *
     * <p>So a unit carves its full cross-section for the whole width of the floodplain but tapers off
     * along the channel, leaving the stretch between two units to their overlapping ellipses — which is
     * why the {@code dx <= width/2} unit spacing noted on {@link RosgenProfile} matters here too.
     */
    @Override
    public double carveFineGrained(double[] pt, double elevAtPixel) {
        if (normal == null) return elevAtPixel;
        final RosgenProfile profile = (RosgenProfile) getProfile();

        final double[] normTangent = VectorOps.perpendicular(normal);
        final double floodPlainLength = profile.floodPlainLength(width);
        final double radiusSq = floodPlainLength * floodPlainLength;
        if (VectorOps.distanceSquared(pt, coord) >= radiusSq) return elevAtPixel;
        if (Math.abs(normal[0]) < 1e-6 || Math.abs(normal[1]) < 1e-6) {
            return elevAtPixel;
        }

        final double SignedPerpDist;
        final double alongDist;
        final double[] ptToUnit = VectorOps.sub(pt, coord);
        SignedPerpDist = VectorOps.dot(normal, ptToUnit);
        alongDist = Math.abs(VectorOps.dot(normTangent, ptToUnit));

        final double uninterpolatedDelta =
                profile.riverAreaDelta(Arrays.hashCode(coord), SignedPerpDist, alongDist, width);

        if (radiusSq - SignedPerpDist * SignedPerpDist < 1e-6) return elevAtPixel;
        final double eccentricity =
                Math.sqrt(Math.abs(1 - (alongDist * alongDist) / (radiusSq - SignedPerpDist * SignedPerpDist)));
        final double t = Math.clamp(0.5 * (Math.tanh(8 * (eccentricity - HydrologyTuning.MAX_ECCENTRICITY)) + 1), 0, 1);
        //  final double t = Math.clamp(eccentricity / HydrologyTuning.MAX_ECCENTRICITY, 0, 1);
        return elevAtPixel + t * uninterpolatedDelta;
    }

    @Override
    public long unitByteSize() {
        // rosgen tag + coord + normal + radius + width + elevation
        return Integer.BYTES + UnitCodec.coordByteSize(coord) + UnitCodec.coordByteSize(normal) + 3L * Double.BYTES;
    }

    @Override
    public byte[] serializeUnit() {
        final ByteBuffer buf = ByteBuffer.allocate((int) byteSize()).order(ByteOrder.LITTLE_ENDIAN);
        // An unclassified reach stamps -1; every other value is a RosgenType ordinal.
        buf.putInt(rosgenType == null ? -1 : rosgenType.ordinal());
        UnitCodec.putCoord(buf, coord);
        UnitCodec.putCoord(buf, normal);
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
        final double r = buf.getDouble();
        final double w = buf.getDouble();
        final double e = buf.getDouble();
        return new River(coords, r, rosgen, normalVec, w, e);
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof River other)) return false;
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
