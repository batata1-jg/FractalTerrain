package me.batata_1.fractal_terrain.math;

/**
 * Mutable {@code (x, y)} float pair, relocated from the embedded {@code FastNoiseLite.Vector2} type.
 * Domain-warp strategies in {@code noise.strategy} mutate the fields in place to accumulate the warp
 * offset — field layout is unchanged from the original, since this was a mechanical relocation, not
 * a redesign.
 */
public class Vector2 {
    public /*FNLfloat*/ float x;
    public /*FNLfloat*/ float y;

    public Vector2(/*FNLfloat*/ float x, /*FNLfloat*/ float y) {
        this.x = x;
        this.y = y;
    }
}
