package me.batata_1.fractal_terrain.hydrology.profile;

/**
 * The carve zones a primitive can claim a point for, <b>declared in descending priority order</b>.
 *
 * <p>The order answers "when two feature types overlap, whose cross-section wins?" — one flat global
 * ranking rather than per-profile, since primitives of different types must be comparable.
 *
 * <p>Zones need not be nested and a profile need not define them all, so adding a feature type means
 * adding its zone here at the right priority. Ordinals are not persisted, so constants may be reordered
 * freely to retune the hierarchy.
 */
public enum ZoneCategory {

    /** Outranks {@link #BED}, so a waterfall's drop is not flattened by the channel feeding it. */
    WATERFALL,

    /** The wetted channel bed — the trench inside the bank-full half-width. */
    BED,

    /** Below {@link #BED}: where a channel runs through a lake, the flowing bed governs. */
    LAKE_BED,

    /** The flat valley floor outside the bed, out to the floodplain half-extent. */
    FLOODPLAIN,

    /** The outer blend band; lowest priority, and every primitive's default fallback zone. */
    INFLUENCE;

    /** {@code values()} without the defensive array copy — this is read on the per-block carve path. */
    public static final ZoneCategory[] BY_PRIORITY = values();

    /** Number of zones; the length of the per-zone accumulator arrays in the carve merge. */
    public static final int COUNT = BY_PRIORITY.length;
}
