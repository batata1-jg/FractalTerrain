package me.batata_1.fractal_terrain.hydrology.rosgen;

/**
 * The measured attributes of one river reach, in the units the Rosgen Level-I key compares against.
 *
 * <p>Only slope, entrenchment and width are genuine observables; {@code widthPerDepth} is prescribed from
 * width (no depth is modelled) and kept only because the published key tests it.
 *
 * <p>Sinuosity is deliberately absent: the meander relaxation produces it, so feeding it back would let
 * meander tuning decide the type, which decides floodplain width — validate against it, never classify
 * from it.
 */
public record ReachMetrics(double slope, double entrenchment, double widthPerDepth, double width, double bedElev) {}
