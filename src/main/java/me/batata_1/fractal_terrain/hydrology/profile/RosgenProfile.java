package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
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
        public double riverInfluence(double width) {
            return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, width / HydrologyTuning.MIN_WIDTH);
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth, double curvature) {
            return -depth * (1 - Math.abs(signedPerpDist));
        }
    },
    Aa {

        @Override
        public double riverInfluence(double width) {
            return Math.clamp(0.5 * (width / HydrologyTuning.MIN_WIDTH), width, HydrologyTuning.MAX_INFLUENCE_RADIUS);
        }

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
        public double riverInfluence(double width) {
            return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, floodPlainLength(width) / HydrologyTuning.MIN_WIDTH);
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth, double curvature) {
            return Math.min(-1, 0.25 * super.bedDelta(seed, signedPerpDist, depth, curvature));
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
        public double riverInfluence(double width) {
            return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, 10 * Math.pow(width, 0.575));
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
        //TODO:Usar fnl
        private static final OctaveSimplexNoiseSampler noiseSampler =
                new OctaveSimplexNoiseSampler(0, 1, 1, 1, 1, 1, null, null);

        private static final double maxHalfWidth = HydrologyTuning.MAX_WIDTH / 2;

        @Override
        public double floodPlainLength(double width) {
            return 1.5 * maxHalfWidth * Math.sqrt(width / HydrologyTuning.MAX_WIDTH);
        }

        @Override
        public double riverInfluence(double width) {
            return Math.min(
                    HydrologyTuning.MAX_INFLUENCE_RADIUS,
                    3 * maxHalfWidth * Math.pow(floodPlainLength(width) / (1.5 * maxHalfWidth), 0.75));
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
        public double riverInfluence(double width) {
            return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, 1.3 * floodPlainLength(width));
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
        public double riverInfluence(double width) {
            return Math.min(
                    HydrologyTuning.MAX_INFLUENCE_RADIUS,
                    1.5 * maxHalfWidth * Math.pow(floodPlainLength(width) / (maxHalfWidth), 0.75));
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth, double curvature) {
            return -Math.min(1, 0.5 * depth * Math.pow(1 - signedPerpDist * signedPerpDist, 0.16)) - 3;
        }
    },
    G {
        @Override
        public double floodPlainLength(double width) {
            return 1.2 * (width / 2);
        }

        @Override
        public double riverInfluence(double width) {
            return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, 2*width);
        }
    };

    // ---- Zone mapping (the HydrologyProfile contract, expressed over a River's width) ----

    /** The nested river zones. INFLUENCE comes from the primitive's index radius, not {@link #riverInfluence},
     *  so carve reach stays identical to the circle the R-tree found the primitive by. */
    @Override
    public double zoneRadius(HydrologicalPrimitive primitive, ZoneCategory category) {
        if (!(primitive instanceof RiverPrimitive riverPrimitive))
            return HydrologyProfile.super.zoneRadius(primitive, category);
        return switch (category) {
            case BED -> ChannelGeometry.bedHalfWidth(riverPrimitive.width());
            case FLOODPLAIN -> floodPlainLength(riverPrimitive.width());
            case INFLUENCE -> riverPrimitive.getRadius();
            default -> NO_ZONE;
        };
    }

    /** Delegates to {@link #riverInfluenceElevation} with the reach's width and bank elevation. */
    @Override
    public double shellElevation(HydrologicalPrimitive primitive, double radialDist, double curElev) {
        if (!(primitive instanceof RiverPrimitive riverPrimitive)) return curElev;
        return riverInfluenceElevation(radialDist, riverPrimitive.width(), curElev, riverPrimitive.elevation());
    }

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

    /** Outer reach of the river, and the primitive's index radius. Calls the virtual
     *  {@link #floodPlainLength}, so overriding only the floodplain still yields consistent influence. */
    public double riverInfluence(double width) {
        return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, width * HydrologyTuning.INFLUENCE_BLEND_MULTIPLIER);
    }

    /** The river's valley pull: full inside the floodplain, released to nothing at the influence edge.
     *  The carver blends this across every primitive reaching a pixel, so a confluence gets both profiles. */
    public double riverInfluenceElevation(double radialDist, double width, double curElev, double primitiveElev) {
        final double riverInfluence = riverInfluence(width);
        final double floodPlainLength = floodPlainLength(width);
        if (radialDist < floodPlainLength) return primitiveElev;
        if (radialDist < riverInfluence) {
            final double t = (radialDist - floodPlainLength) / (riverInfluence - floodPlainLength);
            final double lambda = (1 - t) * 0.5;
            final double influenceContribution = (1 - t) * primitiveElev + t * curElev;
            final double valleyContribution =
                    smoothMin(curElev, primitiveElev + valleyDelta(radialDist - floodPlainLength), lambda);
            return smoothMax(valleyContribution, influenceContribution, lambda);
        }
        return curElev;
    }

    // always starts as 0 and gradually carve the valley shape.
    protected double valleyDelta(double v) {
        return v;
    }

    // ---- Bed (per-pixel residual trench, cut below the already-carved shell) ----


    /** The raw bed trench, before {@link RiverPrimitive#h} fades it over its footprint. */
    public double delta(
            long randSeed, double signedPerpDist, double width, double curvature) {
        final double floodPlainLen = floodPlainLength(width);
        final double marginLen = width / 2;
        final double perpDist = Math.abs(signedPerpDist);
        final double bedContribution = bedDelta(
            randSeed,
            signedPerpDist / marginLen,
            FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depthForWidth(width),
            curvature
        );
        final double floodPlainContribution = floodPlainDelta(
            randSeed,
            signedPerpDist > 0
                    ? ((signedPerpDist - marginLen) / (floodPlainLen - marginLen))
                    : ((marginLen + signedPerpDist) / (marginLen - floodPlainLen)),
            width,
            floodPlainLen,
            curvature
        );
        final double valleyContribution = valleyDelta(perpDist-floodPlainLen);
        if(perpDist<=marginLen) return bedContribution;
        if(perpDist<=floodPlainLen) return floodPlainContribution;
        return valleyContribution;
    }

    // range [-1,0] -> [-floodPlainLen,-marginLen] ;
    // range [0,1] -> [marginLen,floodPlainLen] ;
    protected double floodPlainDelta(
            long seed, double signedPerpDist, double width, double floodPlainLength, double curvature) {
        return 0;
    }

    // should be between the range -1 and 1
    protected double bedDelta(long seed, double signedPerpDist, double depth, double curvature) {
        return -Math.min(1, depth * Math.sqrt(1 - signedPerpDist * signedPerpDist));
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
