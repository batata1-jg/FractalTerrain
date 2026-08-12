# hydrology/ (test)

JUnit gates for the deterministic hydrology math. See `../CLAUDE.md` "Status" for the current
pass/fail baseline — several goldens and Rosgen cases fail pre-existing.

## Files

| File                                     | What                                                          | When to read                                                     |
| ---------------------------------------- | ------------------------------------------------------------- | ---------------------------------------------------------------- |
| `GlobalRiverGoldenTest.java`             | Frozen checksum over the global tile pipeline                 | Changing the global trace, coast/ridge masks, arrow packing      |
| `LocalRiverGoldenTest.java`              | Frozen checksum over the local drainage trace                 | Changing flow accumulation, reach tests, attach rules            |
| `SpatialIndexCorrectnessGoldenTest.java` | Primitive-index correctness against a brute-force reference        | Changing the R-tree, primitive radii, or the cross-tile query         |
| `ChannelGeometryTest.java`               | Width-to-depth law behaviour and guards                       | Changing `W_REF`, `WD_EXPONENT`, or the ratio floor              |

## Subdirectories

| Directory   | What                                            | When to read                                    |
| ----------- | ----------------------------------------------- | ----------------------------------------------- |
| `meanders/` | Meander relaxation + network-seam goldens       | Changing relaxation, the view seam, or capture  |
| `rosgen/`   | Rosgen key, sampler and classifier-order tests  | Changing thresholds, transects, or graph order  |
| `features/` | `RiverPrimitive` channel/knot id bit-packing    | Changing the packed `ids` layout or knot adjacency |
| `profile/`  | Bed-carve geometry: blend-min, nearest-channel sample, polyline chord error | Changing the carve blend, foot-point sampling, or polyline approximation |
