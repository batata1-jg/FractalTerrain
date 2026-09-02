# specs/

## Files

| File                                          | What                                                                                             | When to read                                                                     |
| --------------------------------------------- | ------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------- |
| `2026-08-19-dead-code-survey.md`              | Grep-reachability inventory of `src/main` at `6a7086e`; superseded — acted on in `06a15dd`       | Checking what the dead-code sweep removed, or re-running the same reachability method |
| `2026-08-20-heightmap-slice-sampling-design.md` | Per-chunk tensor slices replacing per-pixel `getValue`, plus relief/biome cache byte budgets; status *proposed* | Understanding why the heightmap fill reads slices, or before changing `ChunkChannelFill`/tile cache limits |
| `2026-09-02-radial-primitive-carve-design.md`  | `RadialPrimitive`/`RadialProfile`: confluences and sources carve a radial bowl in a second `computeRiverGrid` pass; carries the merge-arithmetic decisions (D4–D6) and the `SourcePrimitive` payload change that forces a primitive-store rename; status *proposed* | Before touching the carve merge law, adding a carving feature family, or changing `SourcePrimitive`'s serialized form |
| `2026-09-01-conventions-alignment-design.md`  | fastutil migration (162 sites), injection seams, `profile`↔`providers` cycle, dead code; partitioned by whether an item can change terrain; status *implemented* in `7bd587e`, with the `ml/models` caller migration and `OnnxModel`'s feed map left out of scope | Before migrating a collection, touching the service locator, or changing anything whose iteration order feeds generation |
