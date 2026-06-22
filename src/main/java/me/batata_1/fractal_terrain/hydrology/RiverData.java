package me.batata_1.fractal_terrain.hydrology;

/**
 * Bit layout of the relief tile's {@code riverData} channel (relief channel 7), shared by the writer
 * ({@code ReliefProvider}) and the reader ({@code LocalRiverProvider}) so the two cannot drift.
 *
 * <p>Each pixel is a 32-bit int (stored bit-preserving as a float via {@link Float#intBitsToFloat}):
 *
 * <pre>
 *   bits  0..7  : D8 drainage one-hot (see {@link PipelinePreprocessing#neighbor}, which reads only these)
 *   bits  8..15 : global-river id — 0 = non-global pixel, 1..255 = which global river this pixel belongs to
 *   bits 16..31 : global pixel (id != 0) → position index along that river's spline (0..65535)
 *                 non-global pixel (id == 0) → a slot in the id→width lookup table (below)
 * </pre>
 *
 * <p><b>Width lookup table.</b> Per-pixel data only ever uses bits 0..15, so the upper halfword
 * (bits 16..31) of <i>non-global</i> pixels is free to carry a compact per-tile table. Walking the
 * tile in flattened row-major order and skipping every global pixel (whose upper halfword is a spline
 * position, not table data) yields a stream of slots:
 *
 * <pre>
 *   slot 0      : id count n (low byte)
 *   slot 4k-3   : startWidth[k] high 16 bits   (k = 1..n)
 *   slot 4k-2   : startWidth[k] low  16 bits
 *   slot 4k-1   : endWidth[k]   high 16 bits
 *   slot 4k     : endWidth[k]   low  16 bits
 * </pre>
 *
 * Each width is a {@code float} read as an int and split across two slots; the per-point width of a
 * global river is a lerp between its {@code startWidth} and {@code endWidth}.
 */
public final class RiverData {

    private RiverData() {}

    /** Drainage occupies the low byte (bits 0..7). */
    public static final int DRAINAGE_MASK = 0xFF;
    /** Global-river id occupies the second byte (bits 8..15). */
    public static final int RIVER_ID_SHIFT = 8;

    public static final int RIVER_ID_MASK = 0xFF;
    /** Spline position / table slot occupies the upper halfword (bits 16..31). */
    public static final int UPPER_SHIFT = 16;

    public static final int UPPER_MASK = 0xFFFF;
    /** Largest representable global-river id (the id byte holds 1..255; 0 means "no river"). */
    public static final int MAX_RIVER_ID = 0xFF;

    /** The global-river id packed in a pixel (0 when the pixel is not on a global river). */
    public static int riverId(int packed) {
        return (packed >>> RIVER_ID_SHIFT) & RIVER_ID_MASK;
    }

    /** True when the pixel lies on a global river. */
    public static boolean isGlobalRiver(int packed) {
        return riverId(packed) != 0;
    }

    /** The spline position index packed in a global-river pixel (meaningless when id == 0). */
    public static int splinePosition(int packed) {
        return (packed >>> UPPER_SHIFT) & UPPER_MASK;
    }

    /** The raw upper halfword (bits 16..31) — a table slot value on non-global pixels. */
    public static int upperHalf(int packed) {
        return (packed >>> UPPER_SHIFT) & UPPER_MASK;
    }

    /** Pack a per-pixel value: drainage (bits 0..7), id (bits 8..15), upper halfword (bits 16..31). */
    public static int packPixel(int drainage, int id, int upper) {
        return (drainage & DRAINAGE_MASK)
                | ((id & RIVER_ID_MASK) << RIVER_ID_SHIFT)
                | ((upper & UPPER_MASK) << UPPER_SHIFT);
    }

    /** High 16 bits of a width float (for the high table slot). */
    public static int widthHighHalf(float width) {
        return (Float.floatToIntBits(width) >>> 16) & UPPER_MASK;
    }

    /** Low 16 bits of a width float (for the low table slot). */
    public static int widthLowHalf(float width) {
        return Float.floatToIntBits(width) & UPPER_MASK;
    }

    /** Reassemble a width float from its two 16-bit table slots. */
    public static float widthFromSlots(int highSlot, int lowSlot) {
        return Float.intBitsToFloat(((highSlot & UPPER_MASK) << 16) | (lowSlot & UPPER_MASK));
    }
}
