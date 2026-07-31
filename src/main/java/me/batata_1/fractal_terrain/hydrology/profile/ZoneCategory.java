package me.batata_1.fractal_terrain.hydrology.profile;

/**
 * The carve zones a hydrological unit can claim a query point for, <b>declared in descending priority
 * order</b>.
 *
 * <p>A unit does not contribute to every zone whose radius contains the point. It picks exactly one —
 * the innermost zone its profile says the point falls in ({@link HydrologyProfile#categoryAt}) — and
 * contributes only to that zone's distance-weighted average. {@link HydrologyProfileCarver#carvePrefetched}
 * accumulates one such average per zone and then returns the <em>first zone in this declaration order
 * that any unit actually claimed</em>. Zones nobody claimed are skipped, however high they sit here: a
 * pixel a river's floodplain reaches but no bed contains resolves to {@code FLOODPLAIN}, not to an empty
 * {@code BED}.
 *
 * <p>So this order is the answer to "when two different kinds of feature overlap, whose cross-section
 * wins?" — the more specific, more locally dramatic feature outranks the broad blending one, and the
 * order is the single knob controlling that. It is deliberately a flat global ranking rather than
 * per-profile: two units of different types must be comparable, and only a shared total order makes
 * that well-defined.
 *
 * <p>Zones are <b>not</b> required to be nested, and a profile need not define all of them. A profile
 * declares a zone absent by returning {@link HydrologyProfile#NO_ZONE} from
 * {@link HydrologyProfile#zoneRadius}; the default {@code categoryAt} then never selects it. Adding a
 * feature type therefore means adding its zone here, at the priority it should outrank existing zones
 * at, and implementing {@code zoneRadius} for it — no carve-side change.
 *
 * <p><b>Ordinals are not persisted</b> — no serialized format references this enum — so constants may be
 * reordered freely to retune the hierarchy.
 */
public enum ZoneCategory {

    /**
     * The plunge lip and pool of a waterfall. Outranks {@link #BED}: where a waterfall overlaps the
     * channel that feeds it, the drop is the feature worth seeing, and averaging it with the channel bed
     * that runs into it would flatten exactly the discontinuity that makes it a waterfall.
     */
    WATERFALL,

    /** The wetted channel bed — the trench inside the bank-full half-width. */
    BED,

    /**
     * The floor of a standing-water body (lake, oxbow). Below {@link #BED} because where a channel still
     * runs through a lake the flowing bed is the governing cross-section.
     */
    LAKE_BED,

    /** The flat valley floor outside the bed, out to the floodplain half-extent. */
    FLOODPLAIN,

    /**
     * The outer blend band: the valley walls easing the carve back into the ambient terrain. The
     * lowest-priority zone, and the one every unit defines by default, so it is the fallback whenever a
     * unit reaches a pixel but claims nothing more specific.
     */
    INFLUENCE;

    /** {@code values()} without the defensive array copy — this is read on the per-block carve path. */
    public static final ZoneCategory[] BY_PRIORITY = values();

    /** Number of zones; the length of the per-zone accumulator arrays in the carve merge. */
    public static final int COUNT = BY_PRIORITY.length;
}
