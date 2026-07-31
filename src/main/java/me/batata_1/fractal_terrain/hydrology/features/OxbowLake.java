package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.ZoneCategory;

/**
 * A meander loop cut off from its channel and left as standing water.
 *
 * <p><b>Skeleton.</b> Only the position exists so far — no water level, no loop geometry, no age — so it
 * carves nothing of its own and blends as a plain {@link DefaultProfile} influence disc.
 * {@link ZoneCategory#LAKE_BED} is reserved below {@link ZoneCategory#BED} for it, so a channel still
 * running through the loop will keep governing the cross-section once this record grows a real profile.
 */
public record OxbowLake(double[] coord) implements HydrologicalUnit {

    static final OxbowLake PROTOTYPE = new OxbowLake(new double[] {0.0, 0.0});

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
        return HydrologicalFeature.OXBOW_LAKE;
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
        return new OxbowLake(UnitCodec.readCoord(rawBytes));
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
