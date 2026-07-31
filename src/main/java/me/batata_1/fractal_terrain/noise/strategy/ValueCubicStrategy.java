package me.batata_1.fractal_terrain.noise.strategy;

/**
 * The {@code ValueCubic} noise strategy: value noise with a cubic kernel, smoother than {@code Value}.
 *
 * <p>One of the interchangeable noise-type strategies {@code FastNoiseLite} dispatches to; extracted so the
 * dispatcher stays readable and each variant can be read on its own.
 *
 * <p><b>Do not "clean up" any constant or reorder any expression.</b> This is a mechanical extraction
 * from FastNoiseLite 1.1.1 and must stay byte-identical — every world already generated depends on it.
 */
public final class ValueCubicStrategy {

    private ValueCubicStrategy() {}

    public static float SingleValueCubic(int seed, /*FNLfloat*/ float x, /*FNLfloat*/ float y) {
        int x1 = NoiseTables.FastFloor(x);
        int y1 = NoiseTables.FastFloor(y);

        float xs = (float) (x - x1);
        float ys = (float) (y - y1);

        x1 *= NoiseTables.PrimeX;
        y1 *= NoiseTables.PrimeY;
        int x0 = x1 - NoiseTables.PrimeX;
        int y0 = y1 - NoiseTables.PrimeY;
        int x2 = x1 + NoiseTables.PrimeX;
        int y2 = y1 + NoiseTables.PrimeY;
        int x3 = x1 + (NoiseTables.PrimeX << 1);
        int y3 = y1 + (NoiseTables.PrimeY << 1);

        return NoiseTables.CubicLerp(
                        NoiseTables.CubicLerp(
                                NoiseTables.ValCoord(seed, x0, y0),
                                NoiseTables.ValCoord(seed, x1, y0),
                                NoiseTables.ValCoord(seed, x2, y0),
                                NoiseTables.ValCoord(seed, x3, y0),
                                xs),
                        NoiseTables.CubicLerp(
                                NoiseTables.ValCoord(seed, x0, y1),
                                NoiseTables.ValCoord(seed, x1, y1),
                                NoiseTables.ValCoord(seed, x2, y1),
                                NoiseTables.ValCoord(seed, x3, y1),
                                xs),
                        NoiseTables.CubicLerp(
                                NoiseTables.ValCoord(seed, x0, y2),
                                NoiseTables.ValCoord(seed, x1, y2),
                                NoiseTables.ValCoord(seed, x2, y2),
                                NoiseTables.ValCoord(seed, x3, y2),
                                xs),
                        NoiseTables.CubicLerp(
                                NoiseTables.ValCoord(seed, x0, y3),
                                NoiseTables.ValCoord(seed, x1, y3),
                                NoiseTables.ValCoord(seed, x2, y3),
                                NoiseTables.ValCoord(seed, x3, y3),
                                xs),
                        ys)
                * (1 / (1.5f * 1.5f));
    }

    public static float SingleValueCubic(int seed, /*FNLfloat*/ float x, /*FNLfloat*/ float y, /*FNLfloat*/ float z) {
        int x1 = NoiseTables.FastFloor(x);
        int y1 = NoiseTables.FastFloor(y);
        int z1 = NoiseTables.FastFloor(z);

        float xs = (float) (x - x1);
        float ys = (float) (y - y1);
        float zs = (float) (z - z1);

        x1 *= NoiseTables.PrimeX;
        y1 *= NoiseTables.PrimeY;
        z1 *= NoiseTables.PrimeZ;

        int x0 = x1 - NoiseTables.PrimeX;
        int y0 = y1 - NoiseTables.PrimeY;
        int z0 = z1 - NoiseTables.PrimeZ;
        int x2 = x1 + NoiseTables.PrimeX;
        int y2 = y1 + NoiseTables.PrimeY;
        int z2 = z1 + NoiseTables.PrimeZ;
        int x3 = x1 + (NoiseTables.PrimeX << 1);
        int y3 = y1 + (NoiseTables.PrimeY << 1);
        int z3 = z1 + (NoiseTables.PrimeZ << 1);

        return NoiseTables.CubicLerp(
                        NoiseTables.CubicLerp(
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y0, z0),
                                        NoiseTables.ValCoord(seed, x1, y0, z0),
                                        NoiseTables.ValCoord(seed, x2, y0, z0),
                                        NoiseTables.ValCoord(seed, x3, y0, z0),
                                        xs),
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y1, z0),
                                        NoiseTables.ValCoord(seed, x1, y1, z0),
                                        NoiseTables.ValCoord(seed, x2, y1, z0),
                                        NoiseTables.ValCoord(seed, x3, y1, z0),
                                        xs),
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y2, z0),
                                        NoiseTables.ValCoord(seed, x1, y2, z0),
                                        NoiseTables.ValCoord(seed, x2, y2, z0),
                                        NoiseTables.ValCoord(seed, x3, y2, z0),
                                        xs),
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y3, z0),
                                        NoiseTables.ValCoord(seed, x1, y3, z0),
                                        NoiseTables.ValCoord(seed, x2, y3, z0),
                                        NoiseTables.ValCoord(seed, x3, y3, z0),
                                        xs),
                                ys),
                        NoiseTables.CubicLerp(
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y0, z1),
                                        NoiseTables.ValCoord(seed, x1, y0, z1),
                                        NoiseTables.ValCoord(seed, x2, y0, z1),
                                        NoiseTables.ValCoord(seed, x3, y0, z1),
                                        xs),
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y1, z1),
                                        NoiseTables.ValCoord(seed, x1, y1, z1),
                                        NoiseTables.ValCoord(seed, x2, y1, z1),
                                        NoiseTables.ValCoord(seed, x3, y1, z1),
                                        xs),
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y2, z1),
                                        NoiseTables.ValCoord(seed, x1, y2, z1),
                                        NoiseTables.ValCoord(seed, x2, y2, z1),
                                        NoiseTables.ValCoord(seed, x3, y2, z1),
                                        xs),
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y3, z1),
                                        NoiseTables.ValCoord(seed, x1, y3, z1),
                                        NoiseTables.ValCoord(seed, x2, y3, z1),
                                        NoiseTables.ValCoord(seed, x3, y3, z1),
                                        xs),
                                ys),
                        NoiseTables.CubicLerp(
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y0, z2),
                                        NoiseTables.ValCoord(seed, x1, y0, z2),
                                        NoiseTables.ValCoord(seed, x2, y0, z2),
                                        NoiseTables.ValCoord(seed, x3, y0, z2),
                                        xs),
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y1, z2),
                                        NoiseTables.ValCoord(seed, x1, y1, z2),
                                        NoiseTables.ValCoord(seed, x2, y1, z2),
                                        NoiseTables.ValCoord(seed, x3, y1, z2),
                                        xs),
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y2, z2),
                                        NoiseTables.ValCoord(seed, x1, y2, z2),
                                        NoiseTables.ValCoord(seed, x2, y2, z2),
                                        NoiseTables.ValCoord(seed, x3, y2, z2),
                                        xs),
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y3, z2),
                                        NoiseTables.ValCoord(seed, x1, y3, z2),
                                        NoiseTables.ValCoord(seed, x2, y3, z2),
                                        NoiseTables.ValCoord(seed, x3, y3, z2),
                                        xs),
                                ys),
                        NoiseTables.CubicLerp(
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y0, z3),
                                        NoiseTables.ValCoord(seed, x1, y0, z3),
                                        NoiseTables.ValCoord(seed, x2, y0, z3),
                                        NoiseTables.ValCoord(seed, x3, y0, z3),
                                        xs),
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y1, z3),
                                        NoiseTables.ValCoord(seed, x1, y1, z3),
                                        NoiseTables.ValCoord(seed, x2, y1, z3),
                                        NoiseTables.ValCoord(seed, x3, y1, z3),
                                        xs),
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y2, z3),
                                        NoiseTables.ValCoord(seed, x1, y2, z3),
                                        NoiseTables.ValCoord(seed, x2, y2, z3),
                                        NoiseTables.ValCoord(seed, x3, y2, z3),
                                        xs),
                                NoiseTables.CubicLerp(
                                        NoiseTables.ValCoord(seed, x0, y3, z3),
                                        NoiseTables.ValCoord(seed, x1, y3, z3),
                                        NoiseTables.ValCoord(seed, x2, y3, z3),
                                        NoiseTables.ValCoord(seed, x3, y3, z3),
                                        xs),
                                ys),
                        zs)
                * (1 / (1.5f * 1.5f * 1.5f));
    }
}
