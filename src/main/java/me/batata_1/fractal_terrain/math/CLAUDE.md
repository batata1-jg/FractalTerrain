# math/

Numeric and geometric helpers shared across providers: image filtering, contour/skeleton tracing,
vectors, and biome-band ranges.

## Files

| File                       | What                                                                    | When to read                                       |
| -------------------------- | ----------------------------------------------------------------------- | -------------------------------------------------- |
| `Blur.java`                | Gaussian blur kernels (bounded sigma)                                   | Smoothing a field                                  |
| `DifferenceOfGaussians.java` | DoG band-pass over a tensor (coarse-tile sized)                       | Edge/ridge band-pass, residual DoG                 |
| `Interpolation.java`       | Bilinear/smoothstep interpolation over a sampled function, plus allocation-free samplers over a pre-sliced window (including corner-wise `abs`/`signum`) | Upsampling a field, filling a chunk from a tensor slice |
| `MaskedOps.java`           | Mask-weighted blend operations                                         | Alpha-blending two fields                          |
| `FieldLinePlacer.java`     | Rasterizes ridge/valley point sets into a field-line image             | Generating field lines for skeletonization         |
| `Skeletonizer.java`        | Binary mask → skeleton splines (Zhang-Suen + Catmull-Rom fit)          | Medial-axis tracing to splines                     |
| `MarchingSquares.java`     | Binary mask → border-contour splines                                   | Outline tracing (contour, not skeleton)            |
| `Vector2.java` / `Vector3.java` | 2D/3D vector math                                                 | Vector arithmetic in noise/geometry                |
| `VectorOps.java`           | Static array-vector ops (magnitude, distance, scale)                   | double[]-vector arithmetic                         |
| `Range.java`               | Half-open `[min, max)` band with `contains`/`mid`                      | Biome-parameter bands                              |

## Subdirectories

| Directory | What                                              | When to read                                    |
| --------- | ------------------------------------------------- | ----------------------------------------------- |
| `ds/`     | Spatial-index data structures (quadtree, R-tree)  | Spatial queries, immutable spatial indexes      |
| `spline/` | Spline types (Catmull-Rom quintic Hermite)        | Fitting/evaluating splines                      |
