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
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
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
 * Owner of the per-tile river pipeline, between {@link GlobalRiverProvider} upstream and
 * {@code ReliefProvider} downstream; stage math lives in {@link GlobalNetworkBuilder},
 * {@link LocalDrainageTracer} and {@link ChannelElevationAssigner}.
 *
 * <p>Publishes two stores from a single {@link #buildTile} pass so they can never disagree: an index of
 * {@link HydrologicalUnit} influence circles answering point-stabbing queries, and carved elevation tiles
 * imported as ReliefProvider's elevation channel. Unit coords are stamped in the world relief-pixel
 * frame so a hit from a neighbouring tile needs no translation; carved tiles stay tile-local.
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
        // _v2: unit coords are now persisted in the WORLD relief-pixel frame (see buildTile). A v1 tile
        // on disk holds tile-local coords and would be silently misread as world coords, so the store
        // name is bumped rather than the format version -- old tiles are simply never loaded again.
        units = new NonIntersectingSpatialIndex<>(
                path,
                "local_river_units_v2",
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
        return PADDED;
    }

    /** Headless seam for {@code LocalRiverGoldenTest}: runs the production trace path over
     *  caller-supplied fields, so the trace can be exercised without the ONNX pipeline.
     *  Mutates {@code network} in place; the caller inspects it afterwards. */
    @TestOnly
    public void traceLocalNetworkForTest(int[] drainage, float[] elev, RiverNetwork network) {
        LocalDrainageTracer.traceLocalNetwork(drainage, elev, new float[elev.length], network, null);
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

    /** The per-tile pipeline; both cache artifacts come from this one pass, which is what keeps the two
     *  stores from disagreeing. Step ordering is load-bearing — see the numbered comments in the body.
     *  {@code stages} is a test/debug sink; production calls pass {@code null}. */
    private TileResult buildTile(int tileX, int tileZ, @Nullable Stages stages) {
        final GlobalRiverProvider grp = globalRiverProvider();
        final float[][] base = DecoderChannels.decode(tileX, tileZ, PAD); // padded 514, channels 0..6

        // 1. global rivers: trace + relax, returning the network plus the boundary-elevation map
        //    GlobalNetworkBuilder accumulated for it (source/drain node datum).
        final GlobalNetworkBuilder.Result globalResult = GlobalNetworkBuilder.build(tileX, tileZ, base, grp);
        final Meanders sim = globalResult.network();
        final RiverNetwork network = sim.getNetwork();
        final Map<Integer, Double> boundaryElev = new HashMap<>(globalResult.boundaryElevByNodeIdx());

        final float[] carvedElevationGlobal = base[0].clone();
        ChannelElevationAssigner.assign(network, boundaryElev, carvedElevationGlobal);
        LOG.debug("finished first assign() pass");
        HydrologyProfileCarver.carveRiverShells(
                carvedElevationGlobal, sim.collectUnits(0, 0).toArray(new HydrologicalUnit[0]), PADDED);
        // Snapshot here, not at the end: this buffer is the drainage input for the local trace below, so
        // the harness needs it as it stood when step 2 read it.
        if (stages != null) stages.elevationFirstPass = cropToTile(carvedElevationGlobal).data;

        // 2. sink-fill + drainage on the RAW decoded elevation (not yet carved): the local trace no
        //    longer needs a pre-carved valley to route toward the global network -- LOCAL_ATTACH_RADIUS
        //    proximity (not terrain shape) is what joins locals to the graph -- so drainage can be
        //    computed once, up front, and fed straight into the trace.
        final float[] filled = Drainage.fillSinks(carvedElevationGlobal, PADDED, HydrologyTuning.FILL_PADDING);
        final float[] uniformWeight = new float[PADDED * PADDED];
        Arrays.fill(uniformWeight, 1f);
        final int[] drainagePadded = Drainage.computeDrainageDirection(filled, uniformWeight, PADDED);

        // 3. local rivers: trace + attach into the SAME graph as standalone SOURCE-rooted edges (dangling
        //    JUNCTION end, or coast DRAIN), then run the atomic collision pass which reorients + attaches
        //    each dangling local edge to a nearby global channel via a bed-overlap crossing (or demotes it
        //    to an ABANDONED_RIVER when its DFS branch reaches no drain). update() re-assigns every channel
        //    id but preserves SOURCE/DRAIN node ids, so the boundary map below keys on node type, not the
        //    old (now churn-broken) before/after channel-id snapshot.
        LocalDrainageTracer.traceLocalNetwork(drainagePadded, carvedElevationGlobal, base[4], network, stages);

        // 4. augment the boundary map with every SOURCE/DRAIN node not already seeded — the local ridge
        //    SOURCEs and coast DRAINs the trace minted (decoded terrain at the node, floored at 0), so the
        //    single assign below has a seed for every path too (H4 -- otherwise these nodes float at 0.0).
        for (Endpoint node : network.getNodes()) {
            if (node.type != Endpoint.Type.SOURCE && node.type != Endpoint.Type.DRAIN) continue;
            boundaryElev.putIfAbsent(
                    node.id, Math.max(0, sampleBilinear(carvedElevationGlobal, node.coord[0], node.coord[1])));
        }

        // 5. ONE bed-elevation assignment over the whole unified graph.
        ChannelElevationAssigner.assign(network, boundaryElev, carvedElevationGlobal);
        LOG.debug("finished second assign() pass");
        final float[] carvedElevation = base[0];
        HydrologyProfileCarver.carveRiverShells(
                carvedElevation, sim.collectUnits(0, 0).toArray(new HydrologicalUnit[0]), PADDED);

        // The indexed units are stamped in the WORLD relief-pixel frame. collectUnits subtracts the
        // offset it is given, so (PAD - tileOrigin) drops the halo pad and adds the tile's world origin
        // in one step. The origin matches what the store's TensorWindow hands forEachTileWithin
        // (stride = GRID, offset = 0), which is what makes the query path frame-free.
        //
        // Paid once per unit per tile build instead of once per hit per query, and it keeps the bed-noise
        // seed (River.carveFineGrained hashes the unit's own coords) absolute rather than repeating with
        // a GRID-px period. The two carveRiverShells collects (offset 0) deliberately stay in the padded
        // tile frame: they index against a PADDED x PADDED buffer addressed by pixel index.
        final int tileOriginX = tileX * GRID;
        final int tileOriginZ = tileZ * GRID;
        final List<HydrologicalUnit> unitPoints = sim.collectUnits(PAD - tileOriginX, PAD - tileOriginZ);
        final ImmutableRTree<HydrologicalUnit> unitIndex = new ImmutableRTree<>(unitPoints, HydrologicalUnit.PROTOTYPE);

        final FloatTensor carvedTile = cropToTile(carvedElevation);

        if (stages != null) {
            // The local/global channel-id split no longer survives the collision pass (update() re-assigns
            // every channel id — accepted debug-only regression). All channels are reported together;
            // the local-only PNG is intentionally empty.
            stages.channels = network.getChannels();
            stages.localChannels = new ArrayList<>();
            stages.rawElevation = cropToTile(base[0].clone()).data;
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

    /** Existence-only counterpart to {@link #queryInfluence}, for callers that just need a yes/no and
     *  should not pay for a result list. {@code tileVisitRadius} sizes the cross-tile window and must
     *  upper-bound the distance of any unit {@code test} can accept. */
    public boolean anyInfluencingUnit(double[] pt, double tileVisitRadius, InfluencingUnitTest test) {
        final Predicate<HydrologicalUnit> acceptanceTest = unit -> {
            final double deltaX = unit.coord()[0] - pt[0];
            final double deltaZ = unit.coord()[1] - pt[1];
            return test.test(unit, deltaX * deltaX + deltaZ * deltaZ);
        };
        return units.forEachTileWithin(
                pt,
                tileVisitRadius,
                (tileOriginX, tileOriginZ, tileIndex) -> tileIndex.anyContaining(pt, acceptanceTest));
    }

    /** Every unit influencing {@code pt}, unordered — feeds {@link HydrologyProfileCarver}'s flat
     *  distance-weighted merge. {@code extraRadius} inflates the circles so the per-chunk prefetch can
     *  serve a whole chunk from one query. Returns indexed instances; callers must not mutate them. */
    public HydrologicalUnit[] queryInfluence(double[] pt, double extraRadius) {
        final List<HydrologicalUnit> influencingUnits = new ArrayList<>(64);
        units.forEachTileWithin(
                pt, HydrologyTuning.MAX_INFLUENCE_RADIUS + extraRadius, (tileOriginX, tileOriginZ, tileIndex) -> {
                    tileIndex.queryContaining(pt, extraRadius, influencingUnits);
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

    /** Raw index access for debug rendering ({@code Debug.units.see}) and the spatial-index benchmark.
     *  Units carry world relief-pixel coords, so a tile-local renderer must subtract
     *  {@code (tileX·GRID, tileZ·GRID)}. */
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
     * Debug snapshot of one {@link #buildTile} run, captured for {@code LocalRiverTest} so the harness can
     * render intermediate stages without re-running the pipeline.
     *
     * <p>{@link #localChannels} is always empty: the collision pass re-assigns every channel id, so the
     * local-vs-global split cannot be recovered here and the local-only render is blank. Accepted
     * debug-only limitation.
     */
    @TestOnly
    public static final class Stages {
        public float[] flow;
        public float[] rawElevation;
        public boolean[] riverMask;
        /** Elevation after the global-only carve, before the local trace — the field drainage routes on. */
        public float[] elevationFirstPass;

        public float[] carvedElevation;
        /** Every channel of {@link #network}. */
        public List<Channel> channels;
        /** Always empty; kept so the harness's local-only render still compiles. */
        public List<Channel> localChannels;
        /** The single unified per-tile graph. */
        public RiverNetwork network;

        /** World-framed, unlike the tile-local rasters above; subtract the tile origin to render it. */
        public ImmutableRTree<HydrologicalUnit> unitTree;
    }
}
