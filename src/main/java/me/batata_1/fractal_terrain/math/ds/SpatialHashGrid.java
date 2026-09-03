package me.batata_1.fractal_terrain.math.ds;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;

/**
 * Mutable, bucketed point index for live insert/remove interleaved with circle queries — the one access
 * pattern neither immutable index in this package covers, since {@link ImmutableQuadTree}/
 * {@link ImmutableRTree} have no mutation path by design (see {@code README.md}).
 *
 * <p>Exists for {@code RiverNetwork.manageCutoffs}'s cut-and-continue walk, which removes points
 * mid-scan; rebuilding a whole immutable tree after every cut would cost far more than the bulk load it
 * amortizes elsewhere. No lock: per {@code README.md}'s invariant, every caller builds and mutates one
 * index per tile on a single thread, so {@link QuadTree}'s {@code ReentrantReadWriteLock} would only buy
 * uncontended overhead here.
 */
public final class SpatialHashGrid<T extends SpatialIndexPoint> implements SpatialIndex<T> {

    /** Cells are {@code cellSize} wide on each axis; size it to the caller's own query radius. */
    public SpatialHashGrid(double cellSize) {
        if (!(cellSize > 0)) throw new IllegalArgumentException("cellSize must be > 0: " + cellSize);
        this.cellSize = cellSize;
    }

    public void insertPoint(T pt) {
        SpatialIndex.requirePlanar(pt, "point");
        final long key = cellKey(pt.get(X), pt.get(Z));
        ObjectArrayList<T> bucket = buckets.get(key);
        if (bucket == null) {
            bucket = new ObjectArrayList<>();
            buckets.put(key, bucket);
        }
        bucket.add(pt);
        size++;
    }

    public void removePoint(T pt) {
        SpatialIndex.requirePlanar(pt, "point");
        final long key = cellKey(pt.get(X), pt.get(Z));
        final ObjectArrayList<T> bucket = buckets.get(key);
        if (bucket != null && bucket.remove(pt)) {
            size--;
            if (bucket.isEmpty()) buckets.remove(key);
        }
    }

    public void clear() {
        buckets.clear();
        size = 0;
    }

    public boolean containsPoint(T pt) {
        final ObjectArrayList<T> bucket = buckets.get(cellKey(pt.get(X), pt.get(Z)));
        return bucket != null && bucket.contains(pt);
    }

    /** Exact circle query: the covering cells are a superset, so every candidate is re-tested against
     *  the true squared distance before being returned (see {@code README.md}'s correctness note). */
    public List<T> getPointsInCircle(double[] center, double radius) {
        SpatialIndex.requirePlanar(center, "center");
        final List<T> hits = new ObjectArrayList<>();
        final double radiusSq = radius * radius;
        final long minCellX = cellIndex(center[X] - radius);
        final long maxCellX = cellIndex(center[X] + radius);
        final long minCellZ = cellIndex(center[Z] - radius);
        final long maxCellZ = cellIndex(center[Z] + radius);
        for (long cx = minCellX; cx <= maxCellX; cx++) {
            for (long cz = minCellZ; cz <= maxCellZ; cz++) {
                final ObjectArrayList<T> bucket = buckets.get(packKey(cx, cz));
                if (bucket == null) continue;
                for (T pt : bucket) {
                    final double deltaX = pt.get(X) - center[X];
                    final double deltaZ = pt.get(Z) - center[Z];
                    if (deltaX * deltaX + deltaZ * deltaZ <= radiusSq) hits.add(pt);
                }
            }
        }
        return hits;
    }

    @Override
    public int numEntries() {
        return size;
    }

    @Override
    public List<T> getAllEntries() {
        final List<T> all = new ObjectArrayList<>(size);
        for (ObjectArrayList<T> bucket : buckets.values()) all.addAll(bucket);
        return all;
    }

    private static final int X = 0;
    private static final int Z = 1;

    private final double cellSize;
    private final Long2ObjectOpenHashMap<ObjectArrayList<T>> buckets = new Long2ObjectOpenHashMap<>();
    private int size = 0;

    private long cellIndex(double coord) {
        return (long) Math.floor(coord / cellSize);
    }

    private long cellKey(double x, double z) {
        return packKey(cellIndex(x), cellIndex(z));
    }

    /** Packs two cell indices into one lookup key; a true bijection as long as both stay within
     *  {@code int} range, which every real caller's coordinate/cellSize ratio does. */
    private static long packKey(long cx, long cz) {
        return (cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
