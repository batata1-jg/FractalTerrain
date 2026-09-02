# hydrology/ (test)

JUnit gates for the deterministic hydrology math. See `../CLAUDE.md` "Test" for the recorded
pass/fail baseline — several goldens and Rosgen cases fail independently of any local change.

## Files

| File                                     | What                                                          | When to read                                                     |
| ---------------------------------------- | ------------------------------------------------------------- | ---------------------------------------------------------------- |
| `GlobalRiverGoldenTest.java`             | Frozen checksum over the global tile pipeline                 | Changing the global trace, coast/ridge masks, arrow packing      |
| `RiverGoldenTest.java`                   | Frozen checksum over the local drainage trace                 | Changing flow accumulation, reach tests, attach rules            |
| `ChannelGeometryTest.java`               | Width-to-depth law behaviour and guards                       | Changing `W_REF`, `WD_EXPONENT`, or the ratio floor              |
| `OrderIndependentInvariantsTest.java`    | Structural invariants of the coarse arrow field over `GlobalRiverGoldenTest`'s fixture: one outgoing direction per river pixel, every arrow landing on river or coast, no cycles, zero width without an arrow, and a non-degenerate field | Changing arrow packing or the global trace, or checking a refactor left the field's shape intact where a checksum would be too brittle |

## Subdirectories

| Directory   | What                                            | When to read                                    |
| ----------- | ----------------------------------------------- | ----------------------------------------------- |
| `meanders/` | Meander relaxation + network-seam goldens       | Changing relaxation, the view seam, or capture  |
| `network/`  | `Centreline.normalAt` cross-section normal gates | Changing the cross-section stencil or junction hop rules |
| `rosgen/`   | Rosgen key, sampler and classifier-order tests  | Changing thresholds, transects, or graph order  |
| `features/` | Feature-family/sub-type bit packing | Changing the packed `RIVER_TYPE` word or adding a feature family |
| `profile/`  | The lattice carve: `computeRiverGrid` merge law and the cross-section LUT | Changing the carve merge, footprint scale, or `sampleCrossSection` |
| `providers/`| `RiverProvider`'s per-tile memo eviction policy | Changing `recentTiles`, its capacity, or its access ordering |
