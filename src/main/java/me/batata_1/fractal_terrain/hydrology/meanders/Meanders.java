package me.batata_1.fractal_terrain.hydrology.meanders;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

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
import java.util.function.Consumer;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;

/**
 * River-network editor. A {@code Meanders} instance is constructed with a COMPLETE initial network
 * (sources, drains, junctions, channels) and only mutates it — it never originates the network. The
 * source count is fixed; drains are never added but may be deleted when a capture orphans one. The
 * network is a directed dendritic in-tree: edges
 * are {@link Channel}s (keyed by {@code channelId}), vertices are typed {@link Node}s sitting on
 * channel endpoints (see {@link Node} for the single-outflow invariant).
 *
 * <p>New channels and junctions appear only internally when {@link #manageCollisions()} resolves
 * stream-capture events: where a wider river crosses a narrower one, the narrow channel is captured
 * into the wide one at a confluence junction.
 */
public final class Meanders {

    // sampling dist e DT estao muito intimos
    private static final double TWO_THIRDS = 2.0 / 3.0;
    private static final double INF = 1e9;
    private static final double OMEGA = -1.0;
    // aumenta pra aumentar a varian
    private static final double GAMMA = 2.5;

    private static final double K = 0.0164; // aumentar esse faz curvar pra traz
    private static final double FRICTION = 0.011; // diminue a
    private static final double DT = 1;
    // mudar pra 50m depois
    public static final double DX = 1.5;
    /** max per-step displacement for the valley-seeking migration. */
    private static final double MAX_GRAD_MIGRATION = DX;
    /** contiguous index gap (on the same channel pair) below which contacts are one crossing. */
    private static final int CLUSTER_GAP = 3;

    /** When true, step()/manageCollisions() dump per-stage network PNGs into step_&lt;n&gt;/ folders. */
    public static boolean DEBUG_STEPS = false;

    private static final double[] UNIT_VECTOR = new double[] {1, 1};
    private static final Logger LOG = getLogger(Meanders.class);

    private final int gridSize;
    private final float[] gradX;
    private final float[] gradZ;
    private final QuadTree<Channel.ChannelPt> quadTree =
            new QuadTree<>(new double[] {-INF, -INF}, new double[] {INF, INF});

    // graph storage: stable channel/node ids (sparse after split/merge/prune)
    private final Map<Integer, Channel> channels = new HashMap<>();
    private final Map<Integer, Node> nodes = new HashMap<>();
    private int nextChannelId = 0;
    private int nextNodeId = 0;
    private int currentStep = 0; // the step number being processed, for debug image folders

    private final double cutOffThreshold;
    private final double maxMigrationMagnetude;

    /** A vertex of the supplied initial network. */
    public record NodeSpec(double x, double z, Node.NodeType type) {}

    /** A directed edge of the supplied initial network; {@code pts} include the two endpoints. */
    public record EdgeSpec(int startNodeIdx, int endNodeIdx, ArrayList<double[]> pts, double width) {}
    
    public Meanders(int gridSize, float[] gradX, float[] gradZ, List<NodeSpec> nodeSpecs, List<EdgeSpec> edgeSpecs) {
        this.gridSize = gridSize;
        this.gradX = gradX;
        this.gradZ = gradZ;
        cutOffThreshold = 1.5 * DX * Math.max(Math.log(Math.sqrt(DT)), 1) * Math.max(Math.log(FRICTION * 10), 1);
        maxMigrationMagnetude = INF;

        for (int i = 0; i < nodeSpecs.size(); i++) {
            NodeSpec ns = nodeSpecs.get(i);
            nodes.put(i, new Node(i, ns.type(), new double[] {ns.x(), ns.z()}));
        }
        nextNodeId = nodeSpecs.size();

        for (EdgeSpec es : edgeSpecs) {
            final int id = nextChannelId++;
            Channel ch = new Channel(es.width(), new ArrayList<>(es.pts()), id);
            ch.reSample(DX);
            ch.startNodeId = es.startNodeIdx();
            ch.endNodeId = es.endNodeIdx();
            channels.put(id, ch);
            Node start = nodes.get(es.startNodeIdx());
            Node end = nodes.get(es.endNodeIdx());
            Debug.river.seeNetwork(gridSize, nodeSpecs, edgeSpecs, "debug", "invalid_network");
            if (start.outgoing != -1) {

                throw new IllegalArgumentException("node " + start.id + " would have >1 outgoing edge");
            }
            start.outgoing = id;
            end.incoming.add(id);
        }
    }

    /** One simulation step using the Ikeda-Parker-Sawai meander migration. */
    public void step(int i) {
        stepImpl(i, this::migrateMeanders);
    }

    /** One step that only relaxes channels down the terrain gradient (no meandering). */
    public void relaxStep(int i) {
        stepImpl(i, this::migrateLowerGrad);
    }

    /** Relax the whole network down-gradient for {@code steps} steps (cutoffs + collisions run). */
    public void relaxLowerGrad(int steps) {
        for (int i = 1; i <= steps; i++) relaxStep(i);
    }

    private void stepImpl(int i, Consumer<Channel> migrate) {
        currentStep = i;
        //  LOG.info("step {}", i);
        quadTree.clear();
        for (Channel ch : new ArrayList<>(channels.values())) {
            ch.reSample(DX);
            ch.spline = QuinticHermiteSpline.createCatmullRom(ch.spline.points());
            migrate.accept(ch);
            ch.reSample(Math.sqrt(ch.width));
            manageCutoffs(ch);
        }
        dumpNetwork("01_migrated");
        manageCollisions();
        for (Channel ch : new ArrayList<>(channels.values())) {
            ch.reSample(DX);
            ch.spline = QuinticHermiteSpline.createCatmullRom(ch.spline.points());
        }
        dumpNetwork("05_final");
    }

    /** Dumps the whole network into {@code step_<currentStep>/<name>.png} when {@link #DEBUG_STEPS}. */
    private void dumpNetwork(String name) {
        if (!DEBUG_STEPS) return;
        Debug.river.seeNetwork(this, "step_" + currentStep, name);
    }

    public void simulate(int n) {
        for (int i = 1; i <= n; i++) step(i);
    }

    public int getGridSize() {
        return gridSize;
    }

    // ---------------------------------------------------------------------------------------------
    // Migration
    // ---------------------------------------------------------------------------------------------

    private static double[] computeMigrationRates(Channel ch) {
        final double sinuosity = ch.computeSinuosity();
        final double[] localRates = ch.computeLocalRates();
        Debug.isNan(localRates);
        final double sigmaToTheMinus2over3 = Math.pow(sinuosity, -TWO_THIRDS);
        final double alpha = 2 * FRICTION / ch.depth;
        final double expTerm = Math.exp(-alpha * DX);
        double integralTerm = 0;
        final double[] migRates = new double[ch.spline.points().size()];
        for (int i = 0; i < ch.spline.points().size(); i++) {
            integralTerm += localRates[i] * K;
            final double migRate = OMEGA * localRates[i]
                    +
                    // remove alpha
                    GAMMA * alpha * integralTerm;
            integralTerm *= expTerm;
            migRates[i] = migRate * sigmaToTheMinus2over3;
        }
        Debug.isNan(migRates);
        return migRates;
    }

    /**
     *   Migrates points to new locations using the Ikeda-Parker-Sawai meandering model.
     *   The first/last points (graph node endpoints) are pinned so source/drain/junction
     *   coordinates stay locked. Does not migrate the derivatives, only the points; the
     *   derivatives are recomputed from the new points (an approximation).
     */
    public void migrateMeanders(Channel ch) {
        final double[] migrationRates = computeMigrationRates(ch);
        final int pointCount = ch.spline.points().size();
        ArrayList<double[]> migratedPoints = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            if (i == 0 || i == pointCount - 1) {
                migratedPoints.add(ch.spline.points().get(i)); // pin node endpoints
                continue;
            }
            final double rate = Math.clamp(DT * migrationRates[i], -maxMigrationMagnetude, maxMigrationMagnetude);
            final double[] migrationVector = VectorOps.scale(ch.spline.normal(i), -rate);
            double[] migratedPoint = VectorOps.add(ch.spline.points().get(i), migrationVector);
            Debug.isNan(migratedPoint);
            migratedPoints.add(migratedPoint);
        }
        ch.spline = QuinticHermiteSpline.createCatmullRom(migratedPoints);
    }

    /**
     * Pushes each interior point toward the valley (down the terrain gradient) within a maximum
     * migration rate per step. NOTE: written for later use; not called from {@link #step}.
     */
    public void migrateLowerGrad(Channel ch) {
        final int pointCount = ch.spline.points().size();
        ArrayList<double[]> migratedPoints = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            final double[] point = ch.spline.points().get(i);
            if (i == 0 || i == pointCount - 1) {
                migratedPoints.add(point); // pin node endpoints
                continue;
            }
            final double[] gradient = sampleGradient(point[0], point[1]);
            double[] displacement = new double[] {-gradient[0], -gradient[1]}; // valley-seeking = -gradient
            final double magnitude = VectorOps.magnitude(displacement);
            if (magnitude > MAX_GRAD_MIGRATION)
                displacement = VectorOps.scale(displacement, MAX_GRAD_MIGRATION / magnitude);
            migratedPoints.add(VectorOps.add(point, displacement));
        }
        ch.spline = QuinticHermiteSpline.createCatmullRom(migratedPoints);
    }

    private double[] sampleGradient(double x, double z) {
        final int xCell = Math.clamp((int) Math.round(x), 0, gridSize - 1);
        final int zCell = Math.clamp((int) Math.round(z), 0, gridSize - 1);
        final int cellIndex = xCell * gridSize + zCell;
        return new double[] {gradX[cellIndex], gradZ[cellIndex]};
    }

    @TestOnly
    public double[][] computedMigVector(Channel ch) {
        final double[] migRates = computeMigrationRates(ch);
        double[][] newPts = new double[ch.spline.getSize()][2];
        for (int i = 0; i < ch.spline.points().size(); i++) {
            final double rate = Math.clamp(DT * migRates[i], -maxMigrationMagnetude, maxMigrationMagnetude);
            final double[] migVector = VectorOps.scale(ch.spline.normal(i), -rate);
            double[] newPt = VectorOps.add(ch.spline.points().get(i), migVector);
            Debug.isNan(newPt);
            newPts[i] = newPt;
        }
        return newPts;
    }

    // ---------------------------------------------------------------------------------------------
    // Cutoffs (per-channel self-intersection removal) — unchanged logic
    // ---------------------------------------------------------------------------------------------

    private List<Channel.ChannelPt> getPtsCloseTo(Channel.ChannelPt pt) {
        return quadTree.getPointsInCircle(pt.toArray(), Math.sqrt(channels.get(pt.channelId).width));
    }

    private void manageCutoffs(Channel ch) {
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
                if (cpt.index <= id + 1 || cpt.channelId != ch.channelId) continue;
                cutRiverSection(id, cpt.index, ch);
            }
        }
        newPathIndexes.add(ch.numPts() - 1);
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

    // ---------------------------------------------------------------------------------------------
    // split / merge — public single-step graph primitives
    // ---------------------------------------------------------------------------------------------

    /**
     * Splits channel {@code id} at point index {@code pos}, inserting a junction there. Returns the
     * new (downstream) channel's id, or -1 if nothing was done.
     *
     * <p>{@code redirect == false}: one shared junction joins the two halves (a normal mid-channel
     * junction). {@code redirect == true}: two disconnected junctions are created at the same
     * coordinate — one ending the upstream half, one starting the downstream half — so the two
     * halves are no longer connected (used for the captured channel in a collision).
     *
     * <p>Uses only keepOnly / sub-point-lists + createCatmullRom (point positions preserved), never
     * reSample, so point indices stay stable.
     */
    public int split(int id, int pos, boolean redirect) {
        Channel channel = channels.get(id);
        if (channel == null) return -1;
        final int lastIndex = channel.numPts() - 1;
        if (pos <= 0 || pos >= lastIndex) return -1;

        final int downstreamNodeId = channel.endNodeId;
        final double[] splitCoord = channel.spline.points().get(pos).clone();

        // downstream half [pos..lastIndex]
        ArrayList<double[]> downstreamPoints = new ArrayList<>();
        for (int i = pos; i <= lastIndex; i++)
            downstreamPoints.add(channel.spline.points().get(i));
        final int downstreamChannelId = nextChannelId++;
        Channel downstreamChannel = new Channel(channel.width, downstreamPoints, downstreamChannelId);
        downstreamChannel.endNodeId = downstreamNodeId;
        channels.put(downstreamChannelId, downstreamChannel);

        // truncate the original channel to the upstream half [0..pos]
        ArrayList<Integer> upstreamIndexes = new ArrayList<>();
        for (int i = 0; i <= pos; i++) upstreamIndexes.add(i);
        channel.keepOnly(upstreamIndexes);

        // downstream node's incoming: original channel -> downstream channel
        Node downstreamNode = nodes.get(downstreamNodeId);
        downstreamNode.incoming.remove(id);
        downstreamNode.incoming.add(downstreamChannelId);

        if (!redirect) {
            Node junction = newNode(splitCoord);
            channel.endNodeId = junction.id;
            downstreamChannel.startNodeId = junction.id;
            junction.incoming.add(id);
            junction.outgoing = downstreamChannelId;
        } else {
            Node upstreamJunction = newNode(splitCoord);
            Node downstreamJunction = newNode(splitCoord.clone());
            channel.endNodeId = upstreamJunction.id;
            upstreamJunction.incoming.add(id);
            downstreamChannel.startNodeId = downstreamJunction.id;
            downstreamJunction.outgoing = downstreamChannelId;
        }
        return downstreamChannelId;
    }

    /**
     * Merges channel {@code id} with its single downstream channel iff the junction between them is
     * a pass-through (degree 2: exactly one inflow = this channel, one outflow). Returns true if a
     * merge happened.
     */
    public boolean merge(int id) {
        Channel channel = channels.get(id);
        if (channel == null) return false;
        Node junction = nodes.get(channel.endNodeId);
        if (junction == null || junction.type != Node.NodeType.JUNCTION) return false;
        if (junction.outgoing == -1) return false;
        if (!(junction.incoming.size() == 1 && junction.incoming.contains(id))) return false;

        final int downstreamChannelId = junction.outgoing;
        Channel downstreamChannel = channels.get(downstreamChannelId);
        final int upstreamNodeId = channel.startNodeId;
        final int downstreamNodeId = downstreamChannel.endNodeId;

        ArrayList<double[]> mergedPoints = new ArrayList<>(channel.spline.points());
        List<double[]> downstreamPoints = downstreamChannel.spline.points();
        for (int i = 1; i < downstreamPoints.size(); i++)
            mergedPoints.add(downstreamPoints.get(i)); // drop duplicate junction pt

        Channel mergedChannel = new Channel(Math.max(channel.width, downstreamChannel.width), mergedPoints, id);
        mergedChannel.startNodeId = upstreamNodeId;
        mergedChannel.endNodeId = downstreamNodeId;
        channels.put(id, mergedChannel); // replaces the original channel (same id)
        channels.remove(downstreamChannelId);

        Node downstreamNode = nodes.get(downstreamNodeId);
        downstreamNode.incoming.remove(downstreamChannelId);
        downstreamNode.incoming.add(id);
        // upstream node's outgoing already == id (unchanged)

        nodes.remove(junction.id);
        return true;
    }

    private Node newNode(double[] coord) {
        final int nodeId = nextNodeId++;
        Node node = new Node(nodeId, Node.NodeType.JUNCTION, coord);
        nodes.put(nodeId, node);
        return node;
    }

    // ---------------------------------------------------------------------------------------------
    // Collisions (stream capture)
    // ---------------------------------------------------------------------------------------------

    private record Crossing(int channelIdA, int posA, int channelIdB, int posB, double widthA, double widthB) {}

    void manageCollisions() {
        List<Crossing> crossings = detectCrossings();
        //        if (crossings.isEmpty()) return;
        segmentAndResolve(crossings);
        dumpNetwork("02_captured");
        pruneDanglingJunctionLeaves();
        deleteOrphanDrains();
        dumpNetwork("03_pruned");
        mergePassFromSources();
        dumpNetwork("04_merged");
    }

    /**
     * Drains are special: a capture beheads the loser's downstream remnant, which the prune step
     * removes, orphaning that remnant's drain. Such isolated drains are deleted. Drains are never
     * created (sources/drains are otherwise fixed for the instance's lifetime).
     */
    private void deleteOrphanDrains() {
        List<Integer> orphanDrainIds = new ArrayList<>();
        for (Node node : nodes.values())
            if (node.type == Node.NodeType.DRAIN && node.degree() == 0) orphanDrainIds.add(node.id);
        for (int drainId : orphanDrainIds) nodes.remove(drainId);
    }

    /** Single quadtree pass: detect every channel-vs-channel crossing. */
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
            // partner channelId -> contacts, each [posA, posB, distance]
            Map<Integer, List<double[]>> contactsByPartner = new HashMap<>();
            for (int posA = 1; posA < pointCountA - 1; posA++) { // skip endpoints (they are nodes)
                final double[] pointA = channelA.spline.points().get(posA);
                List<Channel.ChannelPt> nearbyPoints = quadTree.getPointsInCircle(pointA, queryRadius);
                nearbyPoints.sort(null);

                // determinism, not Set/tree iteration order
                for (Channel.ChannelPt nearbyPoint : nearbyPoints) {
                    final int channelBId = nearbyPoint.channelId;
                    if (channelBId <= channelAId) continue; // emit each crossing once (a < b)
                    Channel channelB = channels.get(channelBId);
                    final int posB = nearbyPoint.index;
                    if (posB <= 0 || posB >= channelB.numPts() - 1) continue; // skip b endpoints

                    // Only skip proximity that is explained by a junction the two channels share —
                    // i.e. contacts NEAR that shared node. A crossing far from any shared node counts.
                    if (nearSharedNode(channelA, channelB, pointA)) continue;
                    final double distance = VectorOps.distance(pointA, nearbyPoint.toArray());
                    if (distance > 0.5 * (channelA.width + channelB.width)) continue; // not actually touching
                    contactsByPartner
                            .computeIfAbsent(channelBId, partner -> new ArrayList<>())
                            .add(new double[] {posA, posB, distance});
                }
            }
            //          LOG.info("contactsByPartner: {}", contactsByPartner);
            for (Map.Entry<Integer, List<double[]>> entry : contactsByPartner.entrySet()) {
                Channel channelB = channels.get(entry.getKey());
                List<double[]> contactList = entry.getValue();
                contactList.sort(Comparator.comparingDouble(contact -> contact[0])); // by posA
                // A and B may cross several times; keep only the FIRST crossing (smallest posA).
                // contactList is sorted ascending, so the first contiguous cluster is that crossing;
                // its min-distance contact is the split point.
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

    /**
     * True when the contact point sits near a node that A and B share — their proximity here is just
     * that common junction/endpoint, not a real crossing. Channels that share a node but cross far
     * from it (beyond {@code widthA + widthB}) are NOT skipped.
     */
    private boolean nearSharedNode(Channel channelA, Channel channelB, double[] contactPoint) {
        final double radius = channelA.width + channelB.width;
        final double radiusSq = radius * radius;
        for (int nodeA : new int[] {channelA.startNodeId, channelA.endNodeId})
            for (int nodeB : new int[] {channelB.startNodeId, channelB.endNodeId})
                if (nodeA != -1 && nodeA == nodeB) {
                    Node shared = nodes.get(nodeA);
                    if (shared != null && VectorOps.distanceSquared(contactPoint, shared.coord) <= radiusSq)
                        return true;
                }
        return false;
    }

    /**
     * Winner of a crossing: 0 if A wins, 1 if B wins. If one channel is downstream of the other on
     * the same flow path (an ancestor crosses its own descendant), the DOWNSTREAM channel wins —
     * which keeps the network acyclic (the capture becomes a meander cutoff). Otherwise the wider
     * channel wins; ties break to the smaller channelId.
     */
    private int decideWinner(Crossing crossing) {
        if (reachesDownstream(crossing.channelIdA(), crossing.channelIdB())) return 1; // B downstream of A
        if (reachesDownstream(crossing.channelIdB(), crossing.channelIdA())) return 0; // A downstream of B
        if (crossing.widthA() > crossing.widthB()) return 0;
        if (crossing.widthB() > crossing.widthA()) return 1;
        return crossing.channelIdA() < crossing.channelIdB() ? 0 : 1;
    }

    /** True if {@code descendantId} is reachable by following outgoing edges downstream from {@code ancestorId}. */
    private boolean reachesDownstream(int ancestorId, int descendantId) {
        Channel ancestor = channels.get(ancestorId);
        if (ancestor == null) return false;
        int nodeId = ancestor.endNodeId;
        for (int guard = 0; guard <= channels.size() && nodeId != -1; guard++) {
            Node node = nodes.get(nodeId);
            if (node == null || node.outgoing == -1) return false;
            if (node.outgoing == descendantId) return true;
            Channel next = channels.get(node.outgoing);
            if (next == null) return false;
            nodeId = next.endNodeId;
        }
        return false;
    }

    /**
     * Phase A: bulk-segment every channel at all its crossing positions at once (no reSample, no
     * per-split renumbering), wiring junction nodes by each channel's win/lose role. Phase B:
     * reconnect each loser's upstream segment into the winner's junction.
     */
    private void segmentAndResolve(List<Crossing> crossings) {
        final int crossingCount = crossings.size();
        final int[] winnerSide = new int[crossingCount];
        for (int crossingIdx = 0; crossingIdx < crossingCount; crossingIdx++)
            winnerSide[crossingIdx] = decideWinner(crossings.get(crossingIdx));

        // channelId -> (splitPosition -> crossingIndex), positions ascending
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
            Channel originalChannel = channels.get(channelId); // read geometry from the full channel
            final List<Integer> splitPositions =
                    new ArrayList<>(entry.getValue().keySet());
            final List<Integer> crossingAtPosition =
                    new ArrayList<>(entry.getValue().values());
            final int splitCount = splitPositions.size();
            final int upstreamNodeId = originalChannel.startNodeId;
            final int downstreamNodeId = originalChannel.endNodeId;
            final int lastIndex = originalChannel.numPts() - 1;

            // segment ranges [start..end] inclusive: [0..p1], [p1..p2], ..., [pk..lastIndex]
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

            // outer endpoints
            channels.get(segmentIds[0]).startNodeId = upstreamNodeId; // upstream.outgoing already == segmentIds[0]
            channels.get(segmentIds[splitCount]).endNodeId = downstreamNodeId;
            Node downstreamNode = nodes.get(downstreamNodeId);
            downstreamNode.incoming.remove(channelId);
            downstreamNode.incoming.add(segmentIds[splitCount]);

            // boundary junctions, by win/lose role at each crossing
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
                    Node winnerJunction = newNode(junctionCoord);
                    channels.get(upstreamSegmentId).endNodeId = winnerJunction.id;
                    channels.get(downstreamSegmentId).startNodeId = winnerJunction.id;
                    winnerJunction.incoming.add(upstreamSegmentId);
                    winnerJunction.outgoing = downstreamSegmentId;
                    winnerJunctionId[crossingIdx] = winnerJunction.id;
                } else {
                    Node loserUpstreamJunction = newNode(junctionCoord);
                    Node loserDownstreamJunction = newNode(junctionCoord.clone());
                    channels.get(upstreamSegmentId).endNodeId = loserUpstreamJunction.id;
                    loserUpstreamJunction.incoming.add(upstreamSegmentId);
                    channels.get(downstreamSegmentId).startNodeId = loserDownstreamJunction.id;
                    loserDownstreamJunction.outgoing = downstreamSegmentId;
                    loserUpstreamJunctionId[crossingIdx] = loserUpstreamJunction.id;
                    loserUpstreamSegmentId[crossingIdx] = upstreamSegmentId;
                }
            }
        }

        // Phase B: capture reconnection
        for (int crossingIdx = 0; crossingIdx < crossingCount; crossingIdx++) {
            final int winnerJunction = winnerJunctionId[crossingIdx];
            final int loserJunction = loserUpstreamJunctionId[crossingIdx];
            final int loserSegment = loserUpstreamSegmentId[crossingIdx];
            if (winnerJunction == -1 || loserJunction == -1 || loserSegment == -1) continue;
            Channel tributary = channels.get(loserSegment);
            tributary.endNodeId = winnerJunction;
            nodes.get(winnerJunction).incoming.add(loserSegment);
            // The winner junction sits on the winner's crossing point; the tributary's mouth was the
            // loser's (nearby but distinct) crossing point. Snap the mouth onto the confluence node so
            // the channel's last point lines up exactly with its end node.
            ArrayList<double[]> tributaryPoints = tributary.spline.points();
            tributaryPoints.set(
                    tributaryPoints.size() - 1, nodes.get(winnerJunction).coord.clone());
            tributary.spline = QuinticHermiteSpline.createCatmullRom(tributaryPoints);
            nodes.remove(loserJunction); // the loser's now-orphan upstream junction
        }
    }

    /** Iteratively remove channels dangling off leaf JUNCTION nodes until all leaves are sources/drains. */
    private void pruneDanglingJunctionLeaves() {
        ArrayDeque<Integer> leafQueue = new ArrayDeque<>();
        for (Node node : nodes.values())
            if (node.type == Node.NodeType.JUNCTION && node.degree() == 1) leafQueue.add(node.id);
        while (!leafQueue.isEmpty()) {
            final int nodeId = leafQueue.poll();
            Node node = nodes.get(nodeId);
            if (node == null || node.type != Node.NodeType.JUNCTION || node.degree() != 1) continue;

            final boolean incidentIsOutgoing = node.outgoing != -1;
            final int channelId = incidentIsOutgoing
                    ? node.outgoing
                    : node.incoming.iterator().next();
            Channel channel = channels.get(channelId);
            final int otherNodeId = incidentIsOutgoing ? channel.endNodeId : channel.startNodeId;
            Node otherNode = nodes.get(otherNodeId);
            if (otherNode != null) {
                if (otherNode.outgoing == channelId) otherNode.outgoing = -1;
                otherNode.incoming.remove(channelId);
                if (otherNode.type == Node.NodeType.JUNCTION && otherNode.degree() == 1) leafQueue.add(otherNode.id);
            }
            channels.remove(channelId);
            nodes.remove(nodeId);
        }
    }

    /** Walk downstream from every source, collapsing pass-through junctions, to a fixpoint. */
    private void mergePassFromSources() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Node source : new ArrayList<>(nodes.values())) {
                if (source.type != Node.NodeType.SOURCE) continue;
                int channelId = source.outgoing;
                while (channelId != -1) {
                    Channel channel = channels.get(channelId);
                    if (channel == null) break;
                    if (merge(channelId)) {
                        changed = true;
                        continue; // the merged channel keeps id and now reaches further downstream
                    }
                    Node downstreamNode = nodes.get(channel.endNodeId);
                    if (downstreamNode == null) break;
                    channelId = downstreamNode.outgoing;
                }
            }
        }
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

    public Collection<Node> getNodes() {
        return nodes.values();
    }

    public Node getNode(int id) {
        return nodes.get(id);
    }
}
