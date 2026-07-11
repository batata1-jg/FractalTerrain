package me.batata_1.fractal_terrain.noise.strategy;

/**
 * The {@code OpenSimplex2} noise-type strategy: 2D uses ordinary Simplex noise (per the original
 * FastNoiseLite comment, "2D OpenSimplex2 case uses the same algorithm as ordinary Simplex"), 3D uses
 * two offset rotated cube grids.
 *
 * <p><b>Responsibility:</b> {@link #SingleSimplex} (2D) and {@link #SingleOpenSimplex2} (3D) - the
 * {@code NoiseType.OpenSimplex2} branch of {@code FastNoiseLite}'s noise dispatcher.
 *
 * <p><b>Collaborators:</b> {@link NoiseTables} for the shared hash/gradient tables and interpolation
 * helpers; dispatched from {@code FastNoiseLite#GenNoiseSingle}.
 *
 * <p><b>Invariants:</b> mechanical extraction from the embedded FastNoiseLite 1.1.1 implementation -
 * every constant and evaluation order is unchanged; output is byte-identical to the pre-split code.
 */
public final class OpenSimplex2Strategy {

    private OpenSimplex2Strategy() {}

    public static float SingleSimplex(int seed, /*FNLfloat*/ float x, /*FNLfloat*/ float y) {
        // 2D OpenSimplex2 case uses the same algorithm as ordinary Simplex.

        final float SQRT3 = 1.7320508075688772935274463415059f;
        final float G2 = (3 - SQRT3) / 6;

        /*
         * --- Skew moved to switch statements before fractal evaluation ---
         * final FNLfloat F2 = 0.5f * (SQRT3 - 1);
         * FNLfloat s = (x + y) * F2;
         * x += s; y += s;
         */

        int i = NoiseTables.FastFloor(x);
        int j = NoiseTables.FastFloor(y);
        float xi = (float) (x - i);
        float yi = (float) (y - j);

        float t = (xi + yi) * G2;
        float x0 = (float) (xi - t);
        float y0 = (float) (yi - t);

        i *= NoiseTables.PrimeX;
        j *= NoiseTables.PrimeY;

        float n0, n1, n2;

        float a = 0.5f - x0 * x0 - y0 * y0;
        if (a <= 0) n0 = 0;
        else {
            n0 = (a * a) * (a * a) * NoiseTables.GradCoord(seed, i, j, x0, y0);
        }

        float c = (float) (2 * (1 - 2 * G2) * (1 / G2 - 2)) * t + ((float) (-2 * (1 - 2 * G2) * (1 - 2 * G2)) + a);
        if (c <= 0) n2 = 0;
        else {
            float x2 = x0 + (2 * (float) G2 - 1);
            float y2 = y0 + (2 * (float) G2 - 1);
            n2 = (c * c)
                    * (c * c)
                    * NoiseTables.GradCoord(seed, i + NoiseTables.PrimeX, j + NoiseTables.PrimeY, x2, y2);
        }

        if (y0 > x0) {
            float x1 = x0 + (float) G2;
            float y1 = y0 + ((float) G2 - 1);
            float b = 0.5f - x1 * x1 - y1 * y1;
            if (b <= 0) n1 = 0;
            else {
                n1 = (b * b) * (b * b) * NoiseTables.GradCoord(seed, i, j + NoiseTables.PrimeY, x1, y1);
            }
        } else {
            float x1 = x0 + ((float) G2 - 1);
            float y1 = y0 + (float) G2;
            float b = 0.5f - x1 * x1 - y1 * y1;
            if (b <= 0) n1 = 0;
            else {
                n1 = (b * b) * (b * b) * NoiseTables.GradCoord(seed, i + NoiseTables.PrimeX, j, x1, y1);
            }
        }

        return (n0 + n1 + n2) * 99.83685446303647f;
    }

    public static float SingleOpenSimplex2(int seed, /*FNLfloat*/ float x, /*FNLfloat*/ float y, /*FNLfloat*/ float z) {
        // 3D OpenSimplex2 case uses two offset rotated cube grids.

        /*
         * --- Rotation moved to switch statements before fractal evaluation ---
         * final FNLfloat R3 = (FNLfloat)(2.0 / 3.0);
         * FNLfloat r = (x + y + z) * R3; // Rotation, not skew
         * x = r - x; y = r - y; z = r - z;
         */

        int i = NoiseTables.FastRound(x);
        int j = NoiseTables.FastRound(y);
        int k = NoiseTables.FastRound(z);
        float x0 = (float) (x - i);
        float y0 = (float) (y - j);
        float z0 = (float) (z - k);

        int xNSign = (int) (-1.0f - x0) | 1;
        int yNSign = (int) (-1.0f - y0) | 1;
        int zNSign = (int) (-1.0f - z0) | 1;

        float ax0 = xNSign * -x0;
        float ay0 = yNSign * -y0;
        float az0 = zNSign * -z0;

        i *= NoiseTables.PrimeX;
        j *= NoiseTables.PrimeY;
        k *= NoiseTables.PrimeZ;

        float value = 0;
        float a = (0.6f - x0 * x0) - (y0 * y0 + z0 * z0);

        for (int l = 0; ; l++) {
            if (a > 0) {
                value += (a * a) * (a * a) * NoiseTables.GradCoord(seed, i, j, k, x0, y0, z0);
            }

            if (ax0 >= ay0 && ax0 >= az0) {
                float b = a + ax0 + ax0;
                if (b > 1) {
                    b -= 1;
                    value += (b * b)
                            * (b * b)
                            * NoiseTables.GradCoord(seed, i - xNSign * NoiseTables.PrimeX, j, k, x0 + xNSign, y0, z0);
                }
            } else if (ay0 > ax0 && ay0 >= az0) {
                float b = a + ay0 + ay0;
                if (b > 1) {
                    b -= 1;
                    value += (b * b)
                            * (b * b)
                            * NoiseTables.GradCoord(seed, i, j - yNSign * NoiseTables.PrimeY, k, x0, y0 + yNSign, z0);
                }
            } else {
                float b = a + az0 + az0;
                if (b > 1) {
                    b -= 1;
                    value += (b * b)
                            * (b * b)
                            * NoiseTables.GradCoord(seed, i, j, k - zNSign * NoiseTables.PrimeZ, x0, y0, z0 + zNSign);
                }
            }

            if (l == 1) break;

            ax0 = 0.5f - ax0;
            ay0 = 0.5f - ay0;
            az0 = 0.5f - az0;

            x0 = xNSign * ax0;
            y0 = yNSign * ay0;
            z0 = zNSign * az0;

            a += (0.75f - ax0) - (ay0 + az0);

            i += (xNSign >> 1) & NoiseTables.PrimeX;
            j += (yNSign >> 1) & NoiseTables.PrimeY;
            k += (zNSign >> 1) & NoiseTables.PrimeZ;

            xNSign = -xNSign;
            yNSign = -yNSign;
            zNSign = -zNSign;

            seed = ~seed;
        }

        return value * 32.69428253173828125f;
    }
}
