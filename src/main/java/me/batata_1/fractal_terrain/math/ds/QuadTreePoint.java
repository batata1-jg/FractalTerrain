package me.batata_1.fractal_terrain.math.ds;

import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class QuadTreePoint implements Comparable<QuadTreePoint> {

    protected final List<Double> ptCoords;

    public QuadTreePoint(List<Double> ptCoords) {
        this.ptCoords = ptCoords;
    }

    public QuadTreePoint(double[] pt) {
        this.ptCoords = Arrays.stream(pt).boxed().toList();
    }

    public double[] toArray() {
        return ptCoords.stream().mapToDouble(o -> o).toArray();
    }

    public double get(int x) {
        return ptCoords.get(x);
    }

    public int size() {
        return ptCoords.size();
    }

    @Override
    public int compareTo(@NotNull QuadTreePoint pt) {
        if (pt.size() != this.size()) throw new IllegalArgumentException("points must match rank");
        for (int i = 0; i < this.size(); i++) {
            if (pt.get(i) < this.get(i)) return 1;
            if (pt.get(i) > this.get(i)) return -1;
        }
        return 0;
    }
}
