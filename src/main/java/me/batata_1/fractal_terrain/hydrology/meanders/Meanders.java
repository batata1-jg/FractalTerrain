package me.batata_1.fractal_terrain.hydrology.meanders;

import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.spline.CatmullRomSpline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public final class Meanders {

    private static final double INF = 1e9;
    private static final double OMEGA           = -1.0;
    private static final double GAMMA           =  2.5;
    private static final double K               =  1.0;
    private static final double CF              =  0.011;
    private static final double DT              =  50;  // ~109.5 days in seconds
    private static final double MAX_SLOPE       =  1.0;
    private static final double CHANNEL_FALLOFF =  0.05;
    private static final double SAMPLING_DIST   =  10.0;
    private static final int    UPSTREAM_WINDOW =  1500;
    private static final Logger LOG = LoggerFactory.getLogger(Meanders.class);

    private final int      gridSize;
    private final double   metersPerCell;
    private final double[] gradMag;

    private final List<Channel> channels = new ArrayList<>();

    public Meanders(int gridSize, double metersPerCell, double[] gradMag) {
        this.gridSize      = gridSize;
        this.metersPerCell = metersPerCell;
        this.gradMag       = gradMag;
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
            ArrayList<double[]> resampled = CatmullRomSpline.reSample(ch.pts, SAMPLING_DIST);
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

    private static void computeLocalRates(Channel ch) {
        for (int i = 0; i < ch.pts.size(); i++) {
            ch.localRates.set(i, ch.width * CatmullRomSpline.curvature(ch.pts, i));
        }
    }

    private static void computeMigrationRates(Channel ch) {
        int    n     = ch.pts.size();
        double alpha = K * 2.0 * CF / ch.depth;

        double arcLen = 0.0;
        for (int i = 0; i < n - 1; i++) arcLen += VectorOps.distance(ch.pts.get(i),ch.pts.get(i+1));
        double chord  = VectorOps.distance(ch.pts.getFirst(),ch.pts.get(n-1));
        double sinuosity = Math.pow(arcLen / Math.max(chord, 1e-9), -2.0 / 3.0);

        for (int i = 0; i < n; i++) {
            double cumDist = 0.0, sumR0 = 0.0, sumG = 0.0;
            for (int j = i; j >= Math.max(0, i - UPSTREAM_WINDOW); j--) {
                if (j < i) cumDist += VectorOps.distance(ch.pts.get(j),ch.pts.get(j+1));
                double g = Math.exp(-alpha * cumDist);
                sumR0 += ch.localRates.get(j) * g;
                sumG  += g;
            }
            if (sumG == 0.0) sumG = 1.0;
            ch.migRates.set(i, (OMEGA * ch.localRates.get(i) + GAMMA * sumR0 / sumG) * sinuosity);
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
            double   w  = wf * wt;
            final double rate = w * DT * ch.migRates.get(i);
            double[] newPt = VectorOps.add(ch.pts.get(i), VectorOps.scale(CatmullRomSpline.normal(ch.pts,i),-rate));
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

        @Override
        public String toString() {
            return "["+ptCoords.toString()+" "+id+"]";
        }
    }

    private static final QuadTree<ChannelPt> quadTree = new QuadTree<>(new double[]{-INF,-INF},new double[]{INF,INF});

    private void manageCutoffs(Channel ch) {
        quadTree.clear();
        final ChannelPt[] pts = new ChannelPt[ch.pts.size()];
        for(int id=0 ; id<ch.pts.size() ; id++) pts[id] = new ChannelPt(ch.pts.get(id),id);
        for(ChannelPt pt : pts) quadTree.insertPoint(pt);
        ArrayList<Integer> newPathIndexes = new ArrayList<>();

        LOG.info("before cuttoff {}",ch.pts.size());
        int maxListSize = 0;
        for(int id=0 ; id<ch.pts.size() ; id++) {
            newPathIndexes.add(id);
            double[] pt = ch.pts.get(id);
            List<ChannelPt> ptList = quadTree.getPointsInBox(
                    VectorOps.sub(pt,VectorOps.scale(new double[]{1,1},SAMPLING_DIST)),
                    VectorOps.add(pt,VectorOps.scale(new double[]{1,1},SAMPLING_DIST))
                    );
            maxListSize = Math.max(maxListSize, ptList.size());
            int intersectionIndex=-1;
            for(ChannelPt cpt : ptList) {
                if(cpt.id<=id+1) continue;
                intersectionIndex=cpt.id;
                break;
            }
            if(intersectionIndex==-1) continue;
            cutRiverSection(id,intersectionIndex,pts);
            id = intersectionIndex;
        }
        ArrayList<double[]> newPts = new ArrayList<>();
        ArrayList<Double> newMigRates = new ArrayList<>();
        ArrayList<Double> newLocalRates = new ArrayList<>();
        for(int id : newPathIndexes) {
            newPts.add(ch.pts.get(id));
            newLocalRates.add(ch.localRates.get(id));
            newMigRates.add(ch.migRates.get(id));
        }
        ch.pts = newPts;
        LOG.info("after cuttoff {} list size:{}",ch.pts.size(),maxListSize);
        ch.migRates = newMigRates;
        ch.localRates = newLocalRates;
    }

    private void cutRiverSection(int from, int to,ChannelPt[] pts) {
        LOG.info("cutting from {} {}",from,to);
        for(int i=from; i<to ; i++) {
            quadTree.removePoint(pts[i]);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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
