# test/ (JUnit 5)

JUnit 5 golden-gate tests, run via `gradle test` (`useJUnitPlatform()`).

## Subdirectories

| Directory    | What                                                          | When to read                                                       |
| ------------ | ------------------------------------------------------------- | ------------------------------------------------------------------ |
| `hydrology/` | River goldens, channel geometry, primitive packing, lattice carve | Verifying or re-baselining river generation and the carve math   |
| `ml/`        | ONNX pipeline session-lifecycle regression                    | Verifying session reload and model offload thread-safety           |
| `math/`      | `VectorOps` point-onto-segment projection; window-sampler equivalence to the per-pixel interpolation | Changing projection/clamping used by the hydrology carve, or the window samplers |
| `infinitetensor/` | Window-walk geometry and `NonIntersectingInfiniteTensor` slice/budget behaviour | Changing slice assembly, window intersection, or a cache budget |
| `storage/`   | Chunk-window channel fill: equivalence and tile-touch against the per-pixel path | Changing the heightmap fill or the window bounds |

## Status

Does not compile as of `feature/hydrology`, measured 2026-08-23: `gradle build` fails at
`:compileTestJava` — `hydrology/features/ConfluencePrimitiveTest.java` calls
`ConfluencePrimitive.w(double[])` and `.d(double[])`, neither of which exists (it implements only
`h(double[])`); 9 errors. Pre-existing, unrelated to the river-provider refactor — the file was last
modified three commits before that refactor began.

With `ConfluencePrimitiveTest` excluded, a run measured 2026-08-31 after the heightmap slice-sampling
change reports **95 tests, 18 failed, 1 skipped** — measured with a test file excluded, not a clean
baseline. The 18 failures: `RosgenKeyTest` (4), `ChannelGeometryTest` (4), `ComputeRiverGridTest` (3,
newly visible now the suite reaches it, not caused by the river refactor), `RiverGoldenTest` (2),
`MeandersGoldenTest` (2), `GlobalRiverGoldenTest` (1), `ReachMetricsSamplerTest` (1), `CentrelineTest`
(1).

That count is 77 pre-change plus the 18 passes slice sampling added — `SliceGeometryTest` (4),
`NonIntersectingInfiniteTensorSliceTest` (5), `InterpolationWindowSampleTest` (3), `ChunkChannelFillTest`
(2), `InterpolationSignedWindowTest` (4). The same 18 failures, with byte-identical messages, held across
every step of that change; that equality is what evidenced the conversion left generation output
untouched.

Re-measure before blaming your own change; compare the failure *messages* in
`build/test-results/test/*.xml`, not just which test names fail. A worktree needs
`libs/onnxruntime/teste.jar` copied in (`libs/` is git-ignored) or the build reports ~132 phantom errors.
