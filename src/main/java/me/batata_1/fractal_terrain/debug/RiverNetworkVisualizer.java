package me.batata_1.fractal_terrain.debug;

import static me.batata_1.fractal_terrain.debug.Debug.DEBUG_LOGGER;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.meanders.*;
import me.batata_1.fractal_terrain.hydrology.network.AtomicView;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.CoordPoint;
import me.batata_1.fractal_terrain.math.ds.ImmutableQuadTree;

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

        final double samplingDist = HydrologyTuning.DX;
        final double[] curPt = new double[2];
        final double detectDist = HydrologyTuning.DX;
        final List<CoordPoint> treePoints = new ArrayList<>();

        for (Channel c : meanders.getChannels()) {
            DEBUG_LOGGER.info("channel {}", c.channelId);
            //  c.spline = QuinticHermiteSpline.createCatmullRom(c.spline.points());
            var migVector = meanders.computedMigVector(c);
            // c.reSample(0.5);
            for (int i = 0; i < c.spline.points().size(); i++) {
                double[] pt = c.spline.points().get(i);
                treePoints.add(new CoordPoint(VectorOps.scale(pt, scale)));
                insertPt(migVector[i], migPointGrid, gridSize, scale);
                insertPt(pt, splinePointGrid, gridSize, scale);
            }
        }
        var tree = new ImmutableQuadTree<>(new double[] {-INF, -INF}, new double[] {INF, INF}, treePoints);

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
            throw new UncheckedIOException(e);
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
        for (Endpoint endpoint : meanders.getNodes()) {
            drawDot(rgb, size, endpoint.coord, nodeColor(endpoint.type), 2);
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
            List<RiverNetwork.NodeSpec> nodeSpecs,
            List<RiverNetwork.EdgeSpec> edgeSpecs,
            String folder,
            String name) {
        final int size = gridSize * NETWORK_SCALE;
        final int[] rgb = new int[size * size]; // black background

        for (RiverNetwork.EdgeSpec edge : edgeSpecs) {
            final List<double[]> pts = edge.pts();
            for (int i = 1; i < pts.size(); i++) drawLine(rgb, size, pts.get(i - 1), pts.get(i), COLOR_CHANNEL);
        }
        for (RiverNetwork.NodeSpec node : nodeSpecs) {
            drawDot(rgb, size, new double[] {node.x(), node.z()}, nodeColor(node.type()), 2);
        }

        writeImage(rgb, size, folder, name);
    }

    /**
     * Same white-channel / colored-node render as {@link #seeNetwork(Meanders, String, String)} but over an
     * {@link AtomicView}, where every interior spline point is a first-class node. Draws each directed
     * adjacency edge {@code u -> v} as a white segment between {@code pos(u)} and {@code pos(v)}, then marks
     * every node carrying a role (SOURCE/DRAIN/JUNCTION) by type; interior points (null role) get no marker.
     * {@code gridSize} sizes the canvas (an {@link AtomicView} carries no grid extent of its own).
     */
    public void seeNetwork(AtomicView atomic, int gridSize, String folder, String name) {
        final int size = gridSize * NETWORK_SCALE;
        final int[] rgb = new int[size * size]; // black background

        final int n = atomic.size();
        for (int u = 0; u < n; u++) {
            final double[] from = atomic.pos(u);
            for (int v : atomic.adjacency.get(u)) drawLine(rgb, size, from, atomic.pos(v), COLOR_CHANNEL);
        }
        for (int u = 0; u < n; u++) {
            final Endpoint.Type type = atomic.role(u);
            if (type != null) drawDot(rgb, size, atomic.pos(u), nodeColor(type), 2);
        }

        writeImage(rgb, size, folder, name);
    }

    private static int nodeColor(Endpoint.Type type) {
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
            throw new UncheckedIOException(e);
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
