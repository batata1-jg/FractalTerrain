package me.batata_1.fractal_terrain.noise.strategy;

import me.batata_1.fractal_terrain.noise.FastNoiseLite;

/**
 * The {@code Cellular} (Worley/Voronoi) noise-type strategy: nearest/second-nearest feature-point
 * distances under a configurable distance function and return type.
 *
 * <p><b>Responsibility:</b> both overloads of {@link #SingleCellular} - the {@code NoiseType.Cellular}
 * branch of {@code FastNoiseLite}'s noise dispatcher. Unlike the other noise-type strategies, this one
 * takes the cellular distance-function, return-type, and jitter settings as explicit parameters (they
 * were instance fields on {@code FastNoiseLite}).
 *
 * <p><b>Collaborators:</b> {@link NoiseTables} for the shared hash/random-vector tables; {@code
 * FastNoiseLite.CellularDistanceFunction} / {@code FastNoiseLite.CellularReturnType} for the
 * configuration enums; dispatched from {@code FastNoiseLite#GenNoiseSingle}.
 *
 * <p><b>Invariants:</b> mechanical extraction from the embedded FastNoiseLite 1.1.1 implementation -
 * every constant and evaluation order is unchanged; output is byte-identical to the pre-split code.
 */
public final class CellularStrategy {

    private CellularStrategy() {}

    public static float SingleCellular(
            int seed,
            /*FNLfloat*/ float x,
            /*FNLfloat*/ float y,
            FastNoiseLite.CellularDistanceFunction cellularDistanceFunction,
            FastNoiseLite.CellularReturnType cellularReturnType,
            float cellularJitterModifier) {
        int xr = NoiseTables.FastRound(x);
        int yr = NoiseTables.FastRound(y);

        float distance0 = Float.MAX_VALUE;
        float distance1 = Float.MAX_VALUE;
        int closestHash = 0;

        float cellularJitter = 0.43701595f * cellularJitterModifier;

        int xPrimed = (xr - 1) * NoiseTables.PrimeX;
        int yPrimedBase = (yr - 1) * NoiseTables.PrimeY;

        switch (cellularDistanceFunction) {
            default:
            case Euclidean:
            case EuclideanSq:
                for (int xi = xr - 1; xi <= xr + 1; xi++) {
                    int yPrimed = yPrimedBase;

                    for (int yi = yr - 1; yi <= yr + 1; yi++) {
                        int hash = NoiseTables.Hash(seed, xPrimed, yPrimed);
                        int idx = hash & (255 << 1);

                        float vecX = (float) (xi - x) + NoiseTables.RandVecs2D[idx] * cellularJitter;
                        float vecY = (float) (yi - y) + NoiseTables.RandVecs2D[idx | 1] * cellularJitter;

                        float newDistance = vecX * vecX + vecY * vecY;

                        distance1 = NoiseTables.FastMax(NoiseTables.FastMin(distance1, newDistance), distance0);
                        if (newDistance < distance0) {
                            distance0 = newDistance;
                            closestHash = hash;
                        }
                        yPrimed += NoiseTables.PrimeY;
                    }
                    xPrimed += NoiseTables.PrimeX;
                }
                break;
            case Manhattan:
                for (int xi = xr - 1; xi <= xr + 1; xi++) {
                    int yPrimed = yPrimedBase;

                    for (int yi = yr - 1; yi <= yr + 1; yi++) {
                        int hash = NoiseTables.Hash(seed, xPrimed, yPrimed);
                        int idx = hash & (255 << 1);

                        float vecX = (float) (xi - x) + NoiseTables.RandVecs2D[idx] * cellularJitter;
                        float vecY = (float) (yi - y) + NoiseTables.RandVecs2D[idx | 1] * cellularJitter;

                        float newDistance = NoiseTables.FastAbs(vecX) + NoiseTables.FastAbs(vecY);

                        distance1 = NoiseTables.FastMax(NoiseTables.FastMin(distance1, newDistance), distance0);
                        if (newDistance < distance0) {
                            distance0 = newDistance;
                            closestHash = hash;
                        }
                        yPrimed += NoiseTables.PrimeY;
                    }
                    xPrimed += NoiseTables.PrimeX;
                }
                break;
            case Hybrid:
                for (int xi = xr - 1; xi <= xr + 1; xi++) {
                    int yPrimed = yPrimedBase;

                    for (int yi = yr - 1; yi <= yr + 1; yi++) {
                        int hash = NoiseTables.Hash(seed, xPrimed, yPrimed);
                        int idx = hash & (255 << 1);

                        float vecX = (float) (xi - x) + NoiseTables.RandVecs2D[idx] * cellularJitter;
                        float vecY = (float) (yi - y) + NoiseTables.RandVecs2D[idx | 1] * cellularJitter;

                        float newDistance =
                                (NoiseTables.FastAbs(vecX) + NoiseTables.FastAbs(vecY)) + (vecX * vecX + vecY * vecY);

                        distance1 = NoiseTables.FastMax(NoiseTables.FastMin(distance1, newDistance), distance0);
                        if (newDistance < distance0) {
                            distance0 = newDistance;
                            closestHash = hash;
                        }
                        yPrimed += NoiseTables.PrimeY;
                    }
                    xPrimed += NoiseTables.PrimeX;
                }
                break;
        }

        if (cellularDistanceFunction == FastNoiseLite.CellularDistanceFunction.Euclidean
                && cellularReturnType != FastNoiseLite.CellularReturnType.CellValue) {
            distance0 = NoiseTables.FastSqrt(distance0);

            if (cellularReturnType != FastNoiseLite.CellularReturnType.Distance) {
                distance1 = NoiseTables.FastSqrt(distance1);
            }
        }

        switch (cellularReturnType) {
            case CellValue:
                return closestHash * (1 / 2147483648.0f);
            case Distance:
                return distance0 - 1;
            case Distance2:
                return distance1 - 1;
            case Distance2Add:
                return (distance1 + distance0) * 0.5f - 1;
            case Distance2Sub:
                return distance1 - distance0 - 1;
            case Distance2Mul:
                return distance1 * distance0 * 0.5f - 1;
            case Distance2Div:
                return distance0 / distance1 - 1;
            default:
                return 0;
        }
    }

    public static float SingleCellular(
            int seed,
            /*FNLfloat*/ float x,
            /*FNLfloat*/ float y,
            /*FNLfloat*/ float z,
            FastNoiseLite.CellularDistanceFunction cellularDistanceFunction,
            FastNoiseLite.CellularReturnType cellularReturnType,
            float cellularJitterModifier) {
        int xr = NoiseTables.FastRound(x);
        int yr = NoiseTables.FastRound(y);
        int zr = NoiseTables.FastRound(z);

        float distance0 = Float.MAX_VALUE;
        float distance1 = Float.MAX_VALUE;
        int closestHash = 0;

        float cellularJitter = 0.39614353f * cellularJitterModifier;

        int xPrimed = (xr - 1) * NoiseTables.PrimeX;
        int yPrimedBase = (yr - 1) * NoiseTables.PrimeY;
        int zPrimedBase = (zr - 1) * NoiseTables.PrimeZ;

        switch (cellularDistanceFunction) {
            case Euclidean:
            case EuclideanSq:
                for (int xi = xr - 1; xi <= xr + 1; xi++) {
                    int yPrimed = yPrimedBase;

                    for (int yi = yr - 1; yi <= yr + 1; yi++) {
                        int zPrimed = zPrimedBase;

                        for (int zi = zr - 1; zi <= zr + 1; zi++) {
                            int hash = NoiseTables.Hash(seed, xPrimed, yPrimed, zPrimed);
                            int idx = hash & (255 << 2);

                            float vecX = (float) (xi - x) + NoiseTables.RandVecs3D[idx] * cellularJitter;
                            float vecY = (float) (yi - y) + NoiseTables.RandVecs3D[idx | 1] * cellularJitter;
                            float vecZ = (float) (zi - z) + NoiseTables.RandVecs3D[idx | 2] * cellularJitter;

                            float newDistance = vecX * vecX + vecY * vecY + vecZ * vecZ;

                            distance1 = NoiseTables.FastMax(NoiseTables.FastMin(distance1, newDistance), distance0);
                            if (newDistance < distance0) {
                                distance0 = newDistance;
                                closestHash = hash;
                            }
                            zPrimed += NoiseTables.PrimeZ;
                        }
                        yPrimed += NoiseTables.PrimeY;
                    }
                    xPrimed += NoiseTables.PrimeX;
                }
                break;
            case Manhattan:
                for (int xi = xr - 1; xi <= xr + 1; xi++) {
                    int yPrimed = yPrimedBase;

                    for (int yi = yr - 1; yi <= yr + 1; yi++) {
                        int zPrimed = zPrimedBase;

                        for (int zi = zr - 1; zi <= zr + 1; zi++) {
                            int hash = NoiseTables.Hash(seed, xPrimed, yPrimed, zPrimed);
                            int idx = hash & (255 << 2);

                            float vecX = (float) (xi - x) + NoiseTables.RandVecs3D[idx] * cellularJitter;
                            float vecY = (float) (yi - y) + NoiseTables.RandVecs3D[idx | 1] * cellularJitter;
                            float vecZ = (float) (zi - z) + NoiseTables.RandVecs3D[idx | 2] * cellularJitter;

                            float newDistance =
                                    NoiseTables.FastAbs(vecX) + NoiseTables.FastAbs(vecY) + NoiseTables.FastAbs(vecZ);

                            distance1 = NoiseTables.FastMax(NoiseTables.FastMin(distance1, newDistance), distance0);
                            if (newDistance < distance0) {
                                distance0 = newDistance;
                                closestHash = hash;
                            }
                            zPrimed += NoiseTables.PrimeZ;
                        }
                        yPrimed += NoiseTables.PrimeY;
                    }
                    xPrimed += NoiseTables.PrimeX;
                }
                break;
            case Hybrid:
                for (int xi = xr - 1; xi <= xr + 1; xi++) {
                    int yPrimed = yPrimedBase;

                    for (int yi = yr - 1; yi <= yr + 1; yi++) {
                        int zPrimed = zPrimedBase;

                        for (int zi = zr - 1; zi <= zr + 1; zi++) {
                            int hash = NoiseTables.Hash(seed, xPrimed, yPrimed, zPrimed);
                            int idx = hash & (255 << 2);

                            float vecX = (float) (xi - x) + NoiseTables.RandVecs3D[idx] * cellularJitter;
                            float vecY = (float) (yi - y) + NoiseTables.RandVecs3D[idx | 1] * cellularJitter;
                            float vecZ = (float) (zi - z) + NoiseTables.RandVecs3D[idx | 2] * cellularJitter;

                            float newDistance =
                                    (NoiseTables.FastAbs(vecX) + NoiseTables.FastAbs(vecY) + NoiseTables.FastAbs(vecZ))
                                            + (vecX * vecX + vecY * vecY + vecZ * vecZ);

                            distance1 = NoiseTables.FastMax(NoiseTables.FastMin(distance1, newDistance), distance0);
                            if (newDistance < distance0) {
                                distance0 = newDistance;
                                closestHash = hash;
                            }
                            zPrimed += NoiseTables.PrimeZ;
                        }
                        yPrimed += NoiseTables.PrimeY;
                    }
                    xPrimed += NoiseTables.PrimeX;
                }
                break;
            default:
                break;
        }

        if (cellularDistanceFunction == FastNoiseLite.CellularDistanceFunction.Euclidean
                && cellularReturnType != FastNoiseLite.CellularReturnType.CellValue) {
            distance0 = NoiseTables.FastSqrt(distance0);

            if (cellularReturnType != FastNoiseLite.CellularReturnType.Distance) {
                distance1 = NoiseTables.FastSqrt(distance1);
            }
        }

        switch (cellularReturnType) {
            case CellValue:
                return closestHash * (1 / 2147483648.0f);
            case Distance:
                return distance0 - 1;
            case Distance2:
                return distance1 - 1;
            case Distance2Add:
                return (distance1 + distance0) * 0.5f - 1;
            case Distance2Sub:
                return distance1 - distance0 - 1;
            case Distance2Mul:
                return distance1 * distance0 * 0.5f - 1;
            case Distance2Div:
                return distance0 / distance1 - 1;
            default:
                return 0;
        }
    }
}
