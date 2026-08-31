package me.batata_1.fractal_terrain.infinitetensor;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import me.batata_1.fractal_terrain.storage.Storage;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;

public abstract class InfiniteTensor {

    private static final Logger LOG = getLogger(InfiniteTensor.class);

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

    /** Soft cap on cached window bytes; {@code Long.MAX_VALUE} disables eviction. */
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

    /** The read entry point: assembles a slice from however many windows it spans. */
    public FloatTensor getSlice(int[] start, int[] end) {
        int n = shape.length;
        int[][] pixelRange = buildRange(start, end);

        ensureComputed(pixelRange);

        // Accumulate contributions from all intersecting windows.
        int[] outShape = new int[n];
        for (int d = 0; d < n; d++) outShape[d] = end[d] - start[d];
        FloatTensor output = new FloatTensor(outShape);

        if (storage == null) throw new IllegalStateException("storage was not initialized");
        SliceGeometry.forEachIntersection(outputWindow, pixelRange, (windowIndex, dstRegion, srcRegion) -> {
            final FloatTensor cached = getEntryOrRecompute(windowIndex);
            if (cached == null) return;
            updateOutput(output, cached, dstRegion, srcRegion);
        });

        storage.evictIfNeeded(cacheLimitBytes);
        return output;
    }

    /** Self-heals a failed window read by recomputing once, since a failure here means an
     *  already-produced tile became unreadable on disk rather than a genuine miss. */
    private FloatTensor getEntryOrRecompute(int[] windowIndex) {
        try {
            return storage.getEntry(windowIndex);
        } catch (RuntimeException fetchFailure) {
            LOG.warn(
                    "failed to fetch tile {} for tensor '{}'; recomputing",
                    Arrays.toString(windowIndex),
                    id,
                    fetchFailure);
            computeSingle(windowIndex);
            return storage.getEntry(windowIndex);
        }
    }

    protected abstract void updateOutput(
            final FloatTensor output, final FloatTensor src, final int[][] dstRegion, final int[][] srcRegion);

    /** Materializes every window a read will touch, recursing into upstream dependencies first. */
    protected void ensureComputed(int[][] pixelRange) {
        ensureComputedRanges(Collections.singletonList(pixelRange));
    }

    /** Multi-range form of {@link #ensureComputed}. Dedupes rather than taking a bounding-box union,
     *  so scattered ranges do not drag in the windows between them. */
    protected void ensureComputedRanges(List<int[][]> pixelRanges) {
        Set<TileKey> pendingSet = new LinkedHashSet<>();
        for (int[][] range : pixelRanges) {
            int[] lo = outputWindow.getLowestIntersection(range);
            int[] hi = outputWindow.getHighestIntersection(range);
            if (storage == null) throw new IllegalStateException("storage was not initialized");
            iterateWindows(lo, hi, wi -> {
                // TileKey defensively copies wi, so it is safe to capture from the reused buffer.
                if (!storage.inStorage(wi)) pendingSet.add(new TileKey(wi));
            });
        }
        List<int[]> pending = pendingSet.stream().map(TileKey::toIntArray).collect(Collectors.toList());
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
        if (storage == null) throw new IllegalStateException("storage was not initialized");
        // Single-flight: only the thread that wins the claim gathers deps and runs the model; racing
        // threads await the winner's result instead of duplicating (expensive) inference.
        storage.getOrCompute(windowIndex, () -> {
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
            return result;
        });
    }

    private void computeBatched(@NotNull List<int[]> windowIndices) {
        if (storage == null) throw new IllegalStateException("storage was not initialized");
        int from = 0;
        while (from < windowIndices.size()) {
            int to = Math.min(from + batchSize, windowIndices.size());
            List<int[]> slice = windowIndices.subList(from, to);
            from = to;

            // Single-flight per window: claim each, only run inference over the windows we win. Per-element
            // results are independent, so a sub-batch of the won windows yields identical values.
            List<int[]> won = new ArrayList<>(slice.size());
            List<CompletableFuture<FloatTensor>> wonPromises = new ArrayList<>(slice.size());
            List<CompletableFuture<FloatTensor>> lost = new ArrayList<>();
            for (int[] wi : slice) {
                CompletableFuture<FloatTensor> claim = storage.claimForCompute(wi);
                if (claim != null) {
                    won.add(wi);
                    wonPromises.add(claim);
                } else {
                    CompletableFuture<FloatTensor> other = storage.peekFuture(wi);
                    if (other != null) lost.add(other);
                }
            }

            if (!won.isEmpty()) {
                int fulfilled = 0;
                try {
                    // args.get(depIdx) → list of tensors for that dep, one per WON window
                    List<List<FloatTensor>> args = new ArrayList<>(deps.length);
                    for (int i = 0; i < deps.length; i++) {
                        List<FloatTensor> depArgs = new ArrayList<>(won.size());
                        for (int[] windowIndex : won) {
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
                    List<FloatTensor> outputs = batchFunction.apply(won, args);
                    for (; fulfilled < won.size(); fulfilled++) {
                        FloatTensor result = outputs.get(fulfilled);
                        int[] windowIndex = won.get(fulfilled);
                        validateOutputShape(result, windowIndex);
                        storage.fulfillClaim(windowIndex, wonPromises.get(fulfilled), result);
                    }
                } catch (RuntimeException | Error t) {
                    // Release the claims we never settled so the keys can be retried.
                    for (int k = fulfilled; k < won.size(); k++) {
                        storage.abandonClaim(won.get(k), wonPromises.get(k), t);
                    }
                    throw t;
                }
            }

            // Windows other threads are computing: we settled our own claims FIRST, so awaiting here
            // cannot deadlock (claim/await edges only point downstream in the dependency DAG).
            for (CompletableFuture<FloatTensor> f : lost) {
                try {
                    f.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void validateOutputShape(@NotNull FloatTensor result, int[] windowIndex) {
        int n = outputWindow.size.length;
        if (result.ndim() != n) {
            throw new IllegalStateException(
                    "Function for tensor '" + id + "' returned shape with " + result.ndim() + " dims, expected " + n);
        }
        for (int d = 0; d < n; d++) {
            if (result.shape(d) != outputWindow.size[d]) {
                throw new IllegalStateException("Function for tensor '" + id + "' returned shape[" + d + "]="
                        + result.shape(d) + ", expected " + outputWindow.size[d]);
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

    /** Walks window indices without allocating per iteration. The {@code int[]} handed to
     *  {@code action} is a reused buffer — copy it if it must outlive the callback. */
    static void iterateWindows(int[] lo, int[] hi, InfiniteTensor.WindowConsumer action) {
        int n = lo.length;
        for (int d = 0; d < n; d++) {
            if (lo[d] > hi[d]) return;
        }
        int[] current = lo.clone();

        outer:
        while (true) {
            action.accept(current);

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
        storage = new Storage<>(newPath, id, outputWindow.ndim(), new FloatTensor(new int[] {1}));
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
