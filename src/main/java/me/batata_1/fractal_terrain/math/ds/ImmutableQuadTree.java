package me.batata_1.fractal_terrain.math.ds;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import me.batata_1.fractal_terrain.storage.Persistable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build-once, query-many 2-D spatial index — the frozen counterpart to {@link QuadTree}, for cached
 * per-tile point sets fully known at construction.
 *
 * <p>Exists to avoid {@link QuadTree}'s per-node point duplication and its lock: points are stored once
 * in euler-tour order so each node owns a contiguous slice, and node bounds are recomputed during
 * traversal rather than stored. Being frozen is what lets queries run lock-free.
 *
 * <p><b>Root-square alignment is load-bearing and currently unsound — see {@code README.md}.</b>
 */
public final class ImmutableQuadTree<T extends SpatialIndexPoint>
        implements SpatialIndex<T>, Persistable<ImmutableQuadTree<T>> {

    /** Quadrant order (qx high bit, qz low bit): PP=0, PQ=1, QP=2, QQ=3. */
    private static final int X = 0;

    private static final int Z = 1;

    // 10,16
    private static final int DEFAULT_MAX_DEPTH = 8;
    private static final int DEFAULT_MAX_POINTS_NODE = 24;

    /** Turn on to trace each {@link #query} decision when a query returns the wrong points. */
    private static final boolean DEBUG_QUERY = false;

    /** Turn on while changing the build or sort logic; the constructor then self-checks and throws. */
    private static final boolean VALIDATE_ON_BUILD = false;

    /** Turn on to catch NaN query arguments, which otherwise return empty rather than failing. */
    private static final boolean CHECK_QUERY_NAN = false;

    /** Turn on to log root-cell sizing when points go missing near the root square's edge. */
    private static final boolean DEBUG_BUILD = false;

    /** Format tag for the binary tile layout written by {@link #serialize()} ("IQT1"). */
    private static final int MAGIC = 0x49515431;

    private static final Logger LOG = LoggerFactory.getLogger(ImmutableQuadTree.class);

    /** Two ints per node, so the tree stays cache-resident; {@code id} means child base on an internal
     *  node and point-slice start on a leaf. */
    public record Node(int count, int id) {}

    /** Placeholder slot reserved during construction before its real {@link Node} is computed. */
    private static final Node PLACEHOLDER = new Node(0, 0);

    private final T[] points;
    private final Node[] nodes;
    private final int maxDepth;
    private final int maxPointsNode;

    /** Constructor-supplied coverage window, retained for serialization round-trips. */
    private final double[] min;

    private final double[] max;

    /** Root square, sized from the point bbox and <b>not</b> grid-aligned — see {@code README.md}. */
    private final double rootOriginX;

    private final double rootOriginZ;
    private final double rootSize;

    /** Supplies the concrete point type on deserialize; null means this tree can only be cached. */
    private final T pointPrototype;

    /** Convenience: default depth/leaf-capacity, no point prototype (cache-only). */
    public ImmutableQuadTree(double[] min, double[] max, List<T> points) {
        this(min, max, points, null, DEFAULT_MAX_DEPTH, DEFAULT_MAX_POINTS_NODE);
    }

    /** Convenience: default depth/leaf-capacity with a (de)serialization prototype. */
    public ImmutableQuadTree(double[] min, double[] max, List<T> points, T pointPrototype) {
        this(min, max, points, pointPrototype, DEFAULT_MAX_DEPTH, DEFAULT_MAX_POINTS_NODE);
    }

    /** Builds and freezes the tree. {@code min}/{@code max} are coverage metadata only — the root square
     *  is derived from the point bbox — and {@code pointPrototype} may be null for an index never
     *  persisted. */
    @SuppressWarnings("unchecked")
    public ImmutableQuadTree(
            double[] min, double[] max, List<T> inputPoints, T pointPrototype, int maxDepth, int maxPointsNode) {
        //  LOG.info("hello");
        if (maxPointsNode < 4) throw new IllegalArgumentException("maxPointsNode must be >= 4");
        this.min = min.clone();
        this.max = max.clone();
        this.maxDepth = maxDepth;
        this.maxPointsNode = maxPointsNode;
        this.pointPrototype = pointPrototype;

        final ArrayList<T> sorted = new ArrayList<>(inputPoints);
        sorted.removeIf(Objects::isNull);

        // Reject non-finite point coordinates up front. A NaN or infinity poisons the alignment loop
        // below — NaN makes every `floor(coord/m0)` comparison true forever (it never converges), and an
        // infinity drives m0 to overflow — and corrupts every downstream findSection / bounds compare.
        for (final T p : sorted) {
            final double px = p.get(X);
            final double pz = p.get(Z);
            if (!Double.isFinite(px) || !Double.isFinite(pz))
                throw new IllegalArgumentException(
                        "ImmutableQuadTree input point has non-finite coordinates (" + px + ", " + pz + "): " + p);
        }

        // Size the root square to the point bbox (plus a fixed margin) so every point falls inside it.
        // findSection tiling matches recursive bisection at every level because every level halves this
        // same root square. Derived from the point bbox only — the supplied min/max is coverage
        // metadata (it may be unbounded ±INF).
        if (sorted.isEmpty()) {
            this.rootSize = 1.0;
            this.rootOriginX = 0.0;
            this.rootOriginZ = 0.0;
        } else {
            double lowX = Double.POSITIVE_INFINITY, lowZ = Double.POSITIVE_INFINITY;
            double highX = Double.NEGATIVE_INFINITY, highZ = Double.NEGATIVE_INFINITY;
            for (T p : sorted) {
                lowX = Math.min(lowX, p.get(X));
                lowZ = Math.min(lowZ, p.get(Z));
                highX = Math.max(highX, p.get(X));
                highZ = Math.max(highZ, p.get(Z));
            }
            double m0 = 1.0;
            int doublings = 0;

            double width = highX - lowX;
            double height = highZ - lowZ;
            //            while (((int) Math.floor(lowX / m0)) != ((int) Math.floor(highX / m0)) || ((int)
            // Math.floor(lowZ / m0)) != ((int) Math.floor(highZ / m0))) {
            //                m0 *= 2.0;
            //                doublings++;
            //                if(doublings>30) {
            //                    LOG.error("doubles more than 30 tiles, area is becoming large. Condition: {} != {} ||
            // {} != {}",
            //                            ((int) Math.floor(lowX / m0)) , ((int) Math.floor(highX / m0)) , ((int)
            // Math.floor(lowZ / m0)) , ((int) Math.floor(highZ / m0)));
            //                    throw new IllegalArgumentException("ImmutableQuadTree point spread too large to align
            // a"
            //                            + " power-of-two root cell (size overflowed); bbox x=[" + lowX + "," + highX +
            // "] z=["
            //                            + lowZ + "," + highZ + "] doublings: " + doublings);
            //                }
            //                // Guard against doubling past the finite double range: with finite (pre-checked) inputs
            // the
            //                // loop converges long before this, so an overflow means the point spread is
            // unrepresentable.
            //                if (Double.isInfinite(m0))
            //                    throw new IllegalArgumentException("ImmutableQuadTree point spread too large to align
            // a"
            //                            + " power-of-two root cell (size overflowed); bbox x=[" + lowX + "," + highX +
            // "] z=["
            //                            + lowZ + "," + highZ + "] doublings: " + doublings);
            //            }
            this.rootSize = Math.max(width, height) + 10.0;
            this.rootOriginX = lowX - 5.0;
            this.rootOriginZ = lowZ - 5.0;
            if (DEBUG_BUILD)
                LOG.info(
                        "ImmutableQuadTree align: {} pts, bbox x=[{},{}] z=[{},{}] -> root=[{},{})+{} ({} doublings)",
                        sorted.size(),
                        lowX,
                        highX,
                        lowZ,
                        highZ,
                        rootOriginX,
                        rootOriginZ,
                        rootSize,
                        doublings);
        }

        sorted.sort(this::comparator);
        this.points = sorted.toArray((T[]) new SpatialIndexPoint[0]);

        final ArrayList<Node> nodeList = new ArrayList<>();
        nodeList.add(PLACEHOLDER); // root at index 0
        buildInto(nodeList, 0, 0, this.points.length, 0, rootOriginX, rootOriginZ, rootSize);
        this.nodes = nodeList.toArray(new Node[0]);

        if (VALIDATE_ON_BUILD && !validate())
            throw new IllegalStateException("ImmutableQuadTree failed self-validation after build (see log)");
    }

    /** Orders points so each node's slice is contiguous, which is what removes the per-node point
     *  duplication {@link QuadTree} pays. Must agree with {@link #findSection} or slices break. */
    private int comparator(T a, T b) {
        double m = rootSize / 2.0;
        for (int level = 0; level <= maxDepth; level++, m /= 2.0) {
            final int sa = findSection(a, m);
            final int sb = findSection(b, m);
            if (sa != sb) return Integer.compare(sa, sb);
        }
        return 0;
    }

    /** The single quadrant rule the sort, the build and every query must agree on. Its tiling is
     *  anchored at 0, not at the root square — see {@code README.md}. */
    private int findSection(T pt, double m) {
        final int qx = (int) Math.floorMod((long) Math.floor(pt.get(X) / m), 2L);
        final int qz = (int) Math.floorMod((long) Math.floor(pt.get(Z) / m), 2L);
        return qx * 2 + qz;
    }

    /** Packs the pre-sorted range into {@code nodes[slot]} and recurses.
     *  {@code (ox, oz, size)} is the node's square: lower corner plus side length. */
    private void buildInto(
            ArrayList<Node> nodeList, int slot, int start, int end, int depth, double ox, double oz, double size) {
        final int count = end - start;
        if (count <= maxPointsNode || depth == maxDepth) {
            nodeList.set(slot, new Node(count, start)); // leaf: id = points slice start
            return;
        }
        final double cs = size / 2.0;
        // Points are pre-sorted in quadrant order, so each child bucket is a contiguous sub-range.
        final int[] bnd = new int[5];
        bnd[0] = start;
        int c = 0;
        for (int i = start; i < end; i++) {
            final int s = findSection(points[i], cs);
            while (c < s) bnd[++c] = i;
        }
        while (c < 4) bnd[++c] = end;

        final int childBase = nodeList.size();
        for (int k = 0; k < 4; k++) nodeList.add(PLACEHOLDER); // reserve 4 contiguous slots
        nodeList.set(slot, new Node(count, childBase)); // internal: id = children base

        for (int k = 0; k < 4; k++) {
            final int qx = k >> 1;
            final int qz = k & 1;
            buildInto(nodeList, childBase + k, bnd[k], bnd[k + 1], depth + 1, ox + qx * cs, oz + qz * cs, cs);
        }
    }

    public int numPoints() {
        return nodes.length == 0 ? 0 : nodes[0].count;
    }

    @Override
    public int numEntries() {
        return numPoints();
    }

    /** Every real (non-null) stored point, in euler-tour order. */
    @Override
    public List<T> getAllEntries() {
        final List<T> allEntries = new ArrayList<>(numPoints());
        collect(0, points.length, allEntries);
        return allEntries;
    }

    /** Acceptance test for {@link #anyPointInCircle}; gets squared distance so callers can skip a sqrt. */
    @FunctionalInterface
    public interface PointTest<T> {
        boolean test(T point, double distSqToCenter);
    }

    public List<T> getPointsInBox(final double[] b, final double[] d) {
        return getPointsInBox(b, d, new ArrayList<>());
    }

    /** Buffer-reusing overload for hot paths. {@code out} is appended to, never cleared. */
    public List<T> getPointsInBox(final double[] b, final double[] d, final List<T> out) {
        if (b.length != 2 || d.length != 2) throw new IllegalStateException();
        if (CHECK_QUERY_NAN) checkBoxQueryNaN(b, d);
        query(new SpatialIndexShape.Rectangle(b, d), out);
        if (CHECK_QUERY_NAN) checkResultNaN("getPointsInBox", out);
        return out;
    }

    public List<T> getPointsInCircle(final double[] center, final double r) {
        return getPointsInCircle(center, r, new ArrayList<>());
    }

    /** Buffer-reusing overload for hot paths. {@code out} is appended to, never cleared. */
    public List<T> getPointsInCircle(final double[] center, final double r, final List<T> out) {
        if (center.length != 2) throw new IllegalStateException();
        if (CHECK_QUERY_NAN) checkCircleQueryNaN(center, r);
        query(new SpatialIndexShape.Circle(center, r), out);
        if (CHECK_QUERY_NAN) checkResultNaN("getPointsInCircle", out);
        return out;
    }

    /** Existence-only counterpart to {@link #getPointsInCircle}, for callers that need a yes/no and
     *  should not pay for a result list. */
    public boolean anyPointInCircle(final double[] center, final double radius, final PointTest<T> test) {
        if (center.length != 2) throw new IllegalStateException();
        if (CHECK_QUERY_NAN) checkCircleQueryNaN(center, radius);
        return anyInCircle(center, radius, test);
    }

    public List<double[]> getPointCoordsInBox(final double[] b, final double[] d) {
        return getPointsInBox(b, d).stream().map(SpatialIndexPoint::toArray).toList();
    }

    /** The shared walk behind every public query. Written against parallel primitive stacks rather than
     *  recursion so a query allocates nothing per node — this is the hot path. */
    private void query(final SpatialIndexShape shape, final List<T> out) {
        if (nodes.length == 0) return;

        // Stack depth is bounded by the DFS frontier (≤ 3·maxDepth + 4); 64 covers the defaults and
        // grows on demand. Six parallel arrays hold the frame tuple.
        int capacity = 64;
        int[] idxStack = new int[capacity];
        double[] oxStack = new double[capacity];
        double[] ozStack = new double[capacity];
        double[] sizeStack = new double[capacity];
        int[] depthStack = new int[capacity];
        int[] startStack = new int[capacity];

        idxStack[0] = 0;
        oxStack[0] = rootOriginX;
        ozStack[0] = rootOriginZ;
        sizeStack[0] = rootSize;
        depthStack[0] = 0;
        startStack[0] = 0;
        int sp = 1;

        final double[] lo = new double[2];
        final double[] hi = new double[2];

        while (sp > 0) {
            sp--;
            final int idx = idxStack[sp];
            final double ox = oxStack[sp];
            final double oz = ozStack[sp];
            final double size = sizeStack[sp];
            final int depth = depthStack[sp];
            final int pointsStart = startStack[sp];

            final Node n = nodes[idx];
            if (DEBUG_QUERY)
                trace(depth, "visit node[%d] square=[%.3f,%.3f)+%.3f count=%d", idx, ox, oz, size, n.count);
            if (n.count == 0) continue;
            lo[0] = ox;
            lo[1] = oz;
            hi[0] = ox + size;
            hi[1] = oz + size;
            if (shape.notIntersect(lo, hi)) {
                if (DEBUG_QUERY) trace(depth, "  prune (disjoint from query shape)");
                continue;
            }
            if (shape.contains(lo, hi)) {
                if (DEBUG_QUERY) trace(depth, "  accept whole node (%d pts)", n.count);
                collect(pointsStart, pointsStart + n.count, out);
                continue;
            }
            final boolean leaf = n.count <= maxPointsNode || depth == maxDepth;
            if (leaf) {
                if (DEBUG_QUERY) trace(depth, "  leaf-scan %d pts", n.count);
                for (int i = pointsStart; i < pointsStart + n.count; i++) {
                    final T pt = points[i];
                    if (pt != null && shape.contains(pt)) out.add(pt);
                }
                continue;
            }
            if (DEBUG_QUERY) trace(depth, "  descend into 4 children @ base %d", n.id);

            if (sp + 4 > capacity) {
                capacity <<= 1;
                idxStack = Arrays.copyOf(idxStack, capacity);
                oxStack = Arrays.copyOf(oxStack, capacity);
                ozStack = Arrays.copyOf(ozStack, capacity);
                sizeStack = Arrays.copyOf(sizeStack, capacity);
                depthStack = Arrays.copyOf(depthStack, capacity);
                startStack = Arrays.copyOf(startStack, capacity);
            }
            final double cs = size / 2.0;
            int childStart = pointsStart;
            for (int k = 0; k < 4; k++) {
                final int childIdx = n.id + k;
                idxStack[sp] = childIdx;
                oxStack[sp] = ox + (k >> 1) * cs;
                ozStack[sp] = oz + (k & 1) * cs;
                sizeStack[sp] = cs;
                depthStack[sp] = depth + 1;
                startStack[sp] = childStart;
                sp++;
                childStart += nodes[childIdx].count;
            }
        }
    }

    /** {@link #query}'s short-circuiting twin, kept separate so the common gather path carries no
     *  early-exit test. */
    private boolean anyInCircle(final double[] center, final double radius, final PointTest<T> test) {
        if (nodes.length == 0) return false;
        final SpatialIndexShape shape = new SpatialIndexShape.Circle(center, radius);
        final double radiusSq = radius * radius;

        int capacity = 64;
        int[] idxStack = new int[capacity];
        double[] oxStack = new double[capacity];
        double[] ozStack = new double[capacity];
        double[] sizeStack = new double[capacity];
        int[] depthStack = new int[capacity];
        int[] startStack = new int[capacity];

        idxStack[0] = 0;
        oxStack[0] = rootOriginX;
        ozStack[0] = rootOriginZ;
        sizeStack[0] = rootSize;
        depthStack[0] = 0;
        startStack[0] = 0;
        int sp = 1;

        final double[] lo = new double[2];
        final double[] hi = new double[2];

        while (sp > 0) {
            sp--;
            final int idx = idxStack[sp];
            final double ox = oxStack[sp];
            final double oz = ozStack[sp];
            final double size = sizeStack[sp];
            final int depth = depthStack[sp];
            final int pointsStart = startStack[sp];

            final Node n = nodes[idx];
            if (n.count == 0) continue;
            lo[0] = ox;
            lo[1] = oz;
            hi[0] = ox + size;
            hi[1] = oz + size;
            if (shape.notIntersect(lo, hi)) continue;
            // Bulk-accept and leaf-scan collapse into one scan here: either way every candidate in the
            // slice gets its distSq computed and offered to the test.
            final boolean leaf = n.count <= maxPointsNode || depth == maxDepth;
            if (leaf || shape.contains(lo, hi)) {
                for (int i = pointsStart; i < pointsStart + n.count; i++) {
                    final T pt = points[i];
                    if (pt == null) continue;
                    final double dx = pt.get(X) - center[0];
                    final double dz = pt.get(Z) - center[1];
                    final double distSq = dx * dx + dz * dz;
                    if (distSq > radiusSq) continue;
                    if (test.test(pt, distSq)) return true;
                }
                continue;
            }

            if (sp + 4 > capacity) {
                capacity <<= 1;
                idxStack = Arrays.copyOf(idxStack, capacity);
                oxStack = Arrays.copyOf(oxStack, capacity);
                ozStack = Arrays.copyOf(ozStack, capacity);
                sizeStack = Arrays.copyOf(sizeStack, capacity);
                depthStack = Arrays.copyOf(depthStack, capacity);
                startStack = Arrays.copyOf(startStack, capacity);
            }
            final double cs = size / 2.0;
            int childStart = pointsStart;
            for (int k = 0; k < 4; k++) {
                final int childIdx = n.id + k;
                idxStack[sp] = childIdx;
                oxStack[sp] = ox + (k >> 1) * cs;
                ozStack[sp] = oz + (k & 1) * cs;
                sizeStack[sp] = cs;
                depthStack[sp] = depth + 1;
                startStack[sp] = childStart;
                sp++;
                childStart += nodes[childIdx].count;
            }
        }
        return false;
    }

    /** Indented {@code String.format} log line for the {@link #DEBUG_QUERY} / {@link #debugPrint} traces. */
    private static void trace(int depth, String fmt, Object... args) {
        LOG.info("{}{}", "  ".repeat(Math.max(0, depth)), String.format(fmt, args));
    }

    /** Append the non-null points of {@code [from, to)} to {@code out}. */
    private void collect(final int from, final int to, final List<T> out) {
        for (int i = from; i < to; i++) {
            final T pt = points[i];
            if (pt != null) out.add(pt);
        }
    }

    // -------------------------------------------------------------------------
    // NaN / non-finite query guards (see CHECK_QUERY_NAN)
    // -------------------------------------------------------------------------

    /** True if any coordinate in {@code coords} is NaN. */
    public static boolean containsNaN(final double[] coords) {
        for (final double v : coords) if (Double.isNaN(v)) return true;
        return false;
    }

    /** Reports a NaN box query, which would otherwise return empty and read as a legitimate miss. */
    public boolean checkBoxQueryNaN(final double[] b, final double[] d) {
        boolean bad = false;
        if (containsNaN(b)) {
            LOG.warn("getPointsInBox: NaN in corner {}", Arrays.toString(b));
            bad = true;
        }
        if (containsNaN(d)) {
            LOG.warn("getPointsInBox: NaN in extent {}", Arrays.toString(d));
            bad = true;
        }
        return bad;
    }

    /** Reports a NaN circle query, which would otherwise return empty and read as a legitimate miss. */
    public boolean checkCircleQueryNaN(final double[] center, final double radius) {
        boolean bad = false;
        if (containsNaN(center)) {
            LOG.warn("getPointsInCircle: NaN in center {}", Arrays.toString(center));
            bad = true;
        }
        if (Double.isNaN(radius)) {
            LOG.warn("getPointsInCircle: NaN radius");
            bad = true;
        }
        return bad;
    }

    /** Distinguishes corrupt indexed data from a bad query by checking results, not arguments. */
    public int checkResultNaN(final String where, final List<T> out) {
        int count = 0;
        for (final T pt : out) {
            if (pt != null && containsNaN(pt.getCoords())) {
                LOG.warn("{}: result point has NaN coords: {}", where, pt);
                count++;
            }
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Debugging / self-checking (see DEBUG_QUERY, VALIDATE_ON_BUILD)
    // -------------------------------------------------------------------------

    /** Self-check for the frozen structure; logs rather than throws so tests and assertions can call it.
     *  The point-inside-its-leaf-square check is the one that catches sort/query quadrant disagreement. */
    public boolean validate() {
        final List<String> errors = new ArrayList<>();
        checkRootConstants(errors);
        if (nodes.length == 0) {
            if (points.length != 0) errors.add("empty node array but " + points.length + " points");
        } else {
            final int reached = validateRec(0, rootOriginX, rootOriginZ, rootSize, 0, 0, errors);
            if (reached != points.length)
                errors.add("traversal reached " + reached + " points but array holds " + points.length);
            if (nodes[0].count != points.length)
                errors.add("root count " + nodes[0].count + " != point count " + points.length);
        }
        if (!errors.isEmpty()) {
            LOG.warn("ImmutableQuadTree.validate found {} problem(s):", errors.size());
            for (final String e : errors) LOG.warn("  - {}", e);
            return false;
        }
        return true;
    }

    /** Checked first, because a non-finite root square makes every later quadrant decision garbage.
     *  {@code min}/{@code max} may legitimately be infinite, so only NaN is an error there. */
    private void checkRootConstants(final List<String> errors) {
        if (!Double.isFinite(rootOriginX)) errors.add("rootOriginX is not finite: " + rootOriginX);
        if (!Double.isFinite(rootOriginZ)) errors.add("rootOriginZ is not finite: " + rootOriginZ);
        if (!Double.isFinite(rootSize)) errors.add("rootSize is not finite: " + rootSize);
        else if (rootSize <= 0.0) errors.add("rootSize must be > 0 but is " + rootSize);
        // min/max are caller-supplied coverage metadata and may legitimately be ±INF, so only NaN is wrong.
        if (containsNaN(min)) errors.add("min coverage window has NaN: " + Arrays.toString(min));
        if (containsNaN(max)) errors.add("max coverage window has NaN: " + Arrays.toString(max));
    }

    /** {@link #validate()} variant that throws {@link IllegalStateException} instead of returning false. */
    public void validateOrThrow() {
        if (!validate()) throw new IllegalStateException("ImmutableQuadTree failed self-validation (see log)");
    }

    /** Recursive half of {@link #validate}; returns the node's count so the parent can accumulate child
     *  slice offsets. */
    private int validateRec(
            int idx, double ox, double oz, double size, int depth, int pointsStart, List<String> errors) {
        if (idx < 0 || idx >= nodes.length) {
            errors.add("node index out of range: " + idx);
            return 0;
        }
        final Node n = nodes[idx];
        final boolean leaf = n.count <= maxPointsNode || depth == maxDepth;
        if (leaf) {
            if (n.id != pointsStart)
                errors.add("leaf node[" + idx + "] id " + n.id + " != expected slice start " + pointsStart);
            final int to = pointsStart + n.count;
            if (pointsStart < 0 || to > points.length) {
                errors.add("leaf node[" + idx + "] slice [" + pointsStart + "," + to + ") out of points["
                        + points.length + "]");
                return n.count;
            }
            for (int i = pointsStart; i < to; i++) {
                final T pt = points[i];
                if (pt == null) continue;
                final double x = pt.get(X);
                final double z = pt.get(Z);
                if (x < ox || x >= ox + size || z < oz || z >= oz + size)
                    errors.add("point (" + x + "," + z + ") outside leaf node[" + idx + "] square [" + ox + "," + oz
                            + ")+" + size);
            }
            return n.count;
        }
        // Internal node: id is the base of 4 contiguous children.
        if (n.id <= idx || n.id + 3 >= nodes.length) {
            errors.add("internal node[" + idx + "] child base " + n.id + " out of range");
            return n.count;
        }
        final double cs = size / 2.0;
        int childStart = pointsStart;
        int sum = 0;
        for (int k = 0; k < 4; k++) {
            final int childIdx = n.id + k;
            final int qx = k >> 1;
            final int qz = k & 1;
            validateRec(childIdx, ox + qx * cs, oz + qz * cs, cs, depth + 1, childStart, errors);
            childStart += nodes[childIdx].count;
            sum += nodes[childIdx].count;
        }
        if (sum != n.count) errors.add("internal node[" + idx + "] count " + n.count + " != sum of children " + sum);
        return n.count;
    }

    /** Logs the full node tree (indented by depth, with each node's square and count) via {@link #LOG}. */
    public void debugPrint() {
        LOG.info(
                "ImmutableQuadTree: {} nodes, {} points, root square=[{},{})+{}",
                nodes.length,
                points.length,
                rootOriginX,
                rootOriginZ,
                rootSize);
        if (nodes.length != 0) debugPrintRec(0, rootOriginX, rootOriginZ, rootSize, 0, 0);
    }

    private void debugPrintRec(int idx, double ox, double oz, double size, int depth, int pointsStart) {
        final Node n = nodes[idx];
        final boolean leaf = n.count <= maxPointsNode || depth == maxDepth;
        trace(
                depth,
                "node[%d] %s square=[%.3f,%.3f)+%.3f count=%d %s=%d",
                idx,
                leaf ? "leaf" : "internal",
                ox,
                oz,
                size,
                n.count,
                leaf ? "sliceStart" : "childBase",
                n.id);
        if (leaf) return;
        final double cs = size / 2.0;
        int childStart = pointsStart;
        for (int k = 0; k < 4; k++) {
            final int childIdx = n.id + k;
            debugPrintRec(childIdx, ox + (k >> 1) * cs, oz + (k & 1) * cs, cs, depth + 1, childStart);
            childStart += nodes[childIdx].count;
        }
    }

    @Override
    public long byteSize() {
        final long bytesPerNode = 8; // two ints
        final long bytesPerPoint = 48; // SpatialIndexPoint payload, rough
        return (long) nodes.length * bytesPerNode + (long) points.length * bytesPerPoint;
    }

    /** Writes points only; the node layout is cheaper to rebuild on load than to store. Requires a
     *  {@link Persistable} point prototype, else the backing store stays cache-only. */
    @Override
    public byte[] serialize() {
        if (!(pointPrototype instanceof Persistable<?> protoPersistable))
            throw new UnsupportedOperationException(
                    "ImmutableQuadTree.serialize requires a Persistable point prototype: " + pointPrototype);
        try {
            protoPersistable.serialize();
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedOperationException(
                    "ImmutableQuadTree.serialize requires an implemented Persistable point prototype: "
                            + pointPrototype.getClass());
        }
        int realCount = 0;
        long payload = 0;
        final byte[][] chunks = new byte[points.length][];
        for (final T pt : points) {
            if (pt == null) continue;
            if (!(pt instanceof Persistable<?> persistablePoint))
                throw new UnsupportedOperationException("ImmutableQuadTree point type is not Persistable: "
                        + pt.getClass().getName());
            final byte[] chunk = persistablePoint.serialize();
            chunks[realCount++] = chunk;
            payload += Integer.BYTES + chunk.length;
        }
        final int header = Integer.BYTES /* magic */
                + 2 * Integer.BYTES /* maxDepth, maxPointsNode */
                + 4 * Double.BYTES /* min, max */
                + Integer.BYTES /* count */;
        final ByteBuffer buf = ByteBuffer.allocate((int) (header + payload)).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(MAGIC);
        buf.putInt(maxDepth);
        buf.putInt(maxPointsNode);
        buf.putDouble(min[0]).putDouble(min[1]);
        buf.putDouble(max[0]).putDouble(max[1]);
        buf.putInt(realCount);
        for (int i = 0; i < realCount; i++) {
            buf.putInt(chunks[i].length);
            buf.put(chunks[i]);
        }
        return buf.array();
    }

    /** Re-runs construction over the restored points. Returns a fresh tree; the receiver is only a
     *  prototype and is not modified. */
    @Override
    @SuppressWarnings("unchecked")
    public ImmutableQuadTree<T> deserialize(byte[] rawBytes) {
        if (!(pointPrototype instanceof Persistable<?> protoPersistable))
            throw new UnsupportedOperationException(
                    "ImmutableQuadTree.deserialize requires a Persistable point prototype: " + pointPrototype);
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final int magic = buf.getInt();
        if (magic != MAGIC)
            throw new IllegalStateException("incompatible ImmutableQuadTree tile format (got 0x"
                    + Integer.toHexString(magic) + "); delete the fractal_terrain tile cache to regenerate");
        final int storedMaxDepth = buf.getInt();
        final int storedMaxPointsNode = buf.getInt();
        final double[] storedMin = {buf.getDouble(), buf.getDouble()};
        final double[] storedMax = {buf.getDouble(), buf.getDouble()};
        final int count = buf.getInt();
        final List<T> restored = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final int len = buf.getInt();
            final byte[] chunk = new byte[len];
            buf.get(chunk);
            restored.add((T) protoPersistable.deserialize(chunk));
        }
        return new ImmutableQuadTree<>(
                storedMin, storedMax, restored, pointPrototype, storedMaxDepth, storedMaxPointsNode);
    }
}
