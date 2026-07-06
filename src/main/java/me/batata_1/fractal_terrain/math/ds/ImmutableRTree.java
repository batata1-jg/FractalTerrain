package me.batata_1.fractal_terrain.math.ds;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import me.batata_1.fractal_terrain.storage.Persistable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build-once, query-many 2-D R-tree over {@link SpatialIndexShape}s — the shape-storing sibling of
 * {@link ImmutableQuadTree}. Where the quadtree indexes <em>points</em> and answers
 * region-collect queries, this indexes <em>extended shapes</em> (circles, rectangles — each with its
 * own minimum bounding rectangle) and answers <b>point-stabbing</b> queries: given a coordinate,
 * return every stored shape that contains it ({@link #queryContaining}), optionally with the search
 * boundary inflated by a radius, plus the early-exit twin {@link #anyContaining}.
 *
 * <p><b>Bulk load — Sort-Tile-Recursive (STR).</b> The full element set is supplied at construction
 * and packed bottom-up: entries are sorted by MBR center X, cut into {@code ceil(sqrt(numGroups))}
 * vertical slices, each slice sorted by center Z, then cut into groups of {@code leafCapacity}
 * (elements) / {@link #NODE_FANOUT} (nodes). The same tiling is applied level by level — physically
 * reordering each level into its STR order before cutting parents, so every parent's children form a
 * contiguous run — until a single root remains. The result is a fully-packed, spatially-clustered
 * tree with no insert/remove machinery, so it is <b>naturally concurrent</b> and needs no lock.
 *
 * <p><b>Memory layout.</b> Elements are stored once in STR leaf order in {@link #elements} (no
 * nulls). Nodes live in parallel primitive arrays — four {@code double[]} MBR corners plus two
 * {@code int[]}s ({@link #nodeChildStart}, {@link #nodeChildCount}) — with no per-node objects.
 * Levels are flattened leaves-first: nodes {@code [0, leafNodeCount)} are leaves (their
 * {@code childStart} indexes {@link #elements}), everything above is internal (their
 * {@code childStart} indexes the node arrays), and the <b>root is the last node</b>.
 *
 * <p><b>Debugging.</b> Mirrors the {@link ImmutableQuadTree} toolkit: {@link #validate()} /
 * {@link #validateOrThrow()} self-check the frozen structure (flip {@link #VALIDATE_ON_BUILD} to run
 * it in the constructor), and {@link #debugPrint()} dumps the node tree. Compile-time flags — zero
 * cost when off.
 *
 * <p>Implements {@link Persistable} exactly like {@link ImmutableQuadTree}: serializable iff a
 * {@link Persistable} element prototype whose own {@code serialize()} works is supplied (else the
 * backing {@code Storage} is cache-only). Only the elements are written; the tree is rebuilt on
 * {@link #deserialize}.
 */
public final class ImmutableRTree<T extends SpatialIndexShape>
        implements SpatialIndex<T>, Persistable<ImmutableRTree<T>> {

    private static final int X = 0;
    private static final int Z = 1;

    /** Format tag for the binary tile layout written by {@link #serialize()} ("IRT1"). */
    private static final int MAGIC = 0x49525431;

    /**
     * Default max elements per leaf. Stab queries return small hit sets, so a modest capacity keeps
     * leaf scans short while the node arrays stay a few percent of the element count. Overridable per
     * tree via the three-arg constructor (e.g. for benchmark sweeps).
     */
    private static final int DEFAULT_LEAF_CAPACITY = 16;

    /** Max children per internal node (the STR group size above the leaf level). */
    private static final int NODE_FANOUT = 16;

    /**
     * When {@code true}, the constructor runs {@link #validate()} after freezing the tree and throws on
     * any structural inconsistency. Handy while changing the build logic; leave {@code false} in
     * production (validation is O(nodes + elements)).
     */
    private static final boolean VALIDATE_ON_BUILD = false;

    private static final Logger LOG = LoggerFactory.getLogger(ImmutableRTree.class);

    /** Every stored element, exactly once, in STR leaf order (no null slots). */
    private final T[] elements;

    /** Per-node MBR corners, parallel to {@link #nodeChildStart}/{@link #nodeChildCount}. */
    private final double[] nodeMbrMinX;

    private final double[] nodeMbrMinZ;
    private final double[] nodeMbrMaxX;
    private final double[] nodeMbrMaxZ;

    /** Leaf: start of the node's element slice in {@link #elements}. Internal: first child node index. */
    private final int[] nodeChildStart;

    /** Leaf: element-slice length. Internal: number of children. */
    private final int[] nodeChildCount;

    /** Nodes {@code [0, leafNodeCount)} are leaves; the rest are internal; the root is the last node. */
    private final int leafNodeCount;

    private final int leafCapacity;

    /**
     * An element used only to {@linkplain Persistable#deserialize(byte[]) deserialize} stored element
     * chunks (its own state is ignored). {@code null} for a transient, never-persisted index.
     */
    private final T elementPrototype;

    /** Convenience: default leaf capacity. */
    public ImmutableRTree(List<T> inputElements, T elementPrototype) {
        this(inputElements, elementPrototype, DEFAULT_LEAF_CAPACITY);
    }

    /**
     * Builds and freezes the tree via STR bulk load.
     *
     * @param inputElements the full element set; copied, not mutated; {@code null} entries are skipped
     * @param elementPrototype prototype used to rebuild elements on {@link #deserialize(byte[])}; may
     *     be {@code null} for a transient, never-persisted index
     * @param leafCapacity max elements per leaf; must be {@code >= 2}
     */
    @SuppressWarnings("unchecked")
    public ImmutableRTree(List<T> inputElements, T elementPrototype, int leafCapacity) {
        if (leafCapacity < 2) throw new IllegalArgumentException("leafCapacity must be >= 2");
        this.leafCapacity = leafCapacity;
        this.elementPrototype = elementPrototype;

        final ArrayList<T> retained = new ArrayList<>(inputElements);
        retained.removeIf(Objects::isNull);
        final int elementCount = retained.size();

        // Per-element MBRs (reused scratch corners; writeMbrInto never allocates). A non-finite MBR
        // corner would silently break every pruning comparison downstream, so reject it up front —
        // mirroring ImmutableQuadTree's non-finite point guard.
        final double[] elementMbrMinX = new double[elementCount];
        final double[] elementMbrMinZ = new double[elementCount];
        final double[] elementMbrMaxX = new double[elementCount];
        final double[] elementMbrMaxZ = new double[elementCount];
        final double[] elementCenterX = new double[elementCount];
        final double[] elementCenterZ = new double[elementCount];
        final double[] scratchLowerCorner = new double[2];
        final double[] scratchUpperCorner = new double[2];
        for (int i = 0; i < elementCount; i++) {
            retained.get(i).writeMbrInto(scratchLowerCorner, scratchUpperCorner);
            if (!Double.isFinite(scratchLowerCorner[X])
                    || !Double.isFinite(scratchLowerCorner[Z])
                    || !Double.isFinite(scratchUpperCorner[X])
                    || !Double.isFinite(scratchUpperCorner[Z]))
                throw new IllegalArgumentException("ImmutableRTree input element has a non-finite MBR ("
                        + Arrays.toString(scratchLowerCorner) + " .. " + Arrays.toString(scratchUpperCorner) + "): "
                        + retained.get(i));
            elementMbrMinX[i] = scratchLowerCorner[X];
            elementMbrMinZ[i] = scratchLowerCorner[Z];
            elementMbrMaxX[i] = scratchUpperCorner[X];
            elementMbrMaxZ[i] = scratchUpperCorner[Z];
            elementCenterX[i] = (scratchLowerCorner[X] + scratchUpperCorner[X]) * 0.5;
            elementCenterZ[i] = (scratchLowerCorner[Z] + scratchUpperCorner[Z]) * 0.5;
        }

        if (elementCount == 0) {
            this.elements = (T[]) new SpatialIndexShape[0];
            this.nodeMbrMinX = new double[0];
            this.nodeMbrMinZ = new double[0];
            this.nodeMbrMaxX = new double[0];
            this.nodeMbrMaxZ = new double[0];
            this.nodeChildStart = new int[0];
            this.nodeChildCount = new int[0];
            this.leafNodeCount = 0;
            return;
        }

        // ---- STR tiling of the elements -------------------------------------------------------
        final Integer[] elementOrder = strOrder(elementCount, elementCenterX, elementCenterZ, leafCapacity);
        this.elements = (T[]) new SpatialIndexShape[elementCount];
        for (int slot = 0; slot < elementCount; slot++) this.elements[slot] = retained.get(elementOrder[slot]);

        // ---- leaf level ------------------------------------------------------------------------
        final ArrayList<LevelUnderConstruction> levels = new ArrayList<>();
        final int firstLevelCount = ceilDiv(elementCount, leafCapacity);
        LevelUnderConstruction currentLevel = new LevelUnderConstruction(firstLevelCount);
        for (int leaf = 0; leaf < firstLevelCount; leaf++) {
            final int sliceStart = leaf * leafCapacity;
            final int sliceEnd = Math.min(sliceStart + leafCapacity, elementCount);
            currentLevel.childStart[leaf] = sliceStart;
            currentLevel.childCount[leaf] = sliceEnd - sliceStart;
            double mbrMinX = Double.POSITIVE_INFINITY, mbrMinZ = Double.POSITIVE_INFINITY;
            double mbrMaxX = Double.NEGATIVE_INFINITY, mbrMaxZ = Double.NEGATIVE_INFINITY;
            for (int slot = sliceStart; slot < sliceEnd; slot++) {
                final int original = elementOrder[slot];
                mbrMinX = Math.min(mbrMinX, elementMbrMinX[original]);
                mbrMinZ = Math.min(mbrMinZ, elementMbrMinZ[original]);
                mbrMaxX = Math.max(mbrMaxX, elementMbrMaxX[original]);
                mbrMaxZ = Math.max(mbrMaxZ, elementMbrMaxZ[original]);
            }
            currentLevel.setMbr(leaf, mbrMinX, mbrMinZ, mbrMaxX, mbrMaxZ);
        }
        levels.add(currentLevel);

        // ---- upper levels: same STR tiling on node-MBR centers, until a single root remains ----
        while (currentLevel.count > 1) {
            // Physically reorder this level into ITS STR order first, so each parent's children end up
            // a contiguous run of the level. Child pointers travel with the node records, so leaf
            // element slices stay valid.
            final Integer[] levelOrder =
                    strOrder(currentLevel.count, currentLevel.centerX(), currentLevel.centerZ(), NODE_FANOUT);
            currentLevel.permute(levelOrder);

            final int parentCount = ceilDiv(currentLevel.count, NODE_FANOUT);
            final LevelUnderConstruction parentLevel = new LevelUnderConstruction(parentCount);
            for (int parent = 0; parent < parentCount; parent++) {
                final int childrenStart = parent * NODE_FANOUT;
                final int childrenEnd = Math.min(childrenStart + NODE_FANOUT, currentLevel.count);
                parentLevel.childStart[parent] = childrenStart; // index within the child level; offset later
                parentLevel.childCount[parent] = childrenEnd - childrenStart;
                double mbrMinX = Double.POSITIVE_INFINITY, mbrMinZ = Double.POSITIVE_INFINITY;
                double mbrMaxX = Double.NEGATIVE_INFINITY, mbrMaxZ = Double.NEGATIVE_INFINITY;
                for (int child = childrenStart; child < childrenEnd; child++) {
                    mbrMinX = Math.min(mbrMinX, currentLevel.mbrMinX[child]);
                    mbrMinZ = Math.min(mbrMinZ, currentLevel.mbrMinZ[child]);
                    mbrMaxX = Math.max(mbrMaxX, currentLevel.mbrMaxX[child]);
                    mbrMaxZ = Math.max(mbrMaxZ, currentLevel.mbrMaxZ[child]);
                }
                parentLevel.setMbr(parent, mbrMinX, mbrMinZ, mbrMaxX, mbrMaxZ);
            }
            levels.add(parentLevel);
            currentLevel = parentLevel;
        }

        // ---- flatten: leaves first, root last; internal childStart += offset of the level below ----
        int totalNodeCount = 0;
        for (final LevelUnderConstruction level : levels) totalNodeCount += level.count;
        this.nodeMbrMinX = new double[totalNodeCount];
        this.nodeMbrMinZ = new double[totalNodeCount];
        this.nodeMbrMaxX = new double[totalNodeCount];
        this.nodeMbrMaxZ = new double[totalNodeCount];
        this.nodeChildStart = new int[totalNodeCount];
        this.nodeChildCount = new int[totalNodeCount];
        this.leafNodeCount = levels.get(0).count;
        int levelOffset = 0;
        int offsetOfLevelBelow = 0;
        for (int levelIndex = 0; levelIndex < levels.size(); levelIndex++) {
            final LevelUnderConstruction level = levels.get(levelIndex);
            for (int node = 0; node < level.count; node++) {
                final int flatIndex = levelOffset + node;
                nodeMbrMinX[flatIndex] = level.mbrMinX[node];
                nodeMbrMinZ[flatIndex] = level.mbrMinZ[node];
                nodeMbrMaxX[flatIndex] = level.mbrMaxX[node];
                nodeMbrMaxZ[flatIndex] = level.mbrMaxZ[node];
                nodeChildCount[flatIndex] = level.childCount[node];
                nodeChildStart[flatIndex] =
                        (levelIndex == 0) ? level.childStart[node] : level.childStart[node] + offsetOfLevelBelow;
            }
            offsetOfLevelBelow = levelOffset;
            levelOffset += level.count;
        }

        if (VALIDATE_ON_BUILD && !validate())
            throw new IllegalStateException("ImmutableRTree failed self-validation after build (see log)");
    }

    /** One level of the bottom-up STR build; child pointers are level-relative until flattening. */
    private static final class LevelUnderConstruction {
        final int count;
        final double[] mbrMinX;
        final double[] mbrMinZ;
        final double[] mbrMaxX;
        final double[] mbrMaxZ;
        final int[] childStart;
        final int[] childCount;

        LevelUnderConstruction(int count) {
            this.count = count;
            this.mbrMinX = new double[count];
            this.mbrMinZ = new double[count];
            this.mbrMaxX = new double[count];
            this.mbrMaxZ = new double[count];
            this.childStart = new int[count];
            this.childCount = new int[count];
        }

        void setMbr(int node, double minX, double minZ, double maxX, double maxZ) {
            mbrMinX[node] = minX;
            mbrMinZ[node] = minZ;
            mbrMaxX[node] = maxX;
            mbrMaxZ[node] = maxZ;
        }

        double[] centerX() {
            final double[] centers = new double[count];
            for (int node = 0; node < count; node++) centers[node] = (mbrMinX[node] + mbrMaxX[node]) * 0.5;
            return centers;
        }

        double[] centerZ() {
            final double[] centers = new double[count];
            for (int node = 0; node < count; node++) centers[node] = (mbrMinZ[node] + mbrMaxZ[node]) * 0.5;
            return centers;
        }

        /** Reorders every per-node array so slot {@code i} holds what was at {@code order[i]}. */
        void permute(Integer[] order) {
            final double[] oldMinX = mbrMinX.clone();
            final double[] oldMinZ = mbrMinZ.clone();
            final double[] oldMaxX = mbrMaxX.clone();
            final double[] oldMaxZ = mbrMaxZ.clone();
            final int[] oldChildStart = childStart.clone();
            final int[] oldChildCount = childCount.clone();
            for (int slot = 0; slot < count; slot++) {
                final int from = order[slot];
                mbrMinX[slot] = oldMinX[from];
                mbrMinZ[slot] = oldMinZ[from];
                mbrMaxX[slot] = oldMaxX[from];
                mbrMaxZ[slot] = oldMaxZ[from];
                childStart[slot] = oldChildStart[from];
                childCount[slot] = oldChildCount[from];
            }
        }
    }

    /**
     * The Sort-Tile-Recursive ordering of {@code count} entries with the given MBR centers:
     * sort by center X, cut into {@code ceil(sqrt(numGroups))} vertical slices, sort each slice by
     * center Z. Cutting the result into consecutive runs of {@code groupCapacity} yields the STR
     * groups. Returns the permutation (slot → original index).
     */
    private static Integer[] strOrder(int count, double[] centerX, double[] centerZ, int groupCapacity) {
        final Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(index -> centerX[index]));
        final int groupCount = ceilDiv(count, groupCapacity);
        final int sliceCount = (int) Math.ceil(Math.sqrt((double) groupCount));
        final int entriesPerSlice = ceilDiv(count, sliceCount);
        for (int slice = 0; slice < sliceCount; slice++) {
            final int from = slice * entriesPerSlice;
            final int to = Math.min(from + entriesPerSlice, count);
            if (from >= to) break;
            Arrays.sort(order, from, to, Comparator.comparingDouble(index -> centerZ[index]));
        }
        return order;
    }

    private static int ceilDiv(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }

    private boolean isLeaf(int nodeIndex) {
        return nodeIndex < leafNodeCount;
    }

    private int rootNodeIndex() {
        return nodeChildStart.length - 1;
    }

    @Override
    public int numEntries() {
        return elements.length;
    }

    /** Every stored element, in STR leaf order. */
    @Override
    public List<T> getAllEntries() {
        return new ArrayList<>(Arrays.asList(elements));
    }

    // -------------------------------------------------------------------------
    // Stabbing queries
    // -------------------------------------------------------------------------

    /** All stored shapes containing {@code queryPoint} (order unspecified). */
    public List<T> queryContaining(final double[] queryPoint) {
        return queryContaining(queryPoint, 0.0, new ArrayList<>());
    }

    /**
     * {@link #queryContaining(double[])} appending into a caller-supplied buffer (returned for
     * chaining). The buffer is <b>not</b> cleared — hot paths reuse one list across queries and clear
     * it themselves.
     */
    public List<T> queryContaining(final double[] queryPoint, final List<T> out) {
        return queryContaining(queryPoint, 0.0, out);
    }

    /**
     * The stabbing query with the search boundary expanded outward: collects every stored shape lying
     * within {@code inflateRadius} of {@code queryPoint} (via
     * {@link SpatialIndexShape#containsPointInflated}). Node MBRs are pruned with the box inflated by
     * {@code inflateRadius} per axis — a conservative superset of the per-element Euclidean inflate, so
     * nothing is ever wrongly pruned. Appends into {@code out} (not cleared) and returns it.
     */
    public List<T> queryContaining(final double[] queryPoint, final double inflateRadius, final List<T> out) {
        if (queryPoint.length != 2) throw new IllegalStateException();
        if (elements.length == 0) return out;

        int capacity = 64;
        int[] nodeStack = new int[capacity];
        int stackSize = 0;
        final int root = rootNodeIndex();
        if (mbrContainsInflated(root, queryPoint, inflateRadius)) nodeStack[stackSize++] = root;

        while (stackSize > 0) {
            final int nodeIndex = nodeStack[--stackSize];
            final int childStart = nodeChildStart[nodeIndex];
            final int childCount = nodeChildCount[nodeIndex];
            if (isLeaf(nodeIndex)) {
                for (int slot = childStart; slot < childStart + childCount; slot++) {
                    final T element = elements[slot];
                    if (element.containsPointInflated(queryPoint, inflateRadius)) out.add(element);
                }
                continue;
            }
            if (stackSize + childCount > capacity) {
                capacity = Math.max(capacity << 1, stackSize + childCount);
                nodeStack = Arrays.copyOf(nodeStack, capacity);
            }
            for (int child = childStart; child < childStart + childCount; child++) {
                if (mbrContainsInflated(child, queryPoint, inflateRadius)) nodeStack[stackSize++] = child;
            }
        }
        return out;
    }

    /** Early-exit twin of {@link #queryContaining(double[])}: true iff some containing shape passes the test. */
    public boolean anyContaining(final double[] queryPoint, final Predicate<T> acceptanceTest) {
        return anyContaining(queryPoint, 0.0, acceptanceTest);
    }

    /**
     * Early-exit twin of {@link #queryContaining(double[], double, List)}: identical traversal, but the
     * first shape within {@code inflateRadius} of {@code queryPoint} that passes {@code acceptanceTest}
     * ends the walk. Allocates no result list.
     */
    public boolean anyContaining(
            final double[] queryPoint, final double inflateRadius, final Predicate<T> acceptanceTest) {
        if (queryPoint.length != 2) throw new IllegalStateException();
        if (elements.length == 0) return false;

        int capacity = 64;
        int[] nodeStack = new int[capacity];
        int stackSize = 0;
        final int root = rootNodeIndex();
        if (mbrContainsInflated(root, queryPoint, inflateRadius)) nodeStack[stackSize++] = root;

        while (stackSize > 0) {
            final int nodeIndex = nodeStack[--stackSize];
            final int childStart = nodeChildStart[nodeIndex];
            final int childCount = nodeChildCount[nodeIndex];
            if (isLeaf(nodeIndex)) {
                for (int slot = childStart; slot < childStart + childCount; slot++) {
                    final T element = elements[slot];
                    if (element.containsPointInflated(queryPoint, inflateRadius) && acceptanceTest.test(element))
                        return true;
                }
                continue;
            }
            if (stackSize + childCount > capacity) {
                capacity = Math.max(capacity << 1, stackSize + childCount);
                nodeStack = Arrays.copyOf(nodeStack, capacity);
            }
            for (int child = childStart; child < childStart + childCount; child++) {
                if (mbrContainsInflated(child, queryPoint, inflateRadius)) nodeStack[stackSize++] = child;
            }
        }
        return false;
    }

    /** True when {@code queryPoint} lies within the node's MBR inflated by {@code inflateRadius} per axis. */
    private boolean mbrContainsInflated(final int nodeIndex, final double[] queryPoint, final double inflateRadius) {
        return queryPoint[X] >= nodeMbrMinX[nodeIndex] - inflateRadius
                && queryPoint[X] <= nodeMbrMaxX[nodeIndex] + inflateRadius
                && queryPoint[Z] >= nodeMbrMinZ[nodeIndex] - inflateRadius
                && queryPoint[Z] <= nodeMbrMaxZ[nodeIndex] + inflateRadius;
    }

    // -------------------------------------------------------------------------
    // Debugging / self-checking (see VALIDATE_ON_BUILD)
    // -------------------------------------------------------------------------

    /**
     * Checks that the frozen tree is internally consistent and returns {@code true} when it is. Every
     * failed invariant is logged via {@link #LOG} (it does not throw). Verifies:
     *
     * <ul>
     *   <li>node child indices/slices stay in bounds; internal children reference strictly lower node
     *       indices (levels are flattened leaves-first);
     *   <li>every child MBR is contained in its parent's MBR, and every element's MBR is contained in
     *       its leaf's MBR;
     *   <li>the leaf element slices partition {@code [0, elements.length)} exactly — contiguous,
     *       gap-free, no overlap;
     *   <li>every node is reachable from the root exactly once, and node MBR corners are finite.
     * </ul>
     */
    public boolean validate() {
        final List<String> errors = new ArrayList<>();
        final int nodeCount = nodeChildStart.length;
        if (nodeCount == 0) {
            if (elements.length != 0) errors.add("empty node array but " + elements.length + " elements");
        } else {
            final boolean[] reached = new boolean[nodeCount];
            validateRec(rootNodeIndex(), reached, errors);
            for (int node = 0; node < nodeCount; node++)
                if (!reached[node]) errors.add("node[" + node + "] unreachable from root");

            // Leaf slices must partition the element array.
            final int[] sliceCoverage = new int[elements.length];
            for (int leaf = 0; leaf < leafNodeCount; leaf++) {
                final int from = nodeChildStart[leaf];
                final int to = from + nodeChildCount[leaf];
                if (from < 0 || to > elements.length) {
                    errors.add("leaf node[" + leaf + "] slice [" + from + "," + to + ") out of elements["
                            + elements.length + "]");
                    continue;
                }
                for (int slot = from; slot < to; slot++) sliceCoverage[slot]++;
            }
            for (int slot = 0; slot < elements.length; slot++)
                if (sliceCoverage[slot] != 1)
                    errors.add("element slot " + slot + " covered by " + sliceCoverage[slot] + " leaf slices");
        }
        if (!errors.isEmpty()) {
            LOG.warn("ImmutableRTree.validate found {} problem(s):", errors.size());
            for (final String error : errors) LOG.warn("  - {}", error);
            return false;
        }
        return true;
    }

    /** {@link #validate()} variant that throws {@link IllegalStateException} instead of returning false. */
    public void validateOrThrow() {
        if (!validate()) throw new IllegalStateException("ImmutableRTree failed self-validation (see log)");
    }

    private void validateRec(final int nodeIndex, final boolean[] reached, final List<String> errors) {
        if (nodeIndex < 0 || nodeIndex >= nodeChildStart.length) {
            errors.add("node index out of range: " + nodeIndex);
            return;
        }
        if (reached[nodeIndex]) {
            errors.add("node[" + nodeIndex + "] reached twice");
            return;
        }
        reached[nodeIndex] = true;
        if (!Double.isFinite(nodeMbrMinX[nodeIndex])
                || !Double.isFinite(nodeMbrMinZ[nodeIndex])
                || !Double.isFinite(nodeMbrMaxX[nodeIndex])
                || !Double.isFinite(nodeMbrMaxZ[nodeIndex]))
            errors.add("node[" + nodeIndex + "] has a non-finite MBR corner");

        final int childStart = nodeChildStart[nodeIndex];
        final int childCount = nodeChildCount[nodeIndex];
        if (isLeaf(nodeIndex)) {
            final double[] scratchLowerCorner = new double[2];
            final double[] scratchUpperCorner = new double[2];
            for (int slot = childStart; slot < Math.min(childStart + childCount, elements.length); slot++) {
                elements[slot].writeMbrInto(scratchLowerCorner, scratchUpperCorner);
                if (scratchLowerCorner[X] < nodeMbrMinX[nodeIndex]
                        || scratchLowerCorner[Z] < nodeMbrMinZ[nodeIndex]
                        || scratchUpperCorner[X] > nodeMbrMaxX[nodeIndex]
                        || scratchUpperCorner[Z] > nodeMbrMaxZ[nodeIndex])
                    errors.add("element in slot " + slot + " has MBR outside leaf node[" + nodeIndex + "]");
            }
            return;
        }
        for (int child = childStart; child < childStart + childCount; child++) {
            if (child < 0 || child >= nodeChildStart.length) {
                errors.add("internal node[" + nodeIndex + "] child " + child + " out of range");
                continue;
            }
            if (child >= nodeIndex) errors.add("internal node[" + nodeIndex + "] child " + child + " not below it");
            if (nodeMbrMinX[child] < nodeMbrMinX[nodeIndex]
                    || nodeMbrMinZ[child] < nodeMbrMinZ[nodeIndex]
                    || nodeMbrMaxX[child] > nodeMbrMaxX[nodeIndex]
                    || nodeMbrMaxZ[child] > nodeMbrMaxZ[nodeIndex])
                errors.add("child node[" + child + "] MBR outside parent node[" + nodeIndex + "]");
            validateRec(child, reached, errors);
        }
    }

    /** Logs the full node tree (indented by depth, with each node's MBR and child info) via {@link #LOG}. */
    public void debugPrint() {
        LOG.info(
                "ImmutableRTree: {} nodes ({} leaves), {} elements, leafCapacity={}",
                nodeChildStart.length,
                leafNodeCount,
                elements.length,
                leafCapacity);
        if (nodeChildStart.length != 0) debugPrintRec(rootNodeIndex(), 0);
    }

    private void debugPrintRec(final int nodeIndex, final int depth) {
        LOG.info(
                "{}node[{}] {} mbr=[{},{}]..[{},{}] {}={}+{}",
                "  ".repeat(depth),
                nodeIndex,
                isLeaf(nodeIndex) ? "leaf" : "internal",
                nodeMbrMinX[nodeIndex],
                nodeMbrMinZ[nodeIndex],
                nodeMbrMaxX[nodeIndex],
                nodeMbrMaxZ[nodeIndex],
                isLeaf(nodeIndex) ? "elementSlice" : "children",
                nodeChildStart[nodeIndex],
                nodeChildCount[nodeIndex]);
        if (isLeaf(nodeIndex)) return;
        for (int child = nodeChildStart[nodeIndex];
                child < nodeChildStart[nodeIndex] + nodeChildCount[nodeIndex];
                child++) debugPrintRec(child, depth + 1);
    }

    // -------------------------------------------------------------------------
    // Persistable ("IRT1")
    // -------------------------------------------------------------------------

    @Override
    public long byteSize() {
        final long bytesPerNode = 4 * Double.BYTES + 2 * Integer.BYTES; // four MBR corners + two ints
        final long bytesPerElement = 48; // shape payload, rough (mirrors the quadtree estimate)
        return (long) nodeChildStart.length * bytesPerNode + (long) elements.length * bytesPerElement;
    }

    /**
     * Serialize to a flat little-endian byte array:
     * {@code [magic, leafCapacity, elementCount, (chunkLen, chunk)...]}, where each chunk is an
     * element's own {@link Persistable#serialize()} bytes. <b>Elements only</b> — the tree (and every
     * MBR) is rebuilt on {@link #deserialize(byte[])}. Throws {@link UnsupportedOperationException}
     * when the element type is not {@link Persistable} (the backing {@code Storage} is then cache-only).
     */
    @Override
    public byte[] serialize() {
        if (!(elementPrototype instanceof Persistable<?> prototypePersistable))
            throw new UnsupportedOperationException(
                    "ImmutableRTree.serialize requires a Persistable element prototype: " + elementPrototype);
        try {
            prototypePersistable.serialize();
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedOperationException(
                    "ImmutableRTree.serialize requires an implemented Persistable element prototype: "
                            + elementPrototype.getClass());
        }
        long payload = 0;
        final byte[][] chunks = new byte[elements.length][];
        for (int i = 0; i < elements.length; i++) {
            if (!(elements[i] instanceof Persistable<?> persistableElement))
                throw new UnsupportedOperationException("ImmutableRTree element type is not Persistable: "
                        + elements[i].getClass().getName());
            final byte[] chunk = persistableElement.serialize();
            chunks[i] = chunk;
            payload += Integer.BYTES + chunk.length;
        }
        final int header = Integer.BYTES /* magic */ + Integer.BYTES /* leafCapacity */ + Integer.BYTES /* count */;
        final ByteBuffer buf = ByteBuffer.allocate((int) (header + payload)).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(MAGIC);
        buf.putInt(leafCapacity);
        buf.putInt(elements.length);
        for (final byte[] chunk : chunks) {
            buf.putInt(chunk.length);
            buf.put(chunk);
        }
        return buf.array();
    }

    /**
     * Rebuild a tree from bytes produced by {@link #serialize()} by deserializing each element with
     * this tree's {@link #elementPrototype} and re-running the STR construction. Returns a fresh
     * instance; the receiver is only a prototype. A magic mismatch (e.g. a legacy {@code IQT1} tile)
     * throws {@link IllegalStateException} — which {@code Storage.loadInto} converts into a recompute,
     * so stale on-disk caches self-heal.
     */
    @Override
    @SuppressWarnings("unchecked")
    public ImmutableRTree<T> deserialize(byte[] rawBytes) {
        if (!(elementPrototype instanceof Persistable<?> prototypePersistable))
            throw new UnsupportedOperationException(
                    "ImmutableRTree.deserialize requires a Persistable element prototype: " + elementPrototype);
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final int magic = buf.getInt();
        if (magic != MAGIC)
            throw new IllegalStateException("incompatible ImmutableRTree tile format (got 0x"
                    + Integer.toHexString(magic) + "); delete the fractal_terrain tile cache to regenerate");
        final int storedLeafCapacity = buf.getInt();
        final int count = buf.getInt();
        final List<T> restored = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final int len = buf.getInt();
            final byte[] chunk = new byte[len];
            buf.get(chunk);
            restored.add((T) prototypePersistable.deserialize(chunk));
        }
        return new ImmutableRTree<>(restored, elementPrototype, storedLeafCapacity);
    }
}
