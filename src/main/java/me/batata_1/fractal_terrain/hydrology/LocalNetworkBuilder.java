package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.PADDED;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.sampleBilinear;

import java.util.List;
import me.batata_1.fractal_terrain.config.StaticHydrologyConfig;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.network.ChannelTyper;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.profile.RiverInfluenceCarve;
import me.batata_1.fractal_terrain.hydrology.providers.RiverProvider;
import org.jetbrains.annotations.Nullable;

/**
 * The local-trace half of the per-tile hydrology pipeline, symmetric with {@link GlobalNetworkBuilder}.
 *
 * <p>Runs after it over the same per-tile graph, and exists to give {@link LocalDrainageTracer} a carved
 * surface to walk: it assigns bed elevations, shell-carves a private clone of the decoded elevation, then
 * traces the local network against that clone and attaches it onto the graph in place. The clone is
 * scratch — the published elevation comes from {@code RiverProvider.carveRivers}, after {@code Meanders}.
 * Step order is load-bearing; see {@code hydrology/README.md} Invariants.
 */
public final class LocalNetworkBuilder {

    private LocalNetworkBuilder() {}

    public static void build(
            GlobalNetworkBuilder.Context ctx, float[][] base, float[] humidity, @Nullable RiverProvider.Stages stages) {
        final float[] elev = base[0].clone();
        final float[] gradMag = base[4];

        for (Endpoint node : ctx.network().getNodes()) {
            if (!node.isSourceOrDrain()) continue;
            ctx.boundaryElevByNodeIdx()
                    .putIfAbsent(node.id, Math.max(0, sampleBilinear(elev, node.coord[0], node.coord[1])));
        }

        ChannelElevationAssigner.assign(ctx.network(), ctx.boundaryElevByNodeIdx(), elev);

        final List<HydrologicalPrimitive> primitives = collect(ctx.network(), ctx.typer(), elev);
        RiverInfluenceCarve.carveRiverInfluenceGrid(elev, primitives, PADDED);

        LocalDrainageTracer.traceLocalNetwork(
                ctx.drainage(), elev, humidity, gradMag, ctx.network(), stages, StaticHydrologyConfig.INSTANCE);
    }

    private static List<HydrologicalPrimitive> collect(RiverNetwork network, ChannelTyper typer, float[] elev) {
        final List<HydrologicalPrimitive> list =
                network.collectPrimitives(0, 0, channelId -> true, typer, HydrologyTileGeometry.influenceSampler(elev));
        list.sort(HydrologicalPrimitive.comparator);
        return list;
    }
}
