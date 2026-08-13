package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.ZoneCategory;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;

/**
 * A meander loop cut off from its channel and left as standing water.
 *
 * <p><b>Skeleton.</b> Only the position exists so far — no water level, no loop geometry, no age — so it
 * carves nothing of its own and blends as a plain {@link DefaultProfile} influence disc.
 * {@link ZoneCategory#LAKE_BED} is reserved below {@link ZoneCategory#BED} for it, so a channel still
 * running through the loop will keep governing the cross-section once this record grows a real profile.
 */
public record OxbowLakePrimitive(double[] coord) implements HydrologicalPrimitive, SpatialIndexCircle {

    static final OxbowLakePrimitive PROTOTYPE = new OxbowLakePrimitive(new double[] {0.0, 0.0});

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
        return HydrologicalFeature.OXBOW_LAKE;
    }

    @Override
    public HydrologyProfile getProfile() {
        return DefaultProfile.INSTANCE;
    }

    @Override
    public double w(double[] pt) {
        return 0;
    }

    @Override
    public double d(double[] pt) {
        return 0;
    }

    @Override
    public long primitiveByteSize() {
        return PrimitiveCodec.coordByteSize(coord);
    }

    @Override
    public byte[] serializePrimitive() {
        return PrimitiveCodec.writeCoord(coord);
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        return new OxbowLakePrimitive(PrimitiveCodec.readCoord(rawBytes));
    }

    @Override
    public boolean equals(Object o) {
        return PrimitiveCodec.coordsEqual(this, o, coord);
    }

    @Override
    public int hashCode() {
        return PrimitiveCodec.coordsHash(coord);
    }
}
