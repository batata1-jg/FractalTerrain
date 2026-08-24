package me.batata_1.fractal_terrain.debug;

import java.io.File;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.providers.GlobalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.providers.RiverProvider;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.relief.ReliefProvider;
import org.jetbrains.annotations.TestOnly;

/**
 * Fans out each provider's {@code debugStages(tileX, tileZ)} to PNG dumps under a common root, one
 * subdirectory per provider ({@code global_river/}, {@code local_river/}, {@code relief/}). Lets the
 * live {@code FractalTerrainInstance} dump the same imagery the standalone {@code GlobalRiverTest}/
 * {@code LocalRiverTest} harnesses produce, without needing a harness.
 *
 * <p>The same {@code (tileX, tileZ)} pair is forwarded to every provider, but each interprets it in its
 * own tile grid ({@code GlobalRiverProvider}: 64-coarse-px; relief/local: 512-px) — the three
 * subdirectories need not depict the same patch of world.
 */
@TestOnly
public final class InstanceStageDumper {

    private static final int GLOBAL_TILE = 64;
    private static final int RELIEF_TILE = 512;
    private static final int RELIEF_PAD = 1;

    private InstanceStageDumper() {}

    /** Dump every provider's stages for {@code (tileX, tileZ)} into {@code rootPath/<provider>/}. */
    public static void dump(
            String rootPath,
            int tileX,
            int tileZ,
            GlobalRiverProvider global,
            RiverProvider local,
            ReliefProvider relief) {
        dumpGlobal(rootPath + "/global_river", tileX, tileZ, global);
        dumpRiver(rootPath + "/local_river", tileX, tileZ, local);
        dumpRelief(rootPath + "/relief", tileX, tileZ, relief);
    }

    // -------------------------------------------------------------------------
    // Per-provider dumps
    // -------------------------------------------------------------------------

    private static void dumpGlobal(String dir, int tx, int tz, GlobalRiverProvider provider) {
        cleanDir(dir);
        final GlobalRiverProvider.Stages s = provider.debugStages(tx, tz);
        final String prefix = "tile_tx" + tx + "_tz" + tz + "_";
        final int side = s.paddedSide;
        see(dir, s.rawElevation, side, prefix + "01_raw_elevation");
        see(dir, s.rampedElevation, side, prefix + "02_ramped_elevation");
        see(dir, maskToFloat(s.upperMask), side, prefix + "03_upper_mask");
        see(dir, maskToFloat(s.lowerMask), side, prefix + "04_lower_mask");
        see(dir, maskToFloat(s.ridgeMask), side, prefix + "05_ridge_mask");
        see(dir, maskToFloat(s.coastMask), side, prefix + "06_coast_mask");
        see(dir, rasterizePaths(s.descentPaths, side), side, prefix + "07_descent_paths");
        see(dir, arrowPresence(s.arrows), side, prefix + "08_arrows_padded");
        see(dir, s.widths, side, prefix + "09_widths_padded");
        see(dir, s.riverElevation, side, prefix + "09b_river_elev_padded");
        dumpTensorChannels(dir, s.tile, GLOBAL_TILE, prefix + "10_tile_ch");
    }

    private static void dumpRiver(String dir, int tx, int tz, RiverProvider provider) {
        cleanDir(dir);
        final RiverProvider.Stages s = provider.debugStages(tx, tz);
        final String prefix = "tile_tx" + tx + "_tz" + tz + "_";
        see(dir, s.flow, RELIEF_TILE, prefix + "01_flow");
        see(dir, maskToFloat(s.riverMask), RELIEF_TILE, prefix + "02_river_mask");
        see(dir, s.carvedElevation, RELIEF_TILE, prefix + "03_carved_elev");
        see(dir, rasterizeChannels(s.channels, RELIEF_PAD), RELIEF_TILE, prefix + "04_global_channels");
        see(dir, rasterizeChannels(s.localChannels, 0), RELIEF_TILE, prefix + "05_local_channels");
    }

    private static void dumpRelief(String dir, int tx, int tz, ReliefProvider provider) {
        cleanDir(dir);
        final ReliefProvider.Stages s = provider.debugStages(tx, tz);
        final String prefix = "tile_tx" + tx + "_tz" + tz + "_";
        see(dir, s.carvedElevation, RELIEF_TILE, prefix + "01_carved_elev");
        if (s.base != null) {
            for (int ch = 0; ch < s.base.length; ch++) {
                final int side = squareSide(s.base[ch].length);
                see(dir, s.base[ch], side, prefix + "02_base_ch" + ch);
            }
        }
        dumpTensorChannels(dir, s.result, RELIEF_TILE, prefix + "03_result_ch");
    }

    // -------------------------------------------------------------------------
    // Rendering helpers (mirrored from the standalone harnesses)
    // -------------------------------------------------------------------------

    private static void see(String dir, float[] data, int side, String name) {
        if (data == null) return;
        Debug.tensor.see(new FloatTensor(new int[] {side, side}, data), name, dir);
    }

    /** Dump every channel of a {@code [C, H, W]} tensor as its own PNG (square H == W == side). */
    private static void dumpTensorChannels(String dir, FloatTensor tile, int side, String prefix) {
        if (tile == null) return;
        final int channels = tile.shape(0);
        final int pixels = side * side;
        for (int ch = 0; ch < channels; ch++) {
            final float[] out = new float[pixels];
            tile.readInto(ch * pixels, out, 0, pixels);
            see(dir, out, side, prefix + ch);
        }
    }

    private static float[] maskToFloat(boolean[][] mask) {
        if (mask == null) return null;
        final int height = mask.length;
        final int width = mask[0].length;
        final float[] out = new float[height * width];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                out[row * width + col] = mask[row][col] ? 1f : 0f;
            }
        }
        return out;
    }

    private static float[] maskToFloat(boolean[] mask) {
        if (mask == null) return null;
        final float[] out = new float[mask.length];
        for (int i = 0; i < mask.length; i++) out[i] = mask[i] ? 1f : 0f;
        return out;
    }

    /** Rasterize every per-source descent path's pixels onto a {@code side x side} grid. */
    private static float[] rasterizePaths(List<List<int[]>> paths, int side) {
        final float[] grid = new float[side * side];
        if (paths == null) return grid;
        for (List<int[]> path : paths) {
            for (int[] pixel : path) grid[pixel[0] * side + pixel[1]] = 1f;
        }
        return grid;
    }

    /** 1 where a pixel carries a river arrow bit, 0 otherwise. */
    private static float[] arrowPresence(int[] arrows) {
        if (arrows == null) return null;
        final float[] out = new float[arrows.length];
        for (int i = 0; i < arrows.length; i++) out[i] = GlobalRiverProvider.isRiver(arrows[i]) ? 1f : 0f;
        return out;
    }

    /** Rasterize each channel's spline points onto the {@code RELIEF_TILE} grid, subtracting {@code offset}. */
    private static float[] rasterizeChannels(List<Channel> channels, int offset) {
        final float[] grid = new float[RELIEF_TILE * RELIEF_TILE];
        if (channels == null) return grid;
        for (Channel channel : channels) {
            for (double[] point : channel.spline.points()) {
                final int x = (int) Math.round(point[0]) - offset;
                final int z = (int) Math.round(point[1]) - offset;
                if (x >= 0 && x < RELIEF_TILE && z >= 0 && z < RELIEF_TILE) grid[x * RELIEF_TILE + z] = 1f;
            }
        }
        return grid;
    }

    private static int squareSide(int length) {
        return (int) Math.round(Math.sqrt(length));
    }

    private static void cleanDir(String path) {
        final File dir = new File(path);
        if (dir.exists()) {
            final File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isFile()) child.delete();
                    else cleanDir(child.getAbsolutePath());
                }
            }
        }
        dir.mkdirs();
    }
}
