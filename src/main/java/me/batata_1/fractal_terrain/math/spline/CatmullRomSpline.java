package me.batata_1.fractal_terrain.math.spline;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
import me.batata_1.fractal_terrain.math.VectorOps;

import java.util.ArrayList;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.MAX_SPLINE_LENGTH;
import static me.batata_1.fractal_terrain.math.VectorOps.distance;

//2D spline
public class CatmullRomSpline {

    final ArrayList<double[]> points;

    public CatmullRomSpline(ArrayList<double[]> points) {
        this.points = points;
    }

    // returns the parameter t of the next point in the spline at a distance of dist
    public static double nextInSpline(final double curT, final double dist, final ArrayList<double[]> pts, final double maxT) {
        double p = curT;
        double q = maxT;
        double m;
        for (int step = 0; step <= FractalTerrainConfig.BINARY_SEARCH_MAX_STEPS; step++) {
            m = (p + q) / 2;
            if (distance(sample(pts, curT), sample(pts, m)) <= dist) p = m;
            else q = m;
            if (p >= q) break;
        }
        return p;
    }

    // Returns the point on the Catmull-Rom spline at parameter t (segment-indexed).
    public static double[] sample(ArrayList<double[]> pts, double t) {
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
    public static ArrayList<double[]> reSample(ArrayList<double[]> pts, double samplingDist) {
        final double maxT = pts.size()-1;
        ArrayList<Double> newT = new ArrayList<>();
        newT.add(0.0);
        int counter = 0;
        while(newT.getLast()<maxT||counter> MAX_SPLINE_LENGTH) {
            newT.add(nextInSpline(newT.getLast(),samplingDist,pts,maxT));
            counter++;
        }
        if(counter == MAX_SPLINE_LENGTH) {
            Meanders m = new Meanders(300,10,new double[300*300]);
            m.addChannel(pts,0.001);
            Debug.river.see(m,"max_rier_");
            throw new RuntimeException("exeded max river");
        }
        newT.set(newT.size()-1,maxT);
        ArrayList<double[]> result = new ArrayList<>(newT.size());
        for( double t : newT) {
            result.add(sample(pts,t));
        }
        return result;
    }

    private static double[] tangent(ArrayList<double[]> pts, int i) {
        final double[] pi1 = pts.get(Math.min(i+1,pts.size()-1));
        final double[] pi0 = pts.get(Math.max(i-1,0));
        return VectorOps.div(VectorOps.sub(pi1,pi0),2.0);
    }

    public static double[] normal(ArrayList<double[]> pts, int i) {
        double[] t = VectorOps.normalize(tangent(pts,i));
        return new double[]{-t[1], t[0]};
    }

    public static double curvature(ArrayList<double[]> pts, int i) {
        double[] dxy = tangent(pts,i);
        double[] dxy0 = tangent(pts,i-1);
        double[] dxy1 = tangent(pts,i+1);
        double[] ddxy = VectorOps.div(VectorOps.sub(dxy1,dxy0),2.0);
        double absDxy = VectorOps.abs(dxy);
        return VectorOps.cross2D(dxy,ddxy) / absDxy*absDxy*absDxy;
    }

}
