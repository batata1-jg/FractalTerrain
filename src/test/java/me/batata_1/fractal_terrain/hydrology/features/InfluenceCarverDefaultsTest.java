package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.batata_1.fractal_terrain.hydrology.carvers.InfluenceCarver;
import org.junit.jupiter.api.Test;

/** The shell-carve dispatch for the families that never carve: Confluence, Source, Delta, Waterfall
 *  all resolve to {@code NONE}. Written when RiverPrimitive was the only carving family; OxbowLakePrimitive
 *  and AbandonedRiverPrimitive have since joined it (see {@code InfluenceCarverShellTest}), so this only
 *  proves the four families fixtured here still don't. */
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
