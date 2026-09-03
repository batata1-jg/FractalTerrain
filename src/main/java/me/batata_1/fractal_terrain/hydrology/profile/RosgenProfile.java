package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import me.batata_1.fractal_terrain.noise.OctaveSimplexNoiseSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The cross-channel elevation profile, keyed by Rosgen stream type — what makes a steep headwater carve
 * differently from a lowland trunk.
 *
 * <p>Authority for the cross-section's horizontal extents too, nested outward as influence >
 * floodplainLen > width/2; {@code HydrologyTuning} delegates radii to these. Floodplain/blend zones are
 * unions of per-primitive discs, so spacing must stay {@code dx <= width/2} or the corridor scallops.
 *
 * <p><b>Only type A overrides anything today</b>; B, C and D inherit every default.
 */
public enum RosgenProfile implements HydrologyProfile {
    A {
        @Override
        public double floodPlainLength(double width) {
            return 1.2 * (width / 2);
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth, double curvature) {
            return -depth * (1 - Math.abs(signedPerpDist));
        }
    },
    Aa {

        @Override
        protected double valleyDelta(double dist) {
            return dist * 2;
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth, double curvature) {
            return -depth * (1 - Math.abs(signedPerpDist));
        }

        @Override
        protected double floodPlainDelta(
                long seed, double signedPerpDist, double width, double floodPlainLength, double curvature) {
            return 2 * (1 - Math.abs(signedPerpDist));
        }
    },
    B {
        @Override
        public double floodPlainLength(double width) {
            return 1.2 * (width / 2);
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth, double curvature) {
            return Math.min(-1, 0.5 * super.bedDelta(seed, signedPerpDist, depth, curvature));
        }

        @Override
        protected double floodPlainDelta(
                long seed, double signedPerpDist, double width, double floodPlainLength, double curvature) {
            return 1 - Math.abs(signedPerpDist);
        }
    },
    C {

        private static final double maxHalfWidth = HydrologyTuning.MAX_WIDTH / 2;

        @Override
        public double floodPlainLength(double width) {
            return 1.3 * Math.pow(width, 1.1);
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth, double curvature) {
            double sign = Math.signum(curvature);
            return 0.6
                            * depth
                            * smoothMax(
                                    Math.exp(-7 * (sign * signedPerpDist + 1)) - 1,
                                    (sign * signedPerpDist - 1) * 0.6,
                                    0.001)
                    - 3;
        }

        @Override
        protected double floodPlainDelta(
                long seed, double signedPerpDist, double width, double floodPlainLength, double curvature) {
            return -3 * (1 - Math.abs(signedPerpDist));
        }
    },
    D {
        // TODO:Usar fnl
        private static final OctaveSimplexNoiseSampler noiseSampler =
                new OctaveSimplexNoiseSampler(0, 1, 1, 1, 1, 1, null, null);

        private static final double maxHalfWidth = HydrologyTuning.MAX_WIDTH / 2;

        @Override
        public double floodPlainLength(double width) {
            return 1.5 * maxHalfWidth * Math.sqrt(width / HydrologyTuning.MAX_WIDTH);
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth, double curvature) {
            // return -3 * Math.abs(noiseSampler.sample(1.0 / seed, signedPerpDist));
            return -3;
        }
    },
    DA,
    E {
        private static final double maxHalfWidth = HydrologyTuning.MAX_WIDTH / 3;

        @Override
        public double floodPlainLength(double width) {
            return maxHalfWidth * Math.pow(width / HydrologyTuning.MAX_WIDTH, 0.3);
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth, double curvature) {
            return Math.min(-1, 0.5 * super.bedDelta(seed, signedPerpDist, depth, curvature));
        }

        @Override
        protected double valleyDelta(double dist) {
            return Math.pow(dist, 2.5);
        }
    },
    F {
        private static final double maxHalfWidth = HydrologyTuning.MAX_WIDTH / 2;

        @Override
        public double floodPlainLength(double width) {
            return (width / 2) * 1.275;
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth, double curvature) {
            return -Math.max(1, 0.5 * depth * Math.pow(1 - signedPerpDist * signedPerpDist, 0.16)) - 3;
        }
    },
    G {
        @Override
        public double floodPlainLength(double width) {
            return 1.2 * (width / 2);
        }
    };

    // ---- Horizontal extents (type-dependent; shared placeholder law, override per constant) ----
    private static final Logger LOG = LoggerFactory.getLogger(RosgenProfile.class);

    public static double smoothMax(double a, double b, double lambda) {
        return (a + b + Math.sqrt((a - b) * (a - b) + lambda)) / 2;
    }

    public static double smoothMin(double a, double b, double lambda) {
        return (a + b - Math.sqrt((a - b) * (a - b) + lambda)) / 2;
    }

    /** Floodplain half-extent. Placeholder law shared by all types; override per constant. */
    public double floodPlainLength(double width) {
        return width / 2;
    }

    // always starts as 0 and gradually carve the valley shape.
    protected double valleyDelta(double v) {
        return v;
    }

    // ---- Bed (per-pixel residual trench, cut below the already-carved shell) ----

    /** The raw bed trench, before {@link HydrologicalPrimitive#h} fades it over its footprint. */
    public double delta(long randSeed, double signedPerpDist, double width, double curvature) {
        return delta(
                randSeed,
                signedPerpDist,
                floodPlainLength(width),
                width / 2,
                FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depth(width),
                curvature);
    }

    /**
     * {@code delta} with the width-invariant extents pre-computed. Exists so a per-column carve loop can
     * hoist {@link #floodPlainLength}, the margin length and the channel depth out of the loop instead of
     * recomputing them once per column for every column of the same primitive.
     */
    public double delta(
            long randSeed,
            double signedPerpDist,
            double floodPlainLen,
            double marginLen,
            double depth,
            double curvature) {
        final double width = marginLen * 2;
        final double perpDist = Math.abs(signedPerpDist);
        final double bedContribution = bedDelta(randSeed, signedPerpDist / marginLen, depth, curvature);
        final double floodPlainContribution = floodPlainDelta(
                randSeed,
                signedPerpDist > 0
                        ? ((signedPerpDist - marginLen) / (floodPlainLen - marginLen))
                        : ((marginLen + signedPerpDist) / (marginLen - floodPlainLen)),
                width,
                floodPlainLen,
                curvature);
        final double valleyContribution = valleyDelta(perpDist - floodPlainLen);
        if (perpDist <= marginLen) return bedContribution;
        if (perpDist <= floodPlainLen) return floodPlainContribution;
        return valleyContribution;
    }

    /**
     * Tabulates this profile's cross-section into {@code lut}: {@code lut[i]} is the channel surface at
     * signed perpendicular distance {@code (baseIdx + i) * step}, with the primitive's base elevation
     * folded in. Runs once per primitive per grid, so the branchy per-region logic in {@link #delta}
     * leaves the per-lattice-point loop entirely.
     */
    public void sampleCrossSection(
            float[] lut,
            int n,
            double step,
            int baseIdx,
            long seed,
            double elevation,
            double floodPlainLen,
            double marginLen,
            double depth,
            double curvature) {
        for (int i = 0; i < n; i++) {
            lut[i] =
                    (float) (elevation + delta(seed, (baseIdx + i) * step, floodPlainLen, marginLen, depth, curvature));
        }
    }

    // range [-1,0] -> [-floodPlainLen,-marginLen] ;
    // range [0,1] -> [marginLen,floodPlainLen] ;
    protected double floodPlainDelta(
            long seed, double signedPerpDist, double width, double floodPlainLength, double curvature) {
        return 0;
    }

    // should be between the range -1 and 1
    protected double bedDelta(long seed, double signedPerpDist, double depth, double curvature) {
        return -Math.max(1, depth * Math.sqrt(1 - signedPerpDist * signedPerpDist));
    }

    /** The profile for a primitive's Rosgen type. */
    public static RosgenProfile of(RosgenType type) {
        return switch (type) {
            case A -> A;
            case Aa -> Aa;
            case B -> B;
            case C -> C;
            case D -> D;
            case DA -> DA;
            case E -> E;
            case F -> F;
            case G -> G;
        };
    }
}
