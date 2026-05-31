package me.batata_1.fractal_terrain.debug;

import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static me.batata_1.fractal_terrain.debug.Debug.DEBUG_LOGGER;

public class SplineVisualizer {

    private static final double INF = 1e9 ;
    public String debugPath;

    public SplineVisualizer(String debugPath) {
        this.debugPath = debugPath;
    }

    public void see(QuinticHermiteSpline spline, String name) {

        double[] minVec = new double[]{INF,INF};
        double[] maxVec = new double[]{-INF,-INF};

        for(double[] pt : spline.points()) {
            minVec = VectorOps.min(minVec,pt);
            maxVec = VectorOps.max(maxVec,pt);
        }

        double[] boxLen = VectorOps.sub(maxVec,minVec);
        long[] WH = Arrays.stream(boxLen).mapToLong(Math::round).toArray();
        DEBUG_LOGGER.info("spline from {} -> to {} bounding boxlen = {}",minVec,maxVec,WH);
        double[] grid = new double[(int)(WH[0]*WH[1])];

        final double[] curPt = new double[2];
        final double detectDist= Meanders.DX;
        var tree = new QuadTree<>(new double[]{-INF, -INF}, new double[]{INF, INF});

        for(double[] pt : spline.points()) tree.insertPoint(new QuadTreePoint(pt));

        for(int x=0 ; x<(int)WH[0] ; x++) {
            for(int z=0 ; z<(int)WH[1]; z++) {
                final int id =  x*(int)WH[1] + z;
                curPt[0] = x + minVec[0];
                curPt[1] = z + minVec[1];
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

        int[] pixels = new int[(int) (WH[0]*WH[1])];
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
            throw new RuntimeException(e);
        }
    }


}
