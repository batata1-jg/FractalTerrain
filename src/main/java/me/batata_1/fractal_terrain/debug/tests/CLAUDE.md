# tests/

Manual `main()`-class harnesses, each wired to a Gradle `JavaExec` task.

## Files

| File                          | What                                                                                                                                         | Gradle task              | When to read                                              |
| ------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------- | ----------------------------------------------------------- |
| `PipelineTest.java`           | GPU-memory sampler around `nvidia-smi`; currently makes no calls into `WorldPipeline`/`ModelAssetManager`, so it measures an idle baseline, not pipeline VRAM | `pipelineTest`           | Checking the VRAM-budget harness itself                    |
| `GlobalRiverTest.java`        | Loads models, builds a `GlobalRiverProvider`, dumps every intermediate stage to PNG for a few tiles                                          | `globalRiverTest`        | Visual/manual global river network check                   |
| `LocalRiverTest.java`         | Loads models, builds `GlobalRiverProvider`/`LocalRiverProvider` directly, dumps flow/mask/carve/channel PNGs, checks bed monotonicity, dumps the unit tree | `localRiverTest`         | Visual/manual local river network check                    |
| `MeandersTest.java`           | Manual assertion suite for `Meanders` graph ops (collision handling, endpoint alignment) plus a relaxation scenario with PNG dumps. Its `addDanglingTributary` helper has no body, so the two tributary scenarios that call it assert against a network the tributary was never added to | `meandersTest`           | Visual/manual meander relaxation + graph-invariant check   |
| `SpatialIndexBenchmark.java`  | R-tree vs. legacy-quadtree correctness cross-check, then queries/sec throughput benchmark                                                    | `spatialIndexBenchmark`  | Spatial-index microbenchmark                                |
