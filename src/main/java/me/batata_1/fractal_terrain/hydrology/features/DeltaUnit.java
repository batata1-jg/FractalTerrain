package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;

/**
 * A river mouth splaying into distributaries where it meets standing water.
 *
 * <p><b>Skeleton.</b> Only the position exists so far — no distributary fan, no sediment lobe, no base
 * level — so it carves nothing of its own and blends as a plain {@link DefaultProfile} influence disc.
 * Distinct from {@link HydrologicalFeature#DRAIN}, which marks the topological end of a channel; a delta
 * is the depositional landform that may sit there.
 */
public record DeltaUnit(double[] coord) implements HydrologicalUnit {

    static final DeltaUnit PROTOTYPE = new DeltaUnit(new double[] {0.0, 0.0});

    @Override
    public double[] getCoords() {
        return coord;
    }

    @Override
    public double[] getCenter() {
        return coord;
    }

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.DELTA;
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
        return new DeltaUnit(UnitCodec.readCoord(rawBytes));
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
