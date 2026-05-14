package me.batata_1.fractalterrain.debug;

import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of per-tensor cache statistics from a {@link me.batata_1.fractalterrain.infinitetensor.MemoryTileStore}.
 * Obtain via {@code MemoryTileStore#takeSnapshot()}.
 */
public final class
MemoryProfiler {

    private MemoryProfiler() {}

    public static final class TensorStats {
        public final String id;
        /** Windows newly computed and cached (proxy for cache misses). */
        public final long computeCount;
        public final long evictionCount;
        /** Number of windows currently in the LRU cache. */
        public final int cachedWindows;
        public final long currentBytes;
        public final long peakBytes;

        public TensorStats(String id, long computeCount, long evictionCount,
                           int cachedWindows, long currentBytes, long peakBytes) {
            this.id = id;
            this.computeCount = computeCount;
            this.evictionCount = evictionCount;
            this.cachedWindows = cachedWindows;
            this.currentBytes = currentBytes;
            this.peakBytes = peakBytes;
        }
    }

    public static final class Snapshot {
        public final long timestampMs;
        public final List<TensorStats> perTensor;
        public final long totalCurrentBytes;
        public final long totalComputeCount;
        public final long totalEvictions;

        public Snapshot(long timestampMs, List<TensorStats> perTensor) {
            this.timestampMs = timestampMs;
            this.perTensor = Collections.unmodifiableList(perTensor);
            long bytes = 0, computes = 0, evictions = 0;
            for (TensorStats s : perTensor) {
                bytes    += s.currentBytes;
                computes += s.computeCount;
                evictions += s.evictionCount;
            }
            this.totalCurrentBytes = bytes;
            this.totalComputeCount = computes;
            this.totalEvictions    = evictions;
        }

        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== MemoryProfiler Snapshot (t=%dms) ===%n", timestampMs));
            sb.append(String.format("  Total cached       : %s%n",   humanBytes(totalCurrentBytes)));
            sb.append(String.format("  Total computed     : %d windows%n", totalComputeCount));
            sb.append(String.format("  Total evicted      : %d windows%n", totalEvictions));
            sb.append(String.format("  Registered tensors : %d%n",   perTensor.size()));
            sb.append("  --- Per-tensor ---\n");
            for (TensorStats s : perTensor) {
                sb.append(String.format(
                        "    %-36s  cached=%4d  cur=%8s  peak=%8s  computed=%6d  evicted=%6d%n",
                        s.id, s.cachedWindows,
                        humanBytes(s.currentBytes), humanBytes(s.peakBytes),
                        s.computeCount, s.evictionCount));
            }
            return sb.toString();
        }

        private static String humanBytes(long bytes) {
            if (bytes < 1024L)               return bytes + " B";
            if (bytes < 1024L * 1024)        return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
