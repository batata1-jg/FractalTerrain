# tests/

Manual `main()`-class harnesses, each wired to a Gradle `JavaExec` task.

## Files

| File                          | What                                                                                                                                         | Gradle task              | When to read                                              |
| ------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------- | ----------------------------------------------------------- |
| `PipelineTest.java`           | GPU-memory sampler around `nvidia-smi`; currently makes no calls into `WorldPipeline`/`ModelAssetManager`, so it measures an idle baseline, not pipeline VRAM | `pipelineTest`           | Checking the VRAM-budget harness itself                    |
| `GlobalRiverTest.java`        | Loads models, builds a `GlobalRiverProvider`, dumps every intermediate stage to PNG for a few tiles                                          | `globalRiverTest`        | Visual/manual global riverPrimitive network check                   |
| `RiverTest.java`              | Loads models, builds `GlobalRiverProvider`/`RiverProvider` directly, dumps flow/mask/carve/channel PNGs plus the shell carve's distance field and floodplain blend ratio, checks bed monotonicity, dumps the primitive tree | `riverTest`               | Visual/manual local riverPrimitive network check                    |
| `MeandersTest.java`           | Manual assertion suite for `Meanders` graph ops (crossing-to-confluence merge, pruning the losing branch, disjoint channels left untouched, endpoint alignment) plus a relaxation scenario with PNG dumps | `meandersTest`           | Visual/manual meander relaxation + graph-invariant check   |
| `SpatialIndexBenchmark.java`  | R-tree vs. legacy-quadtree correctness cross-check + throughput benchmark, plus RiverNetwork's detectCrossings (R-tree) and detectAndApplyCutoffs (SpatialHashGrid) sections | `spatialIndexBenchmark`  | Spatial-index microbenchmark                                |
| `CaptureSelectionTest.java`   | Manual reproduction of `MeandersGoldenTest`'s stream-capture fixtures; prints facts and a pass/fail summary rather than asserting, so `RiverNetwork.detectAndResolveCaptures` can be observed without a compiling JUnit suite | `captureSelectionTest`   | Checking stream-capture selection without the JUnit suite  |
