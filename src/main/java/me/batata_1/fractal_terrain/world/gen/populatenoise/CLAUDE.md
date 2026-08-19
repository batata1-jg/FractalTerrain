# populatenoise/

## Files

| File                    | What                                                                              | When to read                                |
| ----------------------- | --------------------------------------------------------------------------------- | ------------------------------------------- |
| `PopulateNoiseStep.java`| Second-pass ELEVATION override: fills columns from relief/gradient/param heightmaps. `fineGrainedPrimitivePass` prefetches the chunk's river primitives once, merges them over the chunk's lattice with `HydrologyProfileInprinter.computeRiverGrid`, then blends each column's `(height, water, weight)` triple against ambient, writing `Types.ELEVATION`, `Types.RIVER_DIFFERENCE`, `Types.WATER_HEIGHT` and `Types.RIVER_TYPE`. Below the hot/cold line of abstraction (see `ARCHITECTURE.md`) — no heap allocation in the per-column loop | Elevation/block override during chunk fill; adding a non-river primitive family to the merge; allocation-cost review of the per-column loop |
