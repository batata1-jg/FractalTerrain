package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.GLOBAL_RIVER_CHANNELS;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.X;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.Z;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;
import static me.batata_1.fractal_terrain.debug.Debug.getLogger;
import static me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing.NEIGHBOR_OFFSET_X;
import static me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing.NEIGHBOR_OFFSET_Z;
import static me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing.OPPOSITE_DIRECTION;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.MarchingSquares;
import me.batata_1.fractal_terrain.math.Skeletonizer;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;

/**
 * Builds the large-scale ("global") river network off the coarse elevation map, one 64×64 coarse-px
 * tile at a time. Each tile is emitted as a {@code [GLOBAL_RIVER_CHANNELS=2, 64, 64]}
 * {@link FloatTensor} (cache-only, since the backing {@link NonIntersectingInfiniteTensor} is built
 * with a {@code null} path in production):
 *
 * <ul>
 *   <li><b>channel 0</b> — a packed arrow bitfield per pixel (see the bit layout below), stored as a
 *       float (integers below 2^24 are represented exactly).
 *   <li><b>channel 1</b> — the river width at that pixel (0 on non-river pixels).
 *   <li><b>channel 2</b> — the river-bed elevation (native-px scale), forced monotonically
 *       non-increasing downstream; consumed by {@code ReliefProvider.carveRiver}.
 * </ul>
 *
 * <p>Arrow bitfield (channel 0), using the neighbour ordering of {@link PipelinePreprocessing}. Routing
 * is <b>D4</b> (cardinal neighbours only): drainage uses
 * {@link PipelinePreprocessing#computeDrainageDirectionCardinal}, so only the cardinal direction bits
 * (4..7) are ever set and every river cell has a single edge-aligned exit. Only the <b>downstream</b>
 * (outgoing) direction is stored — upstream/tributary arrows are derived on demand by scanning a pixel's
 * neighbours (see {@link #ingoingMask(int, int)}), which stays consistent across tile borders and
 * naturally represents the multiple upstream branches at a confluence:
 * <pre>
 *   bits  0..7 : outgoing direction mask — which neighbour the river leaves TOWARD (only 4..7 set under D4)
 *   bit   8    : source — this pixel is a ridge seed (river origin)
 *   bit   9    : coast  — this pixel is a river terminus on the coastline
 *   bit   10   : sink   — (legacy) no longer set; depression filling removed interior sinks
 * </pre>
 *
 * <p>Per-tile pipeline: padded coarse elevation → threshold (upper/lower masks on the RAW elevation)
 * → isolate (an elevation ramp toward non-ocean tile borders, then a depression fill, on a descent-gradient
 * copy only) → ridge mask ({@link Skeletonizer#thin}) + coast mask ({@link MarchingSquares#borderMask}) →
 * per-source steepest-descent D4 walk recording outgoing bits until a coast pixel or border outlet → width
 * from a flow-accumulation proxy → packed result tile. Thresholding before the ramp keeps the coast
 * classification anchored to real elevation; the ramp confines flow inward across land borders while the
 * depression fill guarantees every interior cell drains to an outlet (the ocean borders, left unramped).
 */
public class GlobalRiverProvider {

    // ---- Tuning knobs (edit here during testing) ----------------------------
    /** Upper (ridge) region: pixels with normalized elevation ≥ this become river sources. */
    private static final float RIDGE_THRESHOLD = 20f;
    /** Lower (coast) region: pixels with normalized elevation ≤ this form the coastline. */
    private static final float COAST_THRESHOLD = 0f;
    /** Halo width (coarse px) over which the isolate ramp rises toward the tile border. */
    private static final int RAMP_WIDTH = 6;
    /** Peak height added to border pixels by the isolate ramp (decays linearly to 0 inward). */
    private static final float RAMP_HEIGHT = 5000f;
    /** Maximum descent steps per source before bailing (guards against pathological grids). */
    private static final int MAX_WALK_STEPS = 4 * 64 * 64;

    // ---- Bit layout for the packed arrow channel ----------------------------
    private static final int OUTGOING_SHIFT = 0;
    private static final int SOURCE_BIT = 1 << 8;
    private static final int COAST_BIT = 1 << 9;
    private static final int SINK_BIT = 1 << 10;
    private static final int RIVER_BIT = 1 << 11;

    // ---- Geometry -----------------------------------------------------------
    private static final int TILE_SIZE = 64;
    /** Halo so border pixels have neighbours for the gradient descent; equals the ramp width. */
    private static final int PAD = RAMP_WIDTH;

    private static final int PADDED_SIDE = TILE_SIZE + 2 * PAD;
    private static final Logger LOG = getLogger(GlobalRiverProvider.class);

    private final NonIntersectingInfiniteTensor riverTiles;

    public GlobalRiverProvider(String path) {
        this.riverTiles = new NonIntersectingInfiniteTensor(
                path, "global_river", new int[] {GLOBAL_RIVER_CHANNELS, TILE_SIZE, TILE_SIZE}, this::buildRiverTile);
    }

    public NonIntersectingInfiniteTensor getInfiniteTensor() {
        return riverTiles;
    }

    // ---- Pixel accessors (used by ReliefProvider) ---------------------------

    /** Packed arrow bitfield at global coarse-px {@code (cx, cz)}. */
    public int getArrow(int cx, int cz) {
        return Float.floatToIntBits(riverTiles.getValue(new int[] {0, cx, cz}));
    }

    /** River width at global coarse-px {@code (cx, cz)} (0 on non-river pixels). */
    public float getWidth(int cx, int cz) {
        return riverTiles.getValue(new int[] {1, cx, cz});
    }

    /**
     * River-bed elevation at global coarse-px {@code (cx, cz)}, in native-px scale: the original
     * coarse elevation made monotonically non-increasing downstream along the arrow network. Consumed
     * by {@code ReliefProvider.carveRiver} as the target bed to carve toward.
     */
    public float getElevation(int cx, int cz) {
        return riverTiles.getValue(new int[] {2, cx, cz});
    }

    public static int outgoingMask(int arrow) {
        return (arrow >>> OUTGOING_SHIFT) & 0xFF;
    }

    /**
     * Upstream (ingoing) direction mask at global coarse-px {@code (cx, cz)}, derived by scanning the
     * four cardinal neighbours: a river arrives FROM direction {@code d} when the neighbour in that
     * direction drains back toward this pixel (its outgoing mask has the opposite-direction bit set).
     * Deriving upstream this way — rather than storing it — stays consistent across tile borders and
     * represents every tributary at a confluence (a pixel can have multiple upstream arrows). Only the
     * cardinal directions (4..7) are scanned since drainage is D4.
     */
    public int ingoingMask(int cx, int cz) {
        int mask = 0;
        for (int d = 4; d < 8; d++) {
            final int neighborArrow = getArrow(cx + NEIGHBOR_OFFSET_X[d], cz + NEIGHBOR_OFFSET_Z[d]);
            if ((outgoingMask(neighborArrow) & (1 << OPPOSITE_DIRECTION[d])) != 0) mask |= (1 << d);
        }
        return mask;
    }

    public static boolean isSource(int arrow) {
        return (arrow & SOURCE_BIT) != 0;
    }

    public static boolean isCoast(int arrow) {
        return (arrow & COAST_BIT) != 0;
    }

    public static boolean isSink(int arrow) {
        return (arrow & SINK_BIT) != 0;
    }

    public static boolean isRiver(int arrow) {
        return (arrow & RIVER_BIT) != 0;
    }

    // -------------------------------------------------------------------------
    // Per-tile pipeline
    // -------------------------------------------------------------------------

    /** Production tile compute: passes a {@code null} {@link Stages} so nothing is recorded. */
    private FloatTensor buildRiverTile(TileKey key) {
        return computeTile(key.get(X), key.get(Z), null);
    }

    /**
     * Full per-tile compute. When {@code stages != null} every intermediate artifact is recorded for
     * the debug harness (zero allocation / recording overhead when {@code null}).
     */
    private FloatTensor computeTile(int tx, int tz, @Nullable Stages stages) {
        final int tileOriginCx = tx * TILE_SIZE;
        final int tileOriginCz = tz * TILE_SIZE;

        // 1. slice: padded, weight-normalized coarse elevation.
        final float[] elevation = paddedElevation(tileOriginCx, tileOriginCz);

        // 2. threshold on the RAW elevation (so the coast stays anchored to true elevation).
        final boolean[][] ridgeMask = new boolean[PADDED_SIDE][PADDED_SIDE];
        final boolean[][] lowerMask = new boolean[PADDED_SIDE][PADDED_SIDE];
        for (int pi = 0; pi < PADDED_SIDE; pi++) {
            for (int pj = 0; pj < PADDED_SIDE; pj++) {
                final float value = elevation[pi * PADDED_SIDE + pj];
                ridgeMask[pi][pj] = value >= RIDGE_THRESHOLD;
                lowerMask[pi][pj] = value <= COAST_THRESHOLD;
            }
        }

        // Zero out the outer PAD+1 rows/cols of upperMask so the skeletonizer never thins padded pixels.
        for (int pi = 0; pi < PADDED_SIDE; pi++) {
            for (int pj = 0; pj < PADDED_SIDE; pj++) {
                if (pi < PAD + 1 || pi >= PADDED_SIDE - PAD - 1 || pj < PAD + 1 || pj >= PADDED_SIDE - PAD - 1) {
                    ridgeMask[pi][pj] = false;
                }
            }
        }

        // 3. isolate: a ramp rising toward the border on a COPY used only for the descent gradient,
        //    then a depression fill so every interior cell drains to a border outlet (no interior
        //    sinks). Only this descent copy is affected — the real elevation is left untouched. The
        //    ramp leaves ocean border pixels low, so flow exits the tile through the ocean.
        final float[] rampedElevation = PipelinePreprocessing.fillSinks(applyBorderRamp(elevation), PADDED_SIDE, 0);

        // 4. ridge mask (thinned upper region) + coast mask (border of the lower region).
        //        final boolean[][] ridgeMask = Skeletonizer.thin(upperMask);
        final boolean[][] coastMask = MarchingSquares.borderMask(lowerMask);

        // 5. gradient descent: steepest-descent D4 field over the sink-filled ramped elevation, then a
        //    per-source walk recording outgoing bits along each path until it reaches a coast pixel or a
        //    border outlet. Sink filling guarantees the walk never stalls in an interior depression.
        final float[] uniformWeight = new float[PADDED_SIDE * PADDED_SIDE];
        Arrays.fill(uniformWeight, 1f);
        final int[] drainageDirection =
                PipelinePreprocessing.computeDrainageDirectionCardinal(rampedElevation, uniformWeight, PADDED_SIDE);
        final int[] arrows = new int[PADDED_SIDE * PADDED_SIDE];
        for (int i = 0; i < PADDED_SIDE; i++) {
            for (int j = 0; j < PADDED_SIDE; j++) {
                arrows[i * PADDED_SIDE + j] = coastMask[i][j] ? COAST_BIT : 0;
            }
        }
        final List<List<int[]>> descentPaths = (stages != null) ? new ArrayList<>() : null;
        for (int pi = 0; pi < PADDED_SIDE; pi++) {
            for (int pj = 0; pj < PADDED_SIDE; pj++) {
                if (!ridgeMask[pi][pj]) continue;
                walkFromSource(pi, pj, drainageDirection, arrows, descentPaths);
            }
        }

        // 6. width: flow-accumulation proxy mapped through globalRiverWidth, only on river pixels.
        //    NOTE: flow accumulation comes from the raw steepest-descent field, so cells on a
        //    sink-reroute segment get width from natural flow rather than the rerouted drainage.
        final float[] flowAccumulation = PipelinePreprocessing.computeFlow(drainageDirection, PADDED_SIDE);
        final float[] widths = new float[PADDED_SIDE * PADDED_SIDE];
        for (int px = 0; px < arrows.length; px++) {
            if (arrows[px] != 0) widths[px] = globalRiverWidth(flowAccumulation[px]);
        }

        // 6b. river-bed elevation: original (un-ramped) coarse elevation mapped to native scale and
        //     forced monotonically non-increasing downstream along the arrow network.
        final float[] riverElevation = computeRiverElevation(arrows, elevation);

        // 7. result: crop the central 64×64 into a [3,64,64] tile.
        final FloatTensor tile = new FloatTensor(new int[] {GLOBAL_RIVER_CHANNELS, TILE_SIZE, TILE_SIZE});
        final int pixelsPerChannel = TILE_SIZE * TILE_SIZE;
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int z = 0; z < TILE_SIZE; z++) {
                final int paddedIndex = (PAD + x) * PADDED_SIDE + (PAD + z);
                final int localIndex = x * TILE_SIZE + z;
                tile.data[localIndex] = Float.intBitsToFloat(arrows[paddedIndex]);
                tile.data[pixelsPerChannel + localIndex] = widths[paddedIndex];
                tile.data[2 * pixelsPerChannel + localIndex] = riverElevation[paddedIndex];
            }
        }

        if (stages != null) {
            stages.riverElevation = riverElevation;
            stages.paddedSide = PADDED_SIDE;
            stages.pad = PAD;
            stages.tileOriginCx = tileOriginCx;
            stages.tileOriginCz = tileOriginCz;
            stages.rawElevation = elevation;
            stages.rampedElevation = rampedElevation;
            stages.upperMask = ridgeMask;
            stages.lowerMask = lowerMask;
            stages.ridgeMask = ridgeMask;
            stages.coastMask = coastMask;
            stages.descentPaths = descentPaths;
            stages.arrows = arrows;
            stages.widths = widths;
            stages.tile = tile;
        }
        return tile;
    }

    /**
     * Follow the steepest-descent field from ridge seed {@code (startPi, startPj)} pixel-by-pixel.
     * At each step the exited pixel gets its <b>outgoing</b> bit set (upstream arrows are not stored;
     * they are derived later by scanning neighbours). The seed is flagged {@code source} and a terminal
     * coast pixel is flagged {@code coast}. Bits accumulate (OR) so converging tributaries share pixels.
     * Sink filling on the descent elevation guarantees a downhill neighbour exists until a border outlet
     * is reached, so a {@code -1} direction simply ends the walk.
     */
    private void walkFromSource(
            int startPi, int startPj, int[] drainageDirection, int[] arrows, @Nullable List<List<int[]>> descentPaths) {
        arrows[startPi * PADDED_SIDE + startPj] |= SOURCE_BIT;
        final List<int[]> path = (descentPaths != null) ? new ArrayList<>() : null;
        if (path != null) path.add(new int[] {startPi, startPj});

        int pi = startPi;
        int pj = startPj;
        for (int step = 0; step < MAX_WALK_STEPS; step++) {
            if (step == MAX_WALK_STEPS - 1) LOG.warn("max steps reached in walking from source");
            arrows[pi * PADDED_SIDE + pj] |= RIVER_BIT;
            final int direction = PipelinePreprocessing.neighbor(drainageDirection[pi * PADDED_SIDE + pj]);
            if (direction == -1) break; // reached a border outlet — river ends here.
            final int nextPi = pi + NEIGHBOR_OFFSET_X[direction];
            final int nextPj = pj + NEIGHBOR_OFFSET_Z[direction];
            arrows[pi * PADDED_SIDE + pj] |= (1 << (OUTGOING_SHIFT + direction));
            if (path != null) path.add(new int[] {nextPi, nextPj});
            final int nextIndex = nextPi * PADDED_SIDE + nextPj;
            if (isCoast(arrows[nextIndex])) break;
            // next was already walked: its downstream path (deterministic steepest descent) is already
            // recorded, so stop here instead of re-tracing the shared trunk down to the coast. The
            // outgoing link into next is set above, so this tributary still joins the trunk.
            if ((arrows[nextIndex] & RIVER_BIT) != 0) break;
            pi = nextPi;
            pj = nextPj;
        }
        if (descentPaths != null) descentPaths.add(path);
    }

    /** Pulls the padded coarse slice and normalizes elevation by the blend weight (channel 6). */
    private float[] paddedElevation(int tileOriginCx, int tileOriginCz) {
        final int ci0 = tileOriginCx - PAD;
        final int cj0 = tileOriginCz - PAD;
        final int ci1 = tileOriginCx + TILE_SIZE + PAD;
        final int cj1 = tileOriginCz + TILE_SIZE + PAD;
        final FloatTensor slice = pipeline.getCoarseSlice(ci0, cj0, ci1, cj1);
        final int pixelCount = PADDED_SIDE * PADDED_SIDE;
        final float[] elevation = new float[pixelCount];
        for (int px = 0; px < pixelCount; px++) {
            final float weight = slice.data[6 * pixelCount + px];
            elevation[px] = (weight > 1e-6f) ? slice.data[px] / weight : 0f;
        }
        return elevation;
    }

    /**
     * Return a copy of {@code elevation} with a ramp added that rises toward the padded-grid border:
     * border pixels gain {@link #RAMP_HEIGHT}, decaying linearly to 0 at {@link #RAMP_WIDTH} pixels
     * inward. This pushes steepest-descent flow inward so rivers terminate within the tile rather than
     * spilling across its borders. <b>Ocean pixels ({@code elevation < 0}) are left untouched</b>: the
     * ocean is already a natural border, so flow is allowed to exit the tile through it. The central
     * 64×64 region is left untouched (real elevation).
     */
    private float[] applyBorderRamp(float[] elevation) {
        final float[] ramped = elevation.clone();
        for (int pi = 0; pi < PADDED_SIDE; pi++) {
            for (int pj = 0; pj < PADDED_SIDE; pj++) {
                final int idx = pi * PADDED_SIDE + pj;
                final int distanceToBorder =
                        Math.min(Math.min(pi, pj), Math.min(PADDED_SIDE - 1 - pi, PADDED_SIDE - 1 - pj));
                if (distanceToBorder >= RAMP_WIDTH) continue;
                if (elevation[idx] < 0) continue; // ocean acts as a natural border — don't raise it.
                ramped[idx] += RAMP_HEIGHT * (RAMP_WIDTH - distanceToBorder) / (float) RAMP_WIDTH;
            }
        }
        return ramped;
    }

    /**
     * Map a sqrt-scaled flow-accumulation value to a river width. Kept small and tunable: width grows
     * with upstream drainage, giving large rivers downstream and thin tributaries near the ridges.
     */
    private static float globalRiverWidth(float flowAccumulation) {
        return (float) Math.sqrt(flowAccumulation);
    }

    /** Divisor in the coarse→native elevation scale map ({@code nativeElev = coarseElev² / SCALE}). */
    private static final float ELEV_NATIVE_SCALE = 5f;

    /**
     * Per-pixel river-bed elevation in native-px scale. Each pixel starts at its original coarse
     * elevation mapped to native scale ({@code clamp(e,0,∞)² / ELEV_NATIVE_SCALE} — below-sea pixels
     * clamp to 0 so they don't read as high terrain), then a topological (Kahn) downstream pass over
     * the outgoing-arrow graph forces the value monotonically non-increasing:
     * {@code riverElev[down] = min(riverElev[down], riverElev[up])}. The arrow graph (not the raw
     * drainage field) is used because it includes the {@code marchToCoast} reroute segments. Cells in
     * a cycle (not expected) simply keep their initial value.
     */
    private float[] computeRiverElevation(int[] arrows, float[] originalElevation) {
        final int n = PADDED_SIDE * PADDED_SIDE;
        final float[] riverElev = new float[n];
        for (int i = 0; i < n; i++) {
            final float e = Math.max(0f, originalElevation[i]);
            riverElev[i] = e * e / ELEV_NATIVE_SCALE;
        }

        final int[] inDegree = new int[n];
        for (int cell = 0; cell < n; cell++) {
            final int outgoing = outgoingMask(arrows[cell]);
            for (int d = 0; d < 8; d++) {
                if ((outgoing & (1 << d)) == 0) continue;
                final int down = PipelinePreprocessing.neighborIndex(cell, d, PADDED_SIDE);
                if (down != -1) inDegree[down]++;
            }
        }

        final java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int cell = 0; cell < n; cell++) if (inDegree[cell] == 0) queue.add(cell);
        while (!queue.isEmpty()) {
            final int cell = queue.poll();
            final int outgoing = outgoingMask(arrows[cell]);
            for (int d = 0; d < 8; d++) {
                if ((outgoing & (1 << d)) == 0) continue;
                final int down = PipelinePreprocessing.neighborIndex(cell, d, PADDED_SIDE);
                if (down == -1) continue;
                if (riverElev[cell] < riverElev[down]) riverElev[down] = riverElev[cell];
                if (--inDegree[down] == 0) queue.add(down);
            }
        }

        // rivers should always be lower
        for (int i = 0; i < n; i++) {
            // riverElev[i] -= 50;
            if (riverElev[i] < 0) riverElev[i] = 0;
            riverElev[i] = (float) Math.pow(riverElev[i], 0.9);
        }
        return riverElev;
    }

    // -------------------------------------------------------------------------
    // Debug access (used by GlobalRiverTest to render every intermediate stage)
    // -------------------------------------------------------------------------

    /** Recompute one tile, capturing every intermediate stage for visualization. */
    @TestOnly
    public Stages debugStages(int tx, int tz) {
        final Stages stages = new Stages();
        computeTile(tx, tz, stages);
        return stages;
    }

    /** Snapshot of every intermediate artifact produced while building a single tile. */
    @TestOnly
    public static final class Stages {
        public int paddedSide;
        public int pad;
        public int tileOriginCx;
        public int tileOriginCz;
        public float[] rawElevation;
        public float[] rampedElevation;
        public boolean[][] upperMask;
        public boolean[][] lowerMask;
        public boolean[][] ridgeMask;
        public boolean[][] coastMask;
        public List<List<int[]>> descentPaths;
        public int[] arrows;
        public float[] widths;
        public float[] riverElevation;
        public FloatTensor tile;
    }
}
