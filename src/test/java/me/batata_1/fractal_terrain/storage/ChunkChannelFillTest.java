package me.batata_1.fractal_terrain.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.Interpolation;
import org.junit.jupiter.api.Test;

/**
 * The equivalence gate for the slice-based chunk fill: same floats and the same set of tiles
 * materialised as the per-pixel path. Runs on a synthetic in-memory tensor, so no model, no ONNX and
 * no world are involved — {@code ChunkPos} is avoided for the same reason.
 */
class ChunkChannelFillTest {

    private static final float SCALE = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
    private static final int CHANNELS = 3;
    private static final int TILE = 512;

    /** Chunk origins covering a tile interior, both tile-boundary crossings, and negative coordinates. */
    private static final int[][] CHUNK_ORIGINS = {
        {0, 0}, {160, 320}, {2544, 16}, {2540, 2540}, {2544, 2544}, {-16, -16}, {-2560, -2544}, {-2576, 48}
    };

    private static float cell(int ch, int gx, int gz) {
        return (float) (Math.sin(gx * 0.031 + ch) * 40.0 + Math.cos(gz * 0.017 - ch) * 25.0);
    }

    private static NonIntersectingInfiniteTensor tensor(Set<TileKey> built) {
        return new NonIntersectingInfiniteTensor(null, "synthetic", new int[] {CHANNELS, TILE, TILE}, key -> {
            built.add(key);
            final int baseX = key.get(1) * TILE;
            final int baseZ = key.get(2) * TILE;
            final float[] entries = new float[CHANNELS * TILE * TILE];
            for (int ch = 0; ch < CHANNELS; ch++) {
                for (int ix = 0; ix < TILE; ix++) {
                    for (int iz = 0; iz < TILE; iz++) {
                        entries[(ch * TILE + ix) * TILE + iz] = cell(ch, baseX + ix, baseZ + iz);
                    }
                }
            }
            return new FloatTensor(entries, new int[] {CHANNELS, TILE, TILE});
        });
    }

    /** The path this change replaces: four {@code getValue} corner reads per pixel. */
    private static float[] legacyFill(
            NonIntersectingInfiniteTensor t, int channel, int startX, int startZ, boolean smooth) {
        final float[] out = new float[1 << 8];
        final int[] coords = new int[3];
        final float[] nodes = new float[4];
        final Function<int[], Float> source = p -> {
            p[0] = channel;
            return t.getValue(p);
        };
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final float px = (dx + startX) / SCALE;
                final float pz = (dz + startZ) / SCALE;
                out[(dx << 4) + dz] = (float)
                        (smooth
                                ? Interpolation.interpolateSmoothStep(px, pz, coords, nodes, source)
                                : Interpolation.interpolateBilinear(px, pz, coords, nodes, source));
            }
        }
        return out;
    }

    private static float[] newFill(
            NonIntersectingInfiniteTensor t, int channel, int startX, int startZ, boolean smooth) {
        final ChunkChannelFill.ChunkWindow w =
                ChunkChannelFill.open(t, channel, startX, startZ, startX + 15, startZ + 15);
        final float[] out = new float[1 << 8];
        for (int dx = 0; dx < 16; dx++) {
            final float px = (dx + startX) / SCALE;
            for (int dz = 0; dz < 16; dz++) {
                final float pz = (dz + startZ) / SCALE;
                out[(dx << 4) + dz] = (float)
                        (smooth
                                ? Interpolation.sampleWindowSmoothStep(
                                        w.data(), px, pz, w.originX(), w.originZ(), w.rowStride())
                                : Interpolation.sampleWindowBilinear(
                                        w.data(), px, pz, w.originX(), w.originZ(), w.rowStride()));
            }
        }
        return out;
    }

    @Test
    void windowFillIsBitIdenticalToThePerPixelPath() {
        for (int ch = 0; ch < CHANNELS; ch++) {
            for (int[] origin : CHUNK_ORIGINS) {
                for (boolean smooth : new boolean[] {false, true}) {
                    final float[] legacy = legacyFill(tensor(new LinkedHashSet<>()), ch, origin[0], origin[1], smooth);
                    final float[] fresh = newFill(tensor(new LinkedHashSet<>()), ch, origin[0], origin[1], smooth);
                    for (int i = 0; i < legacy.length; i++) {
                        assertEquals(
                                legacy[i],
                                fresh[i],
                                0.0f,
                                "ch " + ch + " chunk (" + origin[0] + "," + origin[1] + ") smooth=" + smooth + " i="
                                        + i);
                    }
                }
            }
        }
    }

    @Test
    void windowFillTouchesExactlyTheTilesThePerPixelPathTouches() {
        for (int[] origin : CHUNK_ORIGINS) {
            final Set<TileKey> legacyTiles = new LinkedHashSet<>();
            final Set<TileKey> freshTiles = new LinkedHashSet<>();
            legacyFill(tensor(legacyTiles), 0, origin[0], origin[1], false);
            newFill(tensor(freshTiles), 0, origin[0], origin[1], false);
            assertEquals(legacyTiles, freshTiles, "chunk (" + origin[0] + "," + origin[1] + ")");
        }
    }
}
