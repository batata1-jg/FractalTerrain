# surfacebuilder/

## Files

| File                               | What                                                                                                                | When to read                                                             |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| `README.md`                        | Why the paint column is tabulated once per column, why a claim may extend it, the silt substitution, the `fh` oddity | Changing surface painting, adding a material, diagnosing a fluid-height bug |
| `FractalTerrainSurfaceSystem.java` | `SurfaceSystem` override applying surface rules from heightmaps + biome params, plus the river paint branch reading `Types.RIVER_TYPE` / `Types.RIVER_DIST`. Below the hot/cold line — no heap allocation in the per-column loop | Surface block selection, material rules, allocation-cost review of the column loop |
| `HydrologySurfacePalette.java`     | The `SurfaceMaterial`-to-`BlockState` table; the only place a hydrology material meets Minecraft                     | Changing which block a river material places, adding a material          |
