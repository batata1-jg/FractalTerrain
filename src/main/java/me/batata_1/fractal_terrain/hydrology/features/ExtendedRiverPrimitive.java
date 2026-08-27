package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;

/**
 * A {@link RiverPrimitive} tagged with the {@code (channelId, knot index)} provenance a bare
 * {@code RiverPrimitive} drops, so a tile-local carve can re-point its bed elevation without
 * re-running {@code RiverNetwork.collectPrimitives}. Tile-local scratch: never indexed, never
 * persisted.
 *
 * <p>Record-generated {@code equals}/{@code hashCode} already delegate to {@link RiverPrimitive}'s own
 * content-based overrides, so this type needs neither, despite {@link HydrologicalPrimitive} asking
 * implementations to provide them.
 */
public record ExtendedRiverPrimitive(RiverPrimitive river, int channelId, int pointIndex)
        implements HydrologicalPrimitive {

    @Override
    public HydrologicalFeature getType() {
        // Must stay RIVER: HydrologicalPrimitive.comparator sorts on type ordinal first and the carve
        // loop stops at the first non-river primitive, so any other type would sort this behind the
        // position-only primitives and the carve would never reach it.
        return HydrologicalFeature.RIVER;
    }

    @Override
    public HydrologyProfile getProfile() {
        return river.getProfile();
    }

    @Override
    public double[] coord() {
        return river.coord();
    }

    @Override
    public boolean channelContains(double distSqFromCentre) {
        return river.channelContains(distSqFromCentre);
    }

    @Override
    public float waterLine() {
        return river.waterLine();
    }

    /** A copy carrying the bed elevation a tile-local carve refresh computed, with everything else —
     *  including {@code influence} and {@code seed} — unchanged. Uses {@link RiverPrimitive}'s 8-arg
     *  constructor: the 7-arg one recomputes {@code seed} from the elevation, which would give the bed
     *  carve a different cross-section noise realization than the shell carve already used. */
    public ExtendedRiverPrimitive withBedElevation(double bedElevation) {
        return new ExtendedRiverPrimitive(
                new RiverPrimitive(
                        river.coord(),
                        river.influence(),
                        river.rosgenType(),
                        river.normal(),
                        river.curvature(),
                        river.width(),
                        bedElevation,
                        river.seed()),
                channelId,
                pointIndex);
    }

    @Override
    public long primitiveByteSize() {
        throw new UnsupportedOperationException("ExtendedRiverPrimitive is tile-local scratch and is never persisted");
    }

    @Override
    public byte[] serializePrimitive() {
        throw new UnsupportedOperationException("ExtendedRiverPrimitive is tile-local scratch and is never persisted");
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        throw new UnsupportedOperationException("ExtendedRiverPrimitive is tile-local scratch and is never persisted");
    }

    @Override
    public boolean notIntersect(double[] lowerCorner, double[] upperCorner) {
        throw new UnsupportedOperationException("ExtendedRiverPrimitive is tile-local scratch and is never indexed");
    }

    @Override
    public boolean contains(double[] lowerCorner, double[] upperCorner) {
        throw new UnsupportedOperationException("ExtendedRiverPrimitive is tile-local scratch and is never indexed");
    }

    @Override
    public boolean containsPoint(double[] queryPoint) {
        throw new UnsupportedOperationException("ExtendedRiverPrimitive is tile-local scratch and is never indexed");
    }

    @Override
    public boolean containsPointInflated(double[] queryPoint, double inflateRadius) {
        throw new UnsupportedOperationException("ExtendedRiverPrimitive is tile-local scratch and is never indexed");
    }

    @Override
    public void writeMbrInto(double[] mbrLowerCornerOut, double[] mbrUpperCornerOut) {
        throw new UnsupportedOperationException("ExtendedRiverPrimitive is tile-local scratch and is never indexed");
    }
}
