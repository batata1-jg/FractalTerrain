package me.batata_1.fractal_terrain.noise.strategy;

/**
 * The {@code OpenSimplex2S} noise strategy: smoother output at the cost of some directional artifacts.
 *
 * <p>One of the interchangeable noise-type strategies {@code FastNoiseLite} dispatches to; extracted so the
 * dispatcher stays readable and each variant can be read on its own.
 *
 * <p><b>Do not "clean up" any constant or reorder any expression.</b> This is a mechanical extraction
 * from FastNoiseLite 1.1.1 and must stay byte-identical — every world already generated depends on it.
 */
public final class OpenSimplex2SStrategy {

    private OpenSimplex2SStrategy() {}

    public static float SingleOpenSimplex2S(int seed, /*FNLfloat*/ float x, /*FNLfloat*/ float y) {
        // 2D OpenSimplex2S case is a modified 2D simplex noise.

        final /*FNLfloat*/ float SQRT3 = (/*FNLfloat*/ float) 1.7320508075688772935274463415059;
        final /*FNLfloat*/ float G2 = (3 - SQRT3) / 6;

        /*
         * --- Skew moved to TransformNoiseCoordinate method ---
         * final FNLfloat F2 = 0.5f * (SQRT3 - 1);
         * FNLfloat s = (x + y) * F2;
         * x += s; y += s;
         */

        int i = NoiseTables.FastFloor(x);
        int j = NoiseTables.FastFloor(y);
        float xi = (float) (x - i);
        float yi = (float) (y - j);

        i *= NoiseTables.PrimeX;
        j *= NoiseTables.PrimeY;
        int i1 = i + NoiseTables.PrimeX;
        int j1 = j + NoiseTables.PrimeY;

        float t = (xi + yi) * (float) G2;
        float x0 = xi - t;
        float y0 = yi - t;

        float a0 = (2.0f / 3.0f) - x0 * x0 - y0 * y0;
        float value = (a0 * a0) * (a0 * a0) * NoiseTables.GradCoord(seed, i, j, x0, y0);

        float a1 = (float) (2 * (1 - 2 * G2) * (1 / G2 - 2)) * t + ((float) (-2 * (1 - 2 * G2) * (1 - 2 * G2)) + a0);
        float x1 = x0 - (float) (1 - 2 * G2);
        float y1 = y0 - (float) (1 - 2 * G2);
        value += (a1 * a1) * (a1 * a1) * NoiseTables.GradCoord(seed, i1, j1, x1, y1);

        // Nested conditionals were faster than compact bit logic/arithmetic.
        float xmyi = xi - yi;
        if (t > G2) {
            if (xi + xmyi > 1) {
                float x2 = x0 + (float) (3 * G2 - 2);
                float y2 = y0 + (float) (3 * G2 - 1);
                float a2 = (2.0f / 3.0f) - x2 * x2 - y2 * y2;
                if (a2 > 0) {
                    value += (a2 * a2)
                            * (a2 * a2)
                            * NoiseTables.GradCoord(
                                    seed, i + (NoiseTables.PrimeX << 1), j + NoiseTables.PrimeY, x2, y2);
                }
            } else {
                float x2 = x0 + (float) G2;
                float y2 = y0 + (float) (G2 - 1);
                float a2 = (2.0f / 3.0f) - x2 * x2 - y2 * y2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * NoiseTables.GradCoord(seed, i, j + NoiseTables.PrimeY, x2, y2);
                }
            }

            if (yi - xmyi > 1) {
                float x3 = x0 + (float) (3 * G2 - 1);
                float y3 = y0 + (float) (3 * G2 - 2);
                float a3 = (2.0f / 3.0f) - x3 * x3 - y3 * y3;
                if (a3 > 0) {
                    value += (a3 * a3)
                            * (a3 * a3)
                            * NoiseTables.GradCoord(
                                    seed, i + NoiseTables.PrimeX, j + (NoiseTables.PrimeY << 1), x3, y3);
                }
            } else {
                float x3 = x0 + (float) (G2 - 1);
                float y3 = y0 + (float) G2;
                float a3 = (2.0f / 3.0f) - x3 * x3 - y3 * y3;
                if (a3 > 0) {
                    value += (a3 * a3) * (a3 * a3) * NoiseTables.GradCoord(seed, i + NoiseTables.PrimeX, j, x3, y3);
                }
            }
        } else {
            if (xi + xmyi < 0) {
                float x2 = x0 + (float) (1 - G2);
                float y2 = y0 - (float) G2;
                float a2 = (2.0f / 3.0f) - x2 * x2 - y2 * y2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * NoiseTables.GradCoord(seed, i - NoiseTables.PrimeX, j, x2, y2);
                }
            } else {
                float x2 = x0 + (float) (G2 - 1);
                float y2 = y0 + (float) G2;
                float a2 = (2.0f / 3.0f) - x2 * x2 - y2 * y2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * NoiseTables.GradCoord(seed, i + NoiseTables.PrimeX, j, x2, y2);
                }
            }

            if (yi < xmyi) {
                float x2 = x0 - (float) G2;
                float y2 = y0 - (float) (G2 - 1);
                float a2 = (2.0f / 3.0f) - x2 * x2 - y2 * y2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * NoiseTables.GradCoord(seed, i, j - NoiseTables.PrimeY, x2, y2);
                }
            } else {
                float x2 = x0 + (float) G2;
                float y2 = y0 + (float) (G2 - 1);
                float a2 = (2.0f / 3.0f) - x2 * x2 - y2 * y2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * NoiseTables.GradCoord(seed, i, j + NoiseTables.PrimeY, x2, y2);
                }
            }
        }

        return value * 18.24196194486065f;
    }

    public static float SingleOpenSimplex2S(
            int seed, /*FNLfloat*/ float x, /*FNLfloat*/ float y, /*FNLfloat*/ float z) {
        // 3D OpenSimplex2S case uses two offset rotated cube grids.

        /*
         * --- Rotation moved to TransformNoiseCoordinate method ---
         * final FNLfloat R3 = (FNLfloat)(2.0 / 3.0);
         * FNLfloat r = (x + y + z) * R3; // Rotation, not skew
         * x = r - x; y = r - y; z = r - z;
         */

        int i = NoiseTables.FastFloor(x);
        int j = NoiseTables.FastFloor(y);
        int k = NoiseTables.FastFloor(z);
        float xi = (float) (x - i);
        float yi = (float) (y - j);
        float zi = (float) (z - k);

        i *= NoiseTables.PrimeX;
        j *= NoiseTables.PrimeY;
        k *= NoiseTables.PrimeZ;
        int seed2 = seed + 1293373;

        int xNMask = (int) (-0.5f - xi);
        int yNMask = (int) (-0.5f - yi);
        int zNMask = (int) (-0.5f - zi);

        float x0 = xi + xNMask;
        float y0 = yi + yNMask;
        float z0 = zi + zNMask;
        float a0 = 0.75f - x0 * x0 - y0 * y0 - z0 * z0;
        float value = (a0 * a0)
                * (a0 * a0)
                * NoiseTables.GradCoord(
                        seed,
                        i + (xNMask & NoiseTables.PrimeX),
                        j + (yNMask & NoiseTables.PrimeY),
                        k + (zNMask & NoiseTables.PrimeZ),
                        x0,
                        y0,
                        z0);

        float x1 = xi - 0.5f;
        float y1 = yi - 0.5f;
        float z1 = zi - 0.5f;
        float a1 = 0.75f - x1 * x1 - y1 * y1 - z1 * z1;
        value += (a1 * a1)
                * (a1 * a1)
                * NoiseTables.GradCoord(
                        seed2, i + NoiseTables.PrimeX, j + NoiseTables.PrimeY, k + NoiseTables.PrimeZ, x1, y1, z1);

        float xAFlipMask0 = ((xNMask | 1) << 1) * x1;
        float yAFlipMask0 = ((yNMask | 1) << 1) * y1;
        float zAFlipMask0 = ((zNMask | 1) << 1) * z1;
        float xAFlipMask1 = (-2 - (xNMask << 2)) * x1 - 1.0f;
        float yAFlipMask1 = (-2 - (yNMask << 2)) * y1 - 1.0f;
        float zAFlipMask1 = (-2 - (zNMask << 2)) * z1 - 1.0f;

        boolean skip5 = false;
        float a2 = xAFlipMask0 + a0;
        if (a2 > 0) {
            float x2 = x0 - (xNMask | 1);
            float y2 = y0;
            float z2 = z0;
            value += (a2 * a2)
                    * (a2 * a2)
                    * NoiseTables.GradCoord(
                            seed,
                            i + (~xNMask & NoiseTables.PrimeX),
                            j + (yNMask & NoiseTables.PrimeY),
                            k + (zNMask & NoiseTables.PrimeZ),
                            x2,
                            y2,
                            z2);
        } else {
            float a3 = yAFlipMask0 + zAFlipMask0 + a0;
            if (a3 > 0) {
                float x3 = x0;
                float y3 = y0 - (yNMask | 1);
                float z3 = z0 - (zNMask | 1);
                value += (a3 * a3)
                        * (a3 * a3)
                        * NoiseTables.GradCoord(
                                seed,
                                i + (xNMask & NoiseTables.PrimeX),
                                j + (~yNMask & NoiseTables.PrimeY),
                                k + (~zNMask & NoiseTables.PrimeZ),
                                x3,
                                y3,
                                z3);
            }

            float a4 = xAFlipMask1 + a1;
            if (a4 > 0) {
                float x4 = (xNMask | 1) + x1;
                float y4 = y1;
                float z4 = z1;
                value += (a4 * a4)
                        * (a4 * a4)
                        * NoiseTables.GradCoord(
                                seed2,
                                i + (xNMask & (NoiseTables.PrimeX * 2)),
                                j + NoiseTables.PrimeY,
                                k + NoiseTables.PrimeZ,
                                x4,
                                y4,
                                z4);
                skip5 = true;
            }
        }

        boolean skip9 = false;
        float a6 = yAFlipMask0 + a0;
        if (a6 > 0) {
            float x6 = x0;
            float y6 = y0 - (yNMask | 1);
            float z6 = z0;
            value += (a6 * a6)
                    * (a6 * a6)
                    * NoiseTables.GradCoord(
                            seed,
                            i + (xNMask & NoiseTables.PrimeX),
                            j + (~yNMask & NoiseTables.PrimeY),
                            k + (zNMask & NoiseTables.PrimeZ),
                            x6,
                            y6,
                            z6);
        } else {
            float a7 = xAFlipMask0 + zAFlipMask0 + a0;
            if (a7 > 0) {
                float x7 = x0 - (xNMask | 1);
                float y7 = y0;
                float z7 = z0 - (zNMask | 1);
                value += (a7 * a7)
                        * (a7 * a7)
                        * NoiseTables.GradCoord(
                                seed,
                                i + (~xNMask & NoiseTables.PrimeX),
                                j + (yNMask & NoiseTables.PrimeY),
                                k + (~zNMask & NoiseTables.PrimeZ),
                                x7,
                                y7,
                                z7);
            }

            float a8 = yAFlipMask1 + a1;
            if (a8 > 0) {
                float x8 = x1;
                float y8 = (yNMask | 1) + y1;
                float z8 = z1;
                value += (a8 * a8)
                        * (a8 * a8)
                        * NoiseTables.GradCoord(
                                seed2,
                                i + NoiseTables.PrimeX,
                                j + (yNMask & (NoiseTables.PrimeY << 1)),
                                k + NoiseTables.PrimeZ,
                                x8,
                                y8,
                                z8);
                skip9 = true;
            }
        }

        boolean skipD = false;
        float aA = zAFlipMask0 + a0;
        if (aA > 0) {
            float xA = x0;
            float yA = y0;
            float zA = z0 - (zNMask | 1);
            value += (aA * aA)
                    * (aA * aA)
                    * NoiseTables.GradCoord(
                            seed,
                            i + (xNMask & NoiseTables.PrimeX),
                            j + (yNMask & NoiseTables.PrimeY),
                            k + (~zNMask & NoiseTables.PrimeZ),
                            xA,
                            yA,
                            zA);
        } else {
            float aB = xAFlipMask0 + yAFlipMask0 + a0;
            if (aB > 0) {
                float xB = x0 - (xNMask | 1);
                float yB = y0 - (yNMask | 1);
                float zB = z0;
                value += (aB * aB)
                        * (aB * aB)
                        * NoiseTables.GradCoord(
                                seed,
                                i + (~xNMask & NoiseTables.PrimeX),
                                j + (~yNMask & NoiseTables.PrimeY),
                                k + (zNMask & NoiseTables.PrimeZ),
                                xB,
                                yB,
                                zB);
            }

            float aC = zAFlipMask1 + a1;
            if (aC > 0) {
                float xC = x1;
                float yC = y1;
                float zC = (zNMask | 1) + z1;
                value += (aC * aC)
                        * (aC * aC)
                        * NoiseTables.GradCoord(
                                seed2,
                                i + NoiseTables.PrimeX,
                                j + NoiseTables.PrimeY,
                                k + (zNMask & (NoiseTables.PrimeZ << 1)),
                                xC,
                                yC,
                                zC);
                skipD = true;
            }
        }

        if (!skip5) {
            float a5 = yAFlipMask1 + zAFlipMask1 + a1;
            if (a5 > 0) {
                float x5 = x1;
                float y5 = (yNMask | 1) + y1;
                float z5 = (zNMask | 1) + z1;
                value += (a5 * a5)
                        * (a5 * a5)
                        * NoiseTables.GradCoord(
                                seed2,
                                i + NoiseTables.PrimeX,
                                j + (yNMask & (NoiseTables.PrimeY << 1)),
                                k + (zNMask & (NoiseTables.PrimeZ << 1)),
                                x5,
                                y5,
                                z5);
            }
        }

        if (!skip9) {
            float a9 = xAFlipMask1 + zAFlipMask1 + a1;
            if (a9 > 0) {
                float x9 = (xNMask | 1) + x1;
                float y9 = y1;
                float z9 = (zNMask | 1) + z1;
                value += (a9 * a9)
                        * (a9 * a9)
                        * NoiseTables.GradCoord(
                                seed2,
                                i + (xNMask & (NoiseTables.PrimeX * 2)),
                                j + NoiseTables.PrimeY,
                                k + (zNMask & (NoiseTables.PrimeZ << 1)),
                                x9,
                                y9,
                                z9);
            }
        }

        if (!skipD) {
            float aD = xAFlipMask1 + yAFlipMask1 + a1;
            if (aD > 0) {
                float xD = (xNMask | 1) + x1;
                float yD = (yNMask | 1) + y1;
                float zD = z1;
                value += (aD * aD)
                        * (aD * aD)
                        * NoiseTables.GradCoord(
                                seed2,
                                i + (xNMask & (NoiseTables.PrimeX << 1)),
                                j + (yNMask & (NoiseTables.PrimeY << 1)),
                                k + NoiseTables.PrimeZ,
                                xD,
                                yD,
                                zD);
            }
        }

        return value * 9.046026385208288f;
    }
}
