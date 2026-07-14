# infinitetensor/

Tiled "infinite" tensor abstraction over `Storage`: windowed compute, slice accumulation, and the frozen
cache-tensor contract (MUST-3, see `ARCHITECTURE.md`).

## Files

| File                              | What                                                                    | When to read                                             |
| --------------------------------- | ----------------------------------------------------------------------- | -------------------------------------------------------- |
| `InfiniteTensor.java`             | Abstract base: shape, output window, per-tile/batched compute function  | Adding an infinite-tensor type, window iteration         |
| `AdditiveInfiniteTensor.java`     | Overlapping-window tensor; slices are summed on accumulation            | Diffusion tensors with overlapping tiles                 |
| `NonIntersectingInfiniteTensor.java` | Non-overlapping-window tensor over `Storage`; recoverable miss recompute | Per-tile caches with disjoint windows                 |
| `FloatTensor.java`                | Backing dense tensor; `freeze()` makes it immutable once cached (MUST-3) | Mutating vs reading cached tensors, freeze boundary      |
| `TensorWindow.java`               | Sliding-window layout (size/stride/offset) mapping window index → pixels | Overlapping/gapped window geometry                       |
| `TensorFunction.java`             | Functional interface for a tile's compute                               | Implementing a tile compute                              |
| `NonIntersectingSpatialIndex.java`| Spatial index of non-intersecting windows                               | Locating the window covering a coordinate                |
