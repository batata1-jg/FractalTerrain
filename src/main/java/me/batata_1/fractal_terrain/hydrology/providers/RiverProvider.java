package me.batata_1.fractal_terrain.hydrology.providers;

import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.GlobalNetworkBuilder;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingSpatialIndex;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import me.batata_1.fractal_terrain.relief.DecoderChannels;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.RELIEF_CHANNELS;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.*;

public class RiverProvider {

    private static final long PRIMITIVE_CACHE_LIMIT_BYTES = 50L * 1024 * 1024;
    private final NonIntersectingSpatialIndex<ImmutableRTree<HydrologicalPrimitive>> primitives;

    private static final long RELIEF_CACHE_LIMIT_BYTES = 50L * 1024 * 1024;
    private final NonIntersectingInfiniteTensor hydrology_relief;

    public RiverProvider(String path) {
        primitives = new NonIntersectingSpatialIndex<>(
                path,
                "local_river_units",
                new int[] {GRID, GRID},
                new ImmutableRTree<>(List.of(), HydrologicalPrimitive.PROTOTYPE),
                key -> buildTile(key,null).primitive(),
                PRIMITIVE_CACHE_LIMIT_BYTES);
        hydrology_relief = new NonIntersectingInfiniteTensor(
                path, "hydrology_relief", new int[] {RELIEF_CHANNELS, 512, 512}, (key) -> buildTile(key,null).tile());
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

    private record HydrologyResult(ImmutableRTree<HydrologicalPrimitive> primitive, FloatTensor tile ) {}

    private GlobalRiverProvider globalRiverProvider() {
        return (globalRiverOverride != null) ? globalRiverOverride : FractalTerrainInstance.getGlobalRiverProvider();
    }

    private HydrologyResult buildTile(TileKey key, Stages stages) {

        final int tileX = key.get(0);
        final int tileZ = key.get(1);

        final GlobalRiverProvider grp = globalRiverProvider();
        final float[][] base = DecoderChannels.decode(tileX, tileZ, PAD); // padded 514, channels 0..6

        final var result = GlobalNetworkBuilder.build(tileX,tileZ,base,grp);

        float[] carvedElev = LocalRiverProvider.apply(result,base,stages);



        return null;
    }


    /** Test-only override for the global river source; {@code null} → use the singleton. */
    @TestOnly
    private @Nullable GlobalRiverProvider globalRiverOverride;


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
        public ImmutableRTree<HydrologicalPrimitive> primitiveTree;
    }
}
