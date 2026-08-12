# test/ (JUnit 5)

JUnit 5 golden-gate tests, run via `gradle test` (`useJUnitPlatform()`).

## Subdirectories

| Directory    | What                                                          | When to read                                                       |
| ------------ | ------------------------------------------------------------- | ------------------------------------------------------------------ |
| `hydrology/` | Global/local river goldens, spatial index, channel geometry   | Verifying or re-baselining river generation and the primitives index    |
| `ml/`        | ONNX pipeline session-lifecycle regression                    | Verifying session reload and model offload thread-safety           |
| `math/`      | `VectorOps` point-onto-segment projection                     | Changing projection/clamping used by the nearest-channel carve     |

## Status

Compiles and runs. The earlier `:compileTestJava` break (`FractalTerrainConfig.maxNativeWidth()`, removed
by commit ea43e40) is fixed — `hydrology/SpatialIndexCorrectnessGoldenTest.java:52` now calls
`HydrologyTuning.maxNativeWidth()`.

Last measured on `ad118e3`: **83 tests, 15 failed, 3 skipped** across 15 classes. The failures are
pre-existing, not flakes, and cluster in three places:

- `RosgenKeyTest` (6) and `ReachMetricsSamplerTest` (1) — Rosgen thresholds/transects
- `ChannelGeometryTest` (3) — the width-to-depth law
- `GlobalRiverGoldenTest` (1), `LocalRiverGoldenTest` (2), `MeandersGoldenTest` (2) — captured goldens

Re-run before assuming this baseline still holds; treat a red run as suspect only if the failure set
differs from the above.
