package me.batata_1.fractal_terrain.debug;

import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.math.VectorOps;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static me.batata_1.fractal_terrain.debug.Debug.DEBUG_LOGGER;
import static me.batata_1.fractal_terrain.hydrology.meanders.Meanders.catmullRomResample;

public class RiverNetworkVisualizer {

    public String debugPath;

    public RiverNetworkVisualizer(String debugPath) {
        this.debugPath = debugPath;
    }

    private static final double INF = 1e9;

    public void see(Meanders meanders, String name) {
        int    gridSize      = meanders.getGridSize();
        double metersPerCell = meanders.getMetersPerCell();
        double constantScaler = 1e4;
        float[] grid = new float[gridSize * gridSize];

        final double samplingDist = 10;
        final double[] curPt = new double[2];
        final double detectDist= 10;
        var tree = new QuadTree<>(new double[]{-INF, -INF}, new double[]{INF, INF});

        for(Channel c : meanders.getChannels()) {
            final ArrayList<double[]> resample = catmullRomResample(c.pts,samplingDist);
            for(double[] pt : resample) {
                tree.insertPoint(new QuadTreePoint(pt));
            }
        }

        for(int x=0 ; x<gridSize ; x++) {
            for(int z=0 ; z<gridSize; z++) {
                final int id = x*gridSize + z;
                curPt[0] = x*metersPerCell;
                curPt[1] = z*metersPerCell;
                List<double[]> pts = tree.getPointCoordsInBox(
                        VectorOps.add(curPt,VectorOps.scale(new double[]{1,1},-detectDist)),
                        VectorOps.add(curPt,VectorOps.scale(new double[]{1,1},detectDist))
                );
                if(!pts.isEmpty()) grid[id]=1;
            }
        }

        tree.clear();

        File dir = new File(debugPath);
        dir.mkdirs();
        File outputFile = new File(dir, name+".png");

        int[] pixels = new int[gridSize * gridSize];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (int) (grid[i] * 255);
        }

        BufferedImage image = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = image.getRaster();
        raster.setSamples(0, 0, gridSize, gridSize, 0, pixels);
        DEBUG_LOGGER.info("creating image bounds:");
        try {
            ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}