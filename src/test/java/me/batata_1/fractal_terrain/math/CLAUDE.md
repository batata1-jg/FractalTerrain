# math/ (test)

Gates the shared vector helpers the hydrology carve depends on, and the window samplers the chunk fill
reads tensor slices through.

## Files

| File                          | What                                                                | When to read                                                        |
| ----------------------------- | -------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `VectorOpsProjectionTest.java`| `VectorOps.projectPointOntoSegment`: clamped segment parameter and bank-signed distance. Its only caller is this test — nothing in `src/main` projects onto a segment | Changing projection, endpoint clamping, the bank sign convention, or the returned `(t, signedDist)` pair; deciding whether the helper still earns its place |
| `InterpolationWindowSampleTest.java` | `sampleWindowBilinear`/`sampleWindowSmoothStep` are bit-identical to the per-pixel `interpolateBilinear`/`interpolateSmoothStep`; an exact pixel reads one column and one row | Changing a window sampler or the floor/ceil corner rule |
| `InterpolationSignedWindowTest.java` | `sampleWindowAbs`/`sampleWindowSignum` match the two-`Interpolation` weirdness path, and apply their transform per corner rather than to the lerped result | Changing the weirdness magnitude/sign split or its corner transform |

## Subdirectories

| Directory | What                                     | When to read                              |
| --------- | ----------------------------------------- | ------------------------------------------ |
| `ds/`     | Spatial-index data structure unit tests   | Changing a `math/ds/` index implementation |
