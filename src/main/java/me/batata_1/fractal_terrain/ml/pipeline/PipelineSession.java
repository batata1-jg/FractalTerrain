package me.batata_1.fractal_terrain.ml.pipeline;

/**
 * Immutable snapshot of the reload-scoped inputs to {@link WorldPipeline} generation: the world
 * {@code seed}, the {@link SyntheticMapFactory} derived from it, and the {@code tau} vector.
 *
 * <p><b>Reload-race contract (MUST-1).</b> These three values must move together: a
 * {@link SyntheticMapFactory} is only ever paired with the exact {@code seed} it was constructed from,
 * and the Gaussian-noise draws in each stage's tile compute derive from that same {@code seed}.
 * Previously the pipeline held them as three separate {@code volatile} fields and a reload
 * ({@code updateInstance}) rewrote them in sequence; a worker computing a tile read the fields
 * independently, so a reload interleaved between two reads could mix an old synthetic map with a new
 * seed's noise (a torn multi-field read). Bundling them into one immutable object swapped behind a single
 * {@code volatile} reference makes the whole triple change atomically from a reader's view: a worker
 * snapshots the session once at the top of its tile closure ({@code final PipelineSession s = session;})
 * and reads all three from that snapshot, so it always sees a self-consistent {@code (seed, map, tau)}.
 *
 * <p>Publication is safe by construction: the fields are {@code final} (a record), so any thread that
 * observes the reference through the pipeline's {@code volatile session} field sees them fully initialized.
 * {@code tau} is treated as immutable — it is never mutated after construction and callers must not mutate
 * the array returned by {@link #tau()}.
 */
public record PipelineSession(long seed, SyntheticMapFactory syntheticMapFactory, float[] tau) {}
