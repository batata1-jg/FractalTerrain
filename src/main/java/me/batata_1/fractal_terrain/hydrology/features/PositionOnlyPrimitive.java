package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.carvers.LatticeCarve;
import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;

/**
 * Shared behaviour for a skeleton feature that carries only a position: a fixed-radius influence disc
 * that blends as a plain {@link DefaultProfile} and carves no cross-section of its own.
 *
 * <p>{@link DeltaPrimitive} and {@link WaterfallPrimitive} implement this and keep only {@code
 * getType()}, {@code deserializePrimitive()} and their {@code coord} component — {@code equals}/{@code
 * hashCode} stay on each record because an interface default cannot override {@link Object}'s. The shed
 * families outgrew it: {@link OxbowLakePrimitive} moved to {@link RosgenCarvedPrimitive},
 * {@link AbandonedRiverPrimitive} to {@link RadialPrimitive}.
 */
interface PositionOnlyPrimitive extends HydrologicalPrimitive, SpatialIndexCircle {

    @Override
    default double[] getCenter() {
        return coord();
    }

    @Override
    default double getRadius() {
        return DEFAULT_RADIUS;
    }

    default HydrologyProfile getProfile() {
        return DefaultProfile.INSTANCE;
    }

    @Override
    default void carveInfluence(LatticeCarve.ShellGrid grid) {}

    /** A skeleton feature carries only a position, so it cuts no bed and tabulates no cross-section. */
    @Override
    default void carveBed(LatticeCarve.BedGrid grid) {}

    @Override
    default void tabulateBedLut(float[] lut, int baseIdx, int n, double resolution) {}

    @Override
    default long primitiveByteSize() {
        return PrimitiveCodec.coordByteSize(coord());
    }

    @Override
    default byte[] serializePrimitive() {
        return PrimitiveCodec.writeCoord(coord());
    }
}
