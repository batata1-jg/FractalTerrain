# populatenoise/

## Files

| File                    | What                                                                              | When to read                                |
| ----------------------- | --------------------------------------------------------------------------------- | ------------------------------------------- |
| `PopulateNoiseStep.java`| Second-pass ELEVATION override: fills columns from relief/gradient/param heightmaps. `fineGrainedPrimitivePass` prefetches the chunk's primitives once, merges two families over the chunk's lattice with `RiverInfluenceCarve.computeRiverGrid` — rivers first, then confluence/source radials in a second pass (prefetch via `HydrologyProfileInprinter.prefetchChunk`) — then blends each column's `(height, water, weight)` triple against ambient, writing `Types.ELEVATION`, `Types.RIVER_DIFFERENCE`, `Types.WATER_HEIGHT`, `Types.RIVER_TYPE` and `Types.RIVER_DIST` (from the river pass's `dist`, untouched by the radial pass's own `radialDist`). Below the hot/cold line of abstraction (see `ARCHITECTURE.md`) — no heap allocation in the per-column loop | Elevation/block override during chunk fill; adding a primitive family to either merge pass; allocation-cost review of the per-column loop |
