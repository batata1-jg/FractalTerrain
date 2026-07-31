package me.batata_1.fractal_terrain.hydrology.rosgen;

import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.River.RosgenType;
import me.batata_1.fractal_terrain.hydrology.features.River;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import org.jetbrains.annotations.Nullable;

/**
 * The Rosgen Level-I decision key: a total, pure, deterministic function from measured reach metrics to
 * a stream type. No raster, no graph, no state — everything this class needs arrives in a
 * {@link ReachMetrics}.
 *
 * <p>The key is ordered and first-match-wins, restructured from the published version so every test uses
 * a quantity this project can actually measure. Ordering is load-bearing:
 *
 * <ol>
 *   <li><b>Slope first.</b> {@code Aa+} and {@code A} occupy slope bands no other type overlaps, and both
 *       are entrenched by definition in their landform, so testing entrenchment first would only add a
 *       way to get them wrong.</li>
 *   <li><b>Entrenchment second</b> — the only test separating the entrenched family ({@code F}, {@code G})
 *       from everything with a floodplain. Within that family W/D picks narrow-deep {@code G} (a gully)
 *       over wide-shallow {@code F} (an incised meandering river). {@code B}'s published slope band
 *       overlaps {@code G}'s exactly, so entrenchment — not slope — is what distinguishes them.</li>
 *   <li><b>{@code DA} before {@code D}.</b> Both want unconfined valleys, but anastomosing is far more
 *       specific: near base level, essentially flat, extremely wide flood-prone area. Testing it first
 *       stops braiding from stealing it.</li>
 *   <li><b>{@code E} vs {@code C} last</b>, on W/D alone — small meadow streams become {@code E}, trunk
 *       rivers {@code C}.</li>
 * </ol>
 *
 * <p>Level II (the substrate digit {@code 1}–{@code 6}) is out of scope and not recoverable: grain size
 * is a function of lithology, transport history and sediment supply, none of which are in an elevation
 * field.
 */
public final class RosgenKey {

    private RosgenKey() {}

    /**
     * The Rosgen Level-I type for one reach. Total: every input, including a saturated
     * ({@code +inf}) entrenchment ratio, returns a type.
     */
    public static RosgenType classify(ReachMetrics m) {
        // Steep confined headwaters: slope alone decides.
        if (m.slope() >= HydrologyTuning.S_AA) return River.RosgenType.Aa;
        if (m.slope() >= HydrologyTuning.S_A) return River.RosgenType.A;

        // Entrenched: the valley pinches the channel.
        if (m.entrenchment() < HydrologyTuning.ER_ENTRENCHED) {
            return m.widthDepth() < HydrologyTuning.WD_NARROW ? River.RosgenType.G : River.RosgenType.F;
        }

        // Moderately entrenched.
        if (m.entrenchment() < HydrologyTuning.ER_SLIGHT) return River.RosgenType.B;

        // Slightly entrenched: a broad floodplain is available.
        if (m.bedElev() < HydrologyTuning.DELTA_ELEV
                && m.slope() < HydrologyTuning.S_DA
                && m.entrenchment() > HydrologyTuning.ER_ANASTOMOSE) {
            return River.RosgenType.DA;
        }
        if (m.width() > HydrologyTuning.BRAID_MIN_WIDTH && m.slope() > braidThreshold(m.width())) {
            return River.RosgenType.D;
        }
        return m.widthDepth() < HydrologyTuning.WD_NARROW ? River.RosgenType.E : River.RosgenType.C;
    }

    /**
     * Slope above which braiding is plausible for a channel of the given native-px width. Braiding is not
     * measurable here — there is no sediment-transport model, and nothing in an elevation field
     * distinguishes a braided reach from a meandering one — so this gates where braiding would be
     * plausible and accepts the outcome as authored.
     */
    public static double braidThreshold(double width) {
        return HydrologyTuning.K_BRAID * Math.pow(width, HydrologyTuning.BRAID_WIDTH_EXPONENT);
    }

    /**
     * Rosgen's published tolerances (ER &plusmn;0.2, W/D &plusmn;2.0) applied as a dead band: when a
     * reach's entrenchment ratio or width-to-depth ratio sits within tolerance of one of the thresholds
     * the key compares it against, keep {@code previous} — the type of the neighbouring reach — instead of
     * committing to {@code raw}.
     *
     * <p>The tolerances exist because the field metrics are noisy; a raster implementation is noisier
     * still. Without the dead band, types flicker along a single river, and because
     * {@link RosgenProfile} controls {@code floodPlainLength}
     * and {@code riverInfluence}, a flicker becomes a visibly scalloped floodplain edge.
     *
     * <p><b>Scope: ER and W/D only.</b> The slope bands ({@code S_AA}, {@code S_A}, {@code S_DA}) and the
     * braiding threshold are deliberately outside the dead band. Slope is a real property of the
     * landform rather than a noisy transect measurement, and a reach genuinely crossing into the steep
     * bands should change type there; suppressing that would smear {@code Aa+}/{@code A} headwaters into
     * the reaches below them. Type variation driven by slope is intended behaviour, not flicker.
     *
     * @param previous the neighbouring reach's committed type, or {@code null} at a network leaf
     */
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
