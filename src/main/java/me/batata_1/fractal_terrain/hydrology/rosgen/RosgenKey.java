package me.batata_1.fractal_terrain.hydrology.rosgen;

import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;

/**
 * The Rosgen Level-I decision key: measured reach metrics in, stream type out, nothing else needed.
 *
 * <p>Kept free of raster and graph so the classification rules can be read and tested on their own,
 * apart from how the metrics were measured.
 *
 * <p>First-match-wins and the order is load-bearing — slope, then entrenchment, then {@code DA} before
 * {@code D}, then {@code E} vs {@code C}. See {@code README.md} for why. Level II (the substrate digit)
 * is not recoverable from an elevation field and is out of scope.
 */
public final class RosgenKey {

    private RosgenKey() {}

    /** The type for one reach. Total — even a saturated {@code +inf} entrenchment returns a type. */
    public static RosgenType classify(ReachMetrics m) {
        // Steep confined headwaters: slope alone decides.
        if (m.slope() >= HydrologyTuning.S_AA) return RiverPrimitive.RosgenType.Aa;
        if (m.slope() >= HydrologyTuning.S_A) return RiverPrimitive.RosgenType.A;

        // Entrenched: the valley pinches the channel.
        if (m.entrenchment() < HydrologyTuning.ER_ENTRENCHED) {
            return m.widthPerDepth() < HydrologyTuning.WD_NARROW
                    ? RiverPrimitive.RosgenType.G
                    : RiverPrimitive.RosgenType.F;
        }

        // Moderately entrenched.
        if (m.entrenchment() < HydrologyTuning.ER_SLIGHT) return RiverPrimitive.RosgenType.B;

        // Slightly entrenched: a broad floodplain is available.
        if (m.bedElev() < HydrologyTuning.DELTA_ELEV
                && m.slope() < HydrologyTuning.S_DA
                && m.entrenchment() > HydrologyTuning.ER_ANASTOMOSE) {
            return RiverPrimitive.RosgenType.DA;
        }
        if (m.width() > HydrologyTuning.BRAID_MIN_WIDTH && m.slope() > braidThreshold(m.width())) {
            return RiverPrimitive.RosgenType.D;
        }
        return m.widthPerDepth() < HydrologyTuning.WD_NARROW
                ? RiverPrimitive.RosgenType.E
                : RiverPrimitive.RosgenType.C;
    }

    /** Gates where braiding is plausible. Braiding is not measurable without a sediment-transport
     *  model, so this is an authored style choice rather than an observation. */
    public static double braidThreshold(double width) {
        return HydrologyTuning.K_BRAID * Math.pow(width, HydrologyTuning.BRAID_WIDTH_EXPONENT);
    }
}
