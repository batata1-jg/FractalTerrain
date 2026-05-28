package me.batata_1.fractal_terrain.math;

import java.util.Arrays;
import java.util.List;

public class QuadTreePoint {

    protected final List<Double> ptCoords;

    public QuadTreePoint(List<Double> ptCoords) {
        this.ptCoords = ptCoords;
    }

    public QuadTreePoint(double[] pt) {
        this.ptCoords = Arrays.stream(pt).boxed().toList();
    }

    public double[] toArray() {
        return ptCoords.stream().mapToDouble(o->o).toArray();
    }

    public double get(int x) {
        return ptCoords.get(x);
    }

    public int size() {
        return ptCoords.size();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj==null) return false;
        if(!(obj instanceof QuadTreePoint)) throw new IllegalArgumentException();
        return ptCoords.equals(((QuadTreePoint) obj).ptCoords);
    }
}
