# specs/

## Files

| File                                          | What                                                                                             | When to read                                                                     |
| --------------------------------------------- | ------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------- |
| `2026-08-19-dead-code-survey.md`              | Grep-reachability inventory of `src/main` at `6a7086e`; superseded — acted on in `06a15dd`       | Checking what the dead-code sweep removed, or re-running the same reachability method |
| `2026-08-20-heightmap-slice-sampling-design.md` | Per-chunk tensor slices replacing per-pixel `getValue`, plus relief/biome cache byte budgets; status *proposed* | Understanding why the heightmap fill reads slices, or before changing `ChunkChannelFill`/tile cache limits |
