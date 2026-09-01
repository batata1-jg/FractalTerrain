package me.batata_1.fractal_terrain.hydrology.profile;

import java.util.List;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.providers.RiverProvider;

/**
 * Per-{@link RiverProvider} instance wrapper: the constructor binds one provider, and
 * {@link #prefetchChunk} answers the one per-chunk influence query {@code PopulateNoiseStep} needs
 * before running {@link RiverInfluenceCarve}'s lattice carve over the chunk. The stateless carve math
 * itself lives in {@link RiverInfluenceCarve}, kept apart so this class's {@code providers} import
 * cannot force a cycle back onto it.
 *
 * <p>The painting twin of {@link HydrologyProfilePainter}.
 */
public final class HydrologyProfileInprinter {

    public HydrologyProfileInprinter(RiverProvider riverProvider) {
        this.riverProvider = riverProvider;
    }

    public List<HydrologicalPrimitive> prefetchChunk(double centerPixelX, double centerPixelZ, double chunkRadiusPx) {
        var primitives = riverProvider.queryInfluence(new double[] {centerPixelX, centerPixelZ}, chunkRadiusPx);
        primitives.sort(HydrologicalPrimitive.comparator);
        return primitives;
    }

    private final RiverProvider riverProvider;
}
