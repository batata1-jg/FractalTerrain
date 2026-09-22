# carvers/ (test)

Gates `LatticeCarve`'s two entry points and the per-primitive dispatch each drives. Fixtures use
resolution 1.0 with a `(1,0)` normal so every sampled perpendicular lands exactly on a LUT entry and the
linear interpolation is exact.

## Files

| File                          | What                                                                                  | When to read                                                          |
| ----------------------------- | ------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `ComputeBedGridTest.java`     | The bed pass's merge law and its guards: centre carved to the profile surface, points outside the footprint untouched, nearer-primitive-wins regardless of elevation or list order, buffer reseeding between calls, every family carved in one sorted walk with no run-length bound, tangent-less primitives skipped, LUT length across a full-diagonal primitive, un-normalised water lane, which primitive the `typeMask` follows, and the banded footprint coordinate written into `dist` | Changing the distance recurrence, the `d` footprint scale, buffer reuse, or what gets stamped into `Types.RIVER_TYPE`/`Types.RIVER_DIST` |
| `RadialCarveTest.java`        | The three merge properties a disc's bed carve depends on once it shares `dist[]` with a river: banded on the same breakpoints, blends against ambient rather than a merged river surface, keeps a river's claim outside the disc's true footprint, publishes no `typeMask`, and stays within `maxLutLen`'s bound at production resolution | Changing the radial bed carve, the shared merge law, or a radial family's depth/water law |
| `InfluenceCarverShellTest.java` | The shell pass's per-primitive dispatch: an oxbow carves identically to a river at the same position, an abandoned river carves a radial disc, confluence still contributes nothing, and the production mint-time sentinels (`influence=0`, `elevation=NaN`) carve as a no-op rather than NaN | Changing the shell dispatch, or the zero-extent/deferred-elevation guards in `InfluenceCarver` |
