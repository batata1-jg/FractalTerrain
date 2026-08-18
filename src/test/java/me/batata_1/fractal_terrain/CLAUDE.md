# test/ (JUnit 5)

JUnit 5 golden-gate tests, run via `gradle test` (`useJUnitPlatform()`).

## Subdirectories

| Directory    | What                                                          | When to read                                                       |
| ------------ | ------------------------------------------------------------- | ------------------------------------------------------------------ |
| `hydrology/` | Global/local river goldens, spatial index, channel geometry   | Verifying or re-baselining river generation and the primitives index    |
| `ml/`        | ONNX pipeline session-lifecycle regression                    | Verifying session reload and model offload thread-safety           |
| `math/`      | `VectorOps` point-onto-segment projection                     | Changing projection/clamping used by the nearest-channel carve     |

## Status

Does NOT compile as of `1d32c85` (verified 2026-08-17): `NearestChannelSampleTest`, `BlendMinTest`,
`PolylineChordErrorTest`, and `SpatialIndexCorrectnessGoldenTest` reference symbols absent from
`src/main` (a deleted `NearestChannelSample` record/3-arg sampler, and a nonexistent
`RosgenProfile.riverInfluence(double)`). See root `CLAUDE.md` "Test" for the full breakdown.

With those four files deleted locally, the suite runs: **74 tests, 19 failed, 1 skipped**, all
pre-existing — `RosgenKeyTest` (6), `ConfluencePrimitiveTest` (4), `ChannelGeometryTest` (3),
`LocalRiverGoldenTest` (2), `MeandersGoldenTest` (2), `GlobalRiverGoldenTest` (1),
`ReachMetricsSamplerTest` (1).
