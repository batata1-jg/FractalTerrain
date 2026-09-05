package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.batata_1.fractal_terrain.hydrology.profile.InfluenceCarver;
import org.junit.jupiter.api.Test;

/** Every family's shell-carve dispatch, before the new primitive shapes exist. Locks in that this
 *  refactor starts behavior-preserving: only RiverPrimitive carves the shell today. */
class InfluenceCarverDefaultsTest {

    @Test
    void onlyRiverCarvesTheShellToday() {
        final RiverPrimitive river =
                new RiverPrimitive(new double[] {0, 0}, 5.0, RiverPrimitive.RosgenType.A, null, 0, 2, 0);
        final ConfluencePrimitive confluence = new ConfluencePrimitive(new double[] {0, 0}, 1.0, 0.0);
        final SourcePrimitive source = new SourcePrimitive(new double[] {0, 0}, 1.0, 0.0);
        final DeltaPrimitive delta = new DeltaPrimitive(new double[] {0, 0});
        final WaterfallPrimitive waterfall = new WaterfallPrimitive(new double[] {0, 0});

        assertEquals(InfluenceCarver.ROSGEN, river.getInfluenceCarver());
        assertEquals(InfluenceCarver.NONE, confluence.getInfluenceCarver());
        assertEquals(InfluenceCarver.NONE, source.getInfluenceCarver());
        assertEquals(InfluenceCarver.NONE, delta.getInfluenceCarver());
        assertEquals(InfluenceCarver.NONE, waterfall.getInfluenceCarver());
    }
}
