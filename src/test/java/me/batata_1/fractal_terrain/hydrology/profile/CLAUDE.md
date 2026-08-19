# profile/ (test)

Gates `computeRiverGrid`, the single lattice carve both the tile shell and the per-chunk bed run
through. Fixtures use resolution 1.0 with a `(1,0)` normal so every sampled perpendicular lands
exactly on a LUT entry and the linear interpolation is exact.

## Files

| File                          | What                                                                                  | When to read                                                          |
| ----------------------------- | ------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `ComputeRiverGridTest.java`   | The merge law and its guards: centre carved to the profile surface, points outside the footprint untouched, nearer-primitive-wins regardless of elevation or list order, buffer reseeding between calls, the stop-at-first-non-river index it reports, tangent-less primitives skipped, LUT length across a full-diagonal primitive, un-normalised water lane, and which primitive the `typeMask` follows | Changing the distance recurrence, the `d` footprint scale, buffer reuse, the river-run stop rule, or what gets stamped into `Types.RIVER_TYPE` |
| `SampleCrossSectionTest.java` | The per-primitive cross-section LUT: every entry equals `RosgenProfile.delta` at its anchored perp distance, nothing is written past `n` in the oversized scratch array, and a negative `baseIdx` samples the far bank rather than the centre | Changing `sampleCrossSection`, the perp-lattice anchoring, or the scratch-buffer contract |
