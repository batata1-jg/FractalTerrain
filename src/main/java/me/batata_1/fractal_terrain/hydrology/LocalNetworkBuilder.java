package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.PADDED;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.sampleBilinear;

import java.util.List;
import me.batata_1.fractal_terrain.hydrology.features.ExtendedRiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.ChannelTyper;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileInprinter;
import me.batata_1.fractal_terrain.hydrology.providers.RiverProvider;
import org.jetbrains.annotations.Nullable;

/**
 * The local-trace half of the per-tile hydrology pipeline, symmetric with {@link GlobalNetworkBuilder}.
 *
 * <p>Runs after it over the same per-tile graph: attaches the drainage-derived local network, re-assigns
 * bed elevations across the unified graph, carves the shell into a fresh elevation clone, then re-points
 * the already-collected river primitives at the final bed elevations and cuts the bed trench into that
 * same buffer. Returns the shell-carved and bed-carved padded ({@code PADDED x PADDED}) elevation; step
 * order is load-bearing — see {@code hydrology/README.md} Invariants.
 */
public final class LocalNetworkBuilder {

    private LocalNetworkBuilder() {}

    public static void build(GlobalNetworkBuilder.Result ctx, float[][] base, @Nullable RiverProvider.Stages stages) {
        final float[] elev = base[0].clone();
        final float[] gradMag = base[4];

        for (Endpoint node : ctx.network().getNodes()) {
            if (node.type != Endpoint.Type.SOURCE && node.type != Endpoint.Type.DRAIN) continue;
            ctx.boundaryElevByNodeIdx()
                    .putIfAbsent(node.id, Math.max(0, sampleBilinear(elev, node.coord[0], node.coord[1])));
        }

        ChannelElevationAssigner.assign(ctx.network(), ctx.boundaryElevByNodeIdx(), elev);

        final List<HydrologicalPrimitive> primitives = collect(ctx.network(), ctx.typer(), elev);
        HydrologyProfileInprinter.carveRiverInfluence(elev, primitives, PADDED);

        LocalDrainageTracer.traceLocalNetwork(ctx.drainage(), elev, gradMag, ctx.network(), stages);
    }

    private static List<HydrologicalPrimitive> collect(RiverNetwork network, ChannelTyper typer, float[] elev) {
        final List<HydrologicalPrimitive> list = network.collectExtendedPrimitives(
                0, 0, channelId -> true, typer, HydrologyTileGeometry.influenceSampler(elev));
        list.sort(HydrologicalPrimitive.comparator);
        return list;
    }

    /**
     * Re-points every {@link ExtendedRiverPrimitive} in {@code primitives} at the bed elevation the just
     * completed {@code ChannelElevationAssigner.assign} produced, in place — an update, not a re-collect,
     * so {@link RiverNetwork#collectExtendedPrimitives} still runs exactly once per tile. Only the bed
     * moves: the influence radius is untouched, which is also what keeps the list's {@link
     * HydrologicalPrimitive#comparator} order valid without re-sorting. Skips a primitive whose channel,
     * {@code bedElevations} or knot index no longer resolves rather than failing the whole tile.
     */
    private static void refreshBedElevations(List<HydrologicalPrimitive> primitives, RiverNetwork network) {
        for (int i = 0; i < primitives.size(); i++) {
            if (!(primitives.get(i) instanceof ExtendedRiverPrimitive extended)) continue;
            final Channel ch = network.getChannel(extended.channelId());
            if (ch == null || ch.bedElevations == null) continue;
            final int pointIndex = extended.pointIndex();
            if (pointIndex < 0 || pointIndex >= ch.bedElevations.length) continue;
            primitives.set(i, extended.withBedElevation(ch.bedElevations[pointIndex]));
        }
    }
}
