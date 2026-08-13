# populatenoise/

## Files

| File                    | What                                                                              | When to read                                |
| ----------------------- | --------------------------------------------------------------------------------- | ------------------------------------------- |
| `PopulateNoiseStep.java`| Second-pass ELEVATION override: fills columns from relief/gradient/param heightmaps. `fineGrainedPrimitivePass`'s per-column loop is below the hot/cold line of abstraction (see `ARCHITECTURE.md`) — no heap allocation in the loop body | Elevation/block override during chunk fill; allocation-cost review of the per-column loop |
