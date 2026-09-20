package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.carvers.RiverInfluenceCarve;
import org.junit.jupiter.api.Test;

/** Which families cut a shell cross-section and which contribute none. Asserted on the carved buffer:
 *  the dispatch is a virtual call, so there is no token to compare. */
class InfluenceCarverDefaultsTest {

    private static final int PADDED = 16;

    private static float[] flat() {
        final float[] elev = new float[PADDED * PADDED];
        Arrays.fill(elev, 20f);
        return elev;
    }

    @Test
    void riverCutsARosgenCrossSection() {
        final float[] elev = flat();
        final RiverPrimitive river = new RiverPrimitive(
                new double[] {8.0, 8.0}, 5.0, RiverPrimitive.RosgenType.A, new double[] {1.0, 0.0}, 0.0, 2.0, 5.0);

        RiverInfluenceCarve.carveRiverInfluenceGrid(elev, List.of(river), PADDED);

        assertTrue(elev[8 * PADDED + 8] < 20f, "the channel centre must be cut");
    }

    @Test
    void confluenceSourceDeltaAndWaterfallContributeNoShell() {
        for (final HydrologicalPrimitive primitive : List.<HydrologicalPrimitive>of(
                new ConfluencePrimitive(new double[] {8.0, 8.0}, 4.0, 5.0),
                new SourcePrimitive(new double[] {8.0, 8.0}, 4.0, 5.0),
                new DeltaPrimitive(new double[] {8.0, 8.0}),
                new WaterfallPrimitive(new double[] {8.0, 8.0}))) {
            final float[] elev = flat();
            final float[] before = elev.clone();

            RiverInfluenceCarve.carveRiverInfluenceGrid(elev, List.of(primitive), PADDED);

            assertArrayEquals(before, elev, 1e-6f, primitive.getType() + " must carve no shell");
        }
    }
}
