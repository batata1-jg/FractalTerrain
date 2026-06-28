package me.batata_1.fractal_terrain.math.ds;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import me.batata_1.fractal_terrain.storage.Persistable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build-once, query-many 2-D spatial index — the immutable, memory-lean counterpart of
 * {@link QuadTree}. The full point set is supplied at construction and the structure is frozen;
 * there is no insert/remove afterwards, so the tree is <b>naturally concurrent</b> and needs no
 * lock.
 *
 * <p>Memory layout (vs. {@link QuadTree}, which duplicates every point into a {@code Set} on every
 * node along its root-to-leaf path and stores explicit bounds per node):
 *
 * <ul>
 *   <li>{@link #points} — every point stored <b>once</b>, laid out in euler-tour (depth-first) order
 *       so each node owns a contiguous slice. May contain {@code null} dummy slots; queries skip
 *       them.
 *   <li>{@link #nodes} — a flat {@link Node} array. Each {@code Node} is just two ints: the number
 *       of real points in its square ({@code count}) and a dual-purpose {@code id} — the base index
 *       of its 4 contiguous children in {@code nodes} (internal node) or the start index of its
 *       point slice in {@code points} (leaf). Node bounds are <b>not</b> stored; they are recomputed
 *       on the fly during traversal.
 * </ul>
 *
 * <p>Quadrant assignment ({@link #findSection}) maps a point onto an infinite tiling of cell size
 * {@code m}: {@code floorMod(floor(coord / m), 2)} per axis. The root square is snapped to a
 * power-of-two aligned cell that contains the whole point set, so this absolute tiling agrees
 * exactly with recursive midpoint bisection — construction, the sort comparator, and query descent
 * all use the same quadrant rule.
 *
 * <p><b>Node squares &amp; traversal coordinates ({@code ox}, {@code oz}, {@code size}).</b> Node
 * bounds are never stored (see {@link #nodes}); instead every recursive walk — construction
 * ({@link #buildInto}), queries ({@link #queryRec}), and validation ({@link #validateRec}) — threads
 * the current node's square explicitly through three parameters: {@code ox} and {@code oz} are the
 * coordinates of the square's <em>lower corner</em> (minimum X and minimum Z), and {@code size} is its
 * side length, so the square spans the half-open box {@code [ox, ox + size) × [oz, oz + size)}. The
 * root square is {@code (rootOriginX, rootOriginZ, rootSize)}. Descending into child quadrant
 * {@code k} halves the side ({@code cs = size / 2}) and offsets the origin by the quadrant's bits —
 * {@code (ox + qx·cs, oz + qz·cs)} with {@code qx = k >> 1}, {@code qz = k & 1}. Because this is the
 * same arithmetic {@link #findSection} uses, bounds can be recomputed on the fly rather than stored.
 *
 * <p><b>Debugging.</b> Two zero-overhead-when-off aids are built in: flip {@link #DEBUG_QUERY} to
 * {@code true} for an indented per-node trace of {@link #queryRec} (prune / accept / leaf-scan /
 * descend decisions), and call {@link #validate()} (or set {@link #VALIDATE_ON_BUILD}) to assert the
 * frozen structure is internally consistent — counts, contiguous point slices, and points-within-square.
 * {@link #debugPrint()} dumps the whole node tree. Both flags are compile-time constants, so when
 * {@code false} the JIT elides the guarded branches entirely.
 *
 * <p>Implements {@link Persistable} exactly like {@link QuadTree}: serializable iff a
 * {@link Persistable} point prototype is supplied (else the backing {@code Storage} is cache-only).
 */
public final class ImmutableQuadTree<T extends QuadTreePoint> implements Persistable<ImmutableQuadTree<T>> {

    /** Quadrant order (qx high bit, qz low bit): PP=0, PQ=1, QP=2, QQ=3. */
    private static final int X = 0;

    private static final int Z = 1;

    private static final int DEFAULT_MAX_DEPTH = 12;
    private static final int DEFAULT_MAX_POINTS_NODE = 8;

    /**
     * When {@code true}, {@link #queryRec} emits an indented, per-node trace (visit / prune / accept /
     * leaf-scan / descend) through {@link #LOG}. Compile-time constant: when {@code false} the JIT
     * removes the guarded branches, so production queries pay nothing.
     */
    private static final boolean DEBUG_QUERY = true;

    /**
     * When {@code true}, the constructor runs {@link #validate()} after freezing the tree and throws on
     * any structural inconsistency. Handy while changing the build/sort logic; leave {@code false} in
     * production (validation is O(nodes + points)).
     */
    private static final boolean VALIDATE_ON_BUILD = false;

    /** Format tag for the binary tile layout written by {@link #serialize()} ("IQT1"). */
    private static final int MAGIC = 0x49515431;

    private static final Logger LOG = LoggerFactory.getLogger(ImmutableQuadTree.class);

    /**
     * A node of the frozen tree. {@code count} is the number of real points contained in this node's
     * square; {@code id} is the base index of this node's 4 contiguous children in {@code nodes}
     * (internal node) or the start index of its point slice in {@code points} (leaf node).
     */
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

    /** Aligned root square: lower corner and side length (power-of-two grid anchor). */
    private final double rootOriginX;

    private final double rootOriginZ;
    private final double rootSize;

    /**
     * A point used only to {@linkplain Persistable#deserialize(byte[]) deserialize} stored point
     * chunks (its own state is ignored). Non-null only on trees built as deserialization prototypes
     * or with a known point type.
     */
    private final T pointPrototype;

    /** Convenience: default depth/leaf-capacity, no point prototype (cache-only). */
    public ImmutableQuadTree(double[] min, double[] max, List<T> points) {
        this(min, max, points, null, DEFAULT_MAX_DEPTH, DEFAULT_MAX_POINTS_NODE);
    }

    /** Convenience: default depth/leaf-capacity with a (de)serialization prototype. */
    public ImmutableQuadTree(double[] min, double[] max, List<T> points, T pointPrototype) {
        this(min, max, points, pointPrototype, DEFAULT_MAX_DEPTH, DEFAULT_MAX_POINTS_NODE);
    }

    /**
     * Builds and freezes the tree.
     *
     * @param min lower corner of the intended coverage window (retained for serialization)
     * @param max upper corner of the intended coverage window
     * @param inputPoints the full point set; copied, not mutated
     * @param pointPrototype prototype used to rebuild points on {@link #deserialize(byte[])}; may be
     *     {@code null} for a transient, never-persisted index
     * @param maxDepth maximum subdivision depth
     * @param maxPointsNode leaf capacity; must be {@code >= 4}
     */
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

        // Snap the root to a power-of-two aligned cell containing every point, so the absolute
        // findSection tiling matches recursive bisection at every level. Alignment is derived from the
        // point bbox only — the supplied min/max is coverage metadata (it may be unbounded ±INF).
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
            while (Math.floor(lowX / m0) != Math.floor(highX / m0) || Math.floor(lowZ / m0) != Math.floor(highZ / m0)) {
                m0 *= 2.0;
            }
            this.rootSize = m0;
            this.rootOriginX = Math.floor(lowX / m0) * m0;
            this.rootOriginZ = Math.floor(lowZ / m0) * m0;
        }

        sorted.sort(this::comparator);
        this.points = sorted.toArray((T[]) new QuadTreePoint[0]);

        final ArrayList<Node> nodeList = new ArrayList<>();
        nodeList.add(PLACEHOLDER); // root at index 0
        buildInto(nodeList, 0, 0, this.points.length, 0, rootOriginX, rootOriginZ, rootSize);
        this.nodes = nodeList.toArray(new Node[0]);

        if (VALIDATE_ON_BUILD && !validate())
            throw new IllegalStateException("ImmutableQuadTree failed self-validation after build (see log)");
    }

    /**
     * The euler-tour ordering comparator: orders points by their quadrant (coarse-to-fine), so that
     * points sharing a node are contiguous and the four child quadrants appear in PP&lt;PQ&lt;QP&lt;QQ order.
     *
     * <p>NOTE: implemented here as the Morton/Z-order comparison consistent with {@link #findSection}
     * so the structure builds correctly end-to-end; review/replace if a different euler-tour order is
     * desired.
     */
    private int comparator(T a, T b) {
        double m = rootSize / 2.0;
        for (int level = 0; level <= maxDepth; level++, m /= 2.0) {
            final int sa = findSection(a, m);
            final int sb = findSection(b, m);
            if (sa != sb) return Integer.compare(sa, sb);
        }
        return 0;
    }

    /**
     * Maps {@code pt} to one of the four quadrants of an infinite tiling of cell size {@code m}:
     * {@code floorMod(floor(coord / m), 2)} per axis, combined as {@code qx * 2 + qz}. {@code floorMod}
     * (not {@code %}) keeps negative coordinates in {0, 1}.
     */
    private int findSection(T pt, double m) {
        final int qx = (int) Math.floorMod((long) Math.floor(pt.get(X) / m), 2L);
        final int qz = (int) Math.floorMod((long) Math.floor(pt.get(Z) / m), 2L);
        return qx * 2 + qz;
    }

    /**
     * Fills {@code nodes[slot]} for the (pre-sorted) point range {@code [start, end)} and recurses into
     * its four child quadrants. {@code (ox, oz, size)} is this node's square — lower corner
     * {@code (ox, oz)} and side {@code size} (see the class javadoc) — used only to position the child
     * squares; bounds are not persisted.
     */
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

    public List<T> getPointsInBox(final double[] b, final double[] d) {
        if (b.length != 2 || d.length != 2) throw new IllegalStateException();
        final List<T> out = new ArrayList<>();
        query(new QuadTreeShape.QuadTreeBox(b, d), out);
        return out;
    }

    public List<T> getPointsInCircle(final double[] center, final double r) {
        if (center.length != 2) throw new IllegalStateException();
        final List<T> out = new ArrayList<>();
        query(new QuadTreeShape.QuadTreeCircle(center, r), out);
        return out;
    }

    public List<double[]> getPointCoordsInBox(final double[] b, final double[] d) {
        return getPointsInBox(b, d).stream().map(QuadTreePoint::toArray).toList();
    }

    private void query(final QuadTreeShape shape, final List<T> out) {
        if (nodes.length == 0) return;
        queryRec(shape, 0, rootOriginX, rootOriginZ, rootSize, 0, 0, out);
    }

    /**
     * Recursive query walk. {@code (ox, oz, size)} is the current node's square (lower corner + side;
     * see the class javadoc); {@code pointsStart} is the start of this node's contiguous slice in
     * {@link #points}. Set {@link #DEBUG_QUERY} to trace each node's decision.
     */
    private void queryRec(
            final QuadTreeShape shape,
            final int idx,
            final double ox,
            final double oz,
            final double size,
            final int depth,
            final int pointsStart,
            final List<T> out) {
        final Node n = nodes[idx];
        if (DEBUG_QUERY) trace(depth, "visit node[%d] square=[%.3f,%.3f)+%.3f count=%d", idx, ox, oz, size, n.count);
        if (n.count == 0) return;
        final double[] p0 = {ox, oz};
        final double[] p1 = {ox + size, oz + size};
        if (shape.notIntersect(p0, p1)) {
            if (DEBUG_QUERY) trace(depth, "  prune (disjoint from query shape)");
            return;
        }
        if (shape.contains(p0, p1)) {
            if (DEBUG_QUERY) trace(depth, "  accept whole node (%d pts)", n.count);
            collect(pointsStart, pointsStart + n.count, out);
            return;
        }
        final boolean leaf = n.count <= maxPointsNode || depth == maxDepth;
        if (leaf) {
            if (DEBUG_QUERY) trace(depth, "  leaf-scan %d pts", n.count);
            for (int i = pointsStart; i < pointsStart + n.count; i++) {
                final T pt = points[i];
                if (pt != null && shape.contains(pt)) out.add(pt);
            }
            return;
        }
        if (DEBUG_QUERY) trace(depth, "  descend into 4 children @ base %d", n.id);
        final double cs = size / 2.0;
        int childStart = pointsStart;
        for (int k = 0; k < 4; k++) {
            final int childIdx = n.id + k;
            final int qx = k >> 1;
            final int qz = k & 1;
            queryRec(shape, childIdx, ox + qx * cs, oz + qz * cs, cs, depth + 1, childStart, out);
            childStart += nodes[childIdx].count;
        }
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
    // Debugging / self-checking (see DEBUG_QUERY, VALIDATE_ON_BUILD)
    // -------------------------------------------------------------------------

    /**
     * Checks that the frozen tree is internally consistent and returns {@code true} when it is. Every
     * failed invariant is logged via {@link #LOG} (it does not throw, so it is safe to call from tests
     * or assertions). Verifies, by walking the same {@code (ox, oz, size)} squares the queries use:
     *
     * <ul>
     *   <li>node/child indices stay in bounds and a node's slice stays within {@link #points};
     *   <li>each internal node's {@code count} equals the sum of its children's counts, and the children
     *       form a contiguous, gap-free partition of the parent's point slice (leaf {@code id} ==
     *       slice start);
     *   <li>every point physically lies inside its leaf node's square {@code [ox, ox+size) ×
     *       [oz, oz+size)} — i.e. the build's quadrant sort agreed with {@link #findSection};
     *   <li>the root count equals the total number of stored points.
     * </ul>
     */
    public boolean validate() {
        final List<String> errors = new ArrayList<>();
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

    /** {@link #validate()} variant that throws {@link IllegalStateException} instead of returning false. */
    public void validateOrThrow() {
        if (!validate()) throw new IllegalStateException("ImmutableQuadTree failed self-validation (see log)");
    }

    /**
     * Recursively validates the subtree rooted at {@code idx} over its square {@code (ox, oz, size)} and
     * point slice starting at {@code pointsStart}; appends any problems to {@code errors} and returns the
     * node's reported point {@code count} (so the parent can accumulate child slice offsets).
     */
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
                    errors.add("point (" + x + "," + z + ") outside leaf node[" + idx + "] square [" + ox + ","
                            + oz + ")+" + size);
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
        if (sum != n.count)
            errors.add("internal node[" + idx + "] count " + n.count + " != sum of children " + sum);
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
        final long bytesPerPoint = 48; // QuadTreePoint payload, rough
        return (long) nodes.length * bytesPerNode + (long) points.length * bytesPerPoint;
    }

    /**
     * Serialize to a flat little-endian byte array:
     * {@code [magic, maxDepth, maxPointsNode, min, max, pointCount, (chunkLen, chunk)...]}, where each
     * chunk is a real point's own {@link Persistable#serialize()} bytes. Only real points are written;
     * the node/dummy layout is rebuilt on {@link #deserialize(byte[])}. Throws
     * {@link UnsupportedOperationException} when the point type is not {@link Persistable}.
     */
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

    /**
     * Rebuild a tree from bytes produced by {@link #serialize()} by deserializing each point with this
     * tree's {@link #pointPrototype} and re-running construction. Returns a fresh instance; the
     * receiver is only a prototype.
     */
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
