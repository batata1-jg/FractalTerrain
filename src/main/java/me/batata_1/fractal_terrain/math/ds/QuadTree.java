package me.batata_1.fractal_terrain.math.ds;

import me.batata_1.fractal_terrain.math.VectorOps;
import oshi.annotation.concurrent.NotThreadSafe;

import java.util.*;

@NotThreadSafe
public class QuadTree<T extends QuadTreePoint> {
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


    public QuadTree(double[] minXZ, double[] maxXZ) {
        this.minXZ = minXZ;
        this.maxXZ = maxXZ;
        tree.add(new Node<>());
        tree.add(new Node<>(minXZ,maxXZ,0));
    }

    public void clear() {
        for(Node<T> n : tree) n.clear();
        tree.clear();
        tree.add(new Node<>());
        tree.add(new Node<>(minXZ,maxXZ,0));
    }

    private double[] mutableMiddle;
    // every point should have its own square
    private void update(final T pt, final int id) {
        final Node<T> cur = tree.get(id);
        if(cur.points.contains(pt)) return;
        if(cur.points.isEmpty()) {
            cur.points.add(pt);
            return;
        }
        mutableMiddle = cur.getMidpoint();
        if(cur.points.size()==1) {
            final T p = cur.points.stream().toList().getFirst();
            final int section = findSection(p, mutableMiddle);
            if(cur.depth<MAX_TREE_DEPTH){
                if (cur.child[section] == 0) cur.child[section] = createNode(
                        getLowerBound(cur.p0, mutableMiddle, section),
                        getUpperBound(mutableMiddle, cur.p1, section),
                        cur.depth + 1
                );
                update(p, cur.child[section]);
            }
        }
        final int section = findSection(pt, mutableMiddle);
        cur.points.add(pt);
        if(cur.depth<MAX_TREE_DEPTH) {
            if (cur.child[section] == 0) cur.child[section] = createNode(
                    getLowerBound(cur.p0, mutableMiddle, section),
                    getUpperBound(mutableMiddle, cur.p1, section),
                    cur.depth + 1
            );
            update(pt, cur.child[section]);
        }
    }

    private void delete(final T pt, final int id) {
        if(id==0) return;
        final Node<T> cur = tree.get(id);
        if(!cur.points.contains(pt)) return;
        mutableMiddle = cur.getMidpoint();
        final int section = findSection(pt, mutableMiddle);
        delete(pt,cur.child[section]);
        cur.points.remove(pt);
    }

    // se so tem um elemento checar esse
    private <S extends QuadTreeShape> ArrayList<T> query(final S shape, final int id ) {
        if(id==0) return null;
        final Node<T> cur = tree.get(id);
        if(shape.notIntersect(cur)) return null;
        if(shape.contains(cur)) return new ArrayList<>(cur.points);
        if(cur.points.size()==1) {
            final var ptList = new ArrayList<>(cur.points);
            var pt = ptList.getFirst();
            return shape.contains(pt) ? ptList : null;
        }
        mutableMiddle = cur.getMidpoint();
        ArrayList<T> mergedPoints = null;
        for(int section : cur.child) {
            mergedPoints = merge(mergedPoints,query(shape,section));
        }
        return mergedPoints;
    }

    public void insertPoint(final T pt) {
        if(pt.size()!=2) throw new IllegalStateException();
        update(pt,1);
    }

    public void removePoint(final T pt) {
        if(pt.size()!=2) throw new IllegalStateException();
        delete(pt,1);
    }

    public List<T> getPointsInBox(final double[] b , final double[] d) {
        if(d.length!=2||b.length!=2) throw new IllegalStateException();
        List<T> resp = query(new QuadTreeShape.QuadTreeBox(b,d),1);
        if(resp==null) return new ArrayList<>();
        return resp;
    }

    public List<T> getPointsInCircle(final double[] pt, final double r) {
        if(pt.length!=2) throw new IllegalStateException();
        List<T> resp = query(new QuadTreeShape.QuadTreeCircle(pt,r),1);
        if(resp==null) return new ArrayList<>();
        return resp;
    }


    public List<double[]> getPointCoordsInBox(final double[] b , final double[] d) {
        if(d.length!=2||b.length!=2) throw new IllegalStateException();
        List<T> resp = query(new QuadTreeShape.QuadTreeBox(b,d),1);
        if(resp==null) return new ArrayList<>();
        return resp.stream().map(QuadTreePoint::toArray).toList();
    }

    private int createNode(double[] p0, double[] p1,int depth) {
        tree.add(new Node<>(p0,p1,depth));
        return tree.size()-1;
    }

    private double[] getLowerBound(double[] p0 , double[] m , int section) {
        if(section==PP) return new double[]{p0[X],p0[Z]};
        if(section==PQ) return new double[]{p0[X],m[Z]};
        if(section==QP) return new double[]{m[X],p0[Z]};
        return new double[]{m[X],m[Z]};
    }

    private double[] getUpperBound(double[] m, double[] p1 , int section) {
        if(section==PP) return new double[]{m[X],m[Z]};
        if(section==PQ) return new double[]{m[X],p1[Z]};
        if(section==QP) return new double[]{p1[X],m[Z]};
        return new double[]{p1[X],p1[Z]};
    }

    private int findSection(T pt, double[] m) {
        if(pt.get(X)<=m[X]) return (pt.get(Z)<=m[Z]) ? PP : PQ;
        return (pt.get(Z)<=m[Z]) ? QP : QQ;
    }

    private ArrayList<T> merge(ArrayList<T> a , ArrayList<T> b ) {
        if(a==null) return b;
        if(b==null) return a;
        if(a.size()<b.size()) {
            b.addAll(a);
            return b;
        }
        a.addAll(b);
        return a;
    }

}
