package me.batata_1.fractal_terrain.infinitetensor;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import me.batata_1.fractal_terrain.storage.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public abstract class InfiniteTensor {

    protected final String id;

    /** Shape in each dimension; null = unbounded. */
    protected final int[] shape;

    /** Defines position and size of each output window. */
    protected final TensorWindow outputWindow;

    /** Non-batched compute function (null if batched). */
    protected final TensorFunction function;

    /** Batched compute function (null if non-batched). */
    protected final TensorFunction.BatchTensorFunction batchFunction;

    /** Maximum number of windows per batch call (0 = non-batched). */
    protected final int batchSize;

    /** Upstream dependency tensors. */
    protected final InfiniteTensor[] deps;

    /** How to slice each dependency for a given window index. */
    protected final TensorWindow[] depWindows;

    /** Owning storage — used for cache reads/writes and dependency resolution. */
    protected volatile Storage<FloatTensor> storage;

    protected volatile AtomicLong counter = new AtomicLong(0);

    /**
     * Soft limit on cached window bytes.  {@code Long.MAX_VALUE} = unlimited.
     * Eviction occurs after a new window is written.
     */
    protected final long cacheLimitBytes;

    InfiniteTensor(
            String id,
            int[] shape,
            TensorWindow outputWindow,
            TensorFunction function,
            TensorFunction.BatchTensorFunction batchFunction,
            int batchSize,
            InfiniteTensor[] deps,
            TensorWindow[] depWindows,
            long cacheLimitBytes) {
        this.id = id;
        this.shape = shape;
        this.outputWindow = outputWindow;
        this.function = function;
        this.batchFunction = batchFunction;
        this.batchSize = batchSize;
        this.deps = deps;
        this.depWindows = depWindows;
        this.storage = null;
        this.cacheLimitBytes = cacheLimitBytes;
    }

    /**
     * Retrieve a contiguous slice of this tensor.
     *
     * @param start inclusive pixel start per dimension
     * @param end   exclusive pixel end per dimension
     * @return the accumulated FloatTensor
     */
    public FloatTensor getSlice(int[] start, int[] end) {
        int n = shape.length;
        int[][] pixelRange = buildRange(start, end);

        ensureComputed(pixelRange);

        // Accumulate contributions from all intersecting windows.
        int[] outShape = new int[n];
        for (int d = 0; d < n; d++) outShape[d] = end[d] - start[d];
        FloatTensor output = new FloatTensor(outShape);

        int[] lo = outputWindow.getLowestIntersection(pixelRange);
        int[] hi = outputWindow.getHighestIntersection(pixelRange);
        if (storage == null) throw new IllegalStateException("storage was not initialized");
        iterateWindows(lo, hi, windowIndex -> {
            final FloatTensor cached = storage.getEntry(windowIndex);
            // FloatTensor cached = storage.getCachedWindow(id, windowIndex);
            if (cached == null) return;

            final int[][] wBounds = outputWindow.getBounds(windowIndex);

            // Intersection of the window bounds with the requested pixel range.
            final int[][] isect = new int[n][2];
            for (int d = 0; d < n; d++) {
                isect[d][0] = Math.max(pixelRange[d][0], wBounds[d][0]);
                isect[d][1] = Math.min(pixelRange[d][1], wBounds[d][1]);
                if (isect[d][0] >= isect[d][1]) return; // no overlap
            }

            final int[][] srcRegion = new int[n][2];
            final int[][] dstRegion = new int[n][2];
            for (int d = 0; d < n; d++) {
                srcRegion[d][0] = isect[d][0] - wBounds[d][0];
                srcRegion[d][1] = isect[d][1] - wBounds[d][0];
                dstRegion[d][0] = isect[d][0] - pixelRange[d][0];
                dstRegion[d][1] = isect[d][1] - pixelRange[d][0];
            }

            updateOutput(output, cached, dstRegion, srcRegion);
            output.addFrom(cached, dstRegion, srcRegion);
        });

        storage.evictIfNeeded(cacheLimitBytes);
        return output;
    }

    protected abstract void updateOutput(
            final FloatTensor output, final FloatTensor src, final int[][] dstRegion, final int[][] srcRegion);

    /**
     * Ensures every window intersecting {@code pixelRange} is present in the cache.
     * Recursively ensures upstream dependencies are computed first.
     */
    protected void ensureComputed(int[][] pixelRange) {
        ensureComputedRanges(Collections.singletonList(pixelRange));
    }

    /**
     * Ensures every window that intersects any of the given pixel ranges is present.
     * Matches Python _apply_f_range: each range is expanded to window indices, then
     * deduped (no bounding-box union, so we only request windows that actually intersect
     * at least one range).
     */
    protected void ensureComputedRanges(List<int[][]> pixelRanges) {
        Set<List<Integer>> pendingSet = new LinkedHashSet<>();
        for (int[][] range : pixelRanges) {
            int[] lo = outputWindow.getLowestIntersection(range);
            int[] hi = outputWindow.getHighestIntersection(range);
            if (storage == null) throw new IllegalStateException("storage was not initialized");
            iterateWindows(lo, hi, wi -> {
                if (!storage.inStorage(wi)) {
                    List<Integer> key = new ArrayList<>(wi.length);
                    for (int v : wi) key.add(v);
                    pendingSet.add(key);
                }
            });
        }
        List<int[]> pending = pendingSet.stream()
                .map(k -> k.stream().mapToInt(Integer::intValue).toArray())
                .collect(Collectors.toList());
        if (pending.isEmpty()) return;

        // Dependencies get the exact list of pixel ranges (one per our pending window), not a union.
        for (int i = 0; i < deps.length; i++) {
            List<int[][]> depRanges = new ArrayList<>(pending.size());
            for (int[] wi : pending) {
                depRanges.add(depWindows[i].getBounds(wi));
            }
            deps[i].ensureComputedRanges(depRanges);
        }

        if (batchSize > 0 && batchFunction != null) {
            computeBatched(pending);
        } else {
            for (int[] windowIndex : pending) {
                computeSingle(windowIndex);
            }
        }
    }

    protected void ensureComputedSingle(int[] windowIndex) {
        ensureComputed(outputWindow.getBounds(windowIndex));
    }

    private void computeSingle(int[] windowIndex) {
        List<FloatTensor> args = new ArrayList<>(deps.length);
        for (int i = 0; i < deps.length; i++) {
            int[][] bounds = depWindows[i].getBounds(windowIndex);
            int[] depStart = new int[bounds.length];
            int[] depEnd = new int[bounds.length];
            for (int d = 0; d < bounds.length; d++) {
                depStart[d] = bounds[d][0];
                depEnd[d] = bounds[d][1];
            }
            args.add(deps[i].getSlice(depStart, depEnd));
        }
        counter.getAndIncrement();
        FloatTensor result = function.apply(windowIndex, args);
        validateOutputShape(result, windowIndex);
        // storage.cacheWindow(id, windowIndex, result);
        if (storage == null) throw new IllegalStateException("storage was not initialized");
        storage.addOrOverwriteEntry(CompletableFuture.completedFuture(result), windowIndex);
    }

    private void computeBatched(@NotNull List<int[]> windowIndices) {
        int from = 0;
        while (from < windowIndices.size()) {
            int to = Math.min(from + batchSize, windowIndices.size());
            List<int[]> batch = windowIndices.subList(from, to);

            // args.get(depIdx) → list of tensors for that dep, one per window
            List<List<FloatTensor>> args = new ArrayList<>(deps.length);
            for (int i = 0; i < deps.length; i++) {
                List<FloatTensor> depArgs = new ArrayList<>(batch.size());
                for (int[] windowIndex : batch) {
                    int[][] bounds = depWindows[i].getBounds(windowIndex);
                    int[] depStart = new int[bounds.length];
                    int[] depEnd = new int[bounds.length];
                    for (int d = 0; d < bounds.length; d++) {
                        depStart[d] = bounds[d][0];
                        depEnd[d] = bounds[d][1];
                    }
                    depArgs.add(deps[i].getSlice(depStart, depEnd));
                }
                args.add(depArgs);
            }
            counter.getAndIncrement();
            List<FloatTensor> outputs = batchFunction.apply(batch, args);
            if (storage == null) throw new IllegalStateException("storage was not initialized");
            for (int k = 0; k < batch.size(); k++) {
                FloatTensor result = outputs.get(k);
                int[] windowIndex = batch.get(k);
                validateOutputShape(result, windowIndex);
                storage.addOrOverwriteEntry(CompletableFuture.completedFuture(result), windowIndex);
                // storage.cacheWindow(id, windowIndex, result);
            }

            from = to;
        }
    }

    private void validateOutputShape(@NotNull FloatTensor result, int[] windowIndex) {
        int n = outputWindow.size.length;
        if (result.shape.length != n) {
            throw new IllegalStateException("Function for tensor '" + id + "' returned shape with "
                    + result.shape.length + " dims, expected " + n);
        }
        for (int d = 0; d < n; d++) {
            if (result.shape[d] != outputWindow.size[d]) {
                throw new IllegalStateException("Function for tensor '" + id + "' returned shape[" + d + "]="
                        + result.shape[d] + ", expected " + outputWindow.size[d]);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    static int[][] buildRange(int[] start, int[] end) {
        int n = start.length;
        int[][] range = new int[n][2];
        for (int d = 0; d < n; d++) {
            range[d][0] = start[d];
            range[d][1] = end[d];
        }
        return range;
    }

    /**
     * Iterate over all window index combinations in the inclusive range [lo, hi].
     */
    static void iterateWindows(int[] lo, int[] hi, InfiniteTensor.WindowConsumer action) {
        int n = lo.length;
        for (int d = 0; d < n; d++) {
            if (lo[d] > hi[d]) return;
        }
        int[] current = lo.clone();

        outer:
        while (true) {
            action.accept(current.clone());

            // Increment like a mixed-radix counter (last dim first).
            for (int d = n - 1; d >= 0; d--) {
                current[d]++;
                if (current[d] <= hi[d]) break;
                current[d] = lo[d];
                if (d == 0) break outer;
            }
        }
    }

    @Nullable
    public String getCurrentPath() {
        return (storage != null) ? storage.getPath() : null;
    }

    public synchronized long getAppliedFCount() {
        return counter.get();
    }

    public synchronized void updatePath(String newPath) {
        if (Objects.equals(getCurrentPath(), newPath + "/" + id)) return;
        if (storage != null) storage.clear();
        storage = new Storage<>(newPath + "/" + id, outputWindow.ndim(), new FloatTensor(new int[] {1}));
        for (InfiniteTensor dependent : deps) dependent.updatePath(newPath);
    }

    @FunctionalInterface
    interface WindowConsumer {
        void accept(int[] windowIndex);
    }

    @TestOnly
    public Storage<FloatTensor> getStorage() {
        return storage;
    }
}
