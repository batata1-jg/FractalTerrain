package me.batata_1.fractal_terrain.hydrology.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexPoint;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;

/**
 * One directed edge of the {@link RiverNetwork}: a spline plus the per-point flow and bed elevation
 * carried along it.
 *
 * <p>Width is derived from {@link #flow}, never stored. Flow is additive at confluences, so a trunk
 * widens downstream on its own and no separate width-propagation pass is needed.
 */
public class Channel {

    public final int channelId;
    public QuinticHermiteSpline spline;

    /** Per-point flow, the quantity every channel width is derived from. Never null. */
    public double[] flow;

    /** Graph endpoints, upstream then downstream; {@code -1} for a channel used outside any graph. */
    public int startNodeId = -1, endNodeId = -1;

    /** Per-point bed elevation, filled by {@code ChannelElevationAssigner}; null until then. */
    public double[] bedElevations;

    /** The canonical construction path. {@code flow} must carry one entry per point. */
    public Channel(ArrayList<double[]> pts, double[] flow, int channelId) {
        if (flow.length != pts.size()) {
            throw new IllegalArgumentException("flow length " + flow.length + " != point count " + pts.size());
        }
        this.spline = QuinticHermiteSpline.createCatmullRom(pts);
        this.flow = flow.clone();
        this.channelId = channelId;
    }

    /** Derived width at spline index {@code i} = {@code widthFromFlow(flow[i])}. */
    public double widthAt(int i) {
        return HydrologyTuning.widthFromFlow(flow[i]);
    }

    /** Derived width at the upstream (intake) end — the lowest-flow end, so the finest resample spacing. */
    public double intakeWidth() {
        return HydrologyTuning.widthFromFlow(flow[0]);
    }

    /** Derived width at the downstream (discharge) end — the largest-flow end. */
    public double dischargeWidth() {
        return HydrologyTuning.widthFromFlow(flow[flow.length - 1]);
    }

    /** Channel depth derived from the discharge width (the widest, downstream end). */
    public double depth() {
        return ChannelGeometry.depthForWidth(dischargeWidth());
    }

    /** Flow between points, so resampling can interpolate rather than snap to the nearest point. */
    public double flowAt(double t) {
        final int id = Math.clamp((int) Math.floor(t), 0, flow.length - 2);
        final double u = t - id;
        return flow[id] + (flow[id + 1] - flow[id]) * u;
    }

    public double[] computeLocalRates() {
        final double[] res = new double[spline.points().size()];
        for (int t = 0; t < spline.points().size(); t++) {
            res[t] = widthAt(t) * spline.curvature(t);
        }
        return res;
    }

    public double computeSinuosity() {
        double f_x_dx = 0;
        for (int t = 0; t < spline.points().size() - 1; t++)
            f_x_dx += VectorOps.distance(spline.points().get(t), spline.points().get(t + 1));
        return f_x_dx
                / VectorOps.distance(spline.points().getFirst(), spline.points().getLast());
    }

    public int numPts() {
        return spline.points().size();
    }

    public ChannelPt pt(int id) {
        return new ChannelPt(spline.points().get(id), id, channelId, widthAt(id));
    }

    public ChannelPt[] getChannelAsPts() {
        final ChannelPt[] res = new ChannelPt[spline.points().size()];
        for (int i = 0; i < spline.points().size(); i++)
            res[i] = new ChannelPt(spline.points().get(i), i, channelId, widthAt(i));
        return res;
    }

    /** Whether {@link #reSample} can run (delegates to {@link QuinticHermiteSpline#isResampleable}). */
    public boolean isResampleable() {
        return spline.isResampleable();
    }

    public void reSample(double samplingDist) throws IllegalStateException {
        final QuinticHermiteSpline.Resampled resampled = spline.reSampleWithTs(samplingDist);
        final double[] ts = resampled.ts();
        final double[] newFlow = new double[ts.length];
        for (int i = 0; i < ts.length; i++) newFlow[i] = flowAt(ts[i]);
        double[] newBedElevations = null;
        if (bedElevations != null) {
            newBedElevations = new double[ts.length];
            for (int i = 0; i < ts.length; i++) newBedElevations[i] = bedElev(ts[i]);
        }
        this.spline = resampled.spline();
        this.flow = newFlow;
        this.bedElevations = newBedElevations;
    }

    /** Bed elevation between points, the {@link #flowAt} counterpart used by the carve.
     *  Requires {@link #bedElevations} to have been assigned. */
    public double bedElev(double t) {
        final int id = Math.clamp((int) Math.floor(t), 0, bedElevations.length - 2);
        final double u = t - id;
        return bedElevations[id] + (bedElevations[id + 1] - bedElevations[id]) * u;
    }

    @Override
    public String toString() {
        return "Channel{id=" + channelId + ", width="
                + dischargeWidth() + ", depth="
                + depth() + ", pts="
                + (spline.points().size()) + ", sinuosity="
                + String.format("%.3f", computeSinuosity()) + "}";
    }

    public void keepOnly(ArrayList<Integer> newPathIndexes) {
        final double[] newFlow = new double[newPathIndexes.size()];
        for (int i = 0; i < newPathIndexes.size(); i++) newFlow[i] = flow[newPathIndexes.get(i)];
        this.spline = spline.keepOnly(newPathIndexes);
        this.flow = newFlow;
    }

    /**
     * A point of a {@link Channel}, indexed in the {@link me.batata_1.fractal_terrain.math.ds.QuadTree}
     * used by the meander simulation. Carries its in-channel {@code index}, owning {@code channelId},
     * and the per-point {@code width} (derived from the channel's per-point flow). Identity is by
     * {@code channelId} + {@code index} + coordinates (the {@code width} is excluded).
     */
    public record ChannelPt(double[] coords, int index, int channelId, double width) implements SpatialIndexPoint {

        public ChannelPt(double[] pt, int id, int channelId) {
            this(pt, id, channelId, 0.0);
        }

        @Override
        public double[] getCoords() {
            return coords;
        }

        @Override
        public String toString() {
            return "[" + Arrays.toString(coords) + " " + index + " chId:" + channelId + "]";
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ChannelPt comp)) return false;
            return channelId == comp.channelId && index == comp.index && Arrays.equals(coords, comp.coords);
        }

        @Override
        public int hashCode() {
            return Objects.hash(index, channelId, Arrays.hashCode(coords));
        }
    }
}
