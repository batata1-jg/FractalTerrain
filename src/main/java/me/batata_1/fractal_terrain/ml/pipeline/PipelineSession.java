package me.batata_1.fractal_terrain.ml.pipeline;

/**
 * Immutable snapshot of {@link WorldPipeline}'s reload-scoped inputs: {@code seed}, the derived
 * {@link SyntheticMapFactory}, and {@code tau}.
 *
 * <p>Bundled into one record behind a single volatile reference so a reload is atomic. Held as separate
 * fields, a reload interleaving with a worker's reads could pair one seed's synthetic map with another
 * seed's noise. A worker snapshots this once per tile and always sees a consistent triple.
 *
 * <p>{@code tau} must not be mutated by callers of {@link #tau()}.
 */
public record PipelineSession(long seed, SyntheticMapFactory syntheticMapFactory, float[] tau) {}
