package me.batata_1.fractal_terrain.hydrology.meanders;

import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;

import static me.batata_1.fractal_terrain.hydrology.meanders.Meanders.catmullRomResample;

public class MeandersTest {

    private static final Logger LOG = LoggerFactory.getLogger(MeandersTest.class);

    public static void main(String[] args) {
        //testCatmullRomResample();
        testMeanders();
        //quadTreeTest();

    }

    private static Channel getDefaultChannel() {
        int                 N   = 41;
        ArrayList<double[]> pts = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            pts.add(new double[]{
                    100.0 + i * 50.0,
                    1500.0 + 50.0 * Math.sin(2.0 * Math.PI * i / (N - 1))
            });
        }
        ArrayList<Double> localRates = new ArrayList<>(Collections.nCopies(pts.size(), 0.0));
        ArrayList<Double> migRates   = new ArrayList<>(Collections.nCopies(pts.size(), 0.0));
        return new Channel(1, 1, pts, localRates, migRates);
    }

    private static void testMigrate(Meanders sim) {
       sim.migrate(getDefaultChannel());
    }

    private static void testCatmullRomResample() {
        // 41 points, x ∈ [100, 2100], z = 1500 + 50·sin(one full cycle)
        // Endpoints share the same z so chord = 2000 exactly.
        int                 N   = 41;
        ArrayList<double[]> pts = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            pts.add(new double[]{
                    100.0 + i * 50.0,
                    1500.0 + 50.0 * Math.sin(2.0 * Math.PI * i / (N - 1))
            });
        }
        var resp = catmullRomResample(pts,1);
        LOG.info("{}", resp);
    }

    private static void testMeanders() {
        // Flat 3000×3000 m terrain (all gradient zero → maximum migration everywhere)
        int      gridSize      = 300;
        double   metersPerCell = 10.0;
        double[] gradMag       = new double[gridSize * gridSize];

        Meanders sim = new Meanders(gridSize, metersPerCell, gradMag, 42L);

        // 41 points, x ∈ [100, 2100], z = 1500 + 50·sin(one full cycle)
        // Endpoints share the same z so chord = 2000 exactly.
        int                 N   = 80;
        ArrayList<double[]> pts = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            pts.add(new double[]{
                    100.0 + i * 50.0,
                    1500.0 + 50.0 * Math.sin(2.0 * Math.PI * i / (N - 1))
            });
        }
        double width = 0.001;
        sim.addChannel(pts, width);

        double x0   = pts.get(0)[0],     z0   = pts.get(0)[1];
        double xEnd = pts.get(N - 1)[0], zEnd = pts.get(N - 1)[1];

        ArrayList<double[]> before = sim.getChannelPts(0);
        double sBefore = sinuosity(before);
        LOG.info("Before: sinuosity={}  points={}", String.format("%.4f", sBefore), sim.getChannelPointCount(0));

        Debug.river.see(sim, "before");

        sim.simulate(7);

        ArrayList<double[]> result = sim.getChannelPts(0);
        int    n      = result.size();
        double sAfter = sinuosity(result);
        LOG.info("After:  sinuosity={}  points={}", String.format("%.4f", sAfter), n);
        Debug.river.see(sim, "after");

        // 1. All points finite
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(result.get(i)[0]) || !Double.isFinite(result.get(i)[1]))
                throw new AssertionError("NaN/Inf at point " + i);
        }

        // 2. Endpoint stationarity
        double eps = 1e-9;
        if (Math.abs(result.get(0)[0] - x0) > eps || Math.abs(result.get(0)[1] - z0) > eps)
            throw new AssertionError(
                    String.format("Start endpoint moved: (%.6f,%.6f) → (%.6f,%.6f)", x0, z0, result.get(0)[0], result.get(0)[1]));
        if (Math.abs(result.get(n - 1)[0] - xEnd) > eps || Math.abs(result.get(n - 1)[1] - zEnd) > eps)
            throw new AssertionError(
                    String.format("End endpoint moved: (%.6f,%.6f) → (%.6f,%.6f)", xEnd, zEnd, result.get(n - 1)[0], result.get(n - 1)[1]));

        // 3. Point spacing ∈ [0.5 × SAMPLING_DIST, 2.0 × SAMPLING_DIST] = [25, 100]
        for (int i = 1; i < n; i++) {
            double dx = result.get(i)[0] - result.get(i - 1)[0];
            double dz = result.get(i)[1] - result.get(i - 1)[1];
            double d  = Math.sqrt(dx * dx + dz * dz);
            if (d < 25.0 || d > 100.0)
                throw new AssertionError(
                        String.format("Spacing %.3f out of [25, 100] at i=%d", d, i));
        }

        // 4. Sinuosity growth
        if (sAfter <= sBefore)
            throw new AssertionError(
                    String.format("Sinuosity did not grow: before=%.4f after=%.4f", sBefore, sAfter));

        LOG.info("All invariants passed.");
    }

    private static void quadTreeTest() {
        QuadTree<QuadTreePoint> tree = new QuadTree<>(new double[]{-1,-1},new double[]{1,1});
        tree.insertPoint(new QuadTreePoint(new double[]{0,0}));
        tree.insertPoint(new QuadTreePoint(new double[]{0.0625,0.0625}));
        var o = tree.getPointsInBox(new double[]{-0.5,-0.5},new double[]{0.5,0.5});
        LOG.info("{}",o);
    }


    private static double sinuosity(ArrayList<double[]> pts) {
        double arcLen = 0.0;
        for (int i = 1; i < pts.size(); i++) {
            double dx = pts.get(i)[0] - pts.get(i - 1)[0];
            double dz = pts.get(i)[1] - pts.get(i - 1)[1];
            arcLen += Math.sqrt(dx * dx + dz * dz);
        }
        double dx    = pts.get(pts.size() - 1)[0] - pts.get(0)[0];
        double dz    = pts.get(pts.size() - 1)[1] - pts.get(0)[1];
        double chord = Math.sqrt(dx * dx + dz * dz);
        return arcLen / Math.max(chord, 1e-9);
    }
}
