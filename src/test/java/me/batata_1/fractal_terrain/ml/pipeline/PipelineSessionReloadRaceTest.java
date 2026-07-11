package me.batata_1.fractal_terrain.ml.pipeline;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Reload-race regression exercise for MUST-1 (written before the {@link WorldPipeline} swap was wired).
 *
 * <p>A full runtime reproduction would need the ONNX diffusion models and asset-backed
 * {@link SyntheticMapFactory} (~1 GB weights + GPU), which is not headless/CI-runnable (plan DL-002). This
 * is the headless proxy: it drives the exact concurrency mechanism the fix relies on — a single
 * {@code volatile} {@link PipelineSession} reference swapped as one immutable unit versus multiple
 * independent field reads.
 *
 * <p>The reload-scoped triple is {@code (seed, syntheticMapFactory, tau)}, with the invariant that the
 * factory (and the noise draws that use it) always match {@code seed}. Here that pairing is stood in for by
 * {@code tau[0] == seed} (a real {@code SyntheticMapFactory} cannot be built without assets, so it is left
 * {@code null}). A writer thread continuously "reloads" by swapping in a fresh session whose {@code seed}
 * and {@code tau} agree; reader threads snapshot the volatile <b>once</b> and assert the pair is
 * self-consistent. Because the whole triple is published behind one volatile write of a final-field record,
 * no reader can ever observe a torn {@code (seed, tau)} — the property that a pre-fix trio of separate
 * volatile fields could not guarantee.
 */
class PipelineSessionReloadRaceTest {

    /** Mirrors {@code WorldPipeline.session}: the single volatile reference a reload swaps atomically. */
    private volatile PipelineSession session = new PipelineSession(0L, null, new float[] {0f});

    @Test
    @Timeout(30)
    void concurrentReloadNeverTearsTheSessionTriple() throws InterruptedException {
        final int iterations = 2_000_000;
        final int readerCount = 4;
        final AtomicReference<String> tear = new AtomicReference<>(null);
        final AtomicBoolean stop = new AtomicBoolean(false);
        final AtomicBoolean sawMultipleSeeds = new AtomicBoolean(false);

        final Thread writer = new Thread(
                () -> {
                    for (long s = 1; s <= iterations && tear.get() == null; s++) {
                        // Reload: build a fresh, internally consistent session and publish it in one swap.
                        // seed < 2^24 stays exactly representable as the float stored in tau, so a correct
                        // read compares equal; any torn read (old seed, new tau or vice-versa) would not.
                        session = new PipelineSession(s, null, new float[] {(float) s});
                    }
                    stop.set(true);
                },
                "reload-writer");

        final Runnable readerTask = () -> {
            while (!stop.get() && tear.get() == null) {
                final PipelineSession snapshot = session; // snapshot ONCE, like a worker's tile closure
                final long seed = snapshot.seed();
                final long tauSeed = (long) snapshot.tau()[0];
                if (seed != tauSeed) {
                    tear.compareAndSet(null, "torn read: seed=" + seed + " but tau[0]=" + tauSeed);
                    return;
                }
                if (seed > 1) sawMultipleSeeds.set(true);
            }
        };

        final Thread[] readers = new Thread[readerCount];
        for (int i = 0; i < readerCount; i++) {
            readers[i] = new Thread(readerTask, "reload-reader-" + i);
            readers[i].start();
        }
        writer.start();

        writer.join();
        for (final Thread reader : readers) reader.join();

        assertNull(tear.get(), tear.get());
        // Sanity: the readers actually observed reloads in flight (otherwise the test proves nothing).
        assertTrue(sawMultipleSeeds.get(), "readers never observed a reloaded session — race window not exercised");
    }
}
