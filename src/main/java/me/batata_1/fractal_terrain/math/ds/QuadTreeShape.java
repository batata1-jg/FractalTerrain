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
            double dx = Math.abs(center[0] - p0[0]);
            double dy = Math.abs(center[1] - p0[1]);
            double width = p1[0] - p0[0];
            double height = p1[1] - p0[1];
            if (dx > (width/2 + radius)) { return true; }
            if (dy > (height/2 + radius)) { return true; }

            if (dx <= (width/2)) { return false; }
            if (dy <= (height/2)) { return false; }

            double cornerDistance_sq = (dx - width/2)*(dy - height/2) +
                    (dy - height/2)*(dy - height/2);

            return !(cornerDistance_sq <= (radius*radius));
        }

        @Override
        public boolean contains(double[] p0, double[] p1) {
            if(VectorOps.distanceSquared(p0,center) > radius*radius) return false;
            if(VectorOps.distanceSquared(p1,center) > radius*radius) return false;
            if(VectorOps.distanceSquared(new double[]{p0[0],p1[1]},center)> radius*radius) return false;
            return VectorOps.distanceSquared(new double[]{p1[0],p0[1]},center) <= radius*radius;
        }
    }

}
