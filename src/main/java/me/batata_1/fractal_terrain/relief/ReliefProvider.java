package me.batata_1.fractal_terrain.relief;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.DECODER_CHANNELS;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.RELIEF_CHANNELS;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.X;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.Z;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.GlobalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Builds final relief tiles ({@code [RELIEF_CHANNELS=8, 512, 512]}) by decoding the diffusion
 * residual and carving the global river network into the elevation.
 *
 * <p>Channel layout: {@code [0]} elev (carved) {@code [1]} blurredElev {@code [2]} gradX
 * {@code [3]} gradY {@code [4]} refinedGrad {@code [5]} lowFreqGrad {@code [6]} res
 * {@code [7]} drainageDirection (the D8 steepest-descent bitfield).
 *
 * <p>Per-tile pipeline: {@link #decodeBaseChannels} (padded 514×514) → trace the 2×2 owned coarse
 * cells' global-river arrows into splines by gradient descent over the decoded terrain
 * ({@link #traceRiverSplines}) → carve the elevation toward the global river bed along those splines
 * (recording a carve-band mask) → fill
 * sinks ({@link PipelinePreprocessing#fillSinks}) so drainage has no trapped pits → recompute the
 * drainage direction on the filled (padded) elevation, OR-ing {@link PipelinePreprocessing#GLOBAL_RIVER_BIT}
 * into the carve-band pixels → crop to the inner 512×512. Channel 7 stores the int drainage mask
 * bit-preserving via {@link Float#intBitsToFloat}. All tuning literals live grouped near the top.
 */
public class ReliefProvider {

    // ---- Tuning knobs (edit here during testing) ----------------------------
    /** Max distance (native px) from a river sample at which carving still applies. */
    private static final double MAX_CARVE_DIST = 64.0;
    /** Arc-length spacing (native px) used to densify river splines for the spatial index. */
    private static final double CARVE_SAMPLE_SPACING = 2.0;
    /** Extra incision below the target bed at the river centre: CARVE_DEPTH_SCALE × width (0 = none). */
    private static final double CARVE_DEPTH_SCALE = 0.6;
    /** Pixels within (river half-width + this margin) of a sample are flagged as global-river. */
    private static final double GLOBAL_BAND = 1.0;
    /** Border transition width (px) for the sink-fill blend (keeps tile seams consistent). */
    private static final int FILL_PADDING = 64;

    // ---- Geometry -----------------------------------------------------------
    private static final int INNER = 512;
    private static final int PAD = 1;
    private static final int PADDED = INNER + 2 * PAD; // 514
    private static final int BASE_CHANNELS = DECODER_CHANNELS - 1; // 7 (relief ch0..6)
    /** Native px per coarse cell. */
    private static final int COARSE_PX = 256;
    /** Half a coarse cell — offset from a cell origin to its centre. */
    private static final int COARSE_HALF = COARSE_PX / 2;

    private final NonIntersectingInfiniteTensor final_tiles;

    /** Test-only override for the global river source; {@code null} → use the singleton. */
    @TestOnly
    private @Nullable GlobalRiverProvider globalRiverOverride;

    public ReliefProvider(String path) {
        final_tiles = new NonIntersectingInfiniteTensor(
                path, "final_relief_tiles", new int[] {RELIEF_CHANNELS, INNER, INNER}, this::buildReliefTile);
    }

    @TestOnly
    public void setGlobalRiverProvider(GlobalRiverProvider provider) {
        this.globalRiverOverride = provider;
    }

    private GlobalRiverProvider globalRiverProvider() {
        return (globalRiverOverride != null) ? globalRiverOverride : FractalTerrainInstance.getGlobalRiverProvider();
    }

    public NonIntersectingInfiniteTensor getInfiniteTensor() {
        return final_tiles;
    }

    /**
     * The full computed relief tile {@code [RELIEF_CHANNELS, 512, 512]} covering native px
     * {@code [tileX<<9, (tileX+1)<<9) × [tileZ<<9, (tileZ+1)<<9)}. Used by {@code LocalRiverProvider}
     * to read the whole {@code elev}/{@code drainageDirection} channels at once.
     */
    public FloatTensor getTile(int tileX, int tileZ) {
        return final_tiles.getEntry(new int[] {0, tileX, tileZ});
    }

    // -------------------------------------------------------------------------
    // Per-tile pipeline
    // -------------------------------------------------------------------------

    private FloatTensor buildReliefTile(TileKey key) {
        return computeTile(key.get(X), key.get(Z), null);
    }

    private FloatTensor computeTile(int x, int z, @Nullable Stages stages) {
        // 1. decode the 7 weight-normalized base channels at padded 514×514.
        final float[][] baseChannels = decodeBaseChannels(x, z);

        // 2-3. trace the global river arrows over this tile into splines via gradient descent over the
        //       decoded terrain (control points + widths + bed elevations).
        final List<RiverPolyline> riverSplines = traceRiverSplines(x, z, baseChannels);

        // 4. carve the elevation (base channel 0) toward the global river bed, recording the
        //    carve-band mask (pixels that belong to the global river).
        final float[] carvedElevation = baseChannels[0].clone();
        final boolean[] globalRiverMask = carveRiver(carvedElevation, riverSplines, stages);

        // 5a. fill sinks so drainage has no trapped interior pits (border-blended for seam consistency).
        final float[] filledElevation = PipelinePreprocessing.fillSinks(carvedElevation, PADDED, FILL_PADDING);

        // 5b. drainage direction on the filled padded elevation (uniform weight = already normalized);
        //     then flag global-river pixels with GLOBAL_RIVER_BIT.
        final float[] uniformWeight = new float[PADDED * PADDED];
        Arrays.fill(uniformWeight, 1f);
        final int[] drainageDirection =
                PipelinePreprocessing.computeDrainageDirection(filledElevation, uniformWeight, PADDED);
        for (int i = 0; i < drainageDirection.length; i++) {
            if (globalRiverMask[i]) drainageDirection[i] |= PipelinePreprocessing.GLOBAL_RIVER_BIT;
        }

        // 6. crop to the inner 512×512 and assemble the result tensor.
        final float[] entries = new float[RELIEF_CHANNELS * INNER * INNER];
        for (int ix = 0; ix < INNER; ix++) {
            for (int iz = 0; iz < INNER; iz++) {
                final int paddedIndex = (PAD + ix) * PADDED + (PAD + iz);
                final int innerIndex = ix * INNER + iz;
                entries[innerIndex] = filledElevation[paddedIndex];
                for (int ch = 1; ch < BASE_CHANNELS; ch++) {
                    entries[ch * INNER * INNER + innerIndex] = baseChannels[ch][paddedIndex];
                }
                // ch7 holds the int drainage mask, stored bit-preserving (see LocalRiverProvider).
                entries[7 * INNER * INNER + innerIndex] = Float.intBitsToFloat(drainageDirection[paddedIndex]);
            }
        }

        final FloatTensor result = new FloatTensor(entries, new int[] {RELIEF_CHANNELS, INNER, INNER});
        if (stages != null) {
            stages.baseChannels = baseChannels;
            stages.riverSplines = riverSplines;
            stages.carvedElevation = carvedElevation;
            stages.filledElevation = filledElevation;
            final float[] drainageAsFloat = new float[drainageDirection.length];
            for (int i = 0; i < drainageDirection.length; i++) drainageAsFloat[i] = drainageDirection[i];
            stages.drainageDirection = drainageAsFloat;
            stages.result = result;
        }
        return result;
    }

    /**
     * Fetch a +1-pixel-halo decoder slice ({@code [DECODER_CHANNELS=8, 514, 514]}) and weight-normalize
     * it into the 7 relief base channels at padded resolution. Decoder channel 0 is the blend weight;
     * channels 1..7 are the real outputs, each divided by the weight (guarded) and shifted down by one
     * so relief channel {@code c-1} = decoder channel {@code c} / weight.
     *
     * @return {@code baseChannels[c][paddedIndex]} for {@code c} in 0..6, each {@code 514*514} long.
     */
    public float[][] decodeBaseChannels(int x, int z) {
        final FloatTensor fineSlice =
                pipeline.getDecoderSlice((x << 9) - PAD, (z << 9) - PAD, ((x + 1) << 9) + PAD, ((z + 1) << 9) + PAD);
        final int pixelCount = PADDED * PADDED;
        final float[][] baseChannels = new float[BASE_CHANNELS][pixelCount];
        for (int px = 0; px < pixelCount; px++) {
            final float weight = fineSlice.data[px];
            final float inverse = (weight > 1e-6f) ? 1f / weight : 0f;
            for (int c = 1; c < DECODER_CHANNELS; c++) {
                baseChannels[c - 1][px] = fineSlice.data[c * pixelCount + px] * inverse;
            }
        }
        return baseChannels;
    }

    // -------------------------------------------------------------------------
    // River spline tracing (step 2-3)
    // -------------------------------------------------------------------------

    /**
     * One traced global-river branch: ordered control points (padded px) with per-vertex widths and
     * bed elevations (native-px scale, from {@code GlobalRiverProvider.getElevation}).
     */
    public static final class RiverPolyline {
        final ArrayList<double[]> controlPoints = new ArrayList<>();
        final ArrayList<Double> widths = new ArrayList<>();
        final ArrayList<Double> bedElevations = new ArrayList<>();
        QuinticHermiteSpline spline;
    }

    // ---- Gradient-descent tuning knobs --------------------------------------
    /** Arc-length step (padded px) taken per gradient-descent iteration inside a cell. */
    private static final double DESCENT_STEP_SIZE = 2.0;
    /** Hard cap on descent steps per stream (a path can't be longer than ~2 cell widths). */
    private static final int DESCENT_MAX_STEPS = COARSE_PX * 2;
    /** Inward push magnitude of the cell-border "wall" that confines a descent to its own cell. */
    private static final double WALL_STRENGTH = 4.0;
    /** Distance (px) over which the steer-to-exit weight ramps from {@link #MIN_STEER} up to 1. */
    private static final double STEER_RADIUS = COARSE_PX;
    /** Baseline steer-to-exit weight applied everywhere (so a path always trends to its exit). */
    private static final double MIN_STEER = 0.2;
    /** Gain on the (unit) steer-to-exit vector; &gt;1 so it overpowers the wall near the exit. */
    private static final double STEER_GAIN = 2.0;
    /** Gain on the (unit) downhill vector. */
    private static final double DOWN_GAIN = 1.0;
    /** Radius (px) at which a descending stream snaps onto an existing in-cell stream (junction). */
    private static final double MERGE_RADIUS = 2.0;

    /**
     * Build the river polylines covering relief tile {@code (tileX, tileZ)}. The tile owns a 2×2 block
     * of coarse cells; for each river cell we (a) find its <b>exit point</b> on the cardinal-arrow edge
     * (the edge pixel whose decoded elevation is closest to the cell's coarse bed), (b) gather descent
     * <b>origins</b> — the exit points of upstream neighbour cells, or a deterministic random interior
     * seed when the cell is a source — and (c) trace each origin to the exit via gradient descent over
     * the decoded terrain ({@code gradX = baseChannels[2]}, {@code gradZ = baseChannels[3]}), confined to
     * the cell by a border "wall" and steered toward the exit. Streams that meet inside a cell merge at a
     * junction. Each traced path becomes a Catmull-Rom {@link RiverPolyline} with decoded-tracking,
     * monotonically non-increasing bed elevations.
     */
    // TODO: rewrite this
    private List<RiverPolyline> traceRiverSplines(int tileX, int tileZ, float[][] baseChannels) {
        return null;
    }

    /**
     * The exit point (padded px) of coarse cell {@code (ccx, ccz)} on the edge toward cardinal direction
     * {@code dir} (4..7): the edge pixel whose decoded elevation ({@code elev}) is closest to the cell's
     * coarse bed {@code target}. Only the shared-edge pixel line is read, so both tiles flanking the edge
     * compute the same point (seam-deterministic).
     */
    private double[] exitPoint(int ccx, int ccz, int dir, int tileX, int tileZ, float[] elev, double target) {
        final int minXi = PAD + (ccx - tileX * 2) * COARSE_PX;
        final int minZi = PAD + (ccz - tileZ * 2) * COARSE_PX;
        final int maxXi = minXi + COARSE_PX;
        final int maxZi = minZi + COARSE_PX;

        int bestX = -1;
        int bestZ = -1;
        double bestDiff = Double.MAX_VALUE;
        if (dir == 4 || dir == 5) { // z-edge → iterate over x
            final int lineZ = (dir == 4) ? minZi : maxZi;
            if (lineZ >= 0 && lineZ < PADDED) {
                for (int xi = Math.max(0, minXi); xi < Math.min(PADDED, maxXi); xi++) {
                    final double diff = Math.abs(elev[xi * PADDED + lineZ] - target);
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        bestX = xi;
                        bestZ = lineZ;
                    }
                }
            }
        } else { // dir 6 or 7 → x-edge → iterate over z
            final int lineX = (dir == 6) ? minXi : maxXi;
            if (lineX >= 0 && lineX < PADDED) {
                for (int zi = Math.max(0, minZi); zi < Math.min(PADDED, maxZi); zi++) {
                    final double diff = Math.abs(elev[lineX * PADDED + zi] - target);
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        bestX = lineX;
                        bestZ = zi;
                    }
                }
            }
        }
        if (bestX < 0) return new double[] {(minXi + maxXi) / 2.0, (minZi + maxZi) / 2.0};
        return new double[] {bestX, bestZ};
    }

    /** A deterministic, interior-biased seed point (padded px) for a source cell, keyed by its coords. */
    private double[] sourceSeed(int ccx, int ccz, double minX, double minZ) {
        final java.util.Random rng =
                new java.util.Random(((long) ccx * 0x9E3779B97F4A7C15L) ^ ((long) ccz * 0xC2B2AE3D27D4EB4FL));
        final double rx = (rng.nextDouble() - rng.nextDouble()) * (COARSE_HALF * 0.7);
        final double rz = (rng.nextDouble() - rng.nextDouble()) * (COARSE_HALF * 0.7);
        return new double[] {minX + COARSE_HALF + rx, minZ + COARSE_HALF + rz};
    }

    /**
     * Gradient-descend from {@code start} to {@code exit} inside a cell. Each step blends the downhill
     * direction (−∇ of the decoded elevation, from {@code gradX}/{@code gradZ}), an inward "wall" push
     * (within {@code wallBand} of any cell edge) that keeps the path from bleeding into neighbour cells,
     * and a steer-to-exit vector whose weight grows as the exit nears. If the path reaches a point
     * already laid down by an earlier stream in {@code occupancy} it snaps there and stops (a junction).
     */
    private List<double[]> descendInCell(
            double[] start,
            double[] exit,
            float[] gradX,
            float[] gradZ,
            double minX,
            double minZ,
            double maxX,
            double maxZ,
            double wallBand,
            QuadTree<QuadTreePoint> occupancy) {
        final List<double[]> path = new ArrayList<>();
        double px = clamp(start[0], minX, maxX);
        double pz = clamp(start[1], minZ, maxZ);
        path.add(new double[] {px, pz});

        for (int step = 0; step < DESCENT_MAX_STEPS; step++) {
            final double dx = exit[0] - px;
            final double dz = exit[1] - pz;
            final double distToExit = Math.sqrt(dx * dx + dz * dz);
            if (distToExit <= DESCENT_STEP_SIZE) {
                path.add(new double[] {exit[0], exit[1]});
                break;
            }
            final double sx = dx / distToExit;
            final double sz = dz / distToExit;

            final double[] downhill = normalizeOrZero(-sampleBilinear(gradX, px, pz), -sampleBilinear(gradZ, px, pz));
            final double[] wall = wallVector(px, pz, minX, minZ, maxX, maxZ, wallBand);
            final double w = clamp(1.0 - distToExit / STEER_RADIUS, MIN_STEER, 1.0);

            double mx = (1 - w) * (downhill[0] * DOWN_GAIN + wall[0]) + w * STEER_GAIN * sx;
            double mz = (1 - w) * (downhill[1] * DOWN_GAIN + wall[1]) + w * STEER_GAIN * sz;
            double mlen = Math.sqrt(mx * mx + mz * mz);
            if (mlen < 1e-9) {
                mx = sx;
                mz = sz;
                mlen = 1.0;
            }
            double npx = clamp(px + mx / mlen * DESCENT_STEP_SIZE, minX, maxX);
            double npz = clamp(pz + mz / mlen * DESCENT_STEP_SIZE, minZ, maxZ);

            // Junction: snap onto an earlier stream in this cell if we collided with it.
            if (occupancy.numPoints() > 0) {
                final List<QuadTreePoint> near = occupancy.getPointsInCircle(new double[] {npx, npz}, MERGE_RADIUS);
                if (!near.isEmpty()) {
                    final double[] j = nearestOf(near, npx, npz);
                    path.add(new double[] {j[0], j[1]});
                    break;
                }
            }

            // Stalled against a wall: nudge straight toward the exit; if still stuck, finish at the exit.
            if (Math.abs(npx - px) < 1e-6 && Math.abs(npz - pz) < 1e-6) {
                npx = clamp(px + sx * DESCENT_STEP_SIZE, minX, maxX);
                npz = clamp(pz + sz * DESCENT_STEP_SIZE, minZ, maxZ);
                if (Math.abs(npx - px) < 1e-6 && Math.abs(npz - pz) < 1e-6) {
                    path.add(new double[] {exit[0], exit[1]});
                    break;
                }
            }
            px = npx;
            pz = npz;
            path.add(new double[] {px, pz});
        }
        return path;
    }

    /** Sum of inward pushes from each cell edge within {@code band} px (0 when {@code band <= 0}). */
    private static double[] wallVector(
            double px, double pz, double minX, double minZ, double maxX, double maxZ, double band) {
        if (band <= 0) return new double[] {0, 0};
        double wx = 0;
        double wz = 0;
        final double dl = px - minX;
        if (dl < band) wx += WALL_STRENGTH * (1 - dl / band);
        final double dr = maxX - px;
        if (dr < band) wx -= WALL_STRENGTH * (1 - dr / band);
        final double db = pz - minZ;
        if (db < band) wz += WALL_STRENGTH * (1 - db / band);
        final double dt = maxZ - pz;
        if (dt < band) wz -= WALL_STRENGTH * (1 - dt / band);
        return new double[] {wx, wz};
    }

    private static double[] nearestOf(List<QuadTreePoint> pts, double px, double pz) {
        double[] best = null;
        double bestD = Double.MAX_VALUE;
        for (QuadTreePoint p : pts) {
            final double d = VectorOps.distanceSquared(new double[] {px, pz}, p.toArray());
            if (d < bestD) {
                bestD = d;
                best = p.toArray();
            }
        }
        return best;
    }

    private static double[] normalizeOrZero(double x, double z) {
        final double len = Math.sqrt(x * x + z * z);
        return (len < 1e-9) ? new double[] {0, 0} : new double[] {x / len, z / len};
    }

    private static double clamp(double v, double lo, double hi) {
        return (v < lo) ? lo : (v > hi) ? hi : v;
    }

    /** Bilinear sample of a padded {@code PADDED×PADDED} field (row-major {@code x*PADDED + z}). */
    private static double sampleBilinear(float[] field, double px, double pz) {
        int x0 = (int) Math.floor(px);
        int z0 = (int) Math.floor(pz);
        final double fx = px - x0;
        final double fz = pz - z0;
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        x0 = Math.max(0, Math.min(PADDED - 1, x0));
        x1 = Math.max(0, Math.min(PADDED - 1, x1));
        z0 = Math.max(0, Math.min(PADDED - 1, z0));
        z1 = Math.max(0, Math.min(PADDED - 1, z1));
        final double v0 = field[x0 * PADDED + z0] * (1 - fz) + field[x0 * PADDED + z1] * fz;
        final double v1 = field[x1 * PADDED + z0] * (1 - fz) + field[x1 * PADDED + z1] * fz;
        return v0 * (1 - fx) + v1 * fx;
    }

    // -------------------------------------------------------------------------
    // River carving (step 4)
    // -------------------------------------------------------------------------

    /** A densified river sample carrying its interpolated width + bed elevation, indexed in the carve QuadTree. */
    private static final class RiverSample extends QuadTreePoint {
        final double width;
        final double bedElev;

        RiverSample(double px, double pz, double width, double bedElev) {
            super(new double[] {px, pz});
            this.width = width;
            this.bedElev = bedElev;
        }
    }

    /**
     * Carve {@code elevation} (padded 514×514) toward the global river bed along the splines. Each
     * spline is densified into sample points (width and bed elevation lerped between adjacent control
     * points) and inserted into a QuadTree. Each pixel queries the nearest sample (within the
     * {@link #MAX_CARVE_DIST} query cap) and is pulled <b>down toward</b> the river-bed elevation there,
     * forming a tight bank: within {@code margin = width/2} the floor is {@code bedElev}; from
     * {@code margin} out to {@code marginInfluence = width + 1} the result lerps linearly from
     * {@code bedElev} back to the original elevation; beyond {@code marginInfluence} the pixel is left
     * untouched (never raised — only lowered).
     *
     * @return a padded {@code boolean[]} flagging pixels inside the carve band (within
     *     {@code margin + }{@link #GLOBAL_BAND}) — i.e. those that belong to the global river.
     */
    private boolean[] carveRiver(float[] elevation, List<RiverPolyline> riverSplines, @Nullable Stages stages) {
        final boolean[] globalRiverMask = new boolean[PADDED * PADDED];
        if (riverSplines.isEmpty()) return globalRiverMask;

        final QuadTree<RiverSample> index = new QuadTree<>(
                new double[] {-COARSE_PX * 4, -COARSE_PX * 4},
                new double[] {PADDED + COARSE_PX * 4, PADDED + COARSE_PX * 4});
        for (RiverPolyline polyline : riverSplines) {
            if (polyline.controlPoints.size() < 2) continue;
            final QuinticHermiteSpline spline = polyline.spline;
            final int segments = polyline.controlPoints.size() - 1;
            for (int i = 0; i < segments; i++) {
                final double segLength =
                        VectorOps.distance(polyline.controlPoints.get(i), polyline.controlPoints.get(i + 1));
                final int sampleCount = Math.max(1, (int) Math.ceil(segLength / CARVE_SAMPLE_SPACING));
                final double startWidth = polyline.widths.get(i);
                final double endWidth = polyline.widths.get(i + 1);
                final double startBed = polyline.bedElevations.get(i);
                final double endBed = polyline.bedElevations.get(i + 1);
                for (int s = 0; s <= sampleCount; s++) {
                    final double frac = (double) s / sampleCount;
                    final double[] point = spline.sample(i + frac);
                    final double width = startWidth + (endWidth - startWidth) * frac;
                    final double bed = startBed + (endBed - startBed) * frac;
                    index.insertPoint(new RiverSample(point[0], point[1], width, bed));
                }
            }
        }

        final float[] carveDepthField = (stages != null) ? new float[PADDED * PADDED] : null;
        for (int pi = 0; pi < PADDED; pi++) {
            for (int pj = 0; pj < PADDED; pj++) {
                final int idx = pi * PADDED + pj;
                if (elevation[idx] < 0) continue;
                final double[] pixel = {pi, pj};
                final List<RiverSample> nearby = index.getPointsInCircle(pixel, MAX_CARVE_DIST);
                if (nearby.isEmpty()) continue;
                double nearestDist = Double.MAX_VALUE;
                double width = 0;
                double bedElev = 0;
                for (RiverSample sample : nearby) {
                    final double dist = VectorOps.distance(pixel, sample.toArray());
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        width = sample.width;
                        bedElev = sample.bedElev;
                    }
                }
                // MAX_CARVE_DIST is only the QuadTree query/safety cap now; the actual carve influence
                // ends at marginInfluence (rivers wider than ~2*MAX_CARVE_DIST clip at the cap).
                if (nearestDist >= MAX_CARVE_DIST) continue;

                final double margin = width * 0.5;
                final double marginInfluence = 2 * width;
                if (nearestDist <= margin + GLOBAL_BAND) globalRiverMask[idx] = true;

                // Beyond marginInfluence the terrain is untouched; carve only within [0, marginInfluence].
                if (nearestDist >= marginInfluence) continue;

                final float orig = elevation[idx];
                final double centreFloor = bedElev;
                final double frac = (nearestDist <= margin)
                        ? 0.0
                        : Math.min(1.0, (nearestDist - margin) / (marginInfluence - margin));
                final float carved = (float) (centreFloor + (orig - centreFloor) * frac);
                elevation[idx] = carved;
                if (carveDepthField != null) carveDepthField[idx] = orig - carved;
            }
        }
        if (stages != null) stages.carveDepthField = carveDepthField;
        return globalRiverMask;
    }

    // -------------------------------------------------------------------------
    // Pixel accessors
    // -------------------------------------------------------------------------

    public Float get_entry(final int[] mutableCoords, final int ch) {
        mutableCoords[FractalTerrainConfig.CH] = ch;
        return final_tiles.getValue(mutableCoords);
    }

    public Float getElev(int[] xz) {
        return get_entry(xz, 0);
    }

    public Float getBlurredElev(final int[] xz) {
        return get_entry(xz, 1);
    }

    public Float getGradX(final int[] xz) {
        return get_entry(xz, 2);
    }

    public Float getGradY(final int[] xz) {
        return get_entry(xz, 3);
    }

    public Float getRefinedGrad(final int[] xz) {
        return get_entry(xz, 4);
    }

    public Float getLowFreqGrad(final int[] xz) {
        return get_entry(xz, 5);
    }

    public Float getRes(final int[] xz) {
        return get_entry(xz, 6);
    }

    public Float getDrainageDirection(final int[] xz) {
        return get_entry(xz, 7);
    }

    public double getContinentalElev(final int[] xz) {
        return 0;
    }

    public double getRawTemp(final int[] xz) {
        return 0;
    }

    public Float getRawTempSTD(final int[] xz) {
        return (float) 0;
    }

    public double getRawPrecip(final int[] xz) {
        return 0;
    }

    public Float getRawPrecipSTD(final int[] xz) {
        return (float) 0;
    }

    public int getRawGrad(final int[] xz) {
        return 0;
    }

    public double getBlurredGrad(final int[] xz) {
        return 0;
    }

    // -------------------------------------------------------------------------
    // Debug access
    // -------------------------------------------------------------------------

    @TestOnly
    public Stages debugStages(int x, int z) {
        final Stages stages = new Stages();
        computeTile(x, z, stages);
        return stages;
    }

    /**
     * The densified river-spline sample points (padded px) traced for {@code stages}. Mirrors
     * {@link #carveRiver}'s sampling so a rasterization of these points matches what gets carved.
     * {@code RiverPolyline} is private, so callers (the debug tests) get plain {@code [x, z]} arrays.
     */
    @TestOnly
    public List<double[]> debugRiverSplinePoints(Stages stages) {
        final List<double[]> points = new ArrayList<>();
        final List<RiverPolyline> riverSplines = stages.riverSplines;
        if (riverSplines == null) return points;
        for (RiverPolyline polyline : riverSplines) {
            if (polyline.controlPoints.size() < 2) continue;
            final QuinticHermiteSpline spline = polyline.spline;
            final int segments = polyline.controlPoints.size() - 1;
            for (int i = 0; i < segments; i++) {
                final double segLength =
                        VectorOps.distance(polyline.controlPoints.get(i), polyline.controlPoints.get(i + 1));
                final int sampleCount = Math.max(1, (int) Math.ceil(segLength / CARVE_SAMPLE_SPACING));
                for (int s = 0; s <= sampleCount; s++) {
                    points.add(spline.sample(i + (double) s / sampleCount));
                }
            }
        }
        return points;
    }

    @TestOnly
    public static final class Stages {
        public float[][] baseChannels;
        public List<RiverPolyline> riverSplines;
        public float[] carveDepthField;
        public float[] carvedElevation;
        public float[] filledElevation;
        public float[] drainageDirection;
        public FloatTensor result;
    }
}
