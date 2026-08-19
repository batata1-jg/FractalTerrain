# test/ (JUnit 5)

JUnit 5 golden-gate tests, run via `gradle test` (`useJUnitPlatform()`).

## Subdirectories

| Directory    | What                                                          | When to read                                                       |
| ------------ | ------------------------------------------------------------- | ------------------------------------------------------------------ |
| `hydrology/` | River goldens, channel geometry, primitive packing, lattice carve | Verifying or re-baselining river generation and the carve math   |
| `ml/`        | ONNX pipeline session-lifecycle regression                    | Verifying session reload and model offload thread-safety           |
| `math/`      | `VectorOps` point-onto-segment projection                     | Changing projection/clamping used by the hydrology carve           |

## Status

Compiles and runs as of `06a15dd` plus the working-tree changes to `HydrologyProfileInprinter.java`,
`PopulateNoiseStep.java` and `ComputeRiverGridTest.java` (measured 2026-08-19):
**90 tests, 20 failed, 1 skipped.**

The 20 failures, all pre-existing — `RosgenKeyTest` (6), `ConfluencePrimitiveTest` (4),
`ChannelGeometryTest` (3), `LocalRiverGoldenTest` (2), `MeandersGoldenTest` (2),
`GlobalRiverGoldenTest` (1), `ReachMetricsSamplerTest` (1), `CentrelineTest` (1).

Re-measure before blaming your own change; compare the failure *messages* in
`build/test-results/test/*.xml`, not just which test names fail. A worktree needs
`libs/onnxruntime/teste.jar` copied in (`libs/` is git-ignored) or the build reports ~132 phantom errors.
