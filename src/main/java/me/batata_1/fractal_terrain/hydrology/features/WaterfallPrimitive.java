package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;
import me.batata_1.fractal_terrain.hydrology.profile.ZoneCategory;

/**
 * A vertical drop in a channel: the lip and the plunge below it.
 *
 * <p><b>Skeleton.</b> Only the position exists so far — no drop height, no plunge-pool geometry, no
 * upstream/downstream channel link — so it carves nothing of its own and blends as a plain
 * {@link DefaultProfile} influence disc. {@link ZoneCategory#WATERFALL} is already reserved above
 * {@link ZoneCategory#BED} for it, so once this record grows a profile that claims that zone the drop
 * will win over the channel bed running into it with no change on the carve side.
 */
public record WaterfallPrimitive(double[] coord) implements PositionOnlyPrimitive {

    static final WaterfallPrimitive PROTOTYPE = new WaterfallPrimitive(new double[] {0.0, 0.0});

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.WATERFALL;
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        return new WaterfallPrimitive(PrimitiveCodec.readCoord(rawBytes));
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
