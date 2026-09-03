package me.batata_1.fractal_terrain.hydrology.features;

import java.util.Arrays;
import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;
import me.batata_1.fractal_terrain.hydrology.profile.ZoneCategory;
import org.jetbrains.annotations.NotNull;

/**
 * A meander loop cut off from its channel and left as standing water.
 *
 * <p><b>Skeleton.</b> It carries the step that cut it and the width it was cut at, but no water level and
 * no loop geometry, so it carves nothing of its own and blends as a plain {@link DefaultProfile}
 * influence disc. {@link ZoneCategory#LAKE_BED} is reserved below {@link ZoneCategory#BED} for it, so a
 * channel still running through the loop will keep governing the cross-section once this record grows a
 * real profile.
 */
public record OxbowLakePrimitive(double[] coord, byte time, double width, double influence, double elevation, long seed)
        implements HistoricPrimitive {

    static final OxbowLakePrimitive PROTOTYPE = new OxbowLakePrimitive(new double[] {0.0, 0.0}, (byte) 0, 0, 0, 0);

    public OxbowLakePrimitive(double[] coord, byte time, double width, double influence, double elevation) {
        this(
                coord,
                time,
                width,
                influence,
                elevation,
                PrimitiveCodec.historicHash(coord, time, width, influence, elevation));
    }

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.OXBOW_LAKE;
    }

    @Override
    public HistoricPrimitive resolved(double elevation, double influence) {
        return new OxbowLakePrimitive(coord, time, width, influence, elevation);
    }

    @Override
    public long primitiveByteSize() {
        return PrimitiveCodec.historicByteSize(coord);
    }

    @Override
    public byte[] serializePrimitive() {
        return PrimitiveCodec.writeHistoric(coord, time, width, influence, elevation);
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        final PrimitiveCodec.HistoricFields fields = PrimitiveCodec.readHistoric(rawBytes);
        return new OxbowLakePrimitive(
                fields.coord(), fields.time(), fields.width(), fields.influence(), fields.elevation());
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        return PrimitiveCodec.historicEquals(this, o);
    }

    @Override
    public int hashCode() {
        return Math.toIntExact(seed);
    }

    @Override
    public @NotNull String toString() {
        return "Oxbow[coord=" + Arrays.toString(coord) + ", time=" + time + ", width=" + width + ", influence="
                + influence + ", elevation=" + elevation + "]";
    }
}
