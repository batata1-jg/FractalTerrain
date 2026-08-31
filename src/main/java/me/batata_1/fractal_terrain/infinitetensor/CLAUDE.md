# infinitetensor/

Tiled "infinite" tensor abstraction over `Storage`: windowed compute and slice accumulation.

## Files

| File                              | What                                                                    | When to read                                             |
| --------------------------------- | ----------------------------------------------------------------------- | -------------------------------------------------------- |
| `README.md` | Windowed-tile architecture, slice assembly, freeze/publication invariants | Slicing or accumulating windowed tensors, changing window geometry |
| `InfiniteTensor.java`             | Abstract base: shape, output window, per-tile/batched compute function  | Adding an infinite-tensor type, window iteration         |
| `SliceGeometry.java`              | Window-intersection walk shared by both slice paths; geometry only, no fetch or write | Changing which windows a slice touches, adding a slice caller |
| `AdditiveInfiniteTensor.java`     | Overlapping-window tensor; slices are summed on accumulation            | Diffusion tensors with overlapping tiles                 |
| `NonIntersectingInfiniteTensor.java` | Non-overlapping-window tensor over `Storage`; recoverable miss recompute, bulk `getSlice`, byte budget enforced on insert | Per-tile caches with disjoint windows, bulk reads, cache budgets |
| `FloatTensor.java`                | Backing dense tensor; `freeze()` guards its own mutator methods once cached — the public `data` array itself is not guarded | Mutating vs reading cached tensors, freeze boundary |
| `TensorWindow.java`               | Sliding-window layout (size/stride/offset) mapping window index → pixels | Overlapping/gapped window geometry                       |
| `TensorFunction.java`             | Functional interface for a tile's compute                               | Implementing a tile compute                              |
| `NonIntersectingSpatialIndex.java`| Spatial index of non-intersecting windows                               | Locating the window covering a coordinate                |
