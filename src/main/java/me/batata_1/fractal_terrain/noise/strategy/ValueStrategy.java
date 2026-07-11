package me.batata_1.fractal_terrain.noise.strategy;

/**
 * The {@code Value} noise-type strategy: plain hashed-lattice value noise with Hermite interpolation
 * (no gradients).
 *
 * <p><b>Responsibility:</b> both overloads of {@link #SingleValue} - the {@code NoiseType.Value} branch
 * of {@code FastNoiseLite}'s noise dispatcher.
 *
 * <p><b>Collaborators:</b> {@link NoiseTables} for the shared hash/value-coordinate lookup and Hermite
 * interpolation helpers; dispatched from {@code FastNoiseLite#GenNoiseSingle}.
 *
 * <p><b>Invariants:</b> mechanical extraction from the embedded FastNoiseLite 1.1.1 implementation -
 * every constant and evaluation order is unchanged; output is byte-identical to the pre-split code.
 */
public final class ValueStrategy {

    private ValueStrategy() {}

    public static float SingleValue(int seed, /*FNLfloat*/ float x, /*FNLfloat*/ float y) {
        int x0 = NoiseTables.FastFloor(x);
        int y0 = NoiseTables.FastFloor(y);

        float xs = NoiseTables.InterpHermite((float) (x - x0));
        float ys = NoiseTables.InterpHermite((float) (y - y0));

        x0 *= NoiseTables.PrimeX;
        y0 *= NoiseTables.PrimeY;
        int x1 = x0 + NoiseTables.PrimeX;
        int y1 = y0 + NoiseTables.PrimeY;

        float xf0 = NoiseTables.Lerp(NoiseTables.ValCoord(seed, x0, y0), NoiseTables.ValCoord(seed, x1, y0), xs);
        float xf1 = NoiseTables.Lerp(NoiseTables.ValCoord(seed, x0, y1), NoiseTables.ValCoord(seed, x1, y1), xs);

        return NoiseTables.Lerp(xf0, xf1, ys);
    }

    public static float SingleValue(int seed, /*FNLfloat*/ float x, /*FNLfloat*/ float y, /*FNLfloat*/ float z) {
        int x0 = NoiseTables.FastFloor(x);
        int y0 = NoiseTables.FastFloor(y);
        int z0 = NoiseTables.FastFloor(z);

        float xs = NoiseTables.InterpHermite((float) (x - x0));
        float ys = NoiseTables.InterpHermite((float) (y - y0));
        float zs = NoiseTables.InterpHermite((float) (z - z0));

        x0 *= NoiseTables.PrimeX;
        y0 *= NoiseTables.PrimeY;
        z0 *= NoiseTables.PrimeZ;
        int x1 = x0 + NoiseTables.PrimeX;
        int y1 = y0 + NoiseTables.PrimeY;
        int z1 = z0 + NoiseTables.PrimeZ;

        float xf00 =
                NoiseTables.Lerp(NoiseTables.ValCoord(seed, x0, y0, z0), NoiseTables.ValCoord(seed, x1, y0, z0), xs);
        float xf10 =
                NoiseTables.Lerp(NoiseTables.ValCoord(seed, x0, y1, z0), NoiseTables.ValCoord(seed, x1, y1, z0), xs);
        float xf01 =
                NoiseTables.Lerp(NoiseTables.ValCoord(seed, x0, y0, z1), NoiseTables.ValCoord(seed, x1, y0, z1), xs);
        float xf11 =
                NoiseTables.Lerp(NoiseTables.ValCoord(seed, x0, y1, z1), NoiseTables.ValCoord(seed, x1, y1, z1), xs);

        float yf0 = NoiseTables.Lerp(xf00, xf10, ys);
        float yf1 = NoiseTables.Lerp(xf01, xf11, ys);

        return NoiseTables.Lerp(yf0, yf1, zs);
    }
}
