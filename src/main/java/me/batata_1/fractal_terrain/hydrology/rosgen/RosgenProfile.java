package me.batata_1.fractal_terrain.hydrology.rosgen;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileCarver;
import me.batata_1.fractal_terrain.noise.NoiseSampler;
import me.batata_1.fractal_terrain.noise.OctaveSimplexNoiseSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The cross-channel elevation profile of a hydrological feature, keyed by Rosgen stream type.
 *
 * <p>Two deltas are defined here, carved by two different stages:
 *
 * <ul>
 *   <li>{@link #riverInfluenceElevation} — the tile-level valley/floodplain SHELL. Carved once per tile
 *       by {@link HydrologyProfileCarver#carveRiverShells}, which picks the single <em>nearest</em>
 *       influencing unit per pixel and lerps the live buffer's elevation toward that unit's reference
 *       elevation: full pull inside {@link #floodPlainLength}, linearly released to no change at
 *       {@link #riverInfluence}.
 *   <li>{@link #riverAreaDelta} — the per-pixel bed TRENCH below the shell, within the bed half-width.
 *       Applied by {@link HydrologyProfile#computeForUnit}, which fades it in over an elliptical
 *       footprint around the contributing unit rather than applying it raw.
 * </ul>
 *
 * <p>The profile is also the authority for the two horizontal extents of the cross-section —
 * {@link #floodPlainLength} and {@link #riverInfluence} — so a river's floodplain half-extent and outer
 * influence radius can vary by Rosgen type. {@link HydrologyTuning#floodPlainLength} /
 * {@link HydrologyTuning#riverInfluence} are thin delegates to these (their width-only overloads
 * assume {@link RosgenType#A}).
 *
 * <p>The floodplain and blending zones are unions of per-unit radial discs; the smoothness of that
 * union relies on adjacent units' discs overlapping, which requires unit spacing {@code dx <=
 * width/2} (enforced in {@link me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork}).
 * Loosening that spacing risks scalloping or gaps in the floodplain corridor.
 *
 * <p><b>Only type A overrides anything</b> — A supplies its own {@link #floodPlainLength},
 * {@link #bedDelta}, {@link #floodPlainDelta} and {@link #unAffectedDistCalculator}; B, C and D inherit
 * every enum-level default unchanged. Per-type formulas land by overriding the relevant method in a
 * constant's body (e.g. {@code C { @Override public double riverInfluence(double width) { … } }}).
 */

// influence > floodplainLen > width/2
public enum RosgenProfile {
    A {
        @Override
        public double floodPlainLength(double width) {
            return 1.2*(width/2);
        }

        @Override
        public double riverInfluence(double width) {
            return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, width / HydrologyTuning.MIN_WIDTH );
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth) {
            return -depth*(1-Math.abs(signedPerpDist));
        }

    },
    Aa{

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
            return -depth*(1-Math.abs(signedPerpDist));
        }

        @Override
        protected double floodPlainDelta(long seed, double signedPerpDist, double width, double floodPlainLength) {
            return 2*(1-Math.abs(signedPerpDist));
        }

    },
    B{
        @Override
        public double floodPlainLength(double width) {
            return 1.2*(width/2);
        }

        @Override
        public double riverInfluence(double width) {
            return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, floodPlainLength(width) / HydrologyTuning.MIN_WIDTH);
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth) {
            return Math.min(-1,0.25*super.bedDelta(seed, signedPerpDist, depth));
        }

        @Override
        protected double floodPlainDelta(long seed, double signedPerpDist, double width, double floodPlainLength) {
            return 1-Math.abs(signedPerpDist);
        }

    },
    C{

        private static final double maxHalfWidth = HydrologyTuning.MAX_WIDTH / 2;

        @Override
        public double floodPlainLength(double width) {
            return 2*maxHalfWidth*Math.sqrt(width/HydrologyTuning.MAX_WIDTH);
        }

        @Override
        public double riverInfluence(double width) {
            return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, 4*maxHalfWidth*Math.pow(floodPlainLength(width)/(2*maxHalfWidth),0.75) );
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth) {
            return 0.6 * depth * smoothMax(Math.exp(-7*(signedPerpDist+1))-1,(signedPerpDist-1)*0.6,0.001);
        }

    },
    D {

        private static final OctaveSimplexNoiseSampler noiseSampler = new OctaveSimplexNoiseSampler(
                0,
                1,
                1,
                1,
                1,
                1,
                null,
                null
        );

        private static final double maxHalfWidth = HydrologyTuning.MAX_WIDTH / 2;

        @Override
        public double floodPlainLength(double width) {
            return 1.5*maxHalfWidth*Math.sqrt(width/HydrologyTuning.MAX_WIDTH);
        }

        @Override
        public double riverInfluence(double width) {
            return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, 3*maxHalfWidth*Math.pow(floodPlainLength(width)/(1.5*maxHalfWidth),0.75) );
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth) {
            return -3*Math.abs(noiseSampler.sample(1.0/seed, signedPerpDist));
        }
    },
    DA,
    E{
        private static final double maxHalfWidth = HydrologyTuning.MAX_WIDTH / 2;

        @Override
        public double floodPlainLength(double width) {
            return maxHalfWidth*Math.pow(width/HydrologyTuning.MAX_WIDTH,0.3);
        }

        @Override
        public double riverInfluence(double width) {
            return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, 1.3*floodPlainLength(width) );
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth) {
            return Math.min(-1,0.5 * super.bedDelta(seed, signedPerpDist, depth));
        }

        @Override
        protected double floodPlainDelta(long seed, double signedPerpDist, double width, double floodPlainLength) {
            return 3*(1-Math.abs(signedPerpDist));
        }

        @Override
        protected double valleyShapeCarve(double dist) {
            return Math.pow(dist,2.5);
        }

    },
    F{
        private static final double maxHalfWidth = HydrologyTuning.MAX_WIDTH / 2;

        @Override
        public double floodPlainLength(double width) {
            return (width/2)*1.275;
        }

        @Override
        public double riverInfluence(double width) {
            return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, 1.5*maxHalfWidth*Math.pow(floodPlainLength(width)/(maxHalfWidth),0.75) );
        }

        @Override
        protected double bedDelta(long seed, double signedPerpDist, double depth) {
            return -Math.min(1, 0.5 * depth * Math.pow(1 - signedPerpDist * signedPerpDist,0.16));
        }
    },
    G{
        @Override
        public double floodPlainLength(double width) {
            return 1.2*(width/2);
        }

        @Override
        public double riverInfluence(double width) {
            return Math.min(HydrologyTuning.MAX_INFLUENCE_RADIUS, floodPlainLength(width) / HydrologyTuning.MIN_WIDTH);
        }
    };

    // ---- Horizontal extents (type-dependent; shared placeholder law, override per constant) ----
    private static final Logger LOG = LoggerFactory.getLogger(RosgenProfile.class);

    public static double smoothMax(double a , double b , double lambda) {
        return (a + b + Math.sqrt((a-b)*(a-b) + lambda)) / 2;
    }

    public static double smoothMin(double a , double b , double lambda) {
        return (a + b - Math.sqrt((a-b)*(a-b) + lambda)) / 2;
    }

    /**
     * Floodplain half-extent (native px) for a river of the given width under this Rosgen type. Placeholder
     * law shared by all types: {@code FLOODPLAIN_BASE + FLOODPLAIN_WIDTH_FACTOR · width}. Override in a
     * constant's body to make a type's floodplain wider/narrower.
     */
    public double floodPlainLength(double width) {
        return width/2;
    }

    /**
     * Outer influence radius (native px) for a river of the given width under this Rosgen type: floodplain +
     * blend band, clamped to {@link HydrologyTuning#MAX_INFLUENCE_RADIUS}. Beyond this radius the river
     * no longer affects a pixel; it is also the unit's R-tree membership-circle radius
     * ({@link me.batata_1.fractal_terrain.hydrology.HydrologicalUnit#getRadius()}). Placeholder law shared by
     * all types; override per constant to widen/narrow a type's reach. Note it calls the (virtual)
     * {@link #floodPlainLength} so a type that overrides only its floodplain gets a consistent influence.
     */
    public double riverInfluence(double width) {
        return Math.min(
                HydrologyTuning.MAX_INFLUENCE_RADIUS, width * HydrologyTuning.INFLUENCE_BLEND_MULTIPLIER);
    }

    /**
     * The shell elevation at radial distance {@code radialDist} from a unit: {@code unitElev} (full pull
     * to the unit's reference elevation) out to {@link #floodPlainLength}, then linearly released back to
     * {@code curElev} at {@link #riverInfluence} and beyond. {@code curElev} is read from the live carve
     * buffer, so the result depends on the order units are visited; {@code carveRiverShells} sidesteps
     * that by applying only the single nearest unit per pixel.
     */
    public double riverInfluenceElevation(double radialDist, double width, double curElev, double unitElev) {
        final double riverInfluence = riverInfluence(width);
        final double floodPlainLength = floodPlainLength(width);
        if( radialDist < floodPlainLength) return unitElev;
        if( radialDist < riverInfluence) {
            final double t = (radialDist - floodPlainLength) / (riverInfluence - floodPlainLength);
            final double lambda = (1 - t) * 0.5;
            final double influenceContribution = (1 - t) * unitElev + t * curElev;
            final double valleyContribution = smoothMin(curElev, unitElev + valleyShapeCarve(radialDist-floodPlainLength), lambda);
            return smoothMax(valleyContribution,influenceContribution,lambda);
        }
        return curElev;
    }


    //always starts as 0 and gradually carve the valley shape.
    protected double valleyShapeCarve(double v) {
        return v;
    }

    // ---- Bed (per-pixel residual trench, cut below the already-carved shell) ----

    /**
     * Bed-residual depth at a point {@code signedPerpDist} across / {@code alongDist} along the channel:
     * {@link #bedDelta} within the bed half-width, {@link #floodPlainDelta} out to
     * {@link #floodPlainLength}, {@code 0} beyond. The raw (un-faded) delta — see the class javadoc.
     */
    public double riverAreaDelta(long randSeed, double signedPerpDist, double alongDist, double width) {
        final double floodPlainLen = floodPlainLength(width);
        if (Math.hypot(signedPerpDist, alongDist) > floodPlainLen) return 0;
        final double marginLen = width / 2;
        if (Math.abs(signedPerpDist) <= marginLen) {
            // LOG.info("hallooo");
            //return -10;
            return bedDelta(randSeed,signedPerpDist / marginLen, FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depthForWidth(width));
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
        return -Math.min(1,depth * Math.sqrt(1 - signedPerpDist * signedPerpDist));
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
