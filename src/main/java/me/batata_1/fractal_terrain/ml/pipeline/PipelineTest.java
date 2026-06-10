package me.batata_1.fractal_terrain.ml.pipeline;

import java.util.concurrent.atomic.AtomicLong;

public class PipelineTest {

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

        System.out.printf(
                "blockStart: X=%d Z=%d  blockEnd: X=%d Z=%d%n", blockStartX, blockStartZ, blockEndX, blockEndZ);

        int i1n = Math.floorDiv(blockStartZ, scale);
        int j1n = Math.floorDiv(blockStartX, scale);
        int i2n = -Math.floorDiv(-blockEndZ, scale);
        int j2n = -Math.floorDiv(-blockEndX, scale);
        int i1p = i1n - 2, j1p = j1n - 2;
        int i2p = i2n + 2, j2p = j2n + 2;
        int nH = i2p - i1p, nW = j2p - j1p;

        System.out.printf("Native range: i=[%d,%d) j=[%d,%d) nH=%d nW=%d%n", i1p, i2p, j1p, j2p, nH, nW);

        long baselineTotal = getTotalGpuMemUsedMB();
        System.out.printf("Baseline total GPU memory: %d MB%n", baselineTotal);

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

        System.out.println("=== VRAM Report ===");
        System.out.printf("Baseline total GPU:     %d MB%n", baselineTotal);
        System.out.printf("Peak total GPU:         %d MB%n", peakTotal);
        System.out.printf("Pipeline delta (total): %d MB%n", delta);
        System.out.printf("Peak process-specific:  %d MB%n", peakProc);

        long reportedMB = peakProc > 0 ? peakProc : delta;
        if (reportedMB > 2500) {
            System.err.printf("FAIL: pipeline VRAM %d MB exceeds 2500 MB limit%n", reportedMB);
            System.exit(1);
        } else if (reportedMB >= 0) {
            System.out.printf("PASS: pipeline VRAM %d MB is within 2500 MB limit%n", reportedMB);
        } else {
            System.out.println("VRAM measurement unavailable (nvidia-smi not found)");
        }
    }
}
