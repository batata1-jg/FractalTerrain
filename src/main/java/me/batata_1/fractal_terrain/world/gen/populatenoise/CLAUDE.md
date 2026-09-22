# populatenoise/

## Files

| File                    | What                                                                              | When to read                                |
| ----------------------- | --------------------------------------------------------------------------------- | ------------------------------------------- |
| `PopulateNoiseStep.java`| Second-pass ELEVATION override: fills columns from relief/gradient/param heightmaps. `fineGrainedPrimitivePass` queries the chunk's primitives once (`RiverProvider.queryInfluence`, inlined at this call site) and sorts them, then merges every family in one sorted walk over the chunk's lattice with `LatticeCarve.computeBedGrid` into one shared `dist[]` — no separate radial pass — then blends each column's `(height, water, weight)` triple against ambient, writing `Types.ELEVATION`, `Types.RIVER_DIFFERENCE`, `Types.WATER_HEIGHT`, `Types.RIVER_TYPE` and `Types.RIVER_DIST` (taken from that shared `dist[]`, so it reflects whichever primitive won each cell). Below the hot/cold line of abstraction (see `ARCHITECTURE.md`) — no heap allocation in the per-column loop | Elevation/block override during chunk fill; adding a primitive family to the merge; allocation-cost review of the per-column loop |
