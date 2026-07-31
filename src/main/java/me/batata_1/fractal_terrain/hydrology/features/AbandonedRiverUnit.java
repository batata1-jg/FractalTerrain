package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;

/**
 * A former channel the river has since migrated out of — a dry trace the terrain still remembers.
 *
 * <p><b>Skeleton.</b> Only the position exists so far — no width, no normal, no record of how long ago
 * it was abandoned — so it carves nothing of its own and blends as a plain {@link DefaultProfile}
 * influence disc. It will most likely end up claiming a shallow, aged variant of the river zones rather
 * than one of its own.
 */
public record AbandonedRiverUnit(double[] coord) implements HydrologicalUnit {

    static final AbandonedRiverUnit PROTOTYPE = new AbandonedRiverUnit(new double[] {0.0, 0.0});

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
        return HydrologicalFeature.ABANDONED_RIVER;
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
        return new AbandonedRiverUnit(UnitCodec.readCoord(rawBytes));
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
