package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;

/**
 * The head of a channel — the spring or seep a river starts at.
 *
 * <p><b>Skeleton.</b> Only the position exists so far — no discharge, no headwater bowl geometry — so it
 * carves nothing of its own and blends as a plain {@link DefaultProfile} influence disc. Note this is a
 * point of the river it heads, not an independent feature: the network still stamps
 * {@link HydrologicalFeature#SOURCE} on the first point of a channel that begins at a source node.
 */
public record SourceUnit(double[] coord) implements HydrologicalUnit {

    static final SourceUnit PROTOTYPE = new SourceUnit(new double[] {0.0, 0.0});

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
        return DEFAULT_RADIUS;
    }

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.SOURCE;
    }

    @Override
    public HydrologyProfile getProfile() {
        return DefaultProfile.INSTANCE;
    }

    @Override
    public double carveFineGrained(double[] pt, double elevAtPixel) {
        return elevAtPixel;
    }

    @Override
    public long unitByteSize() {
        return UnitCodec.coordByteSize(coord);
    }

    @Override
    public byte[] serializeUnit() {
        return UnitCodec.writeCoord(coord);
    }

    @Override
    public HydrologicalUnit deserializeUnit(byte[] rawBytes) {
        return new SourceUnit(UnitCodec.readCoord(rawBytes));
    }

    @Override
    public boolean equals(Object o) {
        return UnitCodec.coordsEqual(this, o, coord);
    }

    @Override
    public int hashCode() {
        return UnitCodec.coordsHash(coord);
    }
}
