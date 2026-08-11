package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;

/**
 * A river mouth splaying into distributaries where it meets standing water.
 *
 * <p><b>Skeleton.</b> Only the position exists so far — no distributary fan, no sediment lobe, no base
 * level — so it carves nothing of its own and blends as a plain {@link DefaultProfile} influence disc.
 * Distinct from {@link HydrologicalFeature#DRAIN}, which marks the topological end of a channel; a delta
 * is the depositional landform that may sit there.
 */
public record DeltaPrimitive(double[] coord) implements SpatialIndexCircle,HydrologicalPrimitive {

    static final DeltaPrimitive PROTOTYPE = new DeltaPrimitive(new double[] {0.0, 0.0});

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
        return HydrologicalFeature.DELTA;
    }

    @Override
    public HydrologyProfile getProfile() {
        return DefaultProfile.INSTANCE;
    }

    @Override
    public double h(double[] pt, Object... args) {
        return 0;
    }

    @Override
    public double w(double[] pt, Object... args) {
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
        return new DeltaPrimitive(PrimitiveCodec.readCoord(rawBytes));
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
