package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;

/**
 * The head of a channel — the spring or seep a river starts at.
 *
 * <p><b>Skeleton.</b> Only the position exists so far — no discharge, no headwater bowl geometry — so it
 * carves nothing of its own and blends as a plain {@link DefaultProfile} influence disc. Note this is a
 * point of the river it heads, not an independent feature: the network still stamps
 * {@link HydrologicalFeature#SOURCE} on the first point of a channel that begins at a source node.
 */
public record SourcePrimitive(double[] coord) implements PositionOnlyPrimitive {

    static final SourcePrimitive PROTOTYPE = new SourcePrimitive(new double[] {0.0, 0.0});

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.SOURCE;
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        return new SourcePrimitive(PrimitiveCodec.readCoord(rawBytes));
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
