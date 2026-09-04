package me.batata_1.fractal_terrain.debug.tests;

import java.util.concurrent.atomic.AtomicLong;
import me.batata_1.fractal_terrain.debug.Debug;
import org.slf4j.Logger;

/**
 * VRAM-baseline sampler: polls {@code nvidia-smi} for GPU memory for a couple of seconds and reports the
 * peak/delta against a 2500 MB budget. Despite the class name and the {@code pipelineTest} Gradle task,
 * this harness currently makes no calls into {@code WorldPipeline} or {@code ModelAssetManager} — no
 * inference runs, so the reported "pipeline delta" is effectively an idle GPU-memory baseline rather than
 * a measurement of actual pipeline VRAM usage.
 */
public class PipelineTest {

    private static final Logger LOG = Debug.getLogger(PipelineTest.class);

    private static long getProcessGpuMemMB() {
        long pid = ProcessHandle.current().pid();
        try {
            Process p = new ProcessBuilder(
                            "nvidia-smi", "--query-compute-apps=pid,used_memory", "--format=csv,noheader,nounits")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            for (String line : output.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length >= 2 && parts[0].trim().equals(String.valueOf(pid))) {
                    return Long.parseLong(parts[1].trim());
                }
            }
            return 0;
        } catch (Exception e) {
            LOG.warn("Failed to query per-process GPU memory via nvidia-smi", e);
            return -1;
        }
    }

    private static long getTotalGpuMemUsedMB() {
        try {
            Process p = new ProcessBuilder("nvidia-smi", "--query-gpu=memory.used", "--format=csv,noheader,nounits")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            return Long.parseLong(output.split("\n")[0].trim());
        } catch (Exception e) {
            LOG.warn("Failed to query total GPU memory via nvidia-smi", e);
            return -1;
        }
    }

    public static void main(String[] args) throws Exception {
        long seed = -5408366058459925370L;
        int scale = 2;
        int TILE_SIZE = 256;

        int blockX = -16160, blockZ = -59510;
        int blockStartX = (blockX >> 8) << 8;
        int blockStartZ = (blockZ >> 8) << 8;
        int blockEndX = blockStartX + TILE_SIZE;
        int blockEndZ = blockStartZ + TILE_SIZE;

        LOG.info("blockStart: X={} Z={}  blockEnd: X={} Z={}", blockStartX, blockStartZ, blockEndX, blockEndZ);

        int i1n = Math.floorDiv(blockStartZ, scale);
        int j1n = Math.floorDiv(blockStartX, scale);
        int i2n = -Math.floorDiv(-blockEndZ, scale);
        int j2n = -Math.floorDiv(-blockEndX, scale);
        int i1p = i1n - 2, j1p = j1n - 2;
        int i2p = i2n + 2, j2p = j2n + 2;
        int nH = i2p - i1p, nW = j2p - j1p;

        LOG.info("Native range: i=[{},{}) j=[{},{}) nH={} nW={}", i1p, i2p, j1p, j2p, nH, nW);

        long baselineTotal = getTotalGpuMemUsedMB();
        LOG.info("Baseline total GPU memory: {} MB", baselineTotal);

        AtomicLong peakProcessMB = new AtomicLong(0);
        AtomicLong peakTotalMB = new AtomicLong(baselineTotal < 0 ? 0 : baselineTotal);
        Thread monitor = new Thread(
                () -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        long proc = getProcessGpuMemMB();
                        if (proc >= 0) peakProcessMB.updateAndGet(v -> Math.max(v, proc));
                        long total = getTotalGpuMemUsedMB();
                        if (total >= 0) peakTotalMB.updateAndGet(v -> Math.max(v, total));
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                },
                "vram-monitor");
        monitor.setDaemon(true);
        monitor.start();

        monitor.interrupt();
        monitor.join(2000);

        long peakProc = peakProcessMB.get();
        long peakTotal = peakTotalMB.get();
        long delta = baselineTotal >= 0 ? peakTotal - baselineTotal : -1;

        LOG.info("=== VRAM Report ===");
        LOG.info("Baseline total GPU:     {} MB", baselineTotal);
        LOG.info("Peak total GPU:         {} MB", peakTotal);
        LOG.info("Pipeline delta (total): {} MB", delta);
        LOG.info("Peak process-specific:  {} MB", peakProc);

        long reportedMB = peakProc > 0 ? peakProc : delta;
        if (reportedMB > 2500) {
            LOG.error("FAIL: pipeline VRAM {} MB exceeds 2500 MB limit", reportedMB);
            System.exit(1);
        } else if (reportedMB >= 0) {
            LOG.info("PASS: pipeline VRAM {} MB is within 2500 MB limit", reportedMB);
        } else {
            LOG.info("VRAM measurement unavailable (nvidia-smi not found)");
        }
    }
}
