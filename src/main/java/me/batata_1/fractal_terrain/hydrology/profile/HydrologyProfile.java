package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;

/**
 * Shared primitives for the two sides of the same coin — {@link HydrologyProfileCarver} (elevation) and
 * {@code HydrologyProfilePainter} (blocks/biomes/vegetation). Both consume the flat, id-sorted
 * {@link HydrologicalUnit}[] returned by
 * {@link me.batata_1.fractal_terrain.hydrology.LocalRiverProvider#queryInfluence} the same way: walk each
 * contiguous run of equal {@link HydrologicalUnit#id() id} (one hydrological feature), find that run's
 * point nearest the query location, and compute a per-feature contribution that the caller merges.
 */
public final class HydrologyProfile {

    private HydrologyProfile() {}

    /** Receives one id-run (one hydrological feature) from {@link #forEachIdRun}. */
    @FunctionalInterface
    public interface IdRunConsumer {
        /**
         * @param units the full id-sorted array; this run is {@code units[start..end)}
         * @param start inclusive start index of the run
         * @param end exclusive end index of the run
         * @param nearestIdx index (into {@code units}) of the run's point nearest the query location
         * @param nearestDist distance from the query location to that nearest point
         */
        void accept(HydrologicalUnit[] units, int start, int end, int nearestIdx, double nearestDist);
    }

    /**
     * Walk {@code sorted} (which must be sorted by {@link HydrologicalUnit#id()}) one id-run at a time,
     * invoking {@code consumer} once per run with the run's bounds and its point nearest {@code pt}
     * (Euclidean, in the same frame as the units' coords). Allocates nothing per run.
     */
    public static void forEachIdRun(final HydrologicalUnit[] sorted, final double[] pt, final IdRunConsumer consumer) {
        int i = 0;
        while (i < sorted.length) {
            final int id = sorted[i].id();
            int j = i;
            int nearestIdx = i;
            double nearestDistSq = Double.POSITIVE_INFINITY;
            while (j < sorted.length && sorted[j].id() == id) {
                final double dx = sorted[j].coord().get(0) - pt[0];
                final double dz = sorted[j].coord().get(1) - pt[1];
                final double distSq = dx * dx + dz * dz;
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearestIdx = j;
                }
                j++;
            }
            consumer.accept(sorted, i, j, nearestIdx, Math.sqrt(nearestDistSq));
            i = j;
        }
    }
}
