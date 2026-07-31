package me.batata_1.fractal_terrain.noise.strategy;

import me.batata_1.fractal_terrain.math.Vector2;
import me.batata_1.fractal_terrain.math.Vector3;

/**
 * The {@code OpenSimplex2} domain-warp strategy: warps along simplex-lattice gradient contributions.
 *
 * <p>One of the interchangeable domain-warp strategies {@code FastNoiseLite} dispatches to; extracted so the
 * dispatcher stays readable and each variant can be read on its own.
 *
 * <p><b>Do not "clean up" any constant or reorder any expression.</b> This is a mechanical extraction
 * from FastNoiseLite 1.1.1 and must stay byte-identical — every world already generated depends on it.
 */
public final class SimplexGradientWarpStrategy {

    private SimplexGradientWarpStrategy() {}

    public static void SingleDomainWarpSimplexGradient(
            int seed,
            float warpAmp,
            float frequency, /*FNLfloat*/
            float x, /*FNLfloat*/
            float y,
            Vector2 coord,
            boolean outGradOnly) {
        final float SQRT3 = 1.7320508075688772935274463415059f;
        final float G2 = (3 - SQRT3) / 6;

        x *= frequency;
        y *= frequency;

        /*
         * --- Skew moved to switch statements before fractal evaluation  ---
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

        float vx, vy;
        vx = vy = 0;

        float a = 0.5f - x0 * x0 - y0 * y0;
        if (a > 0) {
            float aaaa = (a * a) * (a * a);
            float xo, yo;
            if (outGradOnly) {
                int hash = NoiseTables.Hash(seed, i, j) & (255 << 1);
                xo = NoiseTables.RandVecs2D[hash];
                yo = NoiseTables.RandVecs2D[hash | 1];
            } else {
                int hash = NoiseTables.Hash(seed, i, j);
                int index1 = hash & (127 << 1);
                int index2 = (hash >> 7) & (255 << 1);
                float xg = NoiseTables.Gradients2D[index1];
                float yg = NoiseTables.Gradients2D[index1 | 1];
                float value = x0 * xg + y0 * yg;
                float xgo = NoiseTables.RandVecs2D[index2];
                float ygo = NoiseTables.RandVecs2D[index2 | 1];
                xo = value * xgo;
                yo = value * ygo;
            }
            vx += aaaa * xo;
            vy += aaaa * yo;
        }

        float c = (float) (2 * (1 - 2 * G2) * (1 / G2 - 2)) * t + ((float) (-2 * (1 - 2 * G2) * (1 - 2 * G2)) + a);
        if (c > 0) {
            float x2 = x0 + (2 * (float) G2 - 1);
            float y2 = y0 + (2 * (float) G2 - 1);
            float cccc = (c * c) * (c * c);
            float xo, yo;
            if (outGradOnly) {
                int hash = NoiseTables.Hash(seed, i + NoiseTables.PrimeX, j + NoiseTables.PrimeY) & (255 << 1);
                xo = NoiseTables.RandVecs2D[hash];
                yo = NoiseTables.RandVecs2D[hash | 1];
            } else {
                int hash = NoiseTables.Hash(seed, i + NoiseTables.PrimeX, j + NoiseTables.PrimeY);
                int index1 = hash & (127 << 1);
                int index2 = (hash >> 7) & (255 << 1);
                float xg = NoiseTables.Gradients2D[index1];
                float yg = NoiseTables.Gradients2D[index1 | 1];
                float value = x2 * xg + y2 * yg;
                float xgo = NoiseTables.RandVecs2D[index2];
                float ygo = NoiseTables.RandVecs2D[index2 | 1];
                xo = value * xgo;
                yo = value * ygo;
            }
            vx += cccc * xo;
            vy += cccc * yo;
        }

        if (y0 > x0) {
            float x1 = x0 + (float) G2;
            float y1 = y0 + ((float) G2 - 1);
            float b = 0.5f - x1 * x1 - y1 * y1;
            if (b > 0) {
                float bbbb = (b * b) * (b * b);
                float xo, yo;
                if (outGradOnly) {
                    int hash = NoiseTables.Hash(seed, i, j + NoiseTables.PrimeY) & (255 << 1);
                    xo = NoiseTables.RandVecs2D[hash];
                    yo = NoiseTables.RandVecs2D[hash | 1];
                } else {
                    int hash = NoiseTables.Hash(seed, i, j + NoiseTables.PrimeY);
                    int index1 = hash & (127 << 1);
                    int index2 = (hash >> 7) & (255 << 1);
                    float xg = NoiseTables.Gradients2D[index1];
                    float yg = NoiseTables.Gradients2D[index1 | 1];
                    float value = x1 * xg + y1 * yg;
                    float xgo = NoiseTables.RandVecs2D[index2];
                    float ygo = NoiseTables.RandVecs2D[index2 | 1];
                    xo = value * xgo;
                    yo = value * ygo;
                }
                vx += bbbb * xo;
                vy += bbbb * yo;
            }
        } else {
            float x1 = x0 + ((float) G2 - 1);
            float y1 = y0 + (float) G2;
            float b = 0.5f - x1 * x1 - y1 * y1;
            if (b > 0) {
                float bbbb = (b * b) * (b * b);
                float xo, yo;
                if (outGradOnly) {
                    int hash = NoiseTables.Hash(seed, i + NoiseTables.PrimeX, j) & (255 << 1);
                    xo = NoiseTables.RandVecs2D[hash];
                    yo = NoiseTables.RandVecs2D[hash | 1];
                } else {
                    int hash = NoiseTables.Hash(seed, i + NoiseTables.PrimeX, j);
                    int index1 = hash & (127 << 1);
                    int index2 = (hash >> 7) & (255 << 1);
                    float xg = NoiseTables.Gradients2D[index1];
                    float yg = NoiseTables.Gradients2D[index1 | 1];
                    float value = x1 * xg + y1 * yg;
                    float xgo = NoiseTables.RandVecs2D[index2];
                    float ygo = NoiseTables.RandVecs2D[index2 | 1];
                    xo = value * xgo;
                    yo = value * ygo;
                }
                vx += bbbb * xo;
                vy += bbbb * yo;
            }
        }

        coord.x += vx * warpAmp;
        coord.y += vy * warpAmp;
    }

    public static void SingleDomainWarpOpenSimplex2Gradient(
            int seed,
            float warpAmp,
            float frequency, /*FNLfloat*/
            float x, /*FNLfloat*/
            float y, /*FNLfloat*/
            float z,
            Vector3 coord,
            boolean outGradOnly) {
        x *= frequency;
        y *= frequency;
        z *= frequency;

        /*
         * --- Rotation moved to switch statements before fractal evaluation ---
         * final FNLfloat R3 = (FNLfloat)(2.0 / 3.0);
         * FNLfloat r = (x + y + z) * R3; // Rotation, not skew
         * x = r - x; y = r - y; z = r - z;
         */

        int i = NoiseTables.FastRound(x);
        int j = NoiseTables.FastRound(y);
        int k = NoiseTables.FastRound(z);
        float x0 = (float) x - i;
        float y0 = (float) y - j;
        float z0 = (float) z - k;

        int xNSign = (int) (-x0 - 1.0f) | 1;
        int yNSign = (int) (-y0 - 1.0f) | 1;
        int zNSign = (int) (-z0 - 1.0f) | 1;

        float ax0 = xNSign * -x0;
        float ay0 = yNSign * -y0;
        float az0 = zNSign * -z0;

        i *= NoiseTables.PrimeX;
        j *= NoiseTables.PrimeY;
        k *= NoiseTables.PrimeZ;

        float vx, vy, vz;
        vx = vy = vz = 0;

        float a = (0.6f - x0 * x0) - (y0 * y0 + z0 * z0);
        for (int l = 0; ; l++) {
            if (a > 0) {
                float aaaa = (a * a) * (a * a);
                float xo, yo, zo;
                if (outGradOnly) {
                    int hash = NoiseTables.Hash(seed, i, j, k) & (255 << 2);
                    xo = NoiseTables.RandVecs3D[hash];
                    yo = NoiseTables.RandVecs3D[hash | 1];
                    zo = NoiseTables.RandVecs3D[hash | 2];
                } else {
                    int hash = NoiseTables.Hash(seed, i, j, k);
                    int index1 = hash & (63 << 2);
                    int index2 = (hash >> 6) & (255 << 2);
                    float xg = NoiseTables.Gradients3D[index1];
                    float yg = NoiseTables.Gradients3D[index1 | 1];
                    float zg = NoiseTables.Gradients3D[index1 | 2];
                    float value = x0 * xg + y0 * yg + z0 * zg;
                    float xgo = NoiseTables.RandVecs3D[index2];
                    float ygo = NoiseTables.RandVecs3D[index2 | 1];
                    float zgo = NoiseTables.RandVecs3D[index2 | 2];
                    xo = value * xgo;
                    yo = value * ygo;
                    zo = value * zgo;
                }
                vx += aaaa * xo;
                vy += aaaa * yo;
                vz += aaaa * zo;
            }

            float b = a;
            int i1 = i;
            int j1 = j;
            int k1 = k;
            float x1 = x0;
            float y1 = y0;
            float z1 = z0;

            if (ax0 >= ay0 && ax0 >= az0) {
                x1 += xNSign;
                b = b + ax0 + ax0;
                i1 -= xNSign * NoiseTables.PrimeX;
            } else if (ay0 > ax0 && ay0 >= az0) {
                y1 += yNSign;
                b = b + ay0 + ay0;
                j1 -= yNSign * NoiseTables.PrimeY;
            } else {
                z1 += zNSign;
                b = b + az0 + az0;
                k1 -= zNSign * NoiseTables.PrimeZ;
            }

            if (b > 1) {
                b -= 1;
                float bbbb = (b * b) * (b * b);
                float xo, yo, zo;
                if (outGradOnly) {
                    int hash = NoiseTables.Hash(seed, i1, j1, k1) & (255 << 2);
                    xo = NoiseTables.RandVecs3D[hash];
                    yo = NoiseTables.RandVecs3D[hash | 1];
                    zo = NoiseTables.RandVecs3D[hash | 2];
                } else {
                    int hash = NoiseTables.Hash(seed, i1, j1, k1);
                    int index1 = hash & (63 << 2);
                    int index2 = (hash >> 6) & (255 << 2);
                    float xg = NoiseTables.Gradients3D[index1];
                    float yg = NoiseTables.Gradients3D[index1 | 1];
                    float zg = NoiseTables.Gradients3D[index1 | 2];
                    float value = x1 * xg + y1 * yg + z1 * zg;
                    float xgo = NoiseTables.RandVecs3D[index2];
                    float ygo = NoiseTables.RandVecs3D[index2 | 1];
                    float zgo = NoiseTables.RandVecs3D[index2 | 2];
                    xo = value * xgo;
                    yo = value * ygo;
                    zo = value * zgo;
                }
                vx += bbbb * xo;
                vy += bbbb * yo;
                vz += bbbb * zo;
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

            seed += 1293373;
        }

        coord.x += vx * warpAmp;
        coord.y += vy * warpAmp;
        coord.z += vz * warpAmp;
    }
}
