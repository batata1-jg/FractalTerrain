package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.PADDED;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.sampleBilinear;

import java.util.List;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.network.ChannelTyper;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileInprinter;
import me.batata_1.fractal_terrain.hydrology.providers.RiverProvider;
import me.batata_1.fractal_terrain.math.Interpolation;
import org.jetbrains.annotations.Nullable;

/**
 * The local-trace half of the per-tile hydrology pipeline, symmetric with {@link GlobalNetworkBuilder}.
 *
 * <p>Runs after it over the same per-tile graph: attaches the drainage-derived local network, re-assigns
 * bed elevations across the unified graph, and carves the result into a fresh elevation clone. Returns
 * the carved padded ({@code PADDED x PADDED}) elevation; step order is load-bearing — see
 * {@code hydrology/README.md} Invariants.
 */
public final class LocalNetworkBuilder {

    private LocalNetworkBuilder() {}

    public static float[] build(
            GlobalNetworkBuilder.Result ctx, float[][] base, @Nullable RiverProvider.Stages stages) {
        final float[] elev = base[0].clone();
        final float[] gradMag = base[4];

        LocalDrainageTracer.traceLocalNetwork(ctx.drainage(), elev, gradMag, ctx.network(), stages);

        for (Endpoint node : ctx.network().getNodes()) {
            if (node.type != Endpoint.Type.SOURCE && node.type != Endpoint.Type.DRAIN) continue;
            ctx.boundaryElevByNodeIdx()
                    .putIfAbsent(node.id, Math.max(0, sampleBilinear(elev, node.coord[0], node.coord[1])));
        }

        ChannelElevationAssigner.assign(ctx.network(), ctx.boundaryElevByNodeIdx(), elev);

        HydrologyProfileInprinter.carveRiverInfluence(elev, collect(ctx.network(), ctx.typer(), elev), PADDED);

        // Re-seeds boundary heights against the carved surface so the final assign matches the carve
        // rather than the raw decode the sources/drains were floored against before it.
        for (Endpoint node : ctx.network().getNodes()) {
            if (node.type != Endpoint.Type.SOURCE && node.type != Endpoint.Type.DRAIN) continue;
            ctx.boundaryElevByNodeIdx().put(node.id, Math.max(0, sampleBilinear(elev, node.coord[0], node.coord[1])));
        }
        ChannelElevationAssigner.assign(ctx.network(), ctx.boundaryElevByNodeIdx(), elev);

        return elev;
    }

    private static List<HydrologicalPrimitive> collect(RiverNetwork network, ChannelTyper typer, float[] elev) {
        final List<HydrologicalPrimitive> list =
                network.collectPrimitives(0, 0, channelId -> true, typer, (x, z, bed, width) -> {
                    final double delta = Math.abs(Interpolation.sampleNearest(elev, x, z, PADDED) - bed);
                    return HydrologyTuning.influence(width, delta);
                });
        list.sort(HydrologicalPrimitive.comparator);
        return list;
    }
}
