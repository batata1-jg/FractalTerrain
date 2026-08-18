# profile/ (test)

Targets bed-carve geometry from a design `src/main` no longer has. All three files below reference the
deleted `NearestChannelSample` record — two by calling the deleted 3-arg
`HydrologyProfileInprinter.sampleNearestChannel(...)` that used to return it (current `sampleNearestChannel`
is a void 5-arg method), one (`BlendMinTest`) by constructing `NearestChannelSample` directly — so all
three break `:compileTestJava`. See `../CLAUDE.md` "Status" for the suite-wide count. Kept on disk and
indexed here because they are real files, not because any of them gate anything today.

## Files

| File                           | What                                                                                  | When to read                                                          |
| ------------------------------ | ------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `NearestChannelSampleTest.java`| **Broken — calls the deleted 3-arg `sampleNearestChannel(...)`.** Intended to gate foot-point sampling: exact perpendicular on a straight reach, sign agreement with the knot tangent line, downstream-oriented interpolation, cross-channel and non-consecutive-knot fallbacks | Restoring or replacing bed-carve sampling test coverage |
| `BlendMinTest.java`            | **Broken — constructs the deleted `NearestChannelSample` record.** Intended to gate `RosgenProfile.blendMin`'s contract: exact `min` outside the range, never above the hard min, continuous at the boundary, symmetric, and a river never raises terrain | Restoring `blendMin` test coverage; `blendMin` itself still exists on `RosgenProfile` and compiles fine — only this test's fixtures are dead |
| `PolylineChordErrorTest.java`  | **Broken — calls the deleted 3-arg `sampleNearestChannel(...)`.** Intended to bound two-segment polyline error against an analytic arc, justifying not rebuilding the quintic | Restoring polyline-approximation test coverage |
