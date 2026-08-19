package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;

/**
 * A former channel the river has since migrated out of — a dry trace the terrain still remembers.
 *
 * <p><b>Skeleton.</b> Only the position exists so far — no width, no normal, no record of how long ago
 * it was abandoned — so it carves nothing of its own and blends as a plain {@link DefaultProfile}
 * influence disc. It will most likely end up claiming a shallow, aged variant of the river zones rather
 * than one of its own.
 */
public record AbandonedRiverPrimitive(double[] coord) implements PositionOnlyPrimitive {

    static final AbandonedRiverPrimitive PROTOTYPE = new AbandonedRiverPrimitive(new double[] {0.0, 0.0});

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.ABANDONED_RIVER;
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        return new AbandonedRiverPrimitive(PrimitiveCodec.readCoord(rawBytes));
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
