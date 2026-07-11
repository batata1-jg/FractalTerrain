package me.batata_1.fractal_terrain.math;

/**
 * Mutable 3D float coordinate triple, relocated from the embedded {@code FastNoiseLite.Vector3} type.
 *
 * <p><b>Responsibility:</b> a plain {@code (x, y, z)} triple. Noise domain-warp strategies mutate the
 * fields in place to accumulate the warp offset.
 *
 * <p><b>Collaborators:</b> {@code me.batata_1.fractal_terrain.noise.FastNoiseLite#DomainWarp(Vector3)}
 * and the {@code me.batata_1.fractal_terrain.noise.strategy} domain-warp strategy classes.
 *
 * <p><b>Invariants:</b> field layout and semantics are byte-identical to the original embedded type —
 * this is a mechanical relocation, not a redesign.
 */
public class Vector3 {
    public /*FNLfloat*/ float x;
    public /*FNLfloat*/ float y;
    public /*FNLfloat*/ float z;

    public Vector3(/*FNLfloat*/ float x, /*FNLfloat*/ float y, /*FNLfloat*/ float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
