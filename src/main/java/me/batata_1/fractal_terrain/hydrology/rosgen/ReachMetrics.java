package me.batata_1.fractal_terrain.hydrology.rosgen;

/**
 * The measured attributes of one river reach, in the units the Rosgen Level-I key compares against.
 *
 * <p>Only {@code slope}, {@code entrenchment} and {@code width} are genuine observables of the generated
 * terrain: slope and entrenchment emerge from the diffusion elevation field, width from flow
 * accumulation. {@code widthDepth} is derived from {@code width}
 * ({@link me.batata_1.fractal_terrain.hydrology.ChannelGeometry#widthDepthRatio}) rather than measured —
 * no depth is modelled — and is therefore a prescription dressed as an input, kept here because the
 * published key tests it. Sinuosity is deliberately absent: it is produced by the meander relaxation, so
 * feeding it back would let meander tuning decide the Rosgen type, which then decides floodplain width.
 * Use sinuosity to validate the result, never to produce it.
 *
 * @param slope        along-channel bed slope, dimensionless (drop / arc length), never negative
 * @param entrenchment flood-prone width / bankfull width; {@code +inf} when the transect saturates
 * @param widthDepth   bankfull width / mean bankfull depth, dimensionless
 * @param width        bankfull width, native px
 * @param bedElev      reach bed elevation relative to sea level (which is {@code 0}), native px
 */
public record ReachMetrics(double slope, double entrenchment, double widthDepth, double width, double bedElev) {}
