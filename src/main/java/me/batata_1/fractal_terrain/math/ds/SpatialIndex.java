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
}
