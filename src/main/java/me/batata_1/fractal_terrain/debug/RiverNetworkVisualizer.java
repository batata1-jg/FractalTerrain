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
import java.util.List;

import static me.batata_1.fractal_terrain.debug.Debug.DEBUG_LOGGER;

public class RiverNetworkVisualizer {

    public String debugPath;

    public RiverNetworkVisualizer(String debugPath) {
        this.debugPath = debugPath;
    }

    private static final double INF = 1e9;

    public void see(Meanders meanders, String name) {
        int    gridSize      = meanders.getGridSize();
        float scale = 4;
        gridSize = (int) (gridSize*scale);
        float[] grid = new float[gridSize * gridSize];
        float[] pointGrid = new float[gridSize * gridSize];

        final double samplingDist = Meanders.DX;
        final double[] curPt = new double[2];
        final double detectDist= Meanders.DX;
        var tree = new QuadTree<>(new double[]{-INF, -INF}, new double[]{INF, INF});

        for(Channel c : meanders.getChannels()) {
            DEBUG_LOGGER.info("channel {}",c.channelId);
//            c.reSample(0.5);
            double[][] migVec = meanders.getCurvatureVector(c);
            for(int i=0 ; i<c.spline.points().size() ; i++) {
                double[] pt = c.spline.points().get(i);
                tree.insertPoint(new QuadTreePoint(VectorOps.scale(pt,scale)));
                insertPt(VectorOps.add(pt,migVec[i]),pointGrid,gridSize,scale);
                insertPt(pt,pointGrid,gridSize,scale);
            }
        }

        for(int x=0 ; x<gridSize ; x++) {
            for(int z=0 ; z<gridSize; z++) {
                final int id = x*gridSize + z;
                curPt[0] = x;
                curPt[1] = z;
                List<double[]> pts = tree.getPointCoordsInBox(
                        VectorOps.add(curPt,VectorOps.scale(new double[]{1,1},-detectDist*scale)),
                        VectorOps.add(curPt,VectorOps.scale(new double[]{1,1},detectDist*scale))
                );
                if(!pts.isEmpty()) grid[id]=1;
            }
        }

        tree.clear();

        File dir = new File(debugPath);
        dir.mkdirs();
        File outputFile = new File(dir, name+".png");

        int[] pixels = new int[gridSize * gridSize];
        int[] pixelPoint = new int[gridSize*gridSize];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (int) (grid[i] * 255);
            pixelPoint[i] = (int) (pointGrid[i] * 255);
        }

        BufferedImage image = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        WritableRaster raster = image.getRaster();
        raster.setSamples(0, 0, gridSize, gridSize, 0, pixels);
        raster.setSamples(0, 0, gridSize, gridSize, 1, pixelPoint);
        raster.setSamples(0, 0, gridSize, gridSize, 2, pixels);
        DEBUG_LOGGER.info("creating image bounds a:");
        try {
            ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void insertPt(double[] pt, float[] pointGrid,int gridSize,float scale) {
        if(pt[0]>gridSize*gridSize-1||pt[1]>gridSize*gridSize-1) {
            pointGrid[0] = 1;
            return;
        }
        int id = Math.toIntExact(gridSize * Math.round(scale*pt[0]) + Math.round(scale*pt[1]));
        int pointId = Math.clamp(id,0,gridSize*gridSize-1);
        pointGrid[pointId]=1;
    }
}