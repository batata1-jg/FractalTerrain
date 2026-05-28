package me.batata_1.fractal_terrain.hydrology.meanders;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.math.VectorOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static me.batata_1.fractal_terrain.math.VectorOps.distance;

public final class Meanders {

    private static final double INF = 1e9;
    private static final double OMEGA           = -1.0;
    private static final double GAMMA           =  2.5;
    private static final double K               =  1.0;
    private static final double CF              =  0.011;
    private static final double DT              =  2;  // ~109.5 days in seconds
    private static final double MAX_SLOPE       =  1.0;
    private static final double CHANNEL_FALLOFF =  0.05;
    private static final double SAMPLING_DIST   =  50.0;
    private static final int    UPSTREAM_WINDOW =  1500;
    private static final Logger LOG = LoggerFactory.getLogger(Meanders.class);

    private final int      gridSize;
    private final double   metersPerCell;
    private final double[] gradMag;

    private final List<Channel> channels = new ArrayList<>();

    private final Random rng;

    public Meanders(int gridSize, double metersPerCell, double[] gradMag, long seed) {
        this.gridSize      = gridSize;
        this.metersPerCell = metersPerCell;
        this.gradMag       = gradMag;
        this.rng           = new Random(seed);
    }

    public void addChannel(ArrayList<double[]> xzPts, double width) {
        int n = xzPts.size();
        channels.add(new Channel(
                width,
                Math.max(1.0, Math.pow(width / 18.8, 1.0 / 1.41)),  // Konsoer 2013
                xzPts,
                new ArrayList<>(Collections.nCopies(n, 0.0)),
                new ArrayList<>(Collections.nCopies(n, 0.0))
        ));
    }


    //TODO: change this in order for compute local rates and compute migration rates to be toghether with ch.localRates = new...
    public void step() {
        for (Channel ch : channels) {
            computeLocalRates(ch);
            computeMigrationRates(ch);
            migrate(ch);
            manageCutoffs(ch);
            ArrayList<double[]> resampled = catmullRomResample(ch.pts, SAMPLING_DIST);
            ch.pts        = resampled;
            ch.localRates = new ArrayList<>(Collections.nCopies(resampled.size(), 0.0));
            ch.migRates   = new ArrayList<>(Collections.nCopies(resampled.size(), 0.0));
        }
    }

    public void simulate(int n) {
        for (int i = 0; i < n; i++) step();
    }

    public int getChannelCount() {
        return channels.size();
    }

    public int getChannelPointCount(int i) {
        return channels.get(i).pts.size();
    }

    public ArrayList<double[]> getChannelPts(int i) {
        Channel ch = channels.get(i);
        ArrayList<double[]> copy = new ArrayList<>(ch.pts.size());
        for (double[] pt : ch.pts) {
            copy.add(new double[]{pt[0], pt[1]});
        }
        return copy;
    }

    public int getGridSize() { return gridSize; }

    public double getMetersPerCell() { return metersPerCell; }

    public double getChannelWidth(int i) { return channels.get(i).width; }

    public void addConstraint(double cx, double cz, double radius, double effect) {
        // TODO: PointConstraint gradient influence
    }

    // =========================================================================
    // Per-point geometry helpers
    // =========================================================================

    private static double[] tangent(ArrayList<double[]> pts, int i) {
        final double[] pi1 = pts.get(Math.min(i+1,pts.size()-1));
        final double[] pi0 = pts.get(Math.max(i-1,0));
        return VectorOps.div(VectorOps.sub(pi1,pi0),2.0);
    }

    private static double[] normal(ArrayList<double[]> pts, int i) {
        double[] t = VectorOps.normalize(tangent(pts,i));
        return new double[]{-t[1], t[0]};
    }

    private static double curvature(ArrayList<double[]> pts, int i) {
        double[] dxy = tangent(pts,i);
        double[] dxy0 = tangent(pts,i-1);
        double[] dxy1 = tangent(pts,i+1);
        double[] ddxy = VectorOps.div(VectorOps.sub(dxy1,dxy0),2.0);
        double absDxy = VectorOps.abs(dxy);
        return VectorOps.cross2D(dxy,ddxy) / absDxy*absDxy*absDxy;
    }

    // =========================================================================
    // Algorithm steps
    // =========================================================================

    private static void computeLocalRates(Channel ch) {
        for (int i = 0; i < ch.pts.size(); i++) {
            ch.localRates.set(i, ch.width * curvature(ch.pts, i));
        }
    }

    private static void computeMigrationRates(Channel ch) {
        int    n     = ch.pts.size();
        double alpha = K * 2.0 * CF / ch.depth;

        double arcLen = 0.0;
        for (int i = 0; i < n - 1; i++) arcLen += dist(ch.pts, i, i + 1);
        double chord  = dist(ch.pts, 0, n - 1);
        double sinuos = Math.pow(arcLen / Math.max(chord, 1e-9), -2.0 / 3.0);

        for (int i = 0; i < n; i++) {
            double cumDist = 0.0, sumR0 = 0.0, sumG = 0.0;
            for (int j = i; j >= Math.max(0, i - UPSTREAM_WINDOW); j--) {
                if (j < i) cumDist += dist(ch.pts, j + 1, j);
                double g = Math.exp(-alpha * cumDist);
                sumR0 += ch.localRates.get(j) * g;
                sumG  += g;
            }
            if (sumG == 0.0) sumG = 1.0;
            ch.migRates.set(i, (OMEGA * ch.localRates.get(i) + GAMMA * sumR0 / sumG) * sinuos);
        }
    }

    public void migrate(Channel ch) {
        int    n = ch.pts.size();
        double f = CHANNEL_FALLOFF;
      //  LOG.info("before migration {}",ch.pts);
        for (int i = 1; i < n - 1; i++) {
            double t  = i / (double)(n - 1);
            double wf;
            if (t < f) {
                double s = t / f;
                wf = 3.0*s*s - 2.0*s*s*s;
            } else if (t > 1.0 - f) {
                double s = (1.0 - t) / f;
                wf = 3.0*s*s - 2.0*s*s*s;
            } else {
                wf = 1.0;
            }

            // gy = row index (x-axis), gx = col index (z-axis) per id = x*size + z
            double gy      = ch.pts.get(i)[0] / metersPerCell;
            double gx      = ch.pts.get(i)[1] / metersPerCell;
            double gSample = bilinearSample(gradMag, gridSize, gridSize, gy, gx);
            double wt      = Math.max(0.0, 1.0 - gSample / MAX_SLOPE);
            wt = 1;
            double   w  = wf * wt;
            final double rate = w * DT * ch.migRates.get(i);
            double[] newPt = VectorOps.add(ch.pts.get(i), VectorOps.scale(normal(ch.pts,i),-rate));
            //LOG.info("rate: {}, curmigrate: {} , w: {} , normal: {}",-rate,ch.migRates[i],w,normal(ch.pts,i));
            ch.pts.set(i,newPt);
        }
    //    LOG.info("after migration {}",ch.pts);
    }


    public static class ChannelPt extends QuadTreePoint {

        public final int id;

        public ChannelPt(double[] pt,int id) {
            super(pt);
            this.id=id;
        }
    }

    //Use quadTree for this later
    //precisa implementar remove point pra dar certo
    private static final QuadTree<ChannelPt> quadTree = new QuadTree<>(new double[]{-INF,-INF},new double[]{INF,INF});
    private static void manageCutoffs(Channel ch) {
        quadTree.clear();
        for(int id=0 ; id<ch.pts.size() ; id++) quadTree.insertPoint(new ChannelPt(ch.pts.get(id),id));
        ArrayList<Integer> newPathIndexes = new ArrayList<>();
        double[] width = new double[]{ch.width, ch.width};
        for(int id=0 ; id<ch.pts.size() ; id++) {
            double[] pt = ch.pts.get(id);
            quadTree.getPointsInBox(VectorOps.sub(pt,width),VectorOps.add(pt,width));
        }
    }

    // returns the parameter t of the next point in the spline at a distance of dist
    public static double nextInSpline(final double curT, final double dist, final ArrayList<double[]> pts, final double maxT) {
        double p = curT;
        double q = maxT;
        double m;
        for (int step = 0; step <= FractalTerrainConfig.BINARY_SEARCH_MAX_STEPS; step++) {
            m = (p + q) / 2;
            if (distance(catmullRomSample(pts, curT), catmullRomSample(pts, m)) <= dist) p = m;
            else q = m;
            if (p >= q) break;
        }
        return p;
    }

    // Returns the point on the Catmull-Rom spline at parameter t (segment-indexed).
    public static double[] catmullRomSample(ArrayList<double[]> pts, double t) {
        final int    n   = pts.size();
        final int    seg = (int) Math.clamp(Math.floor(t), 0, n - 1);
        final double u   = t - Math.floor(t);

        final int p0i = Math.max(0, seg - 1);
        final int p1i = seg;
        final int p2i = Math.min(n - 1, seg + 1);
        final int p3i = Math.min(n - 1, seg + 2);

        final double[] result = new double[2];
        for (int dim = 0; dim < 2; dim++) {
            final double P0 = pts.get(p0i)[dim];
            final double P1 = pts.get(p1i)[dim];
            final double P2 = pts.get(p2i)[dim];
            final double P3 = pts.get(p3i)[dim];
            result[dim] = 0.5 * (2*P1
                    + (-P0 + P2)                  * u
                    + (2*P0 - 5*P1 + 4*P2 - P3)  * u*u
                    + (-P0 + 3*P1 - 3*P2 + P3)   * u*u*u);
        }
        return result;
    }

    // keep last point
    public static ArrayList<double[]> catmullRomResample(ArrayList<double[]> pts, double samplingDist) {
        final double maxT = pts.size()-1;
        ArrayList<Double> newT = new ArrayList<>();
        newT.add(0.0);
        while(newT.getLast()<maxT) {
            newT.add(nextInSpline(newT.getLast(),samplingDist,pts,maxT));
        }
        newT.set(newT.size()-1,maxT);
        ArrayList<double[]> result = new ArrayList<>(newT.size());
        for( double t : newT) {
            result.add(catmullRomSample(pts,t));
        }
        return result;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static double dist(ArrayList<double[]> pts, int a, int b) {
        double dx = pts.get(b)[0] - pts.get(a)[0];
        double dz = pts.get(b)[1] - pts.get(a)[1];
        return Math.sqrt(dx*dx + dz*dz);
    }

    private static double dist2(ArrayList<double[]> pts, int a, int b) {
        double dx = pts.get(b)[0] - pts.get(a)[0];
        double dz = pts.get(b)[1] - pts.get(a)[1];
        return dx*dx + dz*dz;
    }

    // Matches WorldPipeline.bilinearSample2D but operates on flat double[H*W] with id = row*W + col
    private static double bilinearSample(double[] src, int H, int W, double gy, double gx) {
        double y  = Math.clamp(gy, 0.0, H - 1.0);
        double x  = Math.clamp(gx, 0.0, W - 1.0);
        int    y0 = (int) y, y1 = Math.min(H - 1, y0 + 1);
        int    x0 = (int) x, x1 = Math.min(W - 1, x0 + 1);
        double wy = y - y0,  wx = x - x0;
        return (1-wy)*(1-wx)*src[y0*W+x0]
             + (1-wy)*wx    *src[y0*W+x1]
             + wy    *(1-wx)*src[y1*W+x0]
             + wy    *wx    *src[y1*W+x1];
    }

    public List<Channel> getChannels() {
        return channels;
    }
}
