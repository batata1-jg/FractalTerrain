package me.batata_1.fractal_terrain.noise.strategy;

/**
 * The {@code Perlin} noise strategy: classic gradient noise with quintic interpolation.
 *
 * <p>One of the interchangeable noise-type strategies {@code FastNoiseLite} dispatches to; extracted so the
 * dispatcher stays readable and each variant can be read on its own.
 *
 * <p><b>Do not "clean up" any constant or reorder any expression.</b> This is a mechanical extraction
 * from FastNoiseLite 1.1.1 and must stay byte-identical — every world already generated depends on it.
 */
public final class PerlinStrategy {

    private PerlinStrategy() {}

    public static float SinglePerlin(int seed, /*FNLfloat*/ float x, /*FNLfloat*/ float y) {
        int x0 = NoiseTables.FastFloor(x);
        int y0 = NoiseTables.FastFloor(y);

        float xd0 = (float) (x - x0);
        float yd0 = (float) (y - y0);
        float xd1 = xd0 - 1;
        float yd1 = yd0 - 1;

        float xs = NoiseTables.InterpQuintic(xd0);
        float ys = NoiseTables.InterpQuintic(yd0);

        x0 *= NoiseTables.PrimeX;
        y0 *= NoiseTables.PrimeY;
        int x1 = x0 + NoiseTables.PrimeX;
        int y1 = y0 + NoiseTables.PrimeY;

        float xf0 = NoiseTables.Lerp(
                NoiseTables.GradCoord(seed, x0, y0, xd0, yd0), NoiseTables.GradCoord(seed, x1, y0, xd1, yd0), xs);
        float xf1 = NoiseTables.Lerp(
                NoiseTables.GradCoord(seed, x0, y1, xd0, yd1), NoiseTables.GradCoord(seed, x1, y1, xd1, yd1), xs);

        return NoiseTables.Lerp(xf0, xf1, ys) * 1.4247691104677813f;
    }

    public static float SinglePerlin(int seed, /*FNLfloat*/ float x, /*FNLfloat*/ float y, /*FNLfloat*/ float z) {
        int x0 = NoiseTables.FastFloor(x);
        int y0 = NoiseTables.FastFloor(y);
        int z0 = NoiseTables.FastFloor(z);

        float xd0 = (float) (x - x0);
        float yd0 = (float) (y - y0);
        float zd0 = (float) (z - z0);
        float xd1 = xd0 - 1;
        float yd1 = yd0 - 1;
        float zd1 = zd0 - 1;

        float xs = NoiseTables.InterpQuintic(xd0);
        float ys = NoiseTables.InterpQuintic(yd0);
        float zs = NoiseTables.InterpQuintic(zd0);

        x0 *= NoiseTables.PrimeX;
        y0 *= NoiseTables.PrimeY;
        z0 *= NoiseTables.PrimeZ;
        int x1 = x0 + NoiseTables.PrimeX;
        int y1 = y0 + NoiseTables.PrimeY;
        int z1 = z0 + NoiseTables.PrimeZ;

        float xf00 = NoiseTables.Lerp(
                NoiseTables.GradCoord(seed, x0, y0, z0, xd0, yd0, zd0),
                NoiseTables.GradCoord(seed, x1, y0, z0, xd1, yd0, zd0),
                xs);
        float xf10 = NoiseTables.Lerp(
                NoiseTables.GradCoord(seed, x0, y1, z0, xd0, yd1, zd0),
                NoiseTables.GradCoord(seed, x1, y1, z0, xd1, yd1, zd0),
                xs);
        float xf01 = NoiseTables.Lerp(
                NoiseTables.GradCoord(seed, x0, y0, z1, xd0, yd0, zd1),
                NoiseTables.GradCoord(seed, x1, y0, z1, xd1, yd0, zd1),
                xs);
        float xf11 = NoiseTables.Lerp(
                NoiseTables.GradCoord(seed, x0, y1, z1, xd0, yd1, zd1),
                NoiseTables.GradCoord(seed, x1, y1, z1, xd1, yd1, zd1),
                xs);

        float yf0 = NoiseTables.Lerp(xf00, xf10, ys);
        float yf1 = NoiseTables.Lerp(xf01, xf11, ys);

        return NoiseTables.Lerp(yf0, yf1, zs) * 0.964921414852142333984375f;
    }
}
