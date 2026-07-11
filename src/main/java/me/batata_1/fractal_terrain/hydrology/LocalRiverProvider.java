package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.GRID;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.PAD;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.PADDED;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.sampleBilinear;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
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

/**
 * Owner of the whole per-tile river pipeline. From the decoded terrain ({@link DecoderChannels}) and the
 * global river network ({@link GlobalRiverProvider}) it computes, per 512×512 relief tile, two
 * artifacts produced by a single build:
 *
 * <ul>
 *   <li>a serializable {@link NonIntersectingSpatialIndex} of per-tile {@link ImmutableRTree}s of
 *       {@link HydrologicalUnit} influence circles — the queryable river network (global rivers traced
 *       + relaxed by a {@link Meanders} sim, plus the detailed local network traced from drainage),
 *       answered by point-stabbing queries; and</li>
 *   <li>a {@link NonIntersectingInfiniteTensor} of {@code [1,512,512]} carved+filled elevation tiles,
 *       imported by {@code ReliefProvider} as its elevation channel.</li>
 * </ul>
 *
 * <p>The two stores are populated together: whichever is requested first runs {@link #buildTile} and
 * cross-fills the other via the storage claim API (single-flight guarantees one build per tile).
 *
 * <p><b>Responsibility (thin orchestrator):</b> owns the dual-store cache/single-flight plumbing and the
 * per-tile pipeline sequencing; the actual per-stage math is delegated to
 * {@link GlobalNetworkBuilder} (global-river trace + relax + bed assignment, which in turn uses
 * {@link ChannelElevationAssigner}) and {@link LocalDrainageTracer} (drainage-derived local network).
 * {@link HydrologyTileGeometry} is the shared tile-frame geometry all three depend on.
 *
 * <p><b>Invariants:</b> {@link #units}/{@link #carved} (a {@link NonIntersectingSpatialIndex} /
 * {@link NonIntersectingInfiniteTensor} pair) are reused per-tile and single-threaded per key — the
 * {@code pending} single-flight map and the storage claim API are what let one {@link #buildTile} call
 * fill both stores; do not bypass them by computing a tile's units/carved elevation independently.
 */
public class LocalRiverProvider {

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
                path, "local_carved_elev", new int[] {1, GRID, GRID}, this::buildCarvedTile);
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
     * unique to this provider (flow accumulation → reach test → segment walk → channel build) — over a
     * supplied {@code GRID*GRID} drainage/elevation pair and global-river mask, with no pipeline
     * dependency. In production the drainage and elevation originate from the ONNX-decoded terrain
     * (via {@link me.batata_1.fractal_terrain.relief.DecoderChannels#decode}); a golden test instead feeds
     * a synthetic seeded elevation field and its {@code PipelinePreprocessing}-computed drainage. Delegates
     * to the exact production {@link LocalDrainageTracer#traceLocalNetwork} path.
     */
    @TestOnly
    public List<Channel> traceLocalNetworkForTest(int[] drainage, float[] elev, boolean[] globalMask) {
        return LocalDrainageTracer.traceLocalNetwork(drainage, elev, globalMask, null);
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

    private TileResult buildTile(int tileX, int tileZ, @Nullable Stages stages) {
        final GlobalRiverProvider grp = globalRiverProvider();
        final float[][] base = DecoderChannels.decode(tileX, tileZ, PAD); // padded 514, channels 0..6

        // 1. global rivers: trace + relax + assign bed elevations.
        final Meanders sim = GlobalNetworkBuilder.build(tileX, tileZ, base, grp);

        // 2. carve the decoded elevation toward the global river beds.
        final float[] carvedElevation = base[0].clone();
        HydrologyProfileCarver.carveGlobalRivers(carvedElevation, sim.getChannels(), PADDED);

        // 3. fill sinks (border-blended), then crop to the inner 512 carved tile.
        final float[] filled = PipelinePreprocessing.fillSinks(carvedElevation, PADDED, HydrologyTuning.FILL_PADDING);
        final FloatTensor carvedTile = cropToTile(filled);

        // 4. drainage on the filled elevation; crop everything to the inner 512 grid.
        final float[] uniformWeight = new float[PADDED * PADDED];
        Arrays.fill(uniformWeight, 1f);
        final int[] drainagePadded = PipelinePreprocessing.computeDrainageDirection(filled, uniformWeight, PADDED);
        final int[] drainage = new int[GRID * GRID];
        final float[] elev = new float[GRID * GRID];
        for (int ix = 0; ix < GRID; ix++) {
            for (int iz = 0; iz < GRID; iz++) {
                final int padded = (PAD + ix) * PADDED + (PAD + iz);
                drainage[ix * GRID + iz] = drainagePadded[padded];
                elev[ix * GRID + iz] = filled[padded];
            }
        }

        // 5. mark the global-river pixels so the local network excludes them.
        final boolean[] globalMask = LocalDrainageTracer.rasterizeGlobalMask(sim.getChannels());

        final List<Channel> localChannels = LocalDrainageTracer.traceLocalNetwork(drainage, elev, globalMask, stages);

        // 7. assemble the unit index: global network units + local channel units (tile-local coords),
        //    STR bulk-loaded into an ImmutableRTree of influence circles (bounds derive from the data).
        //    A shared feature-id counter gives global rivers and the local network one tile-unique id space.
        final RiverNetwork.ElevationSampler decodedSampler = (x, z) -> sampleBilinear(base[0], x, z);
        final int[] nextFeatureId = {0};
        final List<HydrologicalUnit> unitPoints =
                sim.getNetwork().collectUnits(0, decodedSampler, PAD, PAD, nextFeatureId);
        for (Channel ch : localChannels) LocalDrainageTracer.addLocalChannelUnits(unitPoints, ch, elev, nextFeatureId);
        final ImmutableRTree<HydrologicalUnit> unitIndex = new ImmutableRTree<>(unitPoints, HydrologicalUnit.PROTOTYPE);

        if (stages != null) {
            stages.channels = new ArrayList<>(sim.getChannels());
            stages.localChannels = localChannels;
            stages.carvedElevation = carvedTile.copyRange(0, carvedTile.getSize());
            stages.network = sim.getNetwork();
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

    @TestOnly
    public static final class Stages {
        public float[] flow;
        public boolean[] riverMask;
        public float[] carvedElevation;
        public List<Channel> channels;
        public List<Channel> localChannels;
        public RiverNetwork network;
        public ImmutableRTree<HydrologicalUnit> unitTree;
    }
}
