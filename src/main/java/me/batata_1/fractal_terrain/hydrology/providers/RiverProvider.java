package me.batata_1.fractal_terrain.hydrology.providers;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.X;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.Z;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.*;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.*;
import java.util.function.Predicate;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.*;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.ChannelTyper;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileInprinter;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.hydrology.rosgen.ReachRosgenClassifier;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingSpatialIndex;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import me.batata_1.fractal_terrain.relief.DecoderChannels;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Owner of the per-tile river pipeline, between {@link GlobalRiverProvider} upstream and the
 * profile/relief consumers downstream; stage math lives in {@link GlobalNetworkBuilder},
 * {@link LocalNetworkBuilder} and {@link ChannelElevationAssigner}.
 *
 * <p>Publishes two artifacts from {@link #buildTile}: the {@code primitives} index of
 * {@link HydrologicalPrimitive} influence circles (world relief-pixel frame) that {@link #queryInfluence}
 * answers, and a {@code hydrology_relief} carved-elevation tensor that {@code ReliefProvider} imports as
 * relief channel 0.
 */
public class RiverProvider {

    /** Soft cap on the primitives store's cached bytes; mirrors {@code WorldPipeline.cacheLimitBytes}. */
    private static final long PRIMITIVE_CACHE_LIMIT_BYTES = 50L * 1024 * 1024;

    /** Entries held by {@link #recentTiles}; small since each holds a whole tile's primitive tree + tensor. */
    private static final int RECENT_TILE_CAPACITY = 4;

    private final NonIntersectingSpatialIndex<ImmutableRTree<HydrologicalPrimitive>> primitives;
    private final NonIntersectingInfiniteTensor hydrologyRelief;

    /** Last few {@link #buildTile} results, so a tile whose primitives and carved elevation are both
     *  requested does not re-run the trace/carve pipeline twice. A miss only costs a recompute —
     *  {@link #buildTile} is deterministic — never a correctness issue. */
    private final Map<Long, HydrologyResult> recentTiles = newRecentTilesMap();

    /** Test-only override for the global river source; {@code null} → use the singleton. */
    @TestOnly
    private @Nullable GlobalRiverProvider globalRiverOverride;

    public RiverProvider(String path) {
        // The one-primitive prototype index keeps Storage's serializability probe exercising primitive
        // serialization, so the store stays disk-backed. Primitive coords are persisted in the WORLD
        // relief-pixel frame (see buildTile), which is why the store name carries a _v2-equivalent
        // identity ("local_river_units") distinct from the tile-local coordinate scheme it replaced.
        primitives = new NonIntersectingSpatialIndex<>(
                path,
                "local_river_units",
                new int[] {GRID, GRID},
                new ImmutableRTree<>(List.of(), HydrologicalPrimitive.PROTOTYPE),
                key -> buildTile(key.get(0), key.get(1), null).primitive(),
                PRIMITIVE_CACHE_LIMIT_BYTES);
        hydrologyRelief = new NonIntersectingInfiniteTensor(
                path, "hydrology_relief", new int[] {1, GRID, GRID}, key -> buildTile(key.get(X), key.get(Z), null)
                        .tile());
    }

    private static Map<Long, HydrologyResult> newRecentTilesMap() {
        return Collections.synchronizedMap(new LinkedHashMap<>(RECENT_TILE_CAPACITY, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, HydrologyResult> eldest) {
                return size() > RECENT_TILE_CAPACITY;
            }
        });
    }

    private static long tileMemoKey(int tileX, int tileZ) {
        return ((long) tileX << 32) ^ (tileZ & 0xffffffffL);
    }

    static float[] cropToTile(float[] padded) {
        final float[] data = new float[GRID * GRID];
        for (int ix = 0; ix < GRID; ix++) {
            for (int iz = 0; iz < GRID; iz++) {
                data[ix * GRID + iz] = padded[(PAD + ix) * PADDED + (PAD + iz)];
            }
        }
        return data;
    }

    public List<HydrologicalPrimitive> getPrimitivesInTile(TileKey key) {
        return primitives.getEntry(key).getAllEntries();
    }

    private record HydrologyResult(ImmutableRTree<HydrologicalPrimitive> primitive, FloatTensor tile) {}

    private GlobalRiverProvider globalRiverProvider() {
        return (globalRiverOverride != null) ? globalRiverOverride : FractalTerrainInstance.getGlobalRiverProvider();
    }

    // -------------------------------------------------------------------------
    // Core pipeline
    // -------------------------------------------------------------------------

    /** The per-tile pipeline: traces/relaxes the global network, attaches the local trace onto it, carves
     *  the unified graph, then packages both publishable artifacts from the SAME run so neither store
     *  triggers a redundant recompute of the other. {@code stages} is a test/debug sink; production calls
     *  pass {@code null} and go through {@link #recentTiles}. */
    private HydrologyResult buildTile(int tileX, int tileZ, @Nullable Stages stages) {
        if (stages == null) {
            final long memoKey = tileMemoKey(tileX, tileZ);
            final HydrologyResult cached = recentTiles.get(memoKey);
            if (cached != null) return cached;
            final HydrologyResult computed = computeTile(tileX, tileZ, null);
            recentTiles.put(memoKey, computed);
            return computed;
        }
        return computeTile(tileX, tileZ, stages);
    }

    private HydrologyResult computeTile(int tileX, int tileZ, @Nullable Stages stages) {
        final GlobalRiverProvider grp = globalRiverProvider();
        final float[][] base = DecoderChannels.decode(tileX, tileZ, PAD); // padded 514, channels 0..6

        final GlobalNetworkBuilder.Result result = GlobalNetworkBuilder.build(tileX, tileZ, base, grp);

        LocalNetworkBuilder.build(result, base, stages);

        // base[4] (refinedGrad) is read-only for this consumer — Meanders only samples it, never mutates it.
        var lateralErosionSim = new Meanders(result.network(), base[4]);
        lateralErosionSim.simulate(25);

        final float[] carvedElev = carveRivers(result, base[0].clone(), stages);

        // Primitives are stamped in the WORLD relief-pixel frame: (PAD - tileOrigin) drops the halo pad
        // and adds the tile's world origin in one step, matching the offset-free frame every query path
        // assumes. The classifier and surface sampler read base[0] (pre-carve) rather than carvedElev, so
        // a primitive's influence radius reflects the terrain the network was traced over, not the cut.
        final int tileOriginX = tileX * GRID;
        final int tileOriginZ = tileZ * GRID;
        final List<HydrologicalPrimitive> primitivePoints =
                collect(result.network(), base[0], PAD - tileOriginX, PAD - tileOriginZ);
        final ImmutableRTree<HydrologicalPrimitive> primitiveIndex =
                new ImmutableRTree<>(primitivePoints, HydrologicalPrimitive.PROTOTYPE);

        final FloatTensor reliefTile = new FloatTensor(cropToTile(carvedElev), new int[] {1, GRID, GRID});

        if (stages != null) {
            stages.channels = result.network().getChannels();
            stages.localChannels = new ObjectArrayList<>();
            stages.rawElevation = cropToTile(base[0]);
            stages.elevationFirstPass = cropToTile(result.elevCarvedGlobalOnly());
            stages.carvedElevation = cropToTile(carvedElev);
            stages.network = result.network();
            stages.primitiveTree = primitiveIndex;
        }

        return new HydrologyResult(primitiveIndex, reliefTile);
    }

    private float[] carveRivers(GlobalNetworkBuilder.Result ctx, float[] elev, @Nullable Stages stages) {

        for (Endpoint node : ctx.network().getNodes()) {
            if (!node.isSourceOrDrain()) continue;
            ctx.boundaryElevByNodeIdx()
                    .putIfAbsent(node.id, Math.max(0, sampleBilinear(elev, node.coord[0], node.coord[1])));
        }
        ChannelElevationAssigner.assign(ctx.network(), ctx.boundaryElevByNodeIdx(), elev);

        final List<HydrologicalPrimitive> primitives = collect(ctx.network(), ctx.typer(), elev);
        HydrologyProfileInprinter.carveRiverInfluence(elev, primitives, PADDED);

        if (stages != null && !primitives.isEmpty()) {
            stages.distanceField = Arrays.copyOf(HydrologyProfileInprinter.shellDistanceField(), PADDED * PADDED);
            stages.floodPlainBlend = null;
        }

        for (Endpoint node : ctx.network().getNodes()) {
            if (!node.isSourceOrDrain()) continue;
            ctx.boundaryElevByNodeIdx().put(node.id, Math.max(0, sampleBilinear(elev, node.coord[0], node.coord[1])));
        }
        ChannelElevationAssigner.assign(ctx.network(), ctx.boundaryElevByNodeIdx(), elev);

        return elev;
    }

    private static List<HydrologicalPrimitive> collect(RiverNetwork network, ChannelTyper typer, float[] elev) {
        final List<HydrologicalPrimitive> list = network.collectExtendedPrimitives(
                0, 0, channelId -> true, typer, HydrologyTileGeometry.influenceSampler(elev));
        list.sort(HydrologicalPrimitive.comparator);
        return list;
    }

    private static List<HydrologicalPrimitive> collect(
            RiverNetwork network, float[] rawElev, double offsetX, double offsetZ) {
        var resp = network.collectPrimitives(
                offsetX,
                offsetZ,
                channelId -> true,
                new ReachRosgenClassifier(rawElev, PADDED),
                (x, z, bedElev, width, normal, type) ->
                        Math.max(2, 1.5 * RosgenProfile.of(type).floodPlainLength(width)));
        resp.sort(HydrologicalPrimitive.comparator);
        return resp;
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

    /** Headless seam for {@code RiverGoldenTest}: runs the production trace path over caller-supplied
     *  fields, so the trace can be exercised without the ONNX pipeline. Mutates {@code network} in
     *  place; the caller inspects it afterwards. */
    @TestOnly
    public void traceLocalNetworkForTest(int[] drainage, float[] elev, RiverNetwork network) {
        LocalDrainageTracer.traceLocalNetwork(drainage, elev, new float[elev.length], network, null);
    }

    /** Clears both stores' caches. */
    public void clearCaches() {
        primitives.clear();
        hydrologyRelief.clear();
        recentTiles.clear();
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
     *  should not pay for a result list. {@code tileVisitRadius} sizes the cross-tile window and must
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
        final List<HydrologicalPrimitive> influencingPrimitives = new ObjectArrayList<>(64);
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

    /** The carved elevation tile {@code [1, GRID, GRID]}, cropped to the inner grid. Feeds
     *  {@code ReliefProvider}'s channel 0, so the published relief carries the same cut the primitives
     *  in {@link #getPrimitiveTree} were stamped along. */
    public FloatTensor getCarvedElevationTile(int tileX, int tileZ) {
        return hydrologyRelief.getEntry(new int[] {0, tileX, tileZ});
    }

    // -------------------------------------------------------------------------
    // Debug access
    // -------------------------------------------------------------------------

    @TestOnly
    public Stages debugStages(int tileX, int tileZ) {
        final Stages stages = new Stages();
        buildTile(tileX, tileZ, stages);
        return stages;
    }

    /**
     * Debug snapshot of one {@link #buildTile} run, captured for {@code RiverTest} so the harness can
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
        /** Elevation after the global-only carve, before the local trace attaches; the field drainage routes on. */
        public float[] elevationFirstPass;

        public float[] carvedElevation;

        /** The shell carve's per-lattice-point primitive penetration behind {@link #carvedElevation}, in
         *  the PADDED frame (like {@link #flow}); {@code null} when no primitive reached this tile. */
        public float[] distanceField;

        /** Peak {@code w / floodPlainThreshold} any primitive applied at each PADDED-frame point: the
         *  fraction of the way the carve pulled the surface toward the channel profile. Above 1 the
         *  blend extrapolates past that profile rather than interpolating to it. */
        public float[] floodPlainBlend;

        /** Every channel of {@link #network}. */
        public List<Channel> channels;
        /** Always empty; kept so the harness's local-only render still compiles. */
        public List<Channel> localChannels;
        /** The single unified per-tile graph. */
        public RiverNetwork network;

        /** World-framed, unlike the tile-local rasters above; subtract the tile origin to render it. */
        public ImmutableRTree<HydrologicalPrimitive> primitiveTree;
    }
}
