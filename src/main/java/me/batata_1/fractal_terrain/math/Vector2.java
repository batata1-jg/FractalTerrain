package me.batata_1.fractal_terrain.math;

/**
 * Mutable 2D float coordinate pair, relocated from the embedded {@code FastNoiseLite.Vector2} type.
 *
 * <p><b>Responsibility:</b> a plain {@code (x, y)} pair. Noise domain-warp strategies mutate the fields
 * in place to accumulate the warp offset.
 *
 * <p><b>Collaborators:</b> {@code me.batata_1.fractal_terrain.noise.FastNoiseLite#DomainWarp(Vector2)}
 * and the {@code me.batata_1.fractal_terrain.noise.strategy} domain-warp strategy classes.
 *
 * <p><b>Invariants:</b> field layout and semantics are byte-identical to the original embedded type —
 * this is a mechanical relocation, not a redesign.
 */
public class Vector2 {
    public /*FNLfloat*/ float x;
    public /*FNLfloat*/ float y;

    public Vector2(/*FNLfloat*/ float x, /*FNLfloat*/ float y) {
        this.x = x;
        this.y = y;
    }
}
