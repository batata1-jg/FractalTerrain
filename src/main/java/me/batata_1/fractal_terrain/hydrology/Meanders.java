package me.batata_1.fractal_terrain.hydrology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class Meanders {

    private static final double OMEGA           = -1.0;
    private static final double GAMMA           =  2.5;
    private static final double K               =  1.0;
    private static final double CF              =  0.011;
    private static final double DT              =  9_460_800.0;  // ~109.5 days in seconds
    private static final double MAX_SLOPE       =  1.0;
    private static final double CHANNEL_FALLOFF =  0.05;
    private static final double SAMPLING_DIST   =  50.0;
    private static final int    UPSTREAM_WINDOW =  1500;

    private final int      gridSize;
    private final double   metersPerCell;
    private final double[] gradMag;

    private final List<Channel> channels = new ArrayList<>();

    private final Random rng;

    private static final class Channel {
        double   width, depth;
        double[] pts;           // flat [x0,z0, x1,z1, ...], capacity >= 2*n
        int      n;
        double[] localRates, migRates;
    }

    public Meanders(int gridSize, double metersPerCell, double[] gradMag, long seed) {
        this.gridSize      = gridSize;
        this.metersPerCell = metersPerCell;
        this.gradMag       = gradMag;
        this.rng           = new Random(seed);
    }

    public void addChannel(double[] xzPts, int nPts, double width) {
        Channel ch    = new Channel();
        ch.width      = width;
        ch.depth      = Math.max(1.0, Math.pow(width / 18.8, 1.0 / 1.41));  // Konsoer 2013
        ch.n          = nPts;
        ch.pts        = Arrays.copyOf(xzPts, 2 * nPts);
        ch.localRates = new double[nPts];
        ch.migRates   = new double[nPts];
        channels.add(ch);
    }

    public void step() {
        for (Channel ch : channels) {
            computeLocalRates(ch);
            computeMigrationRates(ch);
            migrate(ch);
            manageCutoffs(ch);
            double[] resampled = catmullRomResample(ch.pts, ch.n);
            int m         = resampled.length / 2;
            ch.pts        = resampled;
            ch.n          = m;
            ch.localRates = new double[m];
            ch.migRates   = new double[m];
        }
    }

    public void simulate(int n) {
        for (int i = 0; i < n; i++) step();
    }

    public int getChannelCount() {
        return channels.size();
    }

    public int getChannelPointCount(int i) {
        return channels.get(i).n;
    }

    public double[] getChannelPts(int i) {
        Channel ch = channels.get(i);
        return Arrays.copyOf(ch.pts, 2 * ch.n);
    }

    public void addConstraint(double cx, double cz, double radius, double effect) {
        // TODO: PointConstraint gradient influence
    }

    // =========================================================================
    // Per-point geometry helpers
    // =========================================================================

    private static double[] tangent(double[] pts, int n, int i) {
        if (i == 0) {
            return new double[]{pts[2] - pts[0], pts[3] - pts[1]};
        } else if (i == n - 1) {
            return new double[]{pts[2*(n-1)] - pts[2*(n-2)], pts[2*(n-1)+1] - pts[2*(n-2)+1]};
        } else {
            return new double[]{(pts[2*(i+1)] - pts[2*(i-1)]) * 0.5, (pts[2*(i+1)+1] - pts[2*(i-1)+1]) * 0.5};
        }
    }

    private static double[] normal(double[] pts, int n, int i) {
        double[] t   = tangent(pts, n, i);
        double   len = Math.sqrt(t[0]*t[0] + t[1]*t[1]);
        if (len < 1e-12) return new double[]{0.0, 0.0};
        // rotate 90° CCW: {-z, x}
        return new double[]{-t[1] / len, t[0] / len};
    }

    private static double curvature(double[] pts, int n, int i) {
        if (i == 0 || i == n - 1) return 0.0;
        double[] d     = tangent(pts, n, i);
        double[] tPrev = tangent(pts, n, i - 1);
        double[] tNext = tangent(pts, n, i + 1);
        double ddx   = (tNext[0] - tPrev[0]) * 0.5;
        double ddz   = (tNext[1] - tPrev[1]) * 0.5;
        double dx    = d[0], dz = d[1];
        double denom = Math.pow(dx*dx + dz*dz, 1.5);
        if (denom < 1e-12) return 0.0;
        return (dx * ddz - dz * ddx) / denom;
    }

    // =========================================================================
    // Algorithm steps
    // =========================================================================

    private static void computeLocalRates(Channel ch) {
        for (int i = 0; i < ch.n; i++) {
            ch.localRates[i] = ch.width * curvature(ch.pts, ch.n, i);
        }
    }

    private static void computeMigrationRates(Channel ch) {
        double alpha = K * 2.0 * CF / ch.depth;

        double arcLen = 0.0;
        for (int i = 0; i < ch.n - 1; i++) arcLen += dist(ch.pts, i, i + 1);
        double chord  = dist(ch.pts, 0, ch.n - 1);
        double sinuos = Math.pow(arcLen / Math.max(chord, 1e-9), -2.0 / 3.0);

        for (int i = 0; i < ch.n; i++) {
            double cumDist = 0.0, sumR0 = 0.0, sumG = 0.0;
            for (int j = i; j >= Math.max(0, i - UPSTREAM_WINDOW); j--) {
                if (j < i) cumDist += dist(ch.pts, j + 1, j);
                double g = Math.exp(-alpha * cumDist);
                sumR0 += ch.localRates[j] * g;
                sumG  += g;
            }
            if (sumG == 0.0) sumG = 1.0;
            ch.migRates[i] = (OMEGA * ch.localRates[i] + GAMMA * sumR0 / sumG) * sinuos;
        }
    }

    private void migrate(Channel ch) {
        double f = CHANNEL_FALLOFF;
        for (int i = 1; i < ch.n - 1; i++) {
            double t  = i / (double)(ch.n - 1);
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
            double gy      = ch.pts[2*i]     / metersPerCell;
            double gx      = ch.pts[2*i + 1] / metersPerCell;
            double gSample = bilinearSample(gradMag, gridSize, gridSize, gy, gx);
            double wt      = Math.max(0.0, 1.0 - gSample / MAX_SLOPE);

            double   w  = wf * wt;
            double[] nm = normal(ch.pts, ch.n, i);
            ch.pts[2*i]     += w * DT * ch.migRates[i] * nm[0];
            ch.pts[2*i + 1] += w * DT * ch.migRates[i] * nm[1];
        }
    }

    private static void manageCutoffs(Channel ch) {
        double widthSq = ch.width * ch.width;
        restart:
        while (true) {
            for (int j = 0; j < ch.n - 4; j++) {
                for (int k = j + 4; k < ch.n; k++) {
                    if (dist2(ch.pts, j, k) < widthSq) {
                        // TODO: save pts[j..k] to oxbow list for future rendering
                        int remaining = ch.n - k;
                        System.arraycopy(ch.pts,        2*k, ch.pts,        2*(j+1), 2*remaining);
                        System.arraycopy(ch.localRates, k,   ch.localRates, j+1,     remaining);
                        System.arraycopy(ch.migRates,   k,   ch.migRates,   j+1,     remaining);
                        ch.n -= (k - j - 1);
                        continue restart;
                    }
                }
            }
            break;
        }
    }

    private static double[] catmullRomResample(double[] pts, int n) {
        double[] s = new double[n];
        for (int i = 1; i < n; i++) s[i] = s[i-1] + dist(pts, i-1, i);

        int      m      = Math.max(2, (int)(s[n-1] / SAMPLING_DIST) + 1);
        double[] result = new double[2 * m];

        for (int k = 0; k < m; k++) {
            double t = Math.min(k * SAMPLING_DIST, s[n-1]);

            // Binary search: find seg such that s[seg] <= t < s[seg+1]
            int seg = n - 2, lo = 0, hi = n - 2;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (s[mid + 1] <= t) lo = mid + 1;
                else { seg = mid; hi = mid - 1; }
            }

            double segLen = s[seg+1] - s[seg];
            double u      = (segLen < 1e-12) ? 0.0 : (t - s[seg]) / segLen;

            int p0i = Math.max(0, seg - 1);
            int p1i = seg;
            int p2i = Math.min(n - 1, seg + 1);
            int p3i = Math.min(n - 1, seg + 2);

            for (int dim = 0; dim < 2; dim++) {
                double P0 = pts[2*p0i + dim];
                double P1 = pts[2*p1i + dim];
                double P2 = pts[2*p2i + dim];
                double P3 = pts[2*p3i + dim];
                result[2*k + dim] = 0.5 * (2*P1
                        + (-P0 + P2)                  * u
                        + (2*P0 - 5*P1 + 4*P2 - P3)  * u*u
                        + (-P0 + 3*P1 - 3*P2 + P3)   * u*u*u);
            }
        }
        return result;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static double dist(double[] pts, int a, int b) {
        double dx = pts[2*b]   - pts[2*a];
        double dz = pts[2*b+1] - pts[2*a+1];
        return Math.sqrt(dx*dx + dz*dz);
    }

    private static double dist2(double[] pts, int a, int b) {
        double dx = pts[2*b]   - pts[2*a];
        double dz = pts[2*b+1] - pts[2*a+1];
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
}