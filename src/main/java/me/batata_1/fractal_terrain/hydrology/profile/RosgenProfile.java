package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.features.RiverUnit;
import me.batata_1.fractal_terrain.hydrology.features.RiverUnit.RosgenType;
import me.batata_1.fractal_terrain.noise.OctaveSimplexNoiseSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The cross-channel elevation profile, keyed by Rosgen stream type — what makes a steep headwater carve
 * differently from a lowland trunk.
 *
 * <p>Authority for the cross-section's horizontal extents too, nested outward as influence >
 * floodplainLen > width/2; {@code HydrologyTuning} delegates radii to these. Floodplain/blend zones are
 * unions of per-unit discs, so spacing must stay {@code dx <= width/2} or the corridor scallops.
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
        protected double bedDelta(long seed, double signedPerpDist, double depth) {
            return -depth * (1 - Math.abs(signedPerpDist));
        }
    },
    Aa {

        @Override
        public double riverInfluence(double width) {
            return Math.clamp(0.5 * (width / HydrologyTuning.MIN_WIDTH), width, HydrologyTuning.MAX_INFLUENCE_RADIUS);
        }

        @Override
        protected double valleyShapeCarve(double dist) {
            return dist * 2;
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth) {
            return -depth * (1 - Math.abs(signedPerpDist));
        }

        @Override
        protected double floodPlainDelta(long seed, double signedPerpDist, double width, double floodPlainLength) {
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
        protected double bedDelta(long seed, double signedPerpDist, double depth) {
            return Math.min(-1, 0.25 * super.bedDelta(seed, signedPerpDist, depth));
        }

        @Override
        protected double floodPlainDelta(long seed, double signedPerpDist, double width, double floodPlainLength) {
            return 1 - Math.abs(signedPerpDist);
        }
    },
    C {

        private static final double maxHalfWidth = HydrologyTuning.MAX_WIDTH / 2;

        @Override
        public double floodPlainLength(double width) {
            return 2 * maxHalfWidth * Math.sqrt(width / HydrologyTuning.MAX_WIDTH);
        }

        @Override
        public double riverInfluence(double width) {
            return Math.min(
                    HydrologyTuning.MAX_INFLUENCE_RADIUS,
                    4 * maxHalfWidth * Math.pow(floodPlainLength(width) / (2 * maxHalfWidth), 0.75));
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth) {
            return 0.6 * depth * smoothMax(Math.exp(-7 * (signedPerpDist + 1)) - 1, (signedPerpDist - 1) * 0.6, 0.001);
        }
    },
    D {

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
        protected double bedDelta(long seed, double signedPerpDist, double depth) {
            return -3 * Math.abs(noiseSampler.sample(1.0 / seed, signedPerpDist));
        }
    },
    DA,
    E {
        private static final double maxHalfWidth = HydrologyTuning.MAX_WIDTH / 2;

        @Override
        public double floodPlainLength(double width) {
            return maxHalfWidth * Math.pow(width / HydrologyTuning.MAX_WIDTH, 0.3);
        }

        @Override
        public double riverInfluence(double width) {
            return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, 1.3 * floodPlainLength(width));
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth) {
            return Math.min(-1, 0.5 * super.bedDelta(seed, signedPerpDist, depth));
        }

        @Override
        protected double floodPlainDelta(long seed, double signedPerpDist, double width, double floodPlainLength) {
            return 3 * (1 - Math.abs(signedPerpDist));
        }

        @Override
        protected double valleyShapeCarve(double dist) {
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
        protected double bedDelta(long seed, double signedPerpDist, double depth) {
            return -Math.min(1, 0.5 * depth * Math.pow(1 - signedPerpDist * signedPerpDist, 0.16));
        }
    },
    G {
        @Override
        public double floodPlainLength(double width) {
            return 1.2 * (width / 2);
        }

        @Override
        public double riverInfluence(double width) {
            return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, floodPlainLength(width) / HydrologyTuning.MIN_WIDTH);
        }
    };

    // ---- Zone mapping (the HydrologyProfile contract, expressed over a River's width) ----

    /** The nested river zones. INFLUENCE comes from the unit's index radius, not {@link #riverInfluence},
     *  so carve reach stays identical to the circle the R-tree found the unit by. */
    @Override
    public double zoneRadius(HydrologicalUnit unit, ZoneCategory category) {
        if (!(unit instanceof RiverUnit riverUnit)) return HydrologyProfile.super.zoneRadius(unit, category);
        return switch (category) {
            case BED -> ChannelGeometry.bedHalfWidth(riverUnit.width());
            case FLOODPLAIN -> floodPlainLength(riverUnit.width());
            case INFLUENCE -> riverUnit.getRadius();
            default -> NO_ZONE;
        };
    }

    /** Delegates to {@link #riverInfluenceElevation} with the reach's width and bank elevation. */
    @Override
    public double shellElevation(HydrologicalUnit unit, double radialDist, double curElev) {
        if (!(unit instanceof RiverUnit riverUnit)) return curElev;
        return riverInfluenceElevation(radialDist, riverUnit.width(), curElev, riverUnit.elevation());
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

    /** Outer reach of the river, and the unit's index radius. Calls the virtual
     *  {@link #floodPlainLength}, so overriding only the floodplain still yields consistent influence. */
    public double riverInfluence(double width) {
        return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, width * HydrologyTuning.INFLUENCE_BLEND_MULTIPLIER);
    }

    /** The river's valley pull: full inside the floodplain, released to nothing at the influence edge.
     *  The carver blends this across every unit reaching a pixel, so a confluence gets both profiles. */
    public double riverInfluenceElevation(double radialDist, double width, double curElev, double unitElev) {
        final double riverInfluence = riverInfluence(width);
        final double floodPlainLength = floodPlainLength(width);
        if (radialDist < floodPlainLength) return unitElev;
        if (radialDist < riverInfluence) {
            final double t = (radialDist - floodPlainLength) / (riverInfluence - floodPlainLength);
            final double lambda = (1 - t) * 0.5;
            final double influenceContribution = (1 - t) * unitElev + t * curElev;
            final double valleyContribution =
                    smoothMin(curElev, unitElev + valleyShapeCarve(radialDist - floodPlainLength), lambda);
            return smoothMax(valleyContribution, influenceContribution, lambda);
        }
        return curElev;
    }

    // always starts as 0 and gradually carve the valley shape.
    protected double valleyShapeCarve(double v) {
        return v;
    }

    // ---- Bed (per-pixel residual trench, cut below the already-carved shell) ----

    /** The raw bed trench, before {@link RiverUnit#carveFineGrained} fades it over its footprint. */
    public double riverAreaDelta(long randSeed, double signedPerpDist, double alongDist, double width) {
        final double floodPlainLen = floodPlainLength(width);
        if (Math.hypot(signedPerpDist, alongDist) > floodPlainLen) return 0;
        final double marginLen = width / 2;
        if (Math.abs(signedPerpDist) <= marginLen) {
            // LOG.info("hallooo");
            // return -10;
            return bedDelta(
                    randSeed,
                    signedPerpDist / marginLen,
                    FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depthForWidth(width));
        }
        return floodPlainDelta(
                randSeed,
                signedPerpDist > 0
                        ? ((signedPerpDist - marginLen) / (floodPlainLen - marginLen))
                        : ((marginLen + signedPerpDist) / (marginLen - floodPlainLen)),
                width,
                floodPlainLen);
    }

    // range [-1,0] -> [-floodPlainLen,-marginLen] ;
    // range [0,1] -> [marginLen,floodPlainLen] ;
    protected double floodPlainDelta(long seed, double signedPerpDist, double width, double floodPlainLength) {
        return 0;
    }

    // should be between the range -1 and 1
    protected double bedDelta(long seed, double signedPerpDist, double depth) {
        return -Math.min(1, depth * Math.sqrt(1 - signedPerpDist * signedPerpDist));
    }

    /** The profile for a unit's Rosgen type. */
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
