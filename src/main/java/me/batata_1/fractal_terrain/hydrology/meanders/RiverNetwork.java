package me.batata_1.fractal_terrain.hydrology.meanders;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.HydrologicalFeature;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;

/**
 * The river-network graph and all topology/geometry it owns: a directed dendritic in-tree whose edges
 * are {@link Channel}s (keyed by {@code channelId}) and whose vertices are typed {@link Endpoint}s sitting
 * on channel endpoints (single-outflow invariant — see {@link Endpoint}).
 *
 * <p>This class is the container the {@link Meanders} simulation mutates: it holds the channels/nodes,
 * the working spatial index, and all the structural primitives — {@link #split}/{@link #merge}, leaf
 * pruning, self-intersection cutoffs ({@link #manageCutoffs}) and stream-capture collision resolution
 * ({@link #manageCollisions}). {@code Meanders} contributes only the migration model and step
 * orchestration.
 *
 * <p>When {@code savePreviousStates} is enabled it additionally records, per step, the paths removed by
 * collisions (as {@link HydrologicalFeature#ABANDONED_RIVER}) and by cutoffs (as
 * {@link HydrologicalFeature#OXBOW_LAKE}), plus bounded network snapshots. The relaxation phase runs
 * with recording disabled, so it produces no history.
 *
 * <p>{@link #convertImutableQuadtree} packages the current network (plus recorded removed features) into
 * an immutable {@link QuadTree} of {@link HydrologicalUnit}s, resampling every feature at
 * {@code dx = width/2} so wider features carry proportionally fewer points.
 */
public final class RiverNetwork {

    private static final double INF = 1e9;
    /** contiguous index gap (on the same channel pair) below which contacts are one crossing. */
    private static final int CLUSTER_GAP = 3;
    /** Floor on the resample spacing used when converting features to {@link HydrologicalUnit}s. */
    private static final double MIN_CONVERT_SPACING = 0.5;

    private final int gridSize;
    private final QuadTree<Channel.ChannelPt> quadTree =
            new QuadTree<>(new double[] {-INF, -INF}, new double[] {INF, INF});

    // graph storage: stable channel/node ids (sparse after split/merge/prune)
    private final Map<Integer, Channel> channels = new HashMap<>();
    private final Map<Integer, Endpoint> nodes = new HashMap<>();
    private int nextChannelId = 0;
    private int nextNodeId = 0;

    // history (only populated when savePreviousStates is true)
    private final boolean savePreviousStates;
    private final int maxSavedStates;
    private final ArrayDeque<List<ArrayList<double[]>>> previousStates = new ArrayDeque<>();
    private final List<RemovedPath> removedPaths = new ArrayList<>();

    /** A vertex of the supplied initial network. */
    public record NodeSpec(double x, double z, Endpoint.Type type) {}

    /** A directed edge of the supplied initial network; {@code pts} include the two endpoints. */
    public record EdgeSpec(int startNodeIdx, int endNodeIdx, ArrayList<double[]> pts, double width) {}

    private record Crossing(int channelIdA, int posA, int channelIdB, int posB, double widthA, double widthB) {}

    /** A geometry removed from the active network, retained for {@link #convertImutableQuadtree}. */
    private record RemovedPath(HydrologicalFeature type, ArrayList<double[]> pts, double width, int time) {}

    public RiverNetwork(
            int gridSize,
            List<NodeSpec> nodeSpecs,
            List<EdgeSpec> edgeSpecs,
            boolean savePreviousStates,
            int maxSavedStates,
            double resampleDist) {

        this.gridSize = gridSize;
        this.savePreviousStates = savePreviousStates;
        this.maxSavedStates = maxSavedStates;
        if (FractalTerrainConfig.DEBUG_RIVER_NET)
            Debug.river.seeNetwork(gridSize, nodeSpecs, edgeSpecs, "river_network", "_");

        for (int i = 0; i < nodeSpecs.size(); i++) {
            NodeSpec ns = nodeSpecs.get(i);
            nodes.put(i, new Endpoint(i, ns.type(), new double[] {ns.x(), ns.z()}));
        }
        nextNodeId = nodeSpecs.size();

        for (EdgeSpec es : edgeSpecs) {
            final int id = nextChannelId++;
            Channel ch = new Channel(es.width(), new ArrayList<>(es.pts()), id);
            ch.reSample(resampleDist);
            ch.startNodeId = es.startNodeIdx();
            ch.endNodeId = es.endNodeIdx();
            channels.put(id, ch);
            Endpoint start = nodes.get(es.startNodeIdx());
            Endpoint end = nodes.get(es.endNodeIdx());
            if (start.outgoing != -1) {
                throw new IllegalArgumentException("node " + start.id + " would have >1 outgoing edge");
            }
            start.outgoing = id;
            end.incoming.add(id);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Step hooks used by Meanders
    // ---------------------------------------------------------------------------------------------

    /** Clears the working spatial index at the start of a step. */
    public void beginStep() {
        quadTree.clear();
    }

    public int getGridSize() {
        return gridSize;
    }

    // ---------------------------------------------------------------------------------------------
    // Cutoffs (per-channel self-intersection removal)
    // ---------------------------------------------------------------------------------------------

    private List<Channel.ChannelPt> getPtsCloseTo(Channel.ChannelPt pt) {
        return quadTree.getPointsInCircle(pt.toArray(), Math.sqrt(channels.get(pt.channelId()).width));
    }

    public void manageCutoffs(Channel ch, int step) {
        if (ch.spline.checkNaN()) {
            throw new RuntimeException("cannot cut becuse spline is NaN");
        }
        quadTree.clear();
        insertChannel(ch);
        ArrayList<Integer> newPathIndexes = new ArrayList<>();

        for (int id = 0; id < ch.numPts() - 1; id++) {
            if (!quadTree.containsPoint(ch.pt(id))) continue;
            newPathIndexes.add(id);
            List<Channel.ChannelPt> ptList = getPtsCloseTo(ch.pt(id));
            ptList.sort(null);
            for (Channel.ChannelPt cpt : ptList) {
                if (cpt.index() <= id + 1 || cpt.channelId() != ch.channelId) continue;
                cutRiverSection(id, cpt.index(), ch);
            }
        }
        newPathIndexes.add(ch.numPts() - 1);
        if (savePreviousStates) recordRemovedComplement(ch, newPathIndexes, HydrologicalFeature.OXBOW_LAKE, step);
        ch.keepOnly(newPathIndexes);
    }

    private void insertChannel(Channel ch) {
        Channel.ChannelPt[] pts = ch.getChannelAsPts();
        for (Channel.ChannelPt pt : pts) {
            quadTree.insertPoint(pt);
        }
    }

    private void cutRiverSection(int from, int to, Channel ch) {
        for (int i = from; i < to; i++) quadTree.removePoint(ch.pt(i));
    }

    /** Record the points of {@code ch} NOT in {@code keptIndexes} as a removed feature (oxbow loop). */
    private void recordRemovedComplement(
            Channel ch, ArrayList<Integer> keptIndexes, HydrologicalFeature type, int step) {
        final boolean[] kept = new boolean[ch.numPts()];
        for (int idx : keptIndexes) if (idx >= 0 && idx < kept.length) kept[idx] = true;
        final ArrayList<double[]> removed = new ArrayList<>();
        for (int i = 0; i < ch.numPts(); i++)
            if (!kept[i]) removed.add(ch.spline.points().get(i).clone());
        if (removed.size() >= 2) removedPaths.add(new RemovedPath(type, removed, ch.width, step));
    }

    // ---------------------------------------------------------------------------------------------
    // split / merge — single-step graph primitives
    // ---------------------------------------------------------------------------------------------

    public int split(int id, int pos, boolean redirect) {
        Channel channel = channels.get(id);
        if (channel == null) return -1;
        final int lastIndex = channel.numPts() - 1;
        if (pos <= 0 || pos >= lastIndex) return -1;

        final int downstreamNodeId = channel.endNodeId;
        final double[] splitCoord = channel.spline.points().get(pos).clone();

        ArrayList<double[]> downstreamPoints = new ArrayList<>();
        for (int i = pos; i <= lastIndex; i++)
            downstreamPoints.add(channel.spline.points().get(i));
        final int downstreamChannelId = nextChannelId++;
        Channel downstreamChannel = new Channel(channel.width, downstreamPoints, downstreamChannelId);
        downstreamChannel.endNodeId = downstreamNodeId;
        channels.put(downstreamChannelId, downstreamChannel);

        ArrayList<Integer> upstreamIndexes = new ArrayList<>();
        for (int i = 0; i <= pos; i++) upstreamIndexes.add(i);
        channel.keepOnly(upstreamIndexes);

        Endpoint downstreamEndpoint = nodes.get(downstreamNodeId);
        downstreamEndpoint.incoming.remove(id);
        downstreamEndpoint.incoming.add(downstreamChannelId);

        if (!redirect) {
            Endpoint junction = newNode(splitCoord);
            channel.endNodeId = junction.id;
            downstreamChannel.startNodeId = junction.id;
            junction.incoming.add(id);
            junction.outgoing = downstreamChannelId;
        } else {
            Endpoint upstreamJunction = newNode(splitCoord);
            Endpoint downstreamJunction = newNode(splitCoord.clone());
            channel.endNodeId = upstreamJunction.id;
            upstreamJunction.incoming.add(id);
            downstreamChannel.startNodeId = downstreamJunction.id;
            downstreamJunction.outgoing = downstreamChannelId;
        }
        return downstreamChannelId;
    }

    public boolean merge(int id) {
        Channel channel = channels.get(id);
        if (channel == null) return false;
        Endpoint junction = nodes.get(channel.endNodeId);
        if (junction == null || junction.type != Endpoint.Type.JUNCTION) return false;
        if (junction.outgoing == -1) return false;
        if (!(junction.incoming.size() == 1 && junction.incoming.contains(id))) return false;

        final int downstreamChannelId = junction.outgoing;
        Channel downstreamChannel = channels.get(downstreamChannelId);
        final int upstreamNodeId = channel.startNodeId;
        final int downstreamNodeId = downstreamChannel.endNodeId;

        ArrayList<double[]> mergedPoints = new ArrayList<>(channel.spline.points());
        List<double[]> downstreamPoints = downstreamChannel.spline.points();
        for (int i = 1; i < downstreamPoints.size(); i++) mergedPoints.add(downstreamPoints.get(i));

        Channel mergedChannel = new Channel(Math.max(channel.width, downstreamChannel.width), mergedPoints, id);
        mergedChannel.startNodeId = upstreamNodeId;
        mergedChannel.endNodeId = downstreamNodeId;
        channels.put(id, mergedChannel);
        channels.remove(downstreamChannelId);

        Endpoint downstreamEndpoint = nodes.get(downstreamNodeId);
        downstreamEndpoint.incoming.remove(downstreamChannelId);
        downstreamEndpoint.incoming.add(id);

        nodes.remove(junction.id);
        return true;
    }

    private Endpoint newNode(double[] coord) {
        final int nodeId = nextNodeId++;
        Endpoint endpoint = new Endpoint(nodeId, Endpoint.Type.JUNCTION, coord);
        nodes.put(nodeId, endpoint);
        return endpoint;
    }

    // ---------------------------------------------------------------------------------------------
    // Collisions (stream capture)
    // ---------------------------------------------------------------------------------------------

    public void manageCollisions(int step) {
        List<Crossing> crossings = detectCrossings();
        segmentAndResolve(crossings);
        pruneDanglingJunctionLeaves(step);
        deleteOrphanDrains();
        mergePassFromSources();
    }

    private void deleteOrphanDrains() {
        List<Integer> orphanDrainIds = new ArrayList<>();
        for (Endpoint endpoint : nodes.values())
            if (endpoint.type == Endpoint.Type.DRAIN && endpoint.degree() == 0) orphanDrainIds.add(endpoint.id);
        for (int drainId : orphanDrainIds) nodes.remove(drainId);
    }

    private List<Crossing> detectCrossings() {
        quadTree.clear();
        List<Integer> channelIds = new ArrayList<>(channels.keySet());
        Collections.sort(channelIds);
        for (int channelId : channelIds) insertChannel(channels.get(channelId));

        List<Crossing> crossings = new ArrayList<>();
        for (int channelAId : channelIds) {
            Channel channelA = channels.get(channelAId);
            final int pointCountA = channelA.numPts();
            final double queryRadius = Math.max(channelA.width, 1.0);
            Map<Integer, List<double[]>> contactsByPartner = new HashMap<>();
            for (int posA = 1; posA < pointCountA - 1; posA++) {
                final double[] pointA = channelA.spline.points().get(posA);
                List<Channel.ChannelPt> nearbyPoints = quadTree.getPointsInCircle(pointA, queryRadius);
                nearbyPoints.sort(null);

                for (Channel.ChannelPt nearbyPoint : nearbyPoints) {
                    final int channelBId = nearbyPoint.channelId();
                    if (channelBId <= channelAId) continue;
                    Channel channelB = channels.get(channelBId);
                    final int posB = nearbyPoint.index();
                    if (posB <= 0 || posB >= channelB.numPts() - 1) continue;

                    if (nearSharedNode(channelA, channelB, pointA)) continue;
                    final double distance = VectorOps.distance(pointA, nearbyPoint.toArray());
                    if (distance > 0.5 * (channelA.width + channelB.width)) continue;
                    contactsByPartner
                            .computeIfAbsent(channelBId, partner -> new ArrayList<>())
                            .add(new double[] {posA, posB, distance});
                }
            }
            for (Map.Entry<Integer, List<double[]>> entry : contactsByPartner.entrySet()) {
                Channel channelB = channels.get(entry.getKey());
                List<double[]> contactList = entry.getValue();
                contactList.sort(Comparator.comparingDouble(contact -> contact[0]));
                int firstClusterEnd = 1;
                while (firstClusterEnd < contactList.size()
                        && contactList.get(firstClusterEnd)[0] - contactList.get(firstClusterEnd - 1)[0] <= CLUSTER_GAP)
                    firstClusterEnd++;
                double[] closest = contactList.getFirst();
                for (int scan = 1; scan < firstClusterEnd; scan++)
                    if (contactList.get(scan)[2] < closest[2]) closest = contactList.get(scan);
                crossings.add(new Crossing(
                        channelAId,
                        (int) closest[0],
                        channelB.channelId,
                        (int) closest[1],
                        channelA.width,
                        channelB.width));
            }
        }
        return crossings;
    }

    private boolean nearSharedNode(Channel channelA, Channel channelB, double[] contactPoint) {
        final double radius = channelA.width + channelB.width;
        final double radiusSq = radius * radius;
        for (int nodeA : new int[] {channelA.startNodeId, channelA.endNodeId})
            for (int nodeB : new int[] {channelB.startNodeId, channelB.endNodeId})
                if (nodeA != -1 && nodeA == nodeB) {
                    Endpoint shared = nodes.get(nodeA);
                    if (shared != null && VectorOps.distanceSquared(contactPoint, shared.coord) <= radiusSq)
                        return true;
                }
        return false;
    }

    private int decideWinner(Crossing crossing) {
        if (reachesDownstream(crossing.channelIdA(), crossing.channelIdB())) return 1;
        if (reachesDownstream(crossing.channelIdB(), crossing.channelIdA())) return 0;
        if (crossing.widthA() > crossing.widthB()) return 0;
        if (crossing.widthB() > crossing.widthA()) return 1;
        return crossing.channelIdA() < crossing.channelIdB() ? 0 : 1;
    }

    private boolean reachesDownstream(int ancestorId, int descendantId) {
        Channel ancestor = channels.get(ancestorId);
        if (ancestor == null) return false;
        int nodeId = ancestor.endNodeId;
        for (int guard = 0; guard <= channels.size() && nodeId != -1; guard++) {
            Endpoint endpoint = nodes.get(nodeId);
            if (endpoint == null || endpoint.outgoing == -1) return false;
            if (endpoint.outgoing == descendantId) return true;
            Channel next = channels.get(endpoint.outgoing);
            if (next == null) return false;
            nodeId = next.endNodeId;
        }
        return false;
    }

    private void segmentAndResolve(List<Crossing> crossings) {
        final int crossingCount = crossings.size();
        final int[] winnerSide = new int[crossingCount];
        for (int crossingIdx = 0; crossingIdx < crossingCount; crossingIdx++)
            winnerSide[crossingIdx] = decideWinner(crossings.get(crossingIdx));

        Map<Integer, TreeMap<Integer, Integer>> splitsByChannel = new HashMap<>();
        for (int crossingIdx = 0; crossingIdx < crossingCount; crossingIdx++) {
            Crossing crossing = crossings.get(crossingIdx);
            splitsByChannel
                    .computeIfAbsent(crossing.channelIdA(), channelId -> new TreeMap<>())
                    .put(crossing.posA(), crossingIdx);
            splitsByChannel
                    .computeIfAbsent(crossing.channelIdB(), channelId -> new TreeMap<>())
                    .put(crossing.posB(), crossingIdx);
        }

        final int[] winnerJunctionId = new int[crossingCount];
        final int[] loserUpstreamJunctionId = new int[crossingCount];
        final int[] loserUpstreamSegmentId = new int[crossingCount];
        Arrays.fill(winnerJunctionId, -1);
        Arrays.fill(loserUpstreamJunctionId, -1);
        Arrays.fill(loserUpstreamSegmentId, -1);

        for (Map.Entry<Integer, TreeMap<Integer, Integer>> entry : splitsByChannel.entrySet()) {
            final int channelId = entry.getKey();
            Channel originalChannel = channels.get(channelId);
            final List<Integer> splitPositions =
                    new ArrayList<>(entry.getValue().keySet());
            final List<Integer> crossingAtPosition =
                    new ArrayList<>(entry.getValue().values());
            final int splitCount = splitPositions.size();
            final int upstreamNodeId = originalChannel.startNodeId;
            final int downstreamNodeId = originalChannel.endNodeId;
            final int lastIndex = originalChannel.numPts() - 1;

            final int[] segmentIds = new int[splitCount + 1];
            int rangeStart = 0;
            for (int segmentIndex = 0; segmentIndex <= splitCount; segmentIndex++) {
                final int startIndex = rangeStart;
                final int endIndex = (segmentIndex < splitCount) ? splitPositions.get(segmentIndex) : lastIndex;
                ArrayList<double[]> segmentPoints = new ArrayList<>();
                for (int i = startIndex; i <= endIndex; i++)
                    segmentPoints.add(originalChannel.spline.points().get(i));
                final int segmentId = (segmentIndex == 0) ? channelId : nextChannelId++;
                Channel segment = new Channel(originalChannel.width, segmentPoints, segmentId);
                channels.put(segmentId, segment);
                segmentIds[segmentIndex] = segmentId;
                rangeStart = endIndex;
            }

            channels.get(segmentIds[0]).startNodeId = upstreamNodeId;
            channels.get(segmentIds[splitCount]).endNodeId = downstreamNodeId;
            Endpoint downstreamEndpoint = nodes.get(downstreamNodeId);
            downstreamEndpoint.incoming.remove(channelId);
            downstreamEndpoint.incoming.add(segmentIds[splitCount]);

            for (int boundary = 0; boundary < splitCount; boundary++) {
                final int crossingIdx = crossingAtPosition.get(boundary);
                final int upstreamSegmentId = segmentIds[boundary];
                final int downstreamSegmentId = segmentIds[boundary + 1];
                final double[] junctionCoord = originalChannel
                        .spline
                        .points()
                        .get(splitPositions.get(boundary))
                        .clone();
                Crossing crossing = crossings.get(crossingIdx);
                final boolean channelWins = (channelId == crossing.channelIdA() && winnerSide[crossingIdx] == 0)
                        || (channelId == crossing.channelIdB() && winnerSide[crossingIdx] == 1);
                if (channelWins) {
                    Endpoint winnerJunction = newNode(junctionCoord);
                    channels.get(upstreamSegmentId).endNodeId = winnerJunction.id;
                    channels.get(downstreamSegmentId).startNodeId = winnerJunction.id;
                    winnerJunction.incoming.add(upstreamSegmentId);
                    winnerJunction.outgoing = downstreamSegmentId;
                    winnerJunctionId[crossingIdx] = winnerJunction.id;
                } else {
                    Endpoint loserUpstreamJunction = newNode(junctionCoord);
                    Endpoint loserDownstreamJunction = newNode(junctionCoord.clone());
                    channels.get(upstreamSegmentId).endNodeId = loserUpstreamJunction.id;
                    loserUpstreamJunction.incoming.add(upstreamSegmentId);
                    channels.get(downstreamSegmentId).startNodeId = loserDownstreamJunction.id;
                    loserDownstreamJunction.outgoing = downstreamSegmentId;
                    loserUpstreamJunctionId[crossingIdx] = loserUpstreamJunction.id;
                    loserUpstreamSegmentId[crossingIdx] = upstreamSegmentId;
                }
            }
        }

        for (int crossingIdx = 0; crossingIdx < crossingCount; crossingIdx++) {
            final int winnerJunction = winnerJunctionId[crossingIdx];
            final int loserJunction = loserUpstreamJunctionId[crossingIdx];
            final int loserSegment = loserUpstreamSegmentId[crossingIdx];
            if (winnerJunction == -1 || loserJunction == -1 || loserSegment == -1) continue;
            Channel tributary = channels.get(loserSegment);
            tributary.endNodeId = winnerJunction;
            nodes.get(winnerJunction).incoming.add(loserSegment);
            ArrayList<double[]> tributaryPoints = tributary.spline.points();
            tributaryPoints.set(
                    tributaryPoints.size() - 1, nodes.get(winnerJunction).coord.clone());
            tributary.spline = QuinticHermiteSpline.createCatmullRom(tributaryPoints);
            nodes.remove(loserJunction);
        }
    }

    private void pruneDanglingJunctionLeaves(int step) {
        ArrayDeque<Integer> leafQueue = new ArrayDeque<>();
        for (Endpoint endpoint : nodes.values())
            if (endpoint.type == Endpoint.Type.JUNCTION && endpoint.degree() == 1) leafQueue.add(endpoint.id);
        while (!leafQueue.isEmpty()) {
            final int nodeId = leafQueue.poll();
            Endpoint endpoint = nodes.get(nodeId);
            if (endpoint == null || endpoint.type != Endpoint.Type.JUNCTION || endpoint.degree() != 1) continue;

            final boolean incidentIsOutgoing = endpoint.outgoing != -1;
            final int channelId = incidentIsOutgoing
                    ? endpoint.outgoing
                    : endpoint.incoming.iterator().next();
            Channel channel = channels.get(channelId);
            final int otherNodeId = incidentIsOutgoing ? channel.endNodeId : channel.startNodeId;
            Endpoint otherEndpoint = nodes.get(otherNodeId);
            if (otherEndpoint != null) {
                if (otherEndpoint.outgoing == channelId) otherEndpoint.outgoing = -1;
                otherEndpoint.incoming.remove(channelId);
                if (otherEndpoint.type == Endpoint.Type.JUNCTION && otherEndpoint.degree() == 1)
                    leafQueue.add(otherEndpoint.id);
            }
            if (savePreviousStates && channel != null && channel.numPts() >= 2) {
                removedPaths.add(new RemovedPath(
                        HydrologicalFeature.ABANDONED_RIVER,
                        new ArrayList<>(channel.spline.points()),
                        channel.width,
                        step));
            }
            channels.remove(channelId);
            nodes.remove(nodeId);
        }
    }

    private void mergePassFromSources() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Endpoint source : new ArrayList<>(nodes.values())) {
                if (source.type != Endpoint.Type.SOURCE) continue;
                int channelId = source.outgoing;
                while (channelId != -1) {
                    Channel channel = channels.get(channelId);
                    if (channel == null) break;
                    if (merge(channelId)) {
                        changed = true;
                        continue;
                    }
                    Endpoint downstreamEndpoint = nodes.get(channel.endNodeId);
                    if (downstreamEndpoint == null) break;
                    channelId = downstreamEndpoint.outgoing;
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // History
    // ---------------------------------------------------------------------------------------------

    /** Snapshot the current channel geometry (bounded to {@code maxSavedStates}); no-op if disabled. */
    public void recordState(int step) {
        if (!savePreviousStates) return;
        final List<ArrayList<double[]>> snapshot = new ArrayList<>(channels.size());
        for (Channel ch : channels.values()) {
            final ArrayList<double[]> copy = new ArrayList<>(ch.numPts());
            for (double[] p : ch.spline.points()) copy.add(p.clone());
            snapshot.add(copy);
        }
        previousStates.addLast(snapshot);
        while (previousStates.size() > maxSavedStates) previousStates.removeFirst();
    }

    // ---------------------------------------------------------------------------------------------
    // Conversion to the queryable, persistable unit tree
    // ---------------------------------------------------------------------------------------------

    /** Samples a scalar field (e.g. decoded terrain elevation) at a point in the network's coordinates. */
    @FunctionalInterface
    public interface ElevationSampler {
        double sample(double x, double z);
    }

    /**
     * Build an immutable {@link QuadTree} of {@link HydrologicalUnit}s from the active channels (type
     * {@link HydrologicalFeature#RIVER}) plus any recorded removed features (oxbow lakes / abandoned
     * rivers). Each feature is first resampled at {@code dx = width/2}; emitted coordinates are the
     * network coordinate minus {@code (offsetX, offsetZ)} (e.g. to drop a halo pad). Per-point bed
     * elevation follows the start→endpoint lerp anchored on the channel's node elevations.
     *
     * @param min lower bound (in emitted coordinates) of the returned tree
     * @param max upper bound (in emitted coordinates) of the returned tree
     */
    public QuadTree<HydrologicalUnit> convertImutableQuadtree(
            int time, ElevationSampler decodedElev, double offsetX, double offsetZ, double[] min, double[] max) {
        final List<HydrologicalUnit> units = new ArrayList<>();
        for (Channel ch : channels.values()) {
            final double startElev = nodeElevation(ch.startNodeId);
            final double endElev = nodeElevation(ch.endNodeId);
            addFeatureUnits(
                    units,
                    ch.spline,
                    ch.width,
                    ch.startWidth,
                    ch.endWidth,
                    startElev,
                    endElev,
                    HydrologicalFeature.RIVER,
                    time,
                    decodedElev,
                    offsetX,
                    offsetZ);
        }
        for (RemovedPath rp : removedPaths) {
            final QuinticHermiteSpline spline = QuinticHermiteSpline.createCatmullRom(rp.pts());
            addFeatureUnits(
                    units,
                    spline,
                    rp.width(),
                    rp.width(),
                    rp.width(),
                    Double.NaN,
                    Double.NaN,
                    rp.type(),
                    rp.time(),
                    decodedElev,
                    offsetX,
                    offsetZ);
        }
        return new QuadTree<>(min, max, units, HydrologicalUnit.PROTOTYPE);
    }

    private static void addFeatureUnits(
            List<HydrologicalUnit> out,
            QuinticHermiteSpline spline,
            double width,
            double startWidth,
            double endWidth,
            double startElev,
            double endElev,
            HydrologicalFeature type,
            int time,
            ElevationSampler decodedElev,
            double offsetX,
            double offsetZ) {
        if (spline.getSize() < 2) return;
        final double dx = Math.max(width / 2.0, MIN_CONVERT_SPACING);
        final QuinticHermiteSpline resampled;
        try {
            resampled = spline.reSample(dx);
        } catch (RuntimeException degenerate) {
            return;
        }
        final List<double[]> pts = resampled.points();
        final int n = pts.size();
        final double anchorEnd = Double.isNaN(endElev) ? 0.0 : endElev;
        for (int i = 0; i < n; i++) {
            final double[] p = pts.get(i);
            final double frac = (n == 1) ? 0.0 : (double) i / (n - 1);
            final double w = startWidth + (endWidth - startWidth) * frac;
            final double bed;
            if (i == 0 && !Double.isNaN(startElev)) {
                bed = startElev;
            } else {
                // lerp max(decoded, endpointElev) -> endpointElev by distance along the channel.
                final double candidate = Math.max(decodedElev.sample(p[0], p[1]), anchorEnd);
                bed = candidate + (anchorEnd - candidate) * frac;
            }
            out.add(new HydrologicalUnit(type, null, List.of(p[0] - offsetX, p[1] - offsetZ), w, bed, time));
        }
    }

    private double nodeElevation(int nodeId) {
        final Endpoint endpoint = nodes.get(nodeId);
        return (endpoint == null) ? Double.NaN : endpoint.elevation;
    }

    // ---------------------------------------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------------------------------------

    public List<Channel> getChannels() {
        return new ArrayList<>(channels.values());
    }

    public ArrayList<double[]> getChannelPts(int channelId) {
        return channels.get(channelId).spline.points();
    }

    public int getChannelCount() {
        return channels.size();
    }

    public Channel getChannel(int id) {
        return channels.get(id);
    }

    public Collection<Endpoint> getNodes() {
        return nodes.values();
    }

    public Endpoint getNode(int id) {
        return nodes.get(id);
    }
}
