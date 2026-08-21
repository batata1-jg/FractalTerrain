package me.batata_1.fractal_terrain.hydrology.providers;

import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.GRID;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.PAD;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.PADDED;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.sampleBilinear;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelElevationAssigner;
import me.batata_1.fractal_terrain.hydrology.GlobalNetworkBuilder;
import me.batata_1.fractal_terrain.hydrology.LocalDrainageTracer;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.network.ChannelTyper;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileInprinter;
import me.batata_1.fractal_terrain.hydrology.rosgen.ReachRosgenClassifier;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingSpatialIndex;
import me.batata_1.fractal_terrain.math.Interpolation;
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
 * <p>Publishes one artifact from {@link #buildTile}: an index of {@link HydrologicalPrimitive} influence
 * circles answering point-stabbing queries. Primitive coords are stamped in the world relief-pixel frame
 * so a hit from a neighbouring tile needs no translation.
 */
public class LocalRiverProvider {

    private static final Logger LOG = LoggerFactory.getLogger(LocalRiverProvider.class);

    /** Soft cap on the primitives store's cached bytes; mirrors {@code WorldPipeline.cacheLimitBytes}. */
    private static final long PRIMITIVE_CACHE_LIMIT_BYTES = 50L * 1024 * 1024;

    private final NonIntersectingSpatialIndex<ImmutableRTree<HydrologicalPrimitive>> primitives;

    /** Test-only override for the global river source; {@code null} → use the singleton. */
    @TestOnly
    private @Nullable GlobalRiverProvider globalRiverOverride;

    public LocalRiverProvider(String path) {
        // The one-primitive prototype index keeps Storage's serializability probe exercising primitive
        // serialization, so the store stays disk-backed (mirrors the old seeded prototype tree).
        // _v2: primitive coords are now persisted in the WORLD relief-pixel frame (see buildTile). A v1 tile
        // on disk holds tile-local coords and would be silently misread as world coords, so the store
        // name is bumped rather than the format version -- old tiles are simply never loaded again.
        primitives = new NonIntersectingSpatialIndex<>(
                path,
                "local_river_units",
                new int[] {GRID, GRID},
                new ImmutableRTree<>(List.of(), HydrologicalPrimitive.PROTOTYPE),
                key -> key != null ? buildPrimitivesTile(key) : null,
                PRIMITIVE_CACHE_LIMIT_BYTES);
    }

    public static void apply(GlobalNetworkBuilder.Result ctx, float[][] base, RiverProvider.Stages stages) {

        final var elev = base[0].clone();
        final var gradMag = base[4];

        LocalDrainageTracer.traceLocalNetwork(ctx.drainage(), elev,gradMag, ctx.network(),stages);

        for (Endpoint node : ctx.network().getNodes()) {
            if (node.type != Endpoint.Type.SOURCE && node.type != Endpoint.Type.DRAIN) continue;
            ctx.boundaryElevByNodeIdx().putIfAbsent(
                    node.id, Math.max(0, sampleBilinear(elev, node.coord[0], node.coord[1])));
        }


        ChannelElevationAssigner.assign(ctx.network(),ctx.boundaryElevByNodeIdx(),elev);

        HydrologyProfileInprinter.carveRiverInfluence(elev,collect(ctx.network(),ctx.typer()),PADDED);

        //correct the elevation of the sources
        for (Endpoint node : ctx.network().getNodes()) {
            if (node.type != Endpoint.Type.SOURCE && node.type != Endpoint.Type.DRAIN) continue;
            ctx.boundaryElevByNodeIdx().put(
                    node.id, Math.max(0, sampleBilinear(elev, node.coord[0], node.coord[1])));
        }
        ChannelElevationAssigner.assign(ctx.network(),ctx.boundaryElevByNodeIdx(),elev);

    }

    private static List<HydrologicalPrimitive> collect(RiverNetwork network, ChannelTyper typer) {
        var list = network.collectPrimitives(0,0,channelId -> true, typer,(x,z) -> Interpolation.sampleNearest(elev,x,z,PADDED));
        list.sort(HydrologicalPrimitive.comparator);
        return list;
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

    /** Clears the primitive-index cache. */
    public void clearCaches() {
        primitives.clear();
    }

    // -------------------------------------------------------------------------
    // Core pipeline
    // -------------------------------------------------------------------------

    private ImmutableRTree<HydrologicalPrimitive> buildPrimitivesTile(TileKey key) {
        return buildTile(key.get(0), key.get(1), null);
    }

    /** The per-tile pipeline. Step ordering is load-bearing — see the numbered comments in the body.
     *  {@code stages} is a test/debug sink; production calls pass {@code null}. */
    private ImmutableRTree<HydrologicalPrimitive> buildTile(int tileX, int tileZ, @Nullable RiverProvider.Stages stages) {
        final GlobalRiverProvider grp = globalRiverProvider();
        final float[][] base = DecoderChannels.decode(tileX, tileZ, PAD); // padded 514, channels 0..6
        // 3. local rivers: trace + attach into the SAME graph as standalone SOURCE-rooted edges (dangling
        //    JUNCTION end, or coast DRAIN), then run the atomic collision pass which reorients + attaches
        //    each dangling local edge to a nearby global channel via a bed-overlap crossing (or demotes it
        //    to an ABANDONED_RIVER when the reverse BFS from the drains never reaches it). update() re-assigns
        //    every channel
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

        // The indexed primitives are stamped in the WORLD relief-pixel frame. collectPrimitives subtracts the
        // offset it is given, so (PAD - tileOrigin) drops the halo pad and adds the tile's world origin
        // in one step. The origin matches what the store's TensorWindow hands forEachTileWithin
        // (stride = GRID, offset = 0), which is what makes the query path frame-free.
        //
        // Paid once per primitive per tile build instead of once per hit per query, and it keeps the bed-noise
        // seed (River.h hashes the primitive's own coords) absolute rather than repeating with
        // a GRID-px period. The two carveRiverShells collects (offset 0) deliberately stay in the padded
        // tile frame: they index against a PADDED x PADDED buffer addressed by pixel index.
        final int tileOriginX = tileX * GRID;
        final int tileOriginZ = tileZ * GRID;
        final List<HydrologicalPrimitive> primitivePoints =
                collectPrimitives(network, rawElev, PAD - tileOriginX, PAD - tileOriginZ);
        final ImmutableRTree<HydrologicalPrimitive> primitiveIndex =
                new ImmutableRTree<>(primitivePoints, HydrologicalPrimitive.PROTOTYPE);

        if (stages != null) {
            // The local/global channel-id split no longer survives the collision pass (update() re-assigns
            // every channel id — accepted debug-only regression). All channels are reported together;
            // the local-only PNG is intentionally empty.
            stages.channels = network.getChannels();
            stages.localChannels = new ArrayList<>();
            stages.rawElevation = RiverProvider.cropToTile(base[0]);
            stages.carvedElevation = RiverProvider.cropToTile(carvedElevationGlobal);
            stages.network = network;
            stages.primitiveTree = primitiveIndex;
        }
        return primitiveIndex;
    }


    /** Packages the graph into primitives, each carrying a Rosgen type. A fresh classifier per call:
     *  {@code prepare} rebuilds its whole cache anyway, and the two calls see different graphs.
     *  The surface sampler stays in the padded tile frame both call sites hand it pre-offset points in. */
    private static List<HydrologicalPrimitive> collectPrimitives(
            RiverNetwork network, float[] rawElev, double offsetX, double offsetZ) {
        return network.collectPrimitives(
                offsetX,
                offsetZ,
                channelId -> true,
                new ReachRosgenClassifier(rawElev, PADDED),
                (x, z) -> Interpolation.sampleNearest(rawElev, x, z, PADDED));
    }

    // -------------------------------------------------------------------------
    // Query API
    // -------------------------------------------------------------------------

    /** Per-primitive acceptance test; receives the squared distance to the query point (frame-invariant). */
    @FunctionalInterface
    public interface InfluencingPrimitiveTest {
        boolean test(HydrologicalPrimitive primitive, double distSqToQueryPoint);
    }

    /** Existence-only counterpart to {@link #queryInfluence}, for callers that just need a yes/no and
     *  should not pay for a ctx list. {@code tileVisitRadius} sizes the cross-tile window and must
     *  upper-bound the distance of any primitive {@code test} can accept. */
    public boolean anyInfluencingPrimitive(double[] pt, double tileVisitRadius, InfluencingPrimitiveTest test) {
        final Predicate<HydrologicalPrimitive> acceptanceTest = primitive -> {
            final double deltaX = primitive.coord()[0] - pt[0];
            final double deltaZ = primitive.coord()[1] - pt[1];
            return test.test(primitive, deltaX * deltaX + deltaZ * deltaZ);
        };
        return primitives.forEachTileWithin(
                pt,
                tileVisitRadius,
                (tileOriginX, tileOriginZ, tileIndex) -> tileIndex.anyContaining(pt, acceptanceTest));
    }

    /**
     * Every primitive influencing {@code pt}, unordered — feeds {@link HydrologyProfileInprinter}'s flat
     * distance-weighted merge. {@code extraRadius} inflates the circles so the per-chunk prefetch can
     * serve a whole chunk from one query. Returns indexed instances; callers must not mutate them.
     */
    public List<HydrologicalPrimitive> queryInfluence(double[] pt, double extraRadius) {
        final List<HydrologicalPrimitive> influencingPrimitives = new ArrayList<>(64);
        primitives.forEachTileWithin(
                pt, HydrologyTuning.MAX_INFLUENCE_RADIUS + extraRadius, (tileOriginX, tileOriginZ, tileIndex) -> {
                    tileIndex.queryContaining(pt, extraRadius, influencingPrimitives);
                    return false;
                });
        return influencingPrimitives;
    }

    /** {@link #queryInfluence(double[], double)} with no extra radius (single-point queries). */
    public List<HydrologicalPrimitive> queryInfluence(double[] pt) {
        return queryInfluence(pt, 0.0);
    }

    public ImmutableRTree<HydrologicalPrimitive> getPrimitiveTree(int tileX, int tileZ) {
        return primitives.getEntry(new int[] {tileX, tileZ});
    }

    // -------------------------------------------------------------------------
    // Debug access
    // -------------------------------------------------------------------------

    @TestOnly
    public RiverProvider.Stages debugStages(int tileX, int tileZ) {
        final RiverProvider.Stages stages = new RiverProvider.Stages();
        buildTile(tileX, tileZ, stages);
        return stages;
    }

}
