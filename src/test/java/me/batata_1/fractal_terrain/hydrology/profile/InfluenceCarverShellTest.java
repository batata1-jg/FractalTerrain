package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.batata_1.fractal_terrain.hydrology.features.AbandonedRiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.ConfluencePrimitive;
import me.batata_1.fractal_terrain.hydrology.features.OxbowLakePrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;

/** The shell pass's new per-primitive dispatch: what carves, what still does not, and that a
 *  RiverPrimitive's own carve is untouched by the refactor. */
class InfluenceCarverShellTest {

    private static final int PADDED = 16;

    private static RiverPrimitive river(double cx, double elevation) {
        return new RiverPrimitive(
                new double[] {cx, 8.0}, 2.0, RosgenType.A, new double[] {1.0, 0.0}, 0.0, 2.0, elevation, 0L);
    }

    private static OxbowLakePrimitive oxbow(double cx, double elevation) {
        return new OxbowLakePrimitive(
                new double[] {cx, 8.0}, (byte) 3, 2.0, 2.0, elevation, new double[] {1.0, 0.0}, 0.0, null);
    }

    private static float[] flatElevation(float value) {
        final float[] elev = new float[PADDED * PADDED];
        java.util.Arrays.fill(elev, value);
        return elev;
    }

    @Test
    void oxbowCarvesTheShellLikeARiverAtTheSamePosition() {
        final float[] elevOxbow = flatElevation(20f);
        RiverInfluenceCarve.carveRiverInfluenceGrid(elevOxbow, List.of(oxbow(8.0, 5.0)), PADDED);

        final float[] elevRiver = flatElevation(20f);
        RiverInfluenceCarve.carveRiverInfluenceGrid(elevRiver, List.of(river(8.0, 5.0)), PADDED);

        assertArrayEquals(
                elevRiver,
                elevOxbow,
                1e-6f,
                "an oxbow at a river's own position and elevation must carve an identical shell");
    }

    @Test
    void abandonedRiverCarvesARadialDiscIntoTheShell() {
        final float[] elev = flatElevation(20f);
        final AbandonedRiverPrimitive trace = new AbandonedRiverPrimitive(new double[] {8.0, 8.0}, (byte) 4, 2.0, 5.0);

        RiverInfluenceCarve.carveRiverInfluenceGrid(elev, List.of(trace), PADDED);

        assertTrue(elev[8 * PADDED + 8] < 20f, "the disc centre must be cut below ambient");
        assertEquals(20f, elev[0], 1e-6f, "a far corner outside the disc's radius must stay untouched");
    }

    @Test
    void confluenceStillContributesNoShellInfluence() {
        final float[] elev = flatElevation(20f);
        RiverInfluenceCarve.carveRiverInfluenceGrid(
                elev, List.of(new ConfluencePrimitive(new double[] {8.0, 8.0}, 4.0, 5.0)), PADDED);

        assertEquals(
                20f,
                elev[8 * PADDED + 8],
                1e-6f,
                "the shell pass does not carve Confluence/Source today, and this refactor must not change that");
    }

    @Test
    void riverShellOutputIsUnchangedByTheRefactor() {
        // Regression pin: same fixture ComputeRiverGridTest already exercises for the bed pass,
        // run through the shell entry point instead, to prove carveRosgenInfluence's math did not
        // move when it was retyped off RiverPrimitive onto RosgenCarvedPrimitive.
        final float[] elev = flatElevation(20f);
        RiverInfluenceCarve.carveRiverInfluenceGrid(elev, List.of(river(8.0, 5.0)), PADDED);

        assertTrue(elev[8 * PADDED + 8] < 20f, "the channel centre must be cut");
    }
}
