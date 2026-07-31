package me.batata_1.fractal_terrain.math;

/**
 * Mutable {@code (x, y, z)} float triple, relocated from the embedded {@code FastNoiseLite.Vector3}
 * type. Domain-warp strategies in {@code noise.strategy} mutate the fields in place to accumulate the
 * warp offset — field layout is unchanged from the original, since this was a mechanical relocation,
 * not a redesign.
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
