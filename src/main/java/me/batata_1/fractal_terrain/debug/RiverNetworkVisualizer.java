package me.batata_1.fractal_terrain.debug;

import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static me.batata_1.fractal_terrain.debug.Debug.DEBUG_LOGGER;
import static me.batata_1.fractal_terrain.hydrology.meanders.Meanders.catmullRomResample;
import static me.batata_1.fractal_terrain.math.VectorOps.distance;

public class RiverNetworkVisualizer {

    public String debugPath;

    public RiverNetworkVisualizer(String debugPath) {
        this.debugPath = debugPath;
    }


    public void see(Meanders meanders, String name) {
        int    gridSize      = meanders.getGridSize();
        double metersPerCell = meanders.getMetersPerCell();
        double constantScaler = 1e4;
        float[] grid = new float[gridSize * gridSize];

        final double samplingDist = 10;
        final double[] curPt = new double[2];
        //TODO: otimizar isso
        for(int x=0 ; x<gridSize ; x++) {
            for(int z=0 ; z<gridSize; z++) {
                final int id = x*gridSize + z;
                curPt[0] = x*metersPerCell;
                curPt[1] = z*metersPerCell;
                channelIterator:
                for(Channel c : meanders.getChannels()) {
                    final double width = c.width*constantScaler;
                    final ArrayList<double[]> resample = catmullRomResample(c.pts,samplingDist);
//                    DEBUG_LOGGER.info("num points channel {}", resample.size());
                    for(double[] pt : resample) {
                        if(distance(pt,curPt)<=width) {
                            grid[id] = 1;
                            break channelIterator;
                        }
                    }
                }
            }
        }

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