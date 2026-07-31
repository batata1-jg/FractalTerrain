package me.batata_1.fractal_terrain.noise.strategy;

import me.batata_1.fractal_terrain.math.Vector2;
import me.batata_1.fractal_terrain.math.Vector3;

/**
 * The {@code BasicGrid} domain-warp strategy: warps a coordinate by interpolating hashed lattice random-vectors.
 *
 * <p>One of the interchangeable domain-warp strategies {@code FastNoiseLite} dispatches to; extracted so the
 * dispatcher stays readable and each variant can be read on its own.
 *
 * <p><b>Do not "clean up" any constant or reorder any expression.</b> This is a mechanical extraction
 * from FastNoiseLite 1.1.1 and must stay byte-identical — every world already generated depends on it.
 */
public final class BasicGridWarpStrategy {

    private BasicGridWarpStrategy() {}

    public static void SingleDomainWarpBasicGrid(
            int seed, float warpAmp, float frequency, /*FNLfloat*/ float x, /*FNLfloat*/ float y, Vector2 coord) {
        /*FNLfloat*/ float xf = x * frequency;
        /*FNLfloat*/ float yf = y * frequency;

        int x0 = NoiseTables.FastFloor(xf);
        int y0 = NoiseTables.FastFloor(yf);

        float xs = NoiseTables.InterpHermite((float) (xf - x0));
        float ys = NoiseTables.InterpHermite((float) (yf - y0));

        x0 *= NoiseTables.PrimeX;
        y0 *= NoiseTables.PrimeY;
        int x1 = x0 + NoiseTables.PrimeX;
        int y1 = y0 + NoiseTables.PrimeY;

        int hash0 = NoiseTables.Hash(seed, x0, y0) & (255 << 1);
        int hash1 = NoiseTables.Hash(seed, x1, y0) & (255 << 1);

        float lx0x = NoiseTables.Lerp(NoiseTables.RandVecs2D[hash0], NoiseTables.RandVecs2D[hash1], xs);
        float ly0x = NoiseTables.Lerp(NoiseTables.RandVecs2D[hash0 | 1], NoiseTables.RandVecs2D[hash1 | 1], xs);

        hash0 = NoiseTables.Hash(seed, x0, y1) & (255 << 1);
        hash1 = NoiseTables.Hash(seed, x1, y1) & (255 << 1);

        float lx1x = NoiseTables.Lerp(NoiseTables.RandVecs2D[hash0], NoiseTables.RandVecs2D[hash1], xs);
        float ly1x = NoiseTables.Lerp(NoiseTables.RandVecs2D[hash0 | 1], NoiseTables.RandVecs2D[hash1 | 1], xs);

        coord.x += NoiseTables.Lerp(lx0x, lx1x, ys) * warpAmp;
        coord.y += NoiseTables.Lerp(ly0x, ly1x, ys) * warpAmp;
    }

    public static void SingleDomainWarpBasicGrid(
            int seed,
            float warpAmp,
            float frequency, /*FNLfloat*/
            float x, /*FNLfloat*/
            float y, /*FNLfloat*/
            float z,
            Vector3 coord) {
        /*FNLfloat*/ float xf = x * frequency;
        /*FNLfloat*/ float yf = y * frequency;
        /*FNLfloat*/ float zf = z * frequency;

        int x0 = NoiseTables.FastFloor(xf);
        int y0 = NoiseTables.FastFloor(yf);
        int z0 = NoiseTables.FastFloor(zf);

        float xs = NoiseTables.InterpHermite((float) (xf - x0));
        float ys = NoiseTables.InterpHermite((float) (yf - y0));
        float zs = NoiseTables.InterpHermite((float) (zf - z0));

        x0 *= NoiseTables.PrimeX;
        y0 *= NoiseTables.PrimeY;
        z0 *= NoiseTables.PrimeZ;
        int x1 = x0 + NoiseTables.PrimeX;
        int y1 = y0 + NoiseTables.PrimeY;
        int z1 = z0 + NoiseTables.PrimeZ;

        int hash0 = NoiseTables.Hash(seed, x0, y0, z0) & (255 << 2);
        int hash1 = NoiseTables.Hash(seed, x1, y0, z0) & (255 << 2);

        float lx0x = NoiseTables.Lerp(NoiseTables.RandVecs3D[hash0], NoiseTables.RandVecs3D[hash1], xs);
        float ly0x = NoiseTables.Lerp(NoiseTables.RandVecs3D[hash0 | 1], NoiseTables.RandVecs3D[hash1 | 1], xs);
        float lz0x = NoiseTables.Lerp(NoiseTables.RandVecs3D[hash0 | 2], NoiseTables.RandVecs3D[hash1 | 2], xs);

        hash0 = NoiseTables.Hash(seed, x0, y1, z0) & (255 << 2);
        hash1 = NoiseTables.Hash(seed, x1, y1, z0) & (255 << 2);

        float lx1x = NoiseTables.Lerp(NoiseTables.RandVecs3D[hash0], NoiseTables.RandVecs3D[hash1], xs);
        float ly1x = NoiseTables.Lerp(NoiseTables.RandVecs3D[hash0 | 1], NoiseTables.RandVecs3D[hash1 | 1], xs);
        float lz1x = NoiseTables.Lerp(NoiseTables.RandVecs3D[hash0 | 2], NoiseTables.RandVecs3D[hash1 | 2], xs);

        float lx0y = NoiseTables.Lerp(lx0x, lx1x, ys);
        float ly0y = NoiseTables.Lerp(ly0x, ly1x, ys);
        float lz0y = NoiseTables.Lerp(lz0x, lz1x, ys);

        hash0 = NoiseTables.Hash(seed, x0, y0, z1) & (255 << 2);
        hash1 = NoiseTables.Hash(seed, x1, y0, z1) & (255 << 2);

        lx0x = NoiseTables.Lerp(NoiseTables.RandVecs3D[hash0], NoiseTables.RandVecs3D[hash1], xs);
        ly0x = NoiseTables.Lerp(NoiseTables.RandVecs3D[hash0 | 1], NoiseTables.RandVecs3D[hash1 | 1], xs);
        lz0x = NoiseTables.Lerp(NoiseTables.RandVecs3D[hash0 | 2], NoiseTables.RandVecs3D[hash1 | 2], xs);

        hash0 = NoiseTables.Hash(seed, x0, y1, z1) & (255 << 2);
        hash1 = NoiseTables.Hash(seed, x1, y1, z1) & (255 << 2);

        lx1x = NoiseTables.Lerp(NoiseTables.RandVecs3D[hash0], NoiseTables.RandVecs3D[hash1], xs);
        ly1x = NoiseTables.Lerp(NoiseTables.RandVecs3D[hash0 | 1], NoiseTables.RandVecs3D[hash1 | 1], xs);
        lz1x = NoiseTables.Lerp(NoiseTables.RandVecs3D[hash0 | 2], NoiseTables.RandVecs3D[hash1 | 2], xs);

        coord.x += NoiseTables.Lerp(lx0y, NoiseTables.Lerp(lx0x, lx1x, ys), zs) * warpAmp;
        coord.y += NoiseTables.Lerp(ly0y, NoiseTables.Lerp(ly0x, ly1x, ys), zs) * warpAmp;
        coord.z += NoiseTables.Lerp(lz0y, NoiseTables.Lerp(lz0x, lz1x, ys), zs) * warpAmp;
    }
}
