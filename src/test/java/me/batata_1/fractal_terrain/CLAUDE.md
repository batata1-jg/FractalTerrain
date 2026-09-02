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

## Test

```
gradle test
```

A worktree needs `libs/onnxruntime/teste.jar` copied in (`libs/` is git-ignored) or the build reports
~132 phantom errors.

**Recorded baseline, measured 2026-09-02 at `df7ca2e`: 102 tests, 9 failed, 1 skipped.** The nine:
`RosgenKeyTest` (4), `RiverGoldenTest` (2), `MeandersGoldenTest` (1), `CentrelineTest` (1),
`ReachMetricsSamplerTest` (1). Full failure messages are archived in
`.superpowers/conventions-alignment/post-migration-failures.txt`.

This suite has broken and been repaired several times, so treat the figure above as a claim to
re-verify, not a fact — `HEAD` may have moved since it was taken. Re-measure before blaming your own
change, and compare the failure *messages* in `build/test-results/test/*.xml`, not just which test names
fail; message equality is what evidences that a refactor left generation output untouched.

Three of the nine are wrong expectations rather than defects. `RiverGoldenTest`'s two report `synthetic
field produced no local channels — fixture is degenerate`: the fixture yields zero local channels, so
those assertions never reach the traced network. `MeandersGoldenTest.independentCrossingsAreNotMerged`
expects two channels where the code produces three — `AtomicView.resolveCrossingEdges` inserts one shared
node at a geometric crossing and invariant K1 allows it a single outgoing edge, so a confluence is forced
by planarization and the no-merge outcome is unreachable.
