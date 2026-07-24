package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.GRID;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.PAD;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.PADDED;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.sampleBilinear;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.hydrology.meanders.Endpoint;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileCarver;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingSpatialIndex;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import me.batata_1.fractal_terrain.relief.DecoderChannels;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owner of the whole per-tile river pipeline. From the decoded terrain ({@link DecoderChannels}) and the
 * global river network ({@link GlobalRiverProvider}) it computes, per 512×512 relief tile, two
 * artifacts produced by a single build:
 *
 * <ul>
 *   <li>a serializable {@link NonIntersectingSpatialIndex} of per-tile {@link ImmutableRTree}s of
 *       {@link HydrologicalUnit} influence circles — the queryable river network: global rivers traced
 *       + relaxed by a Meanders sim, with the detailed local network traced from drainage attached onto
 *       the SAME graph as first-class members — answered by point-stabbing queries; and</li>
 *   <li>a {@link NonIntersectingInfiniteTensor} of {@code [1,512,512]} carved+filled elevation tiles,
 *       imported by {@code ReliefProvider} as its elevation channel.</li>
 * </ul>
 *
 * <p>The two stores are populated together: whichever is requested first runs {@link #buildTile} and
 * cross-fills the other via the storage claim API (single-flight guarantees one build per tile).
 *
 * <p><b>Responsibility (thin orchestrator):</b> owns the dual-store cache/single-flight plumbing and the
 * per-tile pipeline sequencing; the actual per-stage math is delegated to
 * {@link GlobalNetworkBuilder} (global-river trace + relax), {@link LocalDrainageTracer} (drainage-derived
 * local network, attached in place onto the global graph) and {@link ChannelElevationAssigner}
 * (bed-elevation propagation). {@link HydrologyTileGeometry} is the shared tile-frame geometry all three
 * depend on.
 *
 * <p>{@code assign} and the global shell carve each run <b>twice</b> per tile: once on the global-only
 * graph, to give the drainage trace a carved valley to route through, and again on the unified graph
 * after the local network is attached. See {@link #buildTile} for the exact ordering.
 *
 * <p><b>Invariants:</b> {@link #units}/{@link #carved} (a {@link NonIntersectingSpatialIndex} /
 * {@link NonIntersectingInfiniteTensor} pair) are reused per-tile and single-threaded per key — the
 * {@code pending} single-flight map and the storage claim API are what let one {@link #buildTile} call
 * fill both stores; do not bypass them by computing a tile's units/carved elevation independently.
 */
public class LocalRiverProvider {

    private static final Logger LOG = LoggerFactory.getLogger(LocalRiverProvider.class);
    private final NonIntersectingSpatialIndex<ImmutableRTree<HydrologicalUnit>> units;
    private final NonIntersectingInfiniteTensor carved;
    private final ConcurrentHashMap<TileKey, TileResult> pending = new ConcurrentHashMap<>();

    /** Test-only override for the global river source; {@code null} → use the singleton. */
    @TestOnly
    private @Nullable GlobalRiverProvider globalRiverOverride;

    /** Both per-tile artifacts produced by one {@link #buildTile}. */
    private record TileResult(ImmutableRTree<HydrologicalUnit> units, FloatTensor carved) {}

    public LocalRiverProvider(String path) {
        // The one-unit prototype index keeps Storage's serializability probe exercising unit
        // serialization, so the store stays disk-backed (mirrors the old seeded prototype tree).
        units = new NonIntersectingSpatialIndex<>(
                path,
                "local_river_units",
                new int[] {GRID, GRID},
                new ImmutableRTree<>(List.of(), HydrologicalUnit.PROTOTYPE),
                key -> key != null ? buildUnitsTile(key) : null);
        carved = new NonIntersectingInfiniteTensor(
                path, "local_carved_elev_v2", new int[] {1, GRID, GRID}, this::buildCarvedTile);
    }

    @TestOnly
    public void setGlobalRiverProvider(GlobalRiverProvider provider) {
        this.globalRiverOverride = provider;
    }

    /** Inner (unpadded) tile side; a headless golden test sizes its synthetic fixtures as
     *  {@code gridSizeForTest()²}. */
    @TestOnly
    public static int gridSizeForTest() {
        return GRID;
    }

    /**
     * Headless seam for {@code LocalRiverGoldenTest}: run the local-network trace — the deterministic core
     * unique to this provider (flow accumulation → reach test → segment walk → attach) — over a supplied
     * {@code GRID*GRID} drainage/elevation pair and a caller-built {@code network} (a synthetic global
     * network in the golden test; the real per-tile graph in production), with no pipeline dependency. In
     * production the drainage and elevation originate from the ONNX-decoded terrain (via {@link
     * me.batata_1.fractal_terrain.relief.DecoderChannels#decode}); a golden test instead feeds a synthetic
     * seeded elevation field and its {@code PipelinePreprocessing}-computed drainage, plus a synthetic
     * central trunk channel for {@code network}. Delegates to the exact production {@link
     * LocalDrainageTracer#traceLocalNetwork} path, which mutates {@code network} in place and returns
     * nothing — the caller inspects {@code network} afterwards.
     */
    @TestOnly
    public void traceLocalNetworkForTest(int[] drainage, float[] elev, RiverNetwork network) {
        LocalDrainageTracer.traceLocalNetwork(drainage, elev, network, null);
    }

    private GlobalRiverProvider globalRiverProvider() {
        return (globalRiverOverride != null) ? globalRiverOverride : FractalTerrainInstance.getGlobalRiverProvider();
    }

    /** Clears both per-tile caches (the unit tree and the carved-elevation tensor). */
    public void clearCaches() {
        units.clear();
        carved.clear();
        pending.clear();
    }

    /** The carved+filled elevation tile {@code [1,512,512]} for {@code (tileX, tileZ)} (imported by ReliefProvider). */
    public FloatTensor getCarvedElev(int tileX, int tileZ) {
        return carved.getEntry(new int[] {0, tileX, tileZ});
    }

    // -------------------------------------------------------------------------
    // Dual-store build (single compute, both stores filled)
    // -------------------------------------------------------------------------

    private TileResult buildOnce(int tileX, int tileZ) {
        return pending.computeIfAbsent(new TileKey(new int[] {tileX, tileZ}), k -> buildTile(tileX, tileZ, null));
    }

    private ImmutableRTree<HydrologicalUnit> buildUnitsTile(TileKey key) {
        final int tileX = key.get(0);
        final int tileZ = key.get(1);
        final TileResult result = buildOnce(tileX, tileZ);
        final int[] carvedKey = {0, tileX, tileZ};
        final CompletableFuture<FloatTensor> claim = carved.claimForCompute(carvedKey);
        if (claim != null) carved.fulfillClaim(carvedKey, claim, result.carved());
        pending.remove(new TileKey(new int[] {tileX, tileZ}));
        return result.units();
    }

    private FloatTensor buildCarvedTile(TileKey key) {
        final int tileX = key.get(1);
        final int tileZ = key.get(2);
        final TileResult result = buildOnce(tileX, tileZ);
        final int[] unitsKey = {tileX, tileZ};
        final CompletableFuture<ImmutableRTree<HydrologicalUnit>> claim = units.claimForCompute(unitsKey);
        if (claim != null) units.fulfillClaim(unitsKey, claim, result.units());
        pending.remove(new TileKey(new int[] {tileX, tileZ}));
        return result.carved();
    }

    // -------------------------------------------------------------------------
    // Core pipeline
    // -------------------------------------------------------------------------

    /**
     * The whole per-tile pipeline, producing both cache artifacts from one pass over a padded
     * {@code PADDED × PADDED} buffer of decoded terrain. Ordering, which is load-bearing:
     *
     * <ol>
     *   <li>Trace + relax the global network ({@link GlobalNetworkBuilder}), which also returns the
     *       boundary elevations it accumulated for its source/drain nodes.</li>
     *   <li>{@link ChannelElevationAssigner#assign} over the global-only graph, then carve the global
     *       shell into the decoded elevation — so the drainage field in step 3 sees valleys.</li>
     *   <li>Sink-fill + drainage direction over that carved elevation.</li>
     *   <li>Trace the local network and attach it in place onto the SAME graph
     *       ({@link LocalDrainageTracer#traceLocalNetwork}, void). A before/after channel-id snapshot
     *       identifies the freshly-minted local channels: a minted channel is local iff its start node is
     *       a new {@code SOURCE} (a {@code split()}-grown global downstream half starts at a
     *       {@code JUNCTION} instead).</li>
     *   <li>Seed the boundary map with those local {@code SOURCE}/{@code DRAIN} nodes, then run
     *       {@code assign} a second time over the now-unified graph.</li>
     *   <li>Collect every unit (global + local, one shared feature-id counter) into the R-tree, run the
     *       shell carve a second time, and crop the padded buffer to {@code GRID × GRID}.</li>
     * </ol>
     *
     * <p>Both carve passes collect units <em>unfiltered</em>. That makes the first pass global-only by
     * timing alone — the local network does not exist yet — while the second pass, running after the
     * local trace, carves local shells too. Local shells are traced with no coarse halo, so one can be
     * truncated at this tile's {@code PAD} border and seam against its neighbour; global floodplains use
     * a 2×2-cell halo and are unaffected. {@code RiverNetwork.collectUnits} has a channel-id-filtering
     * overload that would restrict the carve to global channels, but nothing calls it.
     *
     * <p>{@code stages} is a test/debug sink; production calls pass {@code null}.
     */
    private TileResult buildTile(int tileX, int tileZ, @Nullable Stages stages) {
        final GlobalRiverProvider grp = globalRiverProvider();
        final float[][] base = DecoderChannels.decode(tileX, tileZ, PAD); // padded 514, channels 0..6

        // 1. global rivers: trace + relax, returning the network plus the boundary-elevation map
        //    GlobalNetworkBuilder accumulated for it (source/drain node datum).
        final GlobalNetworkBuilder.Result globalResult = GlobalNetworkBuilder.build(tileX, tileZ, base, grp);
        final RiverNetwork network = globalResult.network().getNetwork();
        final Map<Integer, Double> boundaryElev = new HashMap<>(globalResult.boundaryElevByNodeIdx());

        final float[] carvedElevation = base[0];
        ChannelElevationAssigner.assign(network, boundaryElev, carvedElevation);
        LOG.info("passed first assignemnt");

        final List<HydrologicalUnit> globalUnitsFirstCarvePass = network.collectUnits(0, 0, 0, new int[] {0});
        HydrologyProfileCarver.carveRiverShells(
                carvedElevation, globalUnitsFirstCarvePass.toArray(new HydrologicalUnit[0]), PADDED);

        // 2. sink-fill + drainage on the RAW decoded elevation (not yet carved): the local trace no
        //    longer needs a pre-carved valley to route toward the global network -- LOCAL_ATTACH_RADIUS
        //    proximity (not terrain shape) is what joins locals to the graph -- so drainage can be
        //    computed once, up front, and fed straight into the trace.
        final float[] filled = PipelinePreprocessing.fillSinks(carvedElevation, PADDED, HydrologyTuning.FILL_PADDING);
        final float[] uniformWeight = new float[PADDED * PADDED];
        Arrays.fill(uniformWeight, 1f);
        final int[] drainagePadded = PipelinePreprocessing.computeDrainageDirection(filled, uniformWeight, PADDED);

        // 3. local rivers: trace + attach into the SAME graph as standalone SOURCE-rooted edges (dangling
        //    JUNCTION end, or coast DRAIN), then run the atomic collision pass which reorients + attaches
        //    each dangling local edge to a nearby global channel via a bed-overlap crossing (or demotes it
        //    to an ABANDONED_RIVER when its DFS branch reaches no drain). update() re-assigns every channel
        //    id but preserves SOURCE/DRAIN node ids, so the boundary map below keys on node type, not the
        //    old (now churn-broken) before/after channel-id snapshot.
        LocalDrainageTracer.traceLocalNetwork(drainagePadded, carvedElevation, network, stages);
        network.manageCollisions(0);

        // 4. augment the boundary map with every SOURCE/DRAIN node not already seeded — the local ridge
        //    SOURCEs and coast DRAINs the trace minted (decoded terrain at the node, floored at 0), so the
        //    single assign below has a seed for every path too (H4 -- otherwise these nodes float at 0.0).
        for (Endpoint node : network.getNodes()) {
            if (node.type != Endpoint.Type.SOURCE && node.type != Endpoint.Type.DRAIN) continue;
            boundaryElev.putIfAbsent(
                    node.id, Math.max(0, sampleBilinear(carvedElevation, node.coord[0], node.coord[1])));
        }

        // 5. ONE bed-elevation assignment over the whole unified graph.
        ChannelElevationAssigner.assign(network, boundaryElev, carvedElevation);
        LOG.info("passed second assignemnt");

        final int[] nextFeatureId = {0};
        final List<HydrologicalUnit> unitPoints = network.collectUnits(0, PAD, PAD, nextFeatureId);
        final ImmutableRTree<HydrologicalUnit> unitIndex = new ImmutableRTree<>(unitPoints, HydrologicalUnit.PROTOTYPE);

        final List<HydrologicalUnit> globalUnitsForCarve = network.collectUnits(0, 0, 0, new int[] {0});

        HydrologyProfileCarver.carveRiverShells(
                carvedElevation, globalUnitsForCarve.toArray(new HydrologicalUnit[0]), PADDED);
        final FloatTensor carvedTile = cropToTile(carvedElevation);

        if (stages != null) {
            // The local/global channel-id split no longer survives the collision pass (update() re-assigns
            // every channel id — accepted debug-only regression). All channels are reported together;
            // the local-only PNG is intentionally empty.
            stages.channels = network.getChannels();
            stages.localChannels = new ArrayList<>();
            stages.carvedElevation = carvedTile.copyRange(0, carvedTile.getSize());
            stages.network = network;
            stages.unitTree = unitIndex;
        }
        return new TileResult(unitIndex, carvedTile);
    }

    private FloatTensor cropToTile(float[] padded) {
        final float[] data = new float[GRID * GRID];
        for (int ix = 0; ix < GRID; ix++) {
            for (int iz = 0; iz < GRID; iz++) {
                data[ix * GRID + iz] = padded[(PAD + ix) * PADDED + (PAD + iz)];
            }
        }
        return new FloatTensor(new int[] {1, GRID, GRID}, data);
    }

    // -------------------------------------------------------------------------
    // Query API
    // -------------------------------------------------------------------------

    /** Per-unit acceptance test; receives the squared distance to the query point (frame-invariant). */
    @FunctionalInterface
    public interface InfluencingUnitTest {
        boolean test(HydrologicalUnit unit, double distSqToQueryPoint);
    }

    /**
     * Early-exit existence test over the unit index: true iff some unit whose influence circle contains
     * {@code pt} (relief-pixel frame) passes {@code test}. Candidates come from the per-tile R-tree
     * stabbing query; {@code tileVisitRadius} only sizes the cross-tile window and must upper-bound the
     * distance of any unit the test can accept. Spans tile borders like {@link #queryInfluence} but
     * allocates no result list and stops at the first accepted unit. The test runs in the tile-local
     * frame; distances are translation-invariant, so the {@code distSqToQueryPoint} it receives equals
     * the world-frame squared distance.
     */
    public boolean anyInfluencingUnit(double[] pt, double tileVisitRadius, InfluencingUnitTest test) {
        final double[] tileLocalPoint = new double[2];
        final Predicate<HydrologicalUnit> tileLocalAcceptanceTest = unit -> {
            final double deltaX = unit.coord()[0] - tileLocalPoint[0];
            final double deltaZ = unit.coord()[1] - tileLocalPoint[1];
            return test.test(unit, deltaX * deltaX + deltaZ * deltaZ);
        };
        return units.forEachTileWithin(pt, tileVisitRadius, (tileOriginX, tileOriginZ, tileIndex) -> {
            tileLocalPoint[0] = pt[0] - tileOriginX;
            tileLocalPoint[1] = pt[1] - tileOriginZ;
            return tileIndex.anyContaining(tileLocalPoint, tileLocalAcceptanceTest);
        });
    }

    /**
     * Gather every hydrological unit whose feature influences {@code pt} (a point in the relief-pixel
     * frame), re-stamped into world coords and returned as a flat array in <b>unspecified order</b> —
     * consumed by {@link HydrologyProfileCarver}'s flat
     * distance-weighted merge (every unit contributes; no per-feature grouping).
     *
     * <p>A unit is kept when {@code pt} lies within that unit's own influence circle
     * ({@link HydrologicalUnit#getRadius()} = {@link FractalTerrainConfig#riverInfluence
     * riverInfluence(width)}), optionally inflated by {@code extraRadius} (used by the per-chunk
     * prefetch so one query serves every block of a chunk) — exactly what the per-tile R-tree stabbing
     * query returns, so no per-unit reach re-test is needed. The query spans tile borders (a river
     * within influence may live in a neighbouring tile); stored coords are tile-local, so each hit is
     * re-stamped into the common world frame by adding its owning tile's origin.
     */
    public HydrologicalUnit[] queryInfluence(double[] pt, double extraRadius) {
        final List<HydrologicalUnit> influencingUnits = new ArrayList<>(64);
        final List<HydrologicalUnit> tileLocalHits = new ArrayList<>(64); // reused across tiles; cleared per tile
        final double[] tileLocalPoint = new double[2];
        units.forEachTileWithin(
                pt, FractalTerrainConfig.MAX_INFLUENCE_RADIUS + extraRadius, (tileOriginX, tileOriginZ, tileIndex) -> {
                    tileLocalPoint[0] = pt[0] - tileOriginX;
                    tileLocalPoint[1] = pt[1] - tileOriginZ;
                    tileLocalHits.clear();
                    tileIndex.queryContaining(tileLocalPoint, extraRadius, tileLocalHits);
                    for (final HydrologicalUnit unit : tileLocalHits) {
                        influencingUnits.add(new HydrologicalUnit(
                                unit.type(),
                                unit.rosgenType(),
                                new double[] {unit.coord()[0] + tileOriginX, unit.coord()[1] + tileOriginZ},
                                unit.normal(),
                                unit.width(),
                                unit.elevation(),
                                unit.time(),
                                unit.id()));
                    }
                    return false;
                });
        return influencingUnits.toArray(new HydrologicalUnit[0]);
    }

    /** {@link #queryInfluence(double[], double)} with no extra radius (single-point queries). */
    public HydrologicalUnit[] queryInfluence(double[] pt) {
        return queryInfluence(pt, 0.0);
    }

    // -------------------------------------------------------------------------
    // Debug access
    // -------------------------------------------------------------------------

    /**
     * The built {@link ImmutableRTree} of {@link HydrologicalUnit} influence circles for tile
     * {@code (tileX, tileZ)} (triggers {@link #buildTile} on a cache miss). For debug rendering
     * ({@code Debug.units.see}) and the spatial-index benchmark harness.
     */
    @TestOnly
    public ImmutableRTree<HydrologicalUnit> getUnitTree(int tileX, int tileZ) {
        return units.getEntry(new int[] {tileX, tileZ});
    }

    @TestOnly
    public Stages debugStages(int tileX, int tileZ) {
        final Stages stages = new Stages();
        buildTile(tileX, tileZ, stages);
        return stages;
    }

    /**
     * Debug snapshot of one {@link #buildTile} run, captured for {@code LocalRiverTest}. {@link #network}
     * is the single unified {@link RiverNetwork} graph (global and local channels as one graph, per
     * DL-010); {@link #channels} and {@link #localChannels} are that SAME graph's channels split by the
     * local-vs-global distinction {@link #buildTile} already derives via its before/after channel-id
     * snapshot (a minted channel is "local" iff its start node is a freshly-minted SOURCE), so the harness
     * can render the two colorings from one graph without recomputing that distinction or walking any
     * separate parallel structure.
     */
    @TestOnly
    public static final class Stages {
        public float[] flow;
        public boolean[] riverMask;
        public float[] carvedElevation;
        /** Global-only channels of {@link #network} (see class javadoc for the local/global split). */
        public List<Channel> channels;
        /** Local-only channels of {@link #network} (see class javadoc for the local/global split). */
        public List<Channel> localChannels;
        /** The single unified per-tile graph (global and local channels together). */
        public RiverNetwork network;

        public ImmutableRTree<HydrologicalUnit> unitTree;
    }
}
