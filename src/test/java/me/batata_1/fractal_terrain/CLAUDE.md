# test/ (JUnit 5)

JUnit 5 golden-gate tests, run via `gradle test` (`useJUnitPlatform()`).

## Subdirectories

| Directory    | What                                                          | When to read                                                       |
| ------------ | ------------------------------------------------------------- | ------------------------------------------------------------------ |
| `hydrology/` | River goldens, channel geometry, primitive packing, lattice carve | Verifying or re-baselining river generation and the carve math   |
| `ml/`        | ONNX pipeline session-lifecycle regression                    | Verifying session reload and model offload thread-safety           |
| `math/`      | `VectorOps` point-onto-segment projection                     | Changing projection/clamping used by the hydrology carve           |

## Status

Does not compile as of `feature/hydrology`, measured 2026-08-23: `gradle build` fails at
`:compileTestJava` — `hydrology/features/ConfluencePrimitiveTest.java` calls
`ConfluencePrimitive.w(double[])` and `.d(double[])`, neither of which exists (it implements only
`h(double[])`); 9 errors. Pre-existing, unrelated to the river-provider refactor — the file was last
modified three commits before that refactor began.

With `ConfluencePrimitiveTest` excluded, a run reported **81 tests, 19 failed, 1 skipped** — measured with
a test file excluded, not a clean baseline. The 19 failures — `RosgenKeyTest` (6), `ComputeRiverGridTest`
(3, newly visible now the suite reaches it, not caused by the river refactor), `ChannelGeometryTest` (3),
`RiverGoldenTest` (2), `MeandersGoldenTest` (2), `GlobalRiverGoldenTest` (1), `ReachMetricsSamplerTest`
(1), `CentrelineTest` (1).

Re-measure before blaming your own change; compare the failure *messages* in
`build/test-results/test/*.xml`, not just which test names fail. A worktree needs
`libs/onnxruntime/teste.jar` copied in (`libs/` is git-ignored) or the build reports ~132 phantom errors.
