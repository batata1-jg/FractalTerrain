package me.batata_1.fractal_terrain.relief;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.RELIEF_CHANNELS;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.X;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.Z;
import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.DifferenceOfGaussians;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;

/**
 * Builds final relief tiles ({@code [RELIEF_CHANNELS=7, 512, 512]}). All river handling now lives in
 * {@link LocalRiverProvider}; this provider only decodes the diffusion residual and imports the carved
 * elevation.
 *
 * <p>Channel layout: {@code [0]} elev (carved+filled, imported from {@link LocalRiverProvider})
 * {@code [1]} blurredElev {@code [2]} gradX {@code [3]} gradY {@code [4]} refinedGrad {@code [5]}
 * lowFreqGrad {@code [6]} res — a Difference-of-Gaussians over the decoded (residual) elevation,
 * extracting high-frequency features.
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

    private static final Logger LOG = getLogger(ReliefProvider.class);

    private final NonIntersectingInfiniteTensor final_tiles;

    /** Test-only override for the local river source; {@code null} → use the singleton. */
    @TestOnly
    private @Nullable LocalRiverProvider localRiverOverride;

    public ReliefProvider(String path) {
        final_tiles = new NonIntersectingInfiniteTensor(
                path, "final_relief_tiles", new int[] {RELIEF_CHANNELS, INNER, INNER}, this::buildReliefTile);
    }

    @TestOnly
    public void setLocalRiverProvider(LocalRiverProvider provider) {
        this.localRiverOverride = provider;
    }

    private LocalRiverProvider localRiverProvider() {
        return (localRiverOverride != null) ? localRiverOverride : FractalTerrainInstance.getLocalRiverProvider();
    }

    public NonIntersectingInfiniteTensor getInfiniteTensor() {
        return final_tiles;
    }

    /** The full computed relief tile {@code [RELIEF_CHANNELS, 512, 512]} for tile {@code (tileX, tileZ)}. */
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
        final int pixels = INNER * INNER;

        // ch0: carved+filled elevation imported from the local river pipeline.
        final FloatTensor carved = localRiverProvider().getCarvedElev(x, z);

        // ch1..5 + the DoG source: decode a DoG-haloed slice once.
        final float[][] base = DecoderChannels.decode(x, z, DOG_PAD);
        final float[] dogPadded =
                DifferenceOfGaussians.run(base[0], DOG_PADDED, DOG_PADDED, RES_DOG_SIGMA1, RES_DOG_SIGMA2);

        final float[] entries = new float[RELIEF_CHANNELS * pixels];
        for (int ix = 0; ix < INNER; ix++) {
            for (int iz = 0; iz < INNER; iz++) {
                final int paddedIndex = (DOG_PAD + ix) * DOG_PADDED + (DOG_PAD + iz);
                final int innerIndex = ix * INNER + iz;
                entries[innerIndex] = carved.data[innerIndex]; // ch0
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
            stages.carvedElevation = carved.data;
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

    @TestOnly
    public static final class Stages {
        public float[] carvedElevation;
        public float[][] base;
        public FloatTensor result;
    }
}
