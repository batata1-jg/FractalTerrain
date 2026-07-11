package me.batata_1.fractal_terrain.debug;

import static me.batata_1.fractal_terrain.debug.Debug.DEBUG_LOGGER;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.CoordPoint;
import me.batata_1.fractal_terrain.math.ds.ImmutableQuadTree;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;

public class SplineVisualizer {

    private static final double INF = 1e9;
    public String debugPath;

    public SplineVisualizer(String debugPath) {
        this.debugPath = debugPath;
    }

    public void see(QuinticHermiteSpline spline, String name) {
        final int beyond = 0;
        // lower => more resolution
        final double resolution = 0.125;
        double[] minVec = new double[] {INF, INF};
        double[] maxVec = new double[] {-INF, -INF};

        for (int i = 0; i < spline.getSize() + beyond; i++) {
            minVec = VectorOps.min(minVec, spline.sample(i));
            maxVec = VectorOps.max(maxVec, spline.sample(i));
        }

        double[] boxLen = VectorOps.sub(maxVec, minVec);
        boxLen[0] = Math.min(boxLen[0] / resolution, 4096);
        boxLen[1] = Math.min(boxLen[1] / resolution, 4096);
        // Clamp to >= 1: a straight (axis-aligned) spline has zero extent on one axis, which would
        // otherwise make BufferedImage throw "w and h must be > 0".
        long[] WH =
                Arrays.stream(boxLen).mapToLong(v -> Math.max(Math.round(v), 1)).toArray();
        DEBUG_LOGGER.info("spline size:{} from {} -> to {} bounding boxlen = {}", spline.getSize(), minVec, maxVec, WH);
        double[] grid = new double[(int) (WH[0] * WH[1])];

        final double[] curPt = new double[2];
        final double detectDist = Meanders.DX;
        final List<CoordPoint> treePoints = new ArrayList<>();

        for (double i = 0; i < spline.getSize() + beyond; i += resolution)
            treePoints.add(new CoordPoint(spline.sample(i)));
        var tree = new ImmutableQuadTree<>(new double[] {-INF, -INF}, new double[] {INF, INF}, treePoints);

        for (int x = 0; x < (int) WH[0]; x++) {
            for (int z = 0; z < (int) WH[1]; z++) {
                final int id = x * (int) WH[1] + z;
                curPt[0] = (x * resolution + minVec[0]);
                curPt[1] = (z * resolution + minVec[1]);
                List<double[]> pts = tree.getPointCoordsInBox(
                        VectorOps.add(curPt, VectorOps.scale(new double[] {1 * resolution, resolution}, -detectDist)),
                        VectorOps.add(curPt, VectorOps.scale(new double[] {resolution, resolution}, detectDist)));
                if (!pts.isEmpty()) grid[id] = 1;
            }
        }

        File dir = new File(debugPath);
        dir.mkdirs();
        File outputFile = new File(dir, name + ".png");

        int[] pixels = new int[(int) (WH[0] * WH[1])];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (int) (grid[i] * 255);
        }

        BufferedImage image = new BufferedImage((int) WH[1], (int) WH[0], BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = image.getRaster();
        raster.setSamples(0, 0, (int) WH[1], (int) WH[0], 0, pixels);
        DEBUG_LOGGER.info("creating image bounds:");
        try {
            ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
