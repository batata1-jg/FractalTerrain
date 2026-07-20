package me.batata_1.fractal_terrain.debug;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs {@link #codeBlock()} on a background thread and aborts if it does not finish within the
 * given timeout.
 *
 * <pre>{@code
 * new TimeLimitedCodeBlock(5, TimeUnit.MINUTES) {
 *     @Override
 *     public void codeBlock() {
 *         // Do stuff here.
 *     }
 * }.run();
 * }</pre>
 */
public abstract class TimeLimitedCodeBlock {

    private final long timeout;
    private final TimeUnit unit;

    protected TimeLimitedCodeBlock(long timeout, TimeUnit unit) {
        this.timeout = timeout;
        this.unit = unit;
    }

    public abstract void codeBlock() throws Exception;

    public void run() {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> future = executor.submit(() -> {
                try {
                    codeBlock();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            executor.shutdown(); // This does not cancel the already-scheduled task.

            try {
                future.get(timeout, unit);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("TimeLimitedCodeBlock was interrupted");
            } catch (ExecutionException ee) {
                throw new RuntimeException(ee.getCause());
            } catch (TimeoutException te) {
                future.cancel(true);
                throw new RuntimeException("TimeLimitedCodeBlock interrupted");
            } finally {
                if (!executor.isTerminated()) {
                    executor.shutdownNow();
                }
            }
        }
    }
}
