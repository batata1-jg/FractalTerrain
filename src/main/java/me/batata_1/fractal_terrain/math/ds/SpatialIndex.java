package me.batata_1.fractal_terrain.math.ds;

import java.util.List;

/**
 * Minimal shared surface of every 2-D spatial index in the mod ({@link QuadTree},
 * {@link ImmutableQuadTree}, {@link ImmutableRTree}). Query APIs stay on the concrete types — they
 * differ fundamentally per structure (point circle/box queries on the trees, point-stabbing on the
 * R-tree) — so this interface carries only what index-agnostic consumers need: the tiled store
 * ({@code NonIntersectingSpatialIndex}) sizes and skips tiles by {@link #numEntries()}, and debug
 * renderers dump whole tiles via {@link #getAllEntries()}.
 */
public interface SpatialIndex<EntryType> {

    /** Number of entries stored in this index. */
    int numEntries();

    /** Every stored entry, as a freshly-allocated list (order unspecified). For debug renders/dumps. */
    List<EntryType> getAllEntries();

    /**
     * Guards the 2-D contract every index here assumes; centralized so the rank check cannot drift
     * between the three implementations if one ever grows a third dimension.
     */
    static void requirePlanar(double[] coords, String name) {
        if (coords.length != 2) {
            throw new IllegalStateException(name + " must be 2-D, got rank " + coords.length);
        }
    }

    /** {@link #requirePlanar(double[], String)} for a point's own rank. */
    static void requirePlanar(SpatialIndexPoint point, String name) {
        if (point.size() != 2) {
            throw new IllegalStateException(name + " must be 2-D, got rank " + point.size());
        }
    }

    /**
     * Rejects a non-finite input coordinate pair, which would corrupt every bounds compare the build
     * and the query descent make. Shared by the immutable indexes so what they accept at construction
     * cannot drift apart; {@code indexName} and {@code entry} name the rejecting index and the culprit.
     */
    static void requireFinite(double x, double z, String indexName, Object entry) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException(
                    indexName + " input has non-finite coordinates (" + x + ", " + z + "): " + entry);
        }
    }
}
