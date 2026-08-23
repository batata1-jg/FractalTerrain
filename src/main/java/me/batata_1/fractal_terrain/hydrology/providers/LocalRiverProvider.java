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

    public static float[] apply(GlobalNetworkBuilder.Result ctx, float[][] base, RiverProvider.Stages stages) {

        final var elev = base[0].clone();
        final var gradMag = base[4];

        LocalDrainageTracer.traceLocalNetwork(ctx.drainage(), elev,gradMag, ctx.network(),stages);

        for (Endpoint node : ctx.network().getNodes()) {
            if (node.type != Endpoint.Type.SOURCE && node.type != Endpoint.Type.DRAIN) continue;
            ctx.boundaryElevByNodeIdx().putIfAbsent(
                    node.id, Math.max(0, sampleBilinear(elev, node.coord[0], node.coord[1])));
        }


        ChannelElevationAssigner.assign(ctx.network(),ctx.boundaryElevByNodeIdx(),elev);

        HydrologyProfileInprinter.carveRiverInfluence(elev,collect(ctx.network(),ctx.typer(),elev),PADDED);

        //correct the elevation of the sources
        for (Endpoint node : ctx.network().getNodes()) {
            if (node.type != Endpoint.Type.SOURCE && node.type != Endpoint.Type.DRAIN) continue;
            ctx.boundaryElevByNodeIdx().put(
                    node.id, Math.max(0, sampleBilinear(elev, node.coord[0], node.coord[1])));
        }
        ChannelElevationAssigner.assign(ctx.network(),ctx.boundaryElevByNodeIdx(),elev);
        return elev;
    }

    private static List<HydrologicalPrimitive> collect(RiverNetwork network, ChannelTyper typer,float[] elev) {
        var list = network.collectPrimitives(0,0,channelId -> true, typer,(x,z,bed,width) -> {
            double delta = Math.abs(Interpolation.sampleNearest(elev,x,z,PADDED) - bed);
            return HydrologyTuning.influence(width,delta);
        });
        list.sort(HydrologicalPrimitive.comparator);
        return list;
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
