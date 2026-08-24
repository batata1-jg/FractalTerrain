# hydrology/ (test)

JUnit gates for the deterministic hydrology math. See `../CLAUDE.md` "Status" for the current
pass/fail baseline — several goldens and Rosgen cases fail pre-existing.

## Files

| File                                     | What                                                          | When to read                                                     |
| ---------------------------------------- | ------------------------------------------------------------- | ---------------------------------------------------------------- |
| `GlobalRiverGoldenTest.java`             | Frozen checksum over the global tile pipeline                 | Changing the global trace, coast/ridge masks, arrow packing      |
| `RiverGoldenTest.java`                   | Frozen checksum over the local drainage trace                 | Changing flow accumulation, reach tests, attach rules            |
| `ChannelGeometryTest.java`               | Width-to-depth law behaviour and guards                       | Changing `W_REF`, `WD_EXPONENT`, or the ratio floor              |

## Subdirectories

| Directory   | What                                            | When to read                                    |
| ----------- | ----------------------------------------------- | ----------------------------------------------- |
| `meanders/` | Meander relaxation + network-seam goldens       | Changing relaxation, the view seam, or capture  |
| `network/`  | `Centreline.normalAt` cross-section normal gates | Changing the cross-section stencil or junction hop rules |
| `rosgen/`   | Rosgen key, sampler and classifier-order tests  | Changing thresholds, transects, or graph order  |
| `features/` | Feature-family/sub-type bit packing and confluence geometry | Changing the packed `RIVER_TYPE` word or junction blending |
| `profile/`  | The lattice carve: `computeRiverGrid` merge law and the cross-section LUT | Changing the carve merge, footprint scale, or `sampleCrossSection` |
