package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;

public record ConfluencePrimitive(double[] coord , double influence) implements SpatialIndexCircle , HydrologicalPrimitive {

    static final ConfluencePrimitive PROTOTYPE = new ConfluencePrimitive(new double[2], 0);

    @Override
    public HydrologicalFeature getType() {
        return null;
    }

    @Override
    public double[] getCenter() {
        return coord;
    }

    @Override
    public double getRadius() {
        return influence;
    }

    @Override
    public HydrologyProfile getProfile() {
        return null;
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
        return 0;
    }

    @Override
    public byte[] serializePrimitive() {
        return new byte[0];
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        return null;
    }

}
