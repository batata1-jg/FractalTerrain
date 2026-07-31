package me.batata_1.fractal_terrain.math;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;

/**
 * Traces a binary mask's border into smooth splines — the outline counterpart to
 * {@link Skeletonizer}'s medial axis.
 *
 * <p>Deliberately mirrors {@link Skeletonizer}'s public shape, so a caller can swap outline for
 * skeleton without restructuring. Use this when a filled region is best described by its edge, such
 * as a coastline.
 *
 * <p>Output points are mask-relative; callers add any tile or pad origin themselves.
 */
public class MarchingSquares {

    private final int minPolylineLength;
    private final double resampleSpacing;

    /** @param minPolylineLength discards contours too short to fit meaningfully */
    public MarchingSquares(int minPolylineLength, double resampleSpacing) {
        this.minPolylineLength = minPolylineLength;
        this.resampleSpacing = resampleSpacing;
    }

    /** Border contours as fitted splines. Degenerate results are skipped rather than returned. */
    public List<QuinticHermiteSpline> trace(boolean[][] mask) {
        final List<List<double[]>> contours = traceContours(mask);
        final List<QuinticHermiteSpline> splines = new ArrayList<>();
        for (List<double[]> contour : contours) {
            if (contour.size() < minPolylineLength) continue;
            final ArrayList<double[]> contourPoints = new ArrayList<>(contour);
            try {
                final QuinticHermiteSpline fitted = QuinticHermiteSpline.createCatmullRom(contourPoints);
                splines.add(fitted.reSample(resampleSpacing));
            } catch (RuntimeException degenerate) {
                // Degenerate spline (e.g. MAX_SPLINE_LENGTH exceeded). Skip this contour.
            }
        }
        return splines;
    }

    /** Border as filled cells rather than edge crossings — what the global-river pass needs to derive
     *  a coast mask it can index by pixel. */
    public static boolean[][] borderMask(boolean[][] mask) {
        final int height = mask.length;
        final boolean[][] border = new boolean[height][];
        for (int row = 0; row < height; row++) border[row] = new boolean[mask[row].length];
        if (height < 2) return border;
        final int width = mask[0].length;
        if (width < 2) return border;

        for (int row = 0; row < height - 1; row++) {
            for (int col = 0; col < width - 1; col++) {
                final boolean topLeft = mask[row][col];
                final boolean topRight = mask[row][col + 1];
                final boolean bottomRight = mask[row + 1][col + 1];
                final boolean bottomLeft = mask[row + 1][col];
                final int liveCount =
                        (topLeft ? 1 : 0) + (topRight ? 1 : 0) + (bottomRight ? 1 : 0) + (bottomLeft ? 1 : 0);
                final boolean straddlesContour = liveCount != 0 && liveCount != 4;
                if (!straddlesContour) continue;
                if (topLeft) border[row][col] = true;
                if (topRight) border[row][col + 1] = true;
                if (bottomRight) border[row + 1][col + 1] = true;
                if (bottomLeft) border[row + 1][col] = true;
            }
        }
        return border;
    }

    // -------------------------------------------------------------------------
    // Marching-squares core
    // -------------------------------------------------------------------------

    /** Boundary polylines in true path order, not raster order, so a spline fit follows the contour.
     *  Closed contours repeat their start point so the fit closes cleanly. */
    public static List<List<double[]>> traceContours(boolean[][] mask) {
        final int height = mask.length;
        if (height < 2) return new ArrayList<>();
        final int width = mask[0].length;
        if (width < 2) return new ArrayList<>();

        final List<Segment> segments = new ArrayList<>();
        final Map<Long, Segment> bySource = new HashMap<>();

        for (int r = 0; r < height - 1; r++) {
            for (int c = 0; c < width - 1; c++) {
                final boolean tl = mask[r][c];
                final boolean tr = mask[r][c + 1];
                final boolean br = mask[r + 1][c + 1];
                final boolean bl = mask[r + 1][c];
                final int code = (tl ? 8 : 0) | (tr ? 4 : 0) | (br ? 2 : 0) | (bl ? 1 : 0);

                switch (code) {
                    case 0, 15 -> {} // no crossing
                    case 8 -> addSegment(segments, bySource, r, c, Edge.L, Edge.T);
                    case 4 -> addSegment(segments, bySource, r, c, Edge.T, Edge.R);
                    case 2 -> addSegment(segments, bySource, r, c, Edge.R, Edge.B);
                    case 1 -> addSegment(segments, bySource, r, c, Edge.B, Edge.L);
                    case 12 -> addSegment(segments, bySource, r, c, Edge.L, Edge.R);
                    case 6 -> addSegment(segments, bySource, r, c, Edge.T, Edge.B);
                    case 3 -> addSegment(segments, bySource, r, c, Edge.R, Edge.L);
                    case 9 -> addSegment(segments, bySource, r, c, Edge.B, Edge.T);
                    case 10 -> { // saddle: TL & BR set
                        addSegment(segments, bySource, r, c, Edge.L, Edge.T);
                        addSegment(segments, bySource, r, c, Edge.R, Edge.B);
                    }
                    case 5 -> { // saddle: TR & BL set
                        addSegment(segments, bySource, r, c, Edge.T, Edge.R);
                        addSegment(segments, bySource, r, c, Edge.B, Edge.L);
                    }
                    case 7 -> addSegment(segments, bySource, r, c, Edge.T, Edge.L);
                    case 11 -> addSegment(segments, bySource, r, c, Edge.R, Edge.T);
                    case 13 -> addSegment(segments, bySource, r, c, Edge.B, Edge.R);
                    case 14 -> addSegment(segments, bySource, r, c, Edge.L, Edge.B);
                    default -> {}
                }
            }
        }

        // Keys that are some segment's destination; a source that is NOT a destination starts an
        // open contour (a boundary that runs into the mask edge). Seed those first so open contours
        // come out whole, then sweep up the remaining closed loops.
        final Set<Long> targets = new HashSet<>();
        for (Segment s : segments) targets.add(s.toKey);

        final List<List<double[]>> contours = new ArrayList<>();
        for (Segment s : segments) {
            if (!s.consumed && !targets.contains(s.fromKey)) contours.add(walk(s, bySource));
        }
        for (Segment s : segments) {
            if (!s.consumed) contours.add(walk(s, bySource));
        }
        return contours;
    }

    /** Follow {@code from → to} links from {@code seed} until the contour closes or dead-ends. */
    private static List<double[]> walk(Segment seed, Map<Long, Segment> bySource) {
        final List<double[]> contour = new ArrayList<>();
        final long startKey = seed.fromKey;
        contour.add(seed.from);
        Segment cur = seed;
        while (cur != null && !cur.consumed) {
            cur.consumed = true;
            contour.add(cur.to);
            if (cur.toKey == startKey) break; // closed loop: start point re-appended at the end
            cur = bySource.get(cur.toKey);
        }
        return contour;
    }

    private static void addSegment(
            List<Segment> segments, Map<Long, Segment> bySource, int r, int c, Edge from, Edge to) {
        final Segment seg = new Segment(from.point(r, c), to.point(r, c), from.key(r, c), to.key(r, c));
        segments.add(seg);
        bySource.putIfAbsent(seg.fromKey, seg);
    }

    /** Quantize a half-integer edge midpoint to an exact integer key. */
    private static long key(double row, double col) {
        return (Math.round(row * 2) << 32) | (Math.round(col * 2) & 0xffffffffL);
    }

    /** The four candidate edge midpoints of cell {@code (r, c)}. */
    private enum Edge {
        T,
        R,
        B,
        L;

        double[] point(int r, int c) {
            return switch (this) {
                case T -> new double[] {r, c + 0.5};
                case R -> new double[] {r + 0.5, c + 1};
                case B -> new double[] {r + 1, c + 0.5};
                case L -> new double[] {r + 0.5, c};
            };
        }

        long key(int r, int c) {
            final double[] p = point(r, c);
            return MarchingSquares.key(p[0], p[1]);
        }
    }

    private static final class Segment {
        final double[] from;
        final double[] to;
        final long fromKey;
        final long toKey;
        boolean consumed;

        Segment(double[] from, double[] to, long fromKey, long toKey) {
            this.from = from;
            this.to = to;
            this.fromKey = fromKey;
            this.toKey = toKey;
        }
    }
}
