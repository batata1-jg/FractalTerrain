package me.batata_1.fractal_terrain.math.ds;

import me.batata_1.fractal_terrain.math.VectorOps;

public interface QuadTreeShape {

    default <T extends QuadTreePoint> boolean notIntersect(QuadTree.Node<T> node) {
        return notIntersect(node.p0,node.p1);
    }
    boolean notIntersect(double[] p0, double[] p1);

    default <T extends QuadTreePoint> boolean contains(QuadTree.Node<T> node) {
        return contains(node.p0,node.p1);
    }

    default <T extends QuadTreePoint> boolean contains(T pt) {
        return contains(pt.toArray(), pt.toArray());
    }

    boolean contains(double[] p0, double[] p1);

    record QuadTreeBox(double[] b, double[] d) implements QuadTreeShape {
        @Override
        public boolean notIntersect(double[] p0, double[] p1) {
            return d[0]<p0[0]||p1[0]<b[0] || d[1]<p0[1]||p1[1]<b[1];
        }

        @Override
        public boolean contains(double[] p0, double[] p1) {
            return b[0]<=p0[0]&&p1[0]<=d[0] && b[1]<=p0[1]&&p1[1]<=d[1];
        }
    }

    record QuadTreeCircle(double[] center ,double radius) implements QuadTreeShape{

        @Override
        public boolean notIntersect(double[] p0, double[] p1) {
            double cx = Math.clamp(center[0], p0[0], p1[0]);
            double cy = Math.clamp(center[1], p0[1], p1[1]);
            double dx = center[0] - cx;
            double dy = center[1] - cy;
            return dx * dx + dy * dy > radius * radius;
        }

        @Override
        public boolean contains(double[] p0, double[] p1) {
            double dx = Math.max(Math.abs(p0[0] - center[0]), Math.abs(p1[0] - center[0]));
            double dy = Math.max(Math.abs(p0[1] - center[1]), Math.abs(p1[1] - center[1]));
            return dx * dx + dy * dy <= radius * radius;
        }
    }

}
