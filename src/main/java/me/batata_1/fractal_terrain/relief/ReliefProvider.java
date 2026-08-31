package me.batata_1.fractal_terrain.relief;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.RELIEF_CHANNELS;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.X;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.Z;
import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.Arrays;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.providers.RiverProvider;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.DifferenceOfGaussians;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;

/**
 * Builds final relief tiles ({@code [RELIEF_CHANNELS=7, 512, 512]}). River handling lives in
 * {@link RiverProvider}, which traces and carves off its own decode of the same diffusion residual;
 * this provider imports that carved elevation as channel 0 and decodes the residual itself for every
 * other channel, so the published relief carries the same cut the hydrological primitives were stamped
 * along.
 *
 * <p>Channel layout: {@code [0]} elev (river-carved, from {@link RiverProvider}) {@code [1]} blurredElev
 * {@code [2]} gradX {@code [3]} gradY {@code [4]} refinedGrad {@code [5]} lowFreqGrad {@code [6]} res —
 * a Difference-of-Gaussians over the raw decoded elevation, so the high-frequency band is free of the carve.
 */
public class ReliefProvider {

    // ---- Geometry / tuning --------------------------------------------------
    private static final int INNER = 512;
    /** DoG band-pass sigmas for the high-frequency res channel. */
    private static final double RES_DOG_SIGMA1 = 0.0;

    private static final double RES_DOG_SIGMA2 = 4.0;
    /** Halo (px) needed so the cropped DoG is free of border artifacts. */
    private static final int DOG_PAD = DifferenceOfGaussians.padFor(RES_DOG_SIGMA1, RES_DOG_SIGMA2);

    private static final int DOG_PADDED = INNER + 2 * DOG_PAD;

    /** Cached relief tiles. Far above the 1-4 tile working set (a tile is 160 chunks per axis) because
     *  Storage's byte accounting is FIFO, not LRU: a tile read every chunk still ages out. */
    private static final int MAX_CACHED_TILES = 8;

    private static final long CACHE_LIMIT_BYTES =
            (long) MAX_CACHED_TILES * RELIEF_CHANNELS * INNER * INNER * Float.BYTES;

    private static final Logger LOG = getLogger(ReliefProvider.class);

    private final NonIntersectingInfiniteTensor finalTiles;

    public ReliefProvider(String path) {
        finalTiles = new NonIntersectingInfiniteTensor(
                path,
                "final_relief_tiles",
                new int[] {RELIEF_CHANNELS, INNER, INNER},
                this::buildReliefTile,
                CACHE_LIMIT_BYTES);
    }

    public NonIntersectingInfiniteTensor getInfiniteTensor() {
        return finalTiles;
    }

    /** The full computed relief tile {@code [RELIEF_CHANNELS, 512, 512]} for tile {@code (tileX, tileZ)}. */
    public FloatTensor getReliefTile(int tileX, int tileZ) {
        return finalTiles.getEntry(new int[] {0, tileX, tileZ});
    }

    // -------------------------------------------------------------------------
    // Per-tile pipeline
    // -------------------------------------------------------------------------

    private FloatTensor buildReliefTile(TileKey key) {
        return computeTile(key.get(X), key.get(Z), null);
    }

    private FloatTensor computeTile(int x, int z, @Nullable Stages stages) {
        final int pixels = INNER * INNER;

        // ch0..5 + the DoG source: decode a DoG-haloed slice once.
        final float[][] base = DecoderChannels.decode(x, z, DOG_PAD);
        final float[] dogPadded =
                DifferenceOfGaussians.run(base[0], DOG_PADDED, DOG_PADDED, RES_DOG_SIGMA1, RES_DOG_SIGMA2);

        // Inner-cropped 512x512 already, so it indexes by innerIndex rather than paddedIndex.
        final float[] carvedElev = FractalTerrainInstance.getRiverProvider().getCarvedElevationTile(x, z).data;

        final float[] entries = new float[RELIEF_CHANNELS * pixels];
        for (int ix = 0; ix < INNER; ix++) {
            for (int iz = 0; iz < INNER; iz++) {
                final int paddedIndex = (DOG_PAD + ix) * DOG_PADDED + (DOG_PAD + iz);
                final int innerIndex = ix * INNER + iz;
                entries[innerIndex] = carvedElev[innerIndex]; // ch0 = river-carved elev
                entries[1 * pixels + innerIndex] = base[1][paddedIndex]; // blurredElev
                entries[2 * pixels + innerIndex] = base[2][paddedIndex]; // gradX
                entries[3 * pixels + innerIndex] = base[3][paddedIndex]; // gradY
                entries[4 * pixels + innerIndex] = base[4][paddedIndex]; // refinedGrad
                entries[5 * pixels + innerIndex] = base[5][paddedIndex]; // lowFreqGrad
                entries[6 * pixels + innerIndex] = dogPadded[paddedIndex]; // res = DoG(residual elev)
            }
        }

        final FloatTensor result = new FloatTensor(entries, new int[] {RELIEF_CHANNELS, INNER, INNER});
        if (stages != null) {
            stages.carvedElevation = Arrays.copyOfRange(entries, 0, pixels);
            stages.base = base;
            stages.result = result;
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Pixel accessors
    // -------------------------------------------------------------------------

    public Float get_entry(final int[] mutableCoords, final int ch) {
        mutableCoords[FractalTerrainConfig.CH] = ch;
        return finalTiles.getValue(mutableCoords);
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

    // -------------------------------------------------------------------------
    // Debug access
    // -------------------------------------------------------------------------

    @TestOnly
    public Stages debugStages(int x, int z) {
        final Stages stages = new Stages();
        computeTile(x, z, stages);
        return stages;
    }

    @TestOnly
    public static final class Stages {
        public float[] carvedElevation;
        public float[][] base;
        public FloatTensor result;
    }
}
