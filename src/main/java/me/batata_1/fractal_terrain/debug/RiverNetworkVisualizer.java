package me.batata_1.fractal_terrain.debug;

import static me.batata_1.fractal_terrain.debug.Debug.DEBUG_LOGGER;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
import me.batata_1.fractal_terrain.hydrology.meanders.Node;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;

public class RiverNetworkVisualizer {

    public String debugPath;

    public RiverNetworkVisualizer(String debugPath) {
        this.debugPath = debugPath;
    }

    private static final double INF = 1e9;

    public void see(Meanders meanders, String name) {
        int gridSize = meanders.getGridSize();
        float scale = 4;
        gridSize = (int) (gridSize * scale);
        float[] grid = new float[gridSize * gridSize];
        float[] splinePointGrid = new float[gridSize * gridSize]; // green
        float[] migPointGrid = new float[gridSize * gridSize]; // red

        final double samplingDist = Meanders.DX;
        final double[] curPt = new double[2];
        final double detectDist = Meanders.DX;
        var tree = new QuadTree<>(new double[] {-INF, -INF}, new double[] {INF, INF});

        for (Channel c : meanders.getChannels()) {
            DEBUG_LOGGER.info("channel {}", c.channelId);
            //  c.spline = QuinticHermiteSpline.createCatmullRom(c.spline.points());
            var migVector = meanders.computedMigVector(c);
            // c.reSample(0.5);
            for (int i = 0; i < c.spline.points().size(); i++) {
                double[] pt = c.spline.points().get(i);
                tree.insertPoint(new QuadTreePoint(VectorOps.scale(pt, scale)));
                insertPt(migVector[i], migPointGrid, gridSize, scale);
                insertPt(pt, splinePointGrid, gridSize, scale);
            }
        }

        for (int x = 0; x < gridSize; x++) {
            for (int z = 0; z < gridSize; z++) {
                final int id = x * gridSize + z;
                curPt[0] = x;
                curPt[1] = z;
                List<double[]> pts = tree.getPointCoordsInBox(
                        VectorOps.add(curPt, VectorOps.scale(new double[] {1, 1}, -detectDist * scale)),
                        VectorOps.add(curPt, VectorOps.scale(new double[] {1, 1}, detectDist * scale)));
                if (!pts.isEmpty()) grid[id] = 1;
            }
        }

        tree.clear();

        File dir = new File(debugPath);
        dir.mkdirs();
        File outputFile = new File(dir, name + ".png");

        int[] outline = new int[gridSize * gridSize]; // blue
        int[] splinePixels = new int[gridSize * gridSize]; // green
        int[] migPixels = new int[gridSize * gridSize]; // red
        for (int i = 0; i < outline.length; i++) {
            outline[i] = (int) (grid[i] * 255);
            splinePixels[i] = (int) (splinePointGrid[i] * 255);
            migPixels[i] = (int) (migPointGrid[i] * 255);
        }

        BufferedImage image = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
        WritableRaster raster = image.getRaster();
        raster.setSamples(0, 0, gridSize, gridSize, 0, migPixels); // red = migVector points
        raster.setSamples(0, 0, gridSize, gridSize, 1, splinePixels); // green = spline points
        raster.setSamples(0, 0, gridSize, gridSize, 2, outline); // blue = channel outline
        //  DEBUG_LOGGER.info("creating image bounds a:");
        try {
            ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final int NETWORK_SCALE = 2;
    private static final int COLOR_CHANNEL = 0xFFFFFF; // white
    private static final int COLOR_SOURCE = 0x00FF00; // green
    private static final int COLOR_DRAIN = 0xFF0000; // red
    private static final int COLOR_JUNCTION = 0xFFFF00; // yellow

    /**
     * Fast whole-network render (O(total channel length + node count); no per-pixel query, unlike
     * {@link #see}). Draws channel polylines white and node markers by type, into
     * {@code {debugPath}/{folder}/{name}.png}.
     */
    public void seeNetwork(Meanders meanders, String folder, String name) {
        final int size = meanders.getGridSize() * NETWORK_SCALE;
        final int[] rgb = new int[size * size]; // black background

        for (Channel c : meanders.getChannels()) {
            var pts = c.spline.points();
            for (int i = 1; i < pts.size(); i++) drawLine(rgb, size, pts.get(i - 1), pts.get(i), COLOR_CHANNEL);
        }
        for (Node node : meanders.getNodes()) {
            drawDot(rgb, size, node.coord, nodeColor(node.type), 2);
        }

        writeImage(rgb, size, folder, name);
    }

    /**
     * Same render as {@link #seeNetwork(Meanders, String, String)} but straight from the raw
     * {@code nodeSpecs}/{@code edgeSpecs} — without building a {@link Meanders}. Use this to debug a
     * network that may fail to construct (e.g. a node with two outgoing edges). Edges are drawn between
     * consecutive {@code EdgeSpec.pts()}; nodes by {@code NodeSpec.type()}.
     */
    public void seeNetwork(
            int gridSize,
            List<Meanders.NodeSpec> nodeSpecs,
            List<Meanders.EdgeSpec> edgeSpecs,
            String folder,
            String name) {
        final int size = gridSize * NETWORK_SCALE;
        final int[] rgb = new int[size * size]; // black background

        for (Meanders.EdgeSpec edge : edgeSpecs) {
            final List<double[]> pts = edge.pts();
            for (int i = 1; i < pts.size(); i++) drawLine(rgb, size, pts.get(i - 1), pts.get(i), COLOR_CHANNEL);
        }
        for (Meanders.NodeSpec node : nodeSpecs) {
            drawDot(rgb, size, new double[] {node.x(), node.z()}, nodeColor(node.type()), 2);
        }

        writeImage(rgb, size, folder, name);
    }

    private static int nodeColor(Node.NodeType type) {
        return switch (type) {
            case SOURCE -> COLOR_SOURCE;
            case DRAIN -> COLOR_DRAIN;
            case JUNCTION -> COLOR_JUNCTION;
        };
    }

    private void writeImage(int[] rgb, int size, String folder, String name) {
        File dir = new File(debugPath, folder);
        dir.mkdirs();
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, size, size, rgb, 0, size);
        try {
            ImageIO.write(image, "png", new File(dir, name + ".png"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void drawLine(int[] rgb, int size, double[] a, double[] b, int color) {
        // a/b are in world coords; row = x*scale, col = z*scale (matching see()'s orientation)
        final double r0 = a[0] * NETWORK_SCALE, c0 = a[1] * NETWORK_SCALE;
        final double r1 = b[0] * NETWORK_SCALE, c1 = b[1] * NETWORK_SCALE;
        final int steps = (int) Math.ceil(Math.max(Math.abs(r1 - r0), Math.abs(c1 - c0))) + 1;
        for (int s = 0; s <= steps; s++) {
            final double t = (double) s / steps;
            plot(rgb, size, (int) Math.round(r0 + (r1 - r0) * t), (int) Math.round(c0 + (c1 - c0) * t), color);
        }
    }

    private static void drawDot(int[] rgb, int size, double[] coord, int color, int radius) {
        final int row = (int) Math.round(coord[0] * NETWORK_SCALE);
        final int col = (int) Math.round(coord[1] * NETWORK_SCALE);
        for (int dr = -radius; dr <= radius; dr++)
            for (int dc = -radius; dc <= radius; dc++) plot(rgb, size, row + dr, col + dc, color);
    }

    private static void plot(int[] rgb, int size, int row, int col, int color) {
        if (row < 0 || col < 0 || row >= size || col >= size) return;
        rgb[row * size + col] = color;
    }

    private void insertPt(double[] pt, float[] pointGrid, int gridSize, float scale) {
        if (pt[0] * scale > gridSize * gridSize - 1 || pt[1] * scale > gridSize * gridSize - 1) {
            pointGrid[0] = 1;
            return;
        }
        int id;
        try {
            id = Math.toIntExact(gridSize * Math.round(scale * pt[0]) + Math.round(scale * pt[1]));
        } catch (ArithmeticException e) {
            id = 0;
        }
        ;
        int pointId = Math.clamp(id, 0, gridSize * gridSize - 1);
        pointGrid[pointId] = 1;
    }
}
