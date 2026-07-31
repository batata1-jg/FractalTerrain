# hydrology/ (test)

JUnit gates for the deterministic hydrology math. See the repo-root `CLAUDE.md` "Test" section — this
suite does not currently compile.

## Files

| File                                     | What                                                          | When to read                                                     |
| ---------------------------------------- | ------------------------------------------------------------- | ---------------------------------------------------------------- |
| `GlobalRiverGoldenTest.java`             | Frozen checksum over the global tile pipeline                 | Changing the global trace, coast/ridge masks, arrow packing      |
| `LocalRiverGoldenTest.java`              | Frozen checksum over the local drainage trace                 | Changing flow accumulation, reach tests, attach rules            |
| `SpatialIndexCorrectnessGoldenTest.java` | Unit-index correctness against a brute-force reference        | Changing the R-tree, unit radii, or the cross-tile query         |
| `ChannelGeometryTest.java`               | Width-to-depth law behaviour and guards                       | Changing `W_REF`, `WD_EXPONENT`, or the ratio floor              |

## Subdirectories

| Directory   | What                                            | When to read                                    |
| ----------- | ----------------------------------------------- | ----------------------------------------------- |
| `meanders/` | Meander relaxation + network-seam goldens       | Changing relaxation, the view seam, or capture  |
| `rosgen/`   | Rosgen key, sampler and classifier-order tests  | Changing thresholds, transects, or graph order  |
