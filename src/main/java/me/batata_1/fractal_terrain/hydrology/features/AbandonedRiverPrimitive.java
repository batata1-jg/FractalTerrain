package me.batata_1.fractal_terrain.hydrology.features;

import java.util.Arrays;
import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;
import org.jetbrains.annotations.NotNull;

/**
 * A former channel the river has since migrated out of — a dry trace the terrain still remembers.
 *
 * <p><b>Skeleton.</b> It records how long ago it was abandoned and how wide it ran, but has no normal
 * and no cross-section, so it carves nothing of its own and blends as a plain
 * {@link DefaultProfile} influence disc. It will most likely end up claiming a shallow, aged variant of
 * the river zones rather than one of its own.
 */
public record AbandonedRiverPrimitive(
        double[] coord, byte time, double width, double influence, double elevation, long seed)
        implements HistoricPrimitive {

    static final AbandonedRiverPrimitive PROTOTYPE =
            new AbandonedRiverPrimitive(new double[] {0.0, 0.0}, (byte) 0, 0, 0, 0);

    public AbandonedRiverPrimitive(double[] coord, byte time, double width, double influence, double elevation) {
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
        return HydrologicalFeature.ABANDONED_RIVER;
    }

    @Override
    public HistoricPrimitive resolved(double elevation, double influence) {
        return new AbandonedRiverPrimitive(coord, time, width, influence, elevation);
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
        return new AbandonedRiverPrimitive(
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
        return "Abandoned[coord=" + Arrays.toString(coord) + ", time=" + time + ", width=" + width + ", influence="
                + influence + ", elevation=" + elevation + "]";
    }
}
