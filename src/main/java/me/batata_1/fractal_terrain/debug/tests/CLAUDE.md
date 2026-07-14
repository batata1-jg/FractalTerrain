# tests/

Manual `main()`-class harnesses, each wired to a Gradle `JavaExec` task. Not the JUnit gate — these
produce PNG/visual dumps and (for `pipelineTest`) need the ~1 GB ONNX weights, so they are not CI-runnable.
The numeric golden gate lives in `src/test/java/`.

## Files

| File                        | Gradle task              | When to read                                      |
| --------------------------- | ------------------------ | ------------------------------------------------- |
| `PipelineTest.java`         | `pipelineTest`           | Manually verifying full `WorldPipeline` inference |
| `GlobalRiverTest.java`      | `globalRiverTest`        | Visual/manual global river network check          |
| `LocalRiverTest.java`       | `localRiverTest`         | Visual/manual local river network check           |
| `MeandersTest.java`         | `meandersTest`           | Visual/manual meander relaxation check            |
| `SpatialIndexBenchmark.java`| `spatialIndexBenchmark`  | Spatial-index microbenchmark                      |
