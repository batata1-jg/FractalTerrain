package me.batata_1.fractal_terrain.hydrology.meanders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexPoint;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;

public class Channel {
    public final double width, depth;
    public final int channelId;
    public QuinticHermiteSpline spline;

    /**
     * Per-point width profile: the width at spline index 0 ({@code startWidth}) and at the last index
     * ({@code endWidth}); intermediate points lerp between them by their normalized index. Both default
     * to {@link #width} so callers that only care about a single width (e.g. the {@link Meanders} sim)
     * are unaffected. {@code LocalRiverProvider} sets these so widths taper start→end and align where
     * channels meet (local confluences and the global river).
     */
    public double startWidth, endWidth;

    /**
     * Directed-edge endpoints in the {@link Meanders} river-network graph: spline index 0 sits on
     * {@code startNodeId} (upstream), the last index on {@code endNodeId} (downstream); flow goes
     * 0 -> last. {@code -1} means the channel is not part of a graph (e.g. LocalRiverProvider's
     * direct use), so those callers are unaffected.
     */
    public int startNodeId = -1, endNodeId = -1;

    /**
     * Per-spline-point bed elevation (native-px scale), aligned to {@link #spline} points. Filled once per
     * tile by {@code ChannelElevationAssigner.assign}, which runs a single pass over the whole unified
     * network — global and local channels alike — after local insertion; {@code null} until assigned.
     * {@link #reSample} keeps this array index-aligned to the spline's resampled points. Read directly by
     * {@code carveRiver} and by {@link RiverNetwork#collectUnits}, which emits it as each unit's elevation
     * without deriving or re-sampling it from decoded terrain.
     */
    public double[] bedElevations;

    public Channel(double width, ArrayList<double[]> pts, int channelId) {
        this.width = width;
        this.depth = ChannelGeometry.depthForWidth(width);
        this.spline = QuinticHermiteSpline.createCatmullRom(pts);
        this.channelId = channelId;
        this.startWidth = width;
        this.endWidth = width;
    }

    /** Set the start→end width taper used to assign each {@link ChannelPt}'s per-point width. */
    public void setWidthProfile(double startWidth, double endWidth) {
        this.startWidth = startWidth;
        this.endWidth = endWidth;
    }

    /** Lerped width at spline index {@code i} (start→end by normalized index). */
    private double widthAt(int i) {
        final int size = spline.points().size();
        final double frac = (size <= 1) ? 0.0 : (double) i / (size - 1);
        return startWidth + (endWidth - startWidth) * frac;
    }

    public double[] computeLocalRates() {
        final double[] res = new double[spline.points().size()];
        for (int t = 0; t < spline.points().size(); t++) {
            res[t] = width * spline.curvature(t);
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
        if (bedElevations == null) {
            this.spline = spline.reSample(samplingDist);
            return;
        }
        final QuinticHermiteSpline.Resampled resampled = spline.reSampleWithTs(samplingDist);
        final double[] newBedElevations = new double[resampled.ts().length];
        for (int i = 0; i < newBedElevations.length; i++)
            newBedElevations[i] = bedElev(resampled.ts()[i]);
        this.spline = resampled.spline();
        this.bedElevations = newBedElevations;
    }

    /**
     * Bed elevation at arc-length parameter {@code t}: a linear blend of {@code bedElevations[floor(t)]}
     * and {@code bedElevations[floor(t)+1]} at {@code u = t - floor(t)}, with {@code floor(t)} clamped to
     * the valid segment range, mirroring {@link QuinticHermiteSpline#sample}'s segment indexing.
     * {@link #bedElevations} must be non-null.
     */
    public double bedElev(double t) {
        final int id = Math.clamp((int) Math.floor(t), 0, bedElevations.length - 2);
        final double u = t - id;
        return bedElevations[id] + (bedElevations[id + 1] - bedElevations[id]) * u;
    }

    @Override
    public String toString() {
        return "Channel{id=" + channelId + ", width="
                + width + ", depth="
                + depth + ", pts="
                + (spline.points().size()) + ", sinuosity="
                + String.format("%.3f", computeSinuosity()) + "}";
    }

    public void keepOnly(ArrayList<Integer> newPathIndexes) {
        this.spline = spline.keepOnly(newPathIndexes);
    }

    /**
     * Adds point from a channel to another channel. Keeps the tangents and curvatures of the other channel in the point.
     * */
    public void add(ChannelPt pt, Channel from) {
        this.spline.points().add(pt.toArray());
        this.spline.velocity().add(from.spline.velocity().get(pt.index()));
        this.spline.acceleration().add(from.spline.acceleration().get(pt.index()));
    }

    /**
     * Adds point from a channel to another channel. Keeps the tangents and curvatures of the other channel in the point.
     * */
    public void add(int id, Channel from) {
        this.spline.points().add(from.spline.points().get(id));
        this.spline.velocity().add(from.spline.velocity().get(id));
        this.spline.acceleration().add(from.spline.acceleration().get(id));
    }

    public void addFront(int id, Channel from) {
        this.spline.points().addFirst(from.spline.points().get(id));
        this.spline.velocity().addFirst(from.spline.velocity().get(id));
        this.spline.acceleration().addFirst(from.spline.acceleration().get(id));
    }

    /**
     * removes indexes from this list in the spline.
     * does not assume there is no repetition
     * */
    public void removeIndexes(ArrayList<Integer> indexes) {
        indexes.sort(null);
        final ArrayList<Integer> indexesToAdd = new ArrayList<>();
        for (int i = 0; i < spline.getSize(); i++)
            if (i == indexes.getFirst()) {
                while (i == indexes.getFirst()) indexes.removeFirst();
            } else {
                indexesToAdd.add(i);
            }
        this.spline = new QuinticHermiteSpline(
                new ArrayList<>(
                        indexesToAdd.stream().map(id -> spline.points().get(id)).toList()),
                new ArrayList<>(indexesToAdd.stream()
                        .map(id -> spline.velocity().get(id))
                        .toList()),
                new ArrayList<>(indexesToAdd.stream()
                        .map(id -> spline.acceleration().get(id))
                        .toList()));
    }

    /**
     * A point of a {@link Channel}, indexed in the {@link me.batata_1.fractal_terrain.math.ds.QuadTree}
     * used by the meander simulation. Carries its in-channel {@code index}, owning {@code channelId},
     * and the per-point {@code width} (lerped along the channel's start→end taper). Identity is by
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
