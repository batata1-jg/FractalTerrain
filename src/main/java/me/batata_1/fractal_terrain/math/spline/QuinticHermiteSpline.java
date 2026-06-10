package me.batata_1.fractal_terrain.math.spline;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.math.VectorOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.MAX_SPLINE_LENGTH;
import static me.batata_1.fractal_terrain.debug.Debug.getLogger;
import static me.batata_1.fractal_terrain.math.VectorOps.distance;

public record QuinticHermiteSpline(
        ArrayList<double[]> points,
        ArrayList<double[]> velocity,
        ArrayList<double[]> acceleration
    ) {


    private static final Logger LOG = getLogger(QuinticHermiteSpline.class);

    public static QuinticHermiteSpline createCatmullRom(ArrayList<double[]> pt) {
        ArrayList<double[]> dxy = new ArrayList<>();
        dxy.add(new double[2]);
        for(int i = 1; i< pt.size()-1 ; i++) {
            dxy.add(VectorOps.scale(VectorOps.sub(pt.get(i+1), pt.get(i-1)),0.5));
        }
        dxy.add(new double[2]);
        ArrayList<double[]> ddxy = new ArrayList<>();
        ddxy.add(new double[2]);
        for(int i=1 ; i<pt.size()-1 ; i++) {
            ddxy.add(VectorOps.scale(VectorOps.sub(dxy.get(i+1), dxy.get(i-1)),0.5));
        }
        ddxy.add(new double[2]);
      //  LOG.info("creatingSpline size:{}",pt.size());
        return new QuinticHermiteSpline(pt,dxy,ddxy);
    }

    public int getSize() {
        return points.size();
    }

    public double[] sample(double t) {
        final int id = Math.clamp((int) Math.floor(t),0,points.size()-2);
        final double[] p0 = points.get(id);
        final double[] p1 = points.get(id + 1);
        final double[] v0 = velocity.get(id);
        final double[] v1 = velocity.get(id + 1);
        final double[] a0 = acceleration.get(id);
        final double[] a1 = acceleration.get(id + 1);
        final double u = t - id;
        final int dim = points.getFirst().length;
        final double[] sample = new double[dim];
        for (int d = 0; d < dim; d++) {
            sample[d] =
                p0[d]
                + v0[d] * u
                + (0.5 * a0[d]) * u * u
                + (10 * (p1[d] - p0[d]) - 2 * (2 * v1[d] + 3 * v0[d]) + 0.5 * (a1[d] - 3 * a0[d])) * u * u * u
                + (-15 * (p1[d] - p0[d]) + 7 * v1[d] + 8 * v0[d] - a1[d] + 1.5 * a0[d]) * u * u * u * u
                + ((6 * (p1[d] - p0[d])) - 3 * (v1[d] + v0[d]) + 0.5 * (a1[d] - a0[d])) * u * u * u * u * u;
        }
        return sample;
    }

    public double nextInSpline(double curT, double dist) {
        double p = curT;
        double q = getMaxT();
        double m;
        for (int step = 0; step <= FractalTerrainConfig.BINARY_SEARCH_MAX_STEPS; step++) {
            m = (p + q) / 2;
            if (distance(sample(curT), sample(m)) <= dist&&m<=getMaxT()) p = m;
            else q = m;
            if (p >= q) break;
        }
        return p;
    }

    public QuinticHermiteSpline reSample(double samplingDist) {
        if (this.checkNaN()) throw new IllegalStateException();
        if(points.size()<2) throw new IllegalStateException("spline must have at least 2 points");
        ArrayList<Double> newT = new ArrayList<>();
        newT.add(0.0);
        for (int counter = 0; counter < MAX_SPLINE_LENGTH; counter++) {
            newT.add(nextInSpline(newT.getLast(), samplingDist));
            if (newT.getLast() >= getMaxT()) {
                newT.add(getMaxT());
                return new QuinticHermiteSpline(
                        new ArrayList<>(newT.stream().map(this::sample).toList()),
                        new ArrayList<>(newT.stream().map(this::firstDerivative).toList()),
                        new ArrayList<>(newT.stream().map(this::secondDerivative).toList())
                );
            }
        }
        LOG.error("sampled {}-{}",sample(newT.removeLast()),sample(newT.removeLast()));
        final int samples = 40;
        final double[][] doubles = new double[samples][2];
        for(int i =0; i<samples ; i++) doubles[i] = sample(97.9+i*0.005);
        LOG.error("spline after maxT: {}",Arrays.deepToString(doubles));
        LOG.error("pt98:{} pt97:{}",points.get(98),points.get(97));
        LOG.error("sample98:{} sample97:{}",sample(98),sample(97));
        Debug.spline.see(this, "defective_spline");
        throw new RuntimeException("exeded spline len");
    }

    private Double getMaxT() {
        // special case, in this spline, maxT and indexes coincide
        return (double) getSize() - 1;
    }

    public boolean checkNaN() {
        for (double[] p : points) for (double v : p) if (Double.isNaN(v)) return true;
        for (double[] v : velocity) for (double x : v) if (Double.isNaN(x)) return true;
        for (double[] a : acceleration) for (double x : a) if (Double.isNaN(x)) return true;
        return false;
    }

    public double curvature(double t) {
        final double[] dxy = firstDerivative(t);
        final double absDxy = VectorOps.magnitude(dxy);
        if (absDxy < 1e-8) return 0;
        final double[] ddxy = secondDerivative(t);
        return VectorOps.cross2D(dxy, ddxy) / absDxy * absDxy * absDxy;
    }

    public double curvature(int t) {
        return curvature((double) t);
    }

    public double[] normal(double t) {
        double[] d = VectorOps.normalize(firstDerivative(t));
        return new double[]{d[1], -d[0]};
    }

    public double[] normal(int t) {
        return normal((double) t);
    }

    public double[] firstDerivative(double t) {
        final int id = (int) Math.floor(t);
        if (id == points.size() - 1) return velocity.getLast();
        final double[] p0 = points.get(id);
        final double[] p1 = points.get(id + 1);
        final double[] v0 = velocity.get(id);
        final double[] v1 = velocity.get(id + 1);
        final double[] a0 = acceleration.get(id);
        final double[] a1 = acceleration.get(id + 1);
        final double u = t - id;
        final int dim = points.getFirst().length;
        final double[] sample = new double[dim];
        for (int d = 0; d < dim; d++) {
            sample[d] =
                    v0[d]
                    + (a0[d]) * u
                    + 3 * (10 * (p1[d] - p0[d]) - 2 * (2 * v1[d] + 3 * v0[d]) + 0.5 * (a1[d] - 3 * a0[d])) * u * u
                    + 4 * (-15 * (p1[d] - p0[d]) + 7 * v1[d] + 8 * v0[d] - a1[d] + 1.5 * a0[d]) * u * u * u
                    + 5 * (6*(p1[d] - p0[d]) - 3*(v1[d] + v0[d]) + 0.5*(a1[d] - a0[d])) * u * u * u * u;
        }
        return sample;
    }

    public double[] secondDerivative(double t) {
        final int id = (int) Math.floor(t);
        if (id == points.size() - 1) return acceleration.getLast();
        final double[] p0 = points.get(id);
        final double[] p1 = points.get(id + 1);
        final double[] v0 = velocity.get(id);
        final double[] v1 = velocity.get(id + 1);
        final double[] a0 = acceleration.get(id);
        final double[] a1 = acceleration.get(id + 1);
        final double u = t - id;
        final int dim = points.getFirst().length;
        final double[] sample = new double[dim];
        for (int d = 0; d < dim; d++) {
            sample[d] =
                    a0[d]
                    + (60 * (p1[d] - p0[d]) - 12 * (2 * v1[d] + 3 * v0[d]) + 3 * (a1[d] - 3 * a0[d])) * u
                    + 12 * (-15 * (p1[d] - p0[d]) + 7 * v1[d] + 8 * v0[d] - a1[d] + 1.5 * a0[d]) * u * u
                    + 20 * (6 * (p1[d] - p0[d]) - 3 * (v1[d] + v0[d]) + 0.5 * (a1[d] - a0[d])) * u * u * u;
        }
        return sample;
    }

    public QuinticHermiteSpline keepOnly(final ArrayList<Integer> indexes) {
        return new QuinticHermiteSpline(
                new ArrayList<>(indexes.stream().map(points::get).toList()),
                new ArrayList<>(indexes.stream().map(velocity::get).toList()),
                new ArrayList<>(indexes.stream().map(acceleration::get).toList())
        );
    }

    public void appendBack() {

    }

    public void appendFront() {

    }

}
