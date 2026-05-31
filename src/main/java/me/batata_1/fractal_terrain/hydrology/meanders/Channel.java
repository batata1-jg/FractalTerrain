package me.batata_1.fractal_terrain.hydrology.meanders;

import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import org.slf4j.Logger;

import java.util.ArrayList;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

public class Channel {
    private static final Logger LOG = getLogger(Channel.class);
    public final double              width, depth;
    public final int channelId;
    public QuinticHermiteSpline spline;

    Channel(double width, ArrayList<double[]> pts, int channelId) {
        this.width      = width;
        this.depth      = Math.max(1.0, Math.pow(width / 18.8, 1.0 / 1.41));
        this.spline = QuinticHermiteSpline.createCatmullRom(pts);
        this.channelId = channelId;
    }

    public double[] computeLocalRates() {
        final double[] res = new double[spline.points().size()];
        for (int t = 0; t <spline.points().size(); t++) {
            res[t] = width * spline.curvature(t);
        }
        return res;
    }

    public double computeSinuosity() {
        double f_x_dx = 0;
        for(int t = 0; t <spline.points().size()-1 ; t++) f_x_dx += VectorOps.distance(spline.points().get(t), spline.points().get(t+1));
        return f_x_dx / VectorOps.distance(spline.points().getFirst(), spline.points().getLast());
    }

    public int numPts() {
        return spline.points().size();
    }

    public ChannelPt pt(int id) {
        return new ChannelPt(spline.points().get(id),id,channelId);
    }

    public ChannelPt[] getChannelAsPts() {
        final ChannelPt[] res = new ChannelPt[spline.points().size()];
        for(int i=0 ; i<spline.points().size() ; i++) res[i] = new ChannelPt(spline.points().get(i),i,channelId);
        return res;
    }

    public void reSample(double samplingDist) throws IllegalStateException{
        this.spline = spline.reSample(samplingDist);
    }

    @Override
    public String toString() {
        return "Channel{id=" + channelId +
                ", width=" + width +
                ", depth=" + depth +
                ", pts=" + (spline.points().size()) +
                ", sinuosity=" + String.format("%.3f", computeSinuosity()) +
                "}";
    }

    public void keepOnly(ArrayList<Integer> newPathIndexes) {
        this.spline = spline.keepOnly(newPathIndexes);
    }

    /**
     * Adds point from a channel to another channel. Keeps the tangents and curvatures of the other channel in the point.
     * */
    public void add(ChannelPt pt, Channel from) {
        this.spline.points().add(pt.toArray());
        this.spline.velocity().add(from.spline.velocity().get(pt.index));
        this.spline.acceleration().add(from.spline.acceleration().get(pt.index));
    }

    /**
     * Adds point from a channel to another channel. Keeps the tangents and curvatures of the other channel in the point.
     * */
    public void add(int id, Channel from) {
        this.spline.points().add(from.spline.points().get(id));
        this.spline.velocity().add(from.spline.velocity().get(id));
        this.spline.acceleration().add(from.spline.acceleration().get(id));
    }

    public static class ChannelPt extends QuadTreePoint {

        public final int index,channelId;

        public ChannelPt(double[] pt, int id, int channelId) {
            super(pt);
            this.index = id;
            this.channelId = channelId;
        }

        @Override
        public String toString() {
            return "["+ptCoords.toString()+" "+ index +"]";
        }
    }

}
