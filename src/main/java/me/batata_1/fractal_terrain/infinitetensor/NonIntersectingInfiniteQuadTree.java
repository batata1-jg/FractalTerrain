package me.batata_1.fractal_terrain.infinitetensor;

import com.google.common.base.Function;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import me.batata_1.fractal_terrain.math.ds.ImmutableQuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.storage.EntryNotLoadableException;
import me.batata_1.fractal_terrain.storage.Storage;
import me.batata_1.fractal_terrain.storage.TileKey;

/**
 * Lazily-computed, infinitely-tiled store whose per-tile payload is an {@link ImmutableQuadTree}
 * (instead of a {@code FloatTensor}). Mirrors {@link NonIntersectingInfiniteTensor}: non-overlapping
 * tiles keyed by window index, each computed on demand by {@code entryCreatingFunction} and cached in
 * {@link Storage}.
 *
 * <p>Whether tiles persist to disk depends on the point type: the store is disk-backed when the
 * supplied {@code pointPrototype} is {@link me.batata_1.fractal_terrain.storage.Persistable} (e.g.
 * {@link me.batata_1.fractal_terrain.hydrology.HydrologicalUnit}) and cache-only otherwise (e.g.
 * a bare coordinate point). {@code Storage}'s constructor decides this once by probing the prototype
 * tile — which is why the prototype is seeded with one dummy point, so the probe exercises point
 * serialization.
 *
 * <p>The {@code getValue} of the tensor variant is replaced by {@link #getValuesWithin}: a
 * single-tile lookup that finds the owning tile for a coordinate and runs a spatial query on its
 * {@code ImmutableQuadTree}, bounded to that tile's logical window.
 */
public class NonIntersectingInfiniteQuadTree<T extends QuadTreePoint> extends Storage<ImmutableQuadTree<T>> {

    private final TensorWindow outWindow;
    private final Function<TileKey, ImmutableQuadTree<T>> entryCreatingFunction;

    public NonIntersectingInfiniteQuadTree(
            String path, String name, int[] shape, T pointPrototype, Function<TileKey, ImmutableQuadTree<T>> f) {
        super(path, name, shape.length, prototypeTree(pointPrototype));
        this.entryCreatingFunction = f;
        this.outWindow = new TensorWindow(shape);
    }

    /**
     * The deserialization prototype tree: it carries the {@code pointPrototype} (used to rebuild
     * stored point chunks) and is seeded with that one point so {@code Storage}'s serializability
     * probe actually attempts to serialize a point.
     */
    private static <T extends QuadTreePoint> ImmutableQuadTree<T> prototypeTree(T pointPrototype) {
        return new ImmutableQuadTree<>(
                new double[] {0, 0}, new double[] {1, 1}, List.of(pointPrototype), pointPrototype);
    }

    /**
     * Like {@link NonIntersectingInfiniteTensor#loadInto}: try the base disk-reload path first, and
     * when it cannot produce the entry (signalled by {@link EntryNotLoadableException} — cache-only,
     * unpersisted, or a corrupt/missing file) recompute the tile from {@code entryCreatingFunction}
     * on the CALLING thread. Cleanup of a failed claim is handled by {@code fetchEntry}.
     */
    @Override
    protected void loadInto(TileKey key, CompletableFuture<ImmutableQuadTree<T>> promise) {
        try {
            super.loadInto(key, promise);
        } catch (EntryNotLoadableException miss) {
            final ImmutableQuadTree<T> entry = entryCreatingFunction.apply(key);
            persistAndRecord(key, entry);
            promise.complete(entry);
        }
    }

    /** The (re)computed {@link ImmutableQuadTree} for the tile owning {@code (queryX, queryZ)}. */
    public List<T> getValuesWithin(final double[] coords, final double radius) {
        final ImmutableQuadTree<T> entry = getEntry(outWindow.getSinglePixelIntersection(coords));
        return entry.getPointsInCircle(outWindow.getPerWindowCoord(coords), radius);
    }
}
