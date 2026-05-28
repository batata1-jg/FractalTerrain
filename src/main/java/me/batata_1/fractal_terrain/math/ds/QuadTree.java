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

    public static final class Node<T> {
        // NAO COLOCA ISSO DENTRO DO NODE, VAI GASTAR MEMORIA DEMAIS.
        // nao quero acessar cada elemento como um indice. quero acessar todos de uma so vez.
        public final Set<T> points = new HashSet<>();
        public final int[] child = new int[4];
        public final double[] p0;
        public final double[] p1;

        public Node() {
            this.p0 = new double[2];
            this.p1 = new double[2];
        }

        public Node(double[] p0, double[] p1) {
            this.p0 = p0;
            this.p1 = p1;
        }

        public void clear() {
            points.clear();
        }

        public double[] getMidpoint() {
            return VectorOps.div(VectorOps.add(p0, p1), 2.0);
        }


        public boolean notIntersect(double[] b, double[] d) {
            return d[X]<p0[X]||p1[X]<b[X] || d[Z]<p0[Z]||p1[Z]<b[Z];
        }


        public boolean containedIn(double[] b, double[] d) {
            return b[X]<=p0[X]&&p1[X]<=d[X] && b[Z]<=p0[Z]&&p1[Z]<=d[Z];
        }
    }

    final List<Node<T>> tree = new ArrayList<>(8);


    public QuadTree( double[] minXZ , double[] maxXZ ) {
        tree.add(new Node<>());
        tree.add(new Node<>(minXZ,maxXZ));
    }

    public void clear() {
        for(Node<T> n : tree) n.clear();
        tree.clear();
        tree.add(new Node<>());
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
            if(cur.child[section]==0) cur.child[section]= createNode(
                    getLowerBound(cur.p0, mutableMiddle,section),
                    getUpperBound(mutableMiddle,cur.p1,section)
            );
            update(p,cur.child[section]);
        }
        final int section = findSection(pt, mutableMiddle);
        if(cur.child[section]==0) cur.child[section] = createNode(
                getLowerBound(cur.p0, mutableMiddle,section),
                getUpperBound(mutableMiddle,cur.p1,section)
        );
        cur.points.add(pt);
        update(pt,cur.child[section]);
    }

    private void delete(final T pt, final int id) {
        final Node<T> cur = tree.get(id);
    }

    // se so tem um elemento checar esse
    private ArrayList<T> query(final double[] b , final double[] d, final int id ) {
        if(id==0) return null;
        final Node<T> cur = tree.get(id);
        if(cur.notIntersect(b,d)) return null;
        if(cur.containedIn(b,d)) return new ArrayList<>(cur.points);
        if(cur.points.size()==1) {
            final var ptList = new ArrayList<>(cur.points);
            var pt = ptList.getFirst();
            return (b[X]<=pt.get(X)&&pt.get(X)<=d[X] && b[Z]<=pt.get(Z)&&pt.get(Z)<=d[Z]) ? ptList : null;
        }
        mutableMiddle = cur.getMidpoint();
        ArrayList<T> mergedPoints = null;
        for(int section : cur.child) {
            mergedPoints = merge(mergedPoints,query(b,d,section));
        }
        return mergedPoints;
    }

    public void insertPoint(final T pt) {
        if(pt.size()!=2) throw new IllegalStateException();
        update(pt,1);
    }

    public List<T> getPointsInBox(final double[] b , final double[] d) {
        if(d.length!=2||b.length!=2) throw new IllegalStateException();
        List<T> resp = query(b,d,1);
        if(resp==null) return new ArrayList<>();
        return resp;
    }

    public List<double[]> getPointCoordsInBox(final double[] b , final double[] d) {
        if(d.length!=2||b.length!=2) throw new IllegalStateException();
        List<T> resp = query(b,d,1);
        if(resp==null) return new ArrayList<>();
        return resp.stream().map(QuadTreePoint::toArray).toList();
    }

    private int createNode(double[] p0, double[] p1) {
        tree.add(new Node(p0,p1));
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
