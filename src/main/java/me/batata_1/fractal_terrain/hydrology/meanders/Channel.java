package me.batata_1.fractal_terrain.hydrology.meanders;

import java.util.ArrayList;

public final class Channel {
    public double              width, depth;
    public ArrayList<double[]> pts;           // pts.get(i)[0] = x, pts.get(i)[1] = z
    public ArrayList<Double>   localRates, migRates;

    Channel(double width, double depth, ArrayList<double[]> pts, ArrayList<Double> localRates, ArrayList<Double> migRates) {
        this.width      = width;
        this.depth      = depth;
        this.pts        = pts;
        this.localRates = localRates;
        this.migRates   = migRates;
    }
}
