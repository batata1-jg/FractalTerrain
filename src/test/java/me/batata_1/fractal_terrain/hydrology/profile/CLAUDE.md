# profile/ (test)

Gates the cross-section LUT and the per-type paint contract. See `../carvers/CLAUDE.md` for the lattice
carve itself. Fixtures use resolution 1.0 with a `(1,0)` normal so every sampled perpendicular lands
exactly on a LUT entry and the linear interpolation is exact.

## Files

| File                          | What                                                                                  | When to read                                                          |
| ----------------------------- | ------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `SampleCrossSectionTest.java` | The per-primitive cross-section LUT: every entry equals `RosgenProfile.delta` at its anchored perp distance, nothing is written past `n` in the oversized scratch array, and a negative `baseIdx` samples the far bank rather than the centre | Changing `sampleCrossSection`, the perp-lattice anchoring, or the scratch-buffer contract |
| `RadialProfileTest.java`      | The radial shape laws' zero-at-rim, full-depth-at-centre and monotonic-toward-the-rim properties, shared by `CONFLUENCE`/`SOURCE`/`ABANDONED_RIVER` | Changing a radial family's depth law |
| `RiverPaintDepthTest.java`    | The paint contract every profile answers: a bounded material column keyed by the banded coordinate, over every `RosgenType` and the bed/floodplain band boundaries | Changing per-type materials, the banded-coordinate paint gate, or `riverPaintDepth`'s contract |
