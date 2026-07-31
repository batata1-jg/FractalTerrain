package me.batata_1.fractal_terrain.hydrology.rosgen;

import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.RiverUnit;
import me.batata_1.fractal_terrain.hydrology.features.RiverUnit.RosgenType;
import org.jetbrains.annotations.Nullable;

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
        if (m.slope() >= HydrologyTuning.S_AA) return RiverUnit.RosgenType.Aa;
        if (m.slope() >= HydrologyTuning.S_A) return RiverUnit.RosgenType.A;

        // Entrenched: the valley pinches the channel.
        if (m.entrenchment() < HydrologyTuning.ER_ENTRENCHED) {
            return m.widthDepth() < HydrologyTuning.WD_NARROW ? RiverUnit.RosgenType.G : RiverUnit.RosgenType.F;
        }

        // Moderately entrenched.
        if (m.entrenchment() < HydrologyTuning.ER_SLIGHT) return RiverUnit.RosgenType.B;

        // Slightly entrenched: a broad floodplain is available.
        if (m.bedElev() < HydrologyTuning.DELTA_ELEV
                && m.slope() < HydrologyTuning.S_DA
                && m.entrenchment() > HydrologyTuning.ER_ANASTOMOSE) {
            return RiverUnit.RosgenType.DA;
        }
        if (m.width() > HydrologyTuning.BRAID_MIN_WIDTH && m.slope() > braidThreshold(m.width())) {
            return RiverUnit.RosgenType.D;
        }
        return m.widthDepth() < HydrologyTuning.WD_NARROW ? RiverUnit.RosgenType.E : RiverUnit.RosgenType.C;
    }

    /** Gates where braiding is plausible. Braiding is not measurable without a sediment-transport
     *  model, so this is an authored style choice rather than an observation. */
    public static double braidThreshold(double width) {
        return HydrologyTuning.K_BRAID * Math.pow(width, HydrologyTuning.BRAID_WIDTH_EXPONENT);
    }

    /** Suppresses type flicker near a threshold by keeping the neighbouring reach's type. Exists
     *  because a flickering type scallops the floodplain edge, which the profile derives from it.
     *  Covers ER and W/D only — slope is real landform variation, not measurement noise. */
    public static RosgenType applyDeadBand(ReachMetrics m, RosgenType raw, @Nullable RosgenType previous) {
        return raw;
        //        if (previous == null || raw == previous) return raw;
        //        final boolean onThreshold =
        //                nearThreshold(m.entrenchment(), HydrologyTuning.ER_ENTRENCHED, HydrologyTuning.ER_TOLERANCE)
        //                        || nearThreshold(m.entrenchment(), HydrologyTuning.ER_SLIGHT,
        // HydrologyTuning.ER_TOLERANCE)
        //                        || nearThreshold(m.entrenchment(), HydrologyTuning.ER_ANASTOMOSE,
        // HydrologyTuning.ER_TOLERANCE)
        //                        || nearThreshold(m.widthDepth(), HydrologyTuning.WD_NARROW,
        // HydrologyTuning.WD_TOLERANCE);
        //        return onThreshold ? previous : raw;
    }

    /** Whether {@code value} sits within {@code tolerance} of {@code threshold}. Infinities are never near. */
    private static boolean nearThreshold(double value, double threshold, double tolerance) {
        if (!Double.isFinite(value)) return false;
        return Math.abs(value - threshold) <= tolerance;
    }
}
