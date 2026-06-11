package me.batata_1.fractal_terrain.math.ds;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.storage.Persistable;

/**
 * 2-D spatial index. Concurrency contract: <b>multiple concurrent readers</b>
 * ({@link #getPointsInBox}, {@link #getPointsInCircle}, {@link #getPointCoordsInBox},
 * {@link #containsPoint}) are safe, guarded by the read side of a {@link ReentrantReadWriteLock};
 * mutations ({@link #insertPoint}, {@link #removePoint}, {@link #clear}) take the exclusive write
 * lock, so writes never run concurrently with each other or with reads. All recursion-local state
 * is kept on the stack (no shared mutable fields), so readers do not interfere with one another.
 */
public class QuadTree<T extends QuadTreePoint> implements Persistable<QuadTree<T>> {
    private static final int PP = 0;
    private static final int PQ = 1;
    private static final int QP = 2;
    private static final int QQ = 3;
    private static final int X = 0;
    private static final int Z = 1;
    private static final int MAX_TREE_DEPTH = 50;
    private final double[] minXZ;
    private final double[] maxXZ;

    public static final class Node<T> {
        // NAO COLOCA ISSO DENTRO DO NODE, VAI GASTAR MEMORIA DEMAIS.
        // nao quero acessar cada elemento como um indice. quero acessar todos de uma so vez.
        // TODO: implement storing only the index and the count;
        public final Set<T> points = new HashSet<>();
        public final int[] child = new int[4];
        public final double[] p0;
        public final double[] p1;
        public final int depth;

        public Node() {
            this.p0 = new double[2];
            this.p1 = new double[2];
            depth = -1;
        }

        public Node(double[] p0, double[] p1, int depth) {
            this.p0 = p0;
            this.p1 = p1;
            this.depth = depth;
        }

        public void clear() {
            points.clear();
        }

        public double[] getMidpoint() {
            return VectorOps.div(VectorOps.add(p0, p1), 2.0);
        }
    }

    final List<Node<T>> tree = new ArrayList<>(8);

    /** Guards {@link #tree}: many concurrent readers (queries) or one exclusive writer (mutations). */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public QuadTree(double[] minXZ, double[] maxXZ) {
        this.minXZ = minXZ;
        this.maxXZ = maxXZ;
        tree.add(new Node<>());
        tree.add(new Node<>(minXZ, maxXZ, 0));
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            for (Node<T> n : tree) n.clear();
            tree.clear();
            tree.add(new Node<>());
            tree.add(new Node<>(minXZ, maxXZ, 0));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Approximate in-memory footprint, used by {@link Storage}'s byte-budget eviction. Estimated
     * from node count and the total point count (the root node at index 1 holds every inserted
     * point). {@code serialize}/{@code deserialize} are intentionally left as the throwing defaults,
     * so a {@code QuadTree}-backed {@code Storage} is cache-only (tiles are never written to disk).
     */
    @Override
    public long byteSize() {
        final long bytesPerNode = 120; // child[4] + 2 double[2] + Set overhead, rough
        final long bytesPerPoint = 48; // QuadTreePoint: 2 boxed doubles + list overhead, rough
        lock.readLock().lock();
        try {
            final long nodeCount = tree.size();
            final long pointCount = tree.size() > 1 ? tree.get(1).points.size() : 0;
            return nodeCount * bytesPerNode + pointCount * bytesPerPoint;
        } finally {
            lock.readLock().unlock();
        }
    }

    // every point should have its own square
    private void update(final T pt, final int id) {
        final Node<T> cur = tree.get(id);
        if (cur.points.contains(pt)) return;
        if (cur.points.isEmpty()) {
            cur.points.add(pt);
            return;
        }
        final double[] middle = cur.getMidpoint();
        if (cur.points.size() == 1) {
            final T p = cur.points.stream().toList().getFirst();
            final int section = findSection(p, middle);
            if (cur.depth < MAX_TREE_DEPTH) {
                if (cur.child[section] == 0)
                    cur.child[section] = createNode(
                            getLowerBound(cur.p0, middle, section),
                            getUpperBound(middle, cur.p1, section),
                            cur.depth + 1);
                update(p, cur.child[section]);
            }
        }
        final int section = findSection(pt, middle);
        cur.points.add(pt);
        if (cur.depth < MAX_TREE_DEPTH) {
            if (cur.child[section] == 0)
                cur.child[section] = createNode(
                        getLowerBound(cur.p0, middle, section), getUpperBound(middle, cur.p1, section), cur.depth + 1);
            update(pt, cur.child[section]);
        }
    }

    private void delete(final T pt, final int id) {
        if (id == 0) return;
        final Node<T> cur = tree.get(id);
        if (!cur.points.contains(pt)) return;
        final double[] middle = cur.getMidpoint();
        final int section = findSection(pt, middle);
        delete(pt, cur.child[section]);
        cur.points.remove(pt);
    }

    // se so tem um elemento checar esse
    private <S extends QuadTreeShape> ArrayList<T> query(final S shape, final int id) {
        if (id == 0) return null;
        final Node<T> cur = tree.get(id);
        if (shape.notIntersect(cur)) return null;
        if (shape.contains(cur)) return new ArrayList<>(cur.points);
        if (cur.points.size() == 1) {
            final var ptList = new ArrayList<>(cur.points);
            var pt = ptList.getFirst();
            return shape.contains(pt) ? ptList : null;
        }
        ArrayList<T> mergedPoints = null;
        for (int section : cur.child) {
            mergedPoints = merge(mergedPoints, query(shape, section));
        }
        return mergedPoints;
    }

    public void insertPoint(final T pt) {
        if (pt.size() != 2) throw new IllegalStateException();
        lock.writeLock().lock();
        try {
            update(pt, 1);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removePoint(final T pt) {
        if (pt.size() != 2) throw new IllegalStateException();
        lock.writeLock().lock();
        try {
            delete(pt, 1);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean containsPoint(final T pt) {
        lock.readLock().lock();
        try {
            return tree.get(1).points.contains(pt);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<T> getPointsInBox(final double[] b, final double[] d) {
        if (d.length != 2 || b.length != 2) throw new IllegalStateException();
        lock.readLock().lock();
        try {
            List<T> resp = query(new QuadTreeShape.QuadTreeBox(b, d), 1);
            if (resp == null) return new ArrayList<>();
            return resp;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<T> getPointsInCircle(final double[] pt, final double r) {
        if (pt.length != 2) throw new IllegalStateException();
        lock.readLock().lock();
        try {
            List<T> resp = query(new QuadTreeShape.QuadTreeCircle(pt, r), 1);
            if (resp == null) return new ArrayList<>();
            return resp;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<double[]> getPointCoordsInBox(final double[] b, final double[] d) {
        if (d.length != 2 || b.length != 2) throw new IllegalStateException();
        lock.readLock().lock();
        try {
            List<T> resp = query(new QuadTreeShape.QuadTreeBox(b, d), 1);
            if (resp == null) return new ArrayList<>();
            return resp.stream().map(QuadTreePoint::toArray).toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    private int createNode(double[] p0, double[] p1, int depth) {
        tree.add(new Node<>(p0, p1, depth));
        return tree.size() - 1;
    }

    private double[] getLowerBound(double[] p0, double[] m, int section) {
        if (section == PP) return new double[] {p0[X], p0[Z]};
        if (section == PQ) return new double[] {p0[X], m[Z]};
        if (section == QP) return new double[] {m[X], p0[Z]};
        return new double[] {m[X], m[Z]};
    }

    private double[] getUpperBound(double[] m, double[] p1, int section) {
        if (section == PP) return new double[] {m[X], m[Z]};
        if (section == PQ) return new double[] {m[X], p1[Z]};
        if (section == QP) return new double[] {p1[X], m[Z]};
        return new double[] {p1[X], p1[Z]};
    }

    private int findSection(T pt, double[] m) {
        if (pt.get(X) <= m[X]) return (pt.get(Z) <= m[Z]) ? PP : PQ;
        return (pt.get(Z) <= m[Z]) ? QP : QQ;
    }

    private ArrayList<T> merge(ArrayList<T> a, ArrayList<T> b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.size() < b.size()) {
            b.addAll(a);
            return b;
        }
        a.addAll(b);
        return a;
    }
}
