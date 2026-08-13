# populatenoise/

## Files

| File                    | What                                                                              | When to read                                |
| ----------------------- | --------------------------------------------------------------------------------- | ------------------------------------------- |
| `PopulateNoiseStep.java`| Second-pass ELEVATION override: fills columns from relief/gradient/param heightmaps. `resolveRiverColumns` resolves the river primitives for all 256 columns up front into an (elevation, weight) `double[]`; `fineGrainedPrimitivePass`'s per-column loop then only merges those pairs with ambient. Both are below the hot/cold line of abstraction (see `ARCHITECTURE.md`) — no heap allocation in either loop body | Elevation/block override during chunk fill; adding a non-river primitive family to the merge; allocation-cost review of the per-column loop |
