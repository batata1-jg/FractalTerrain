# test/ (JUnit 5)

JUnit 5 golden-gate tests, run via `gradle test` (`useJUnitPlatform()`).

## Files

| File                                                | What                                                                                                          | When to read                                                       |
| ---------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `hydrology/GlobalRiverGoldenTest.java`               | Golden-checksum + determinism gate for `GlobalRiverProvider.computeTileForTest` over a synthetic elevation field                                                        | Verifying/re-baselining the global river network build                                                     |
| `hydrology/LocalRiverGoldenTest.java`                | Structural-invariant + determinism gate for `LocalRiverProvider.traceLocalNetworkForTest` over a synthetic single-trunk global network                                  | Verifying the local network trace: trunk attachment, bed monotonicity, tile-edge containment                |
| `hydrology/SpatialIndexCorrectnessGoldenTest.java`   | R-tree-vs-brute-force cross-check + golden checksum over a synthetic `HydrologicalUnit` set                                                                             | Verifying spatial-index query correctness (former `SpatialIndexBenchmark` correctness portion)             |
| `hydrology/meanders/MeandersGoldenTest.java`         | Graph-primitive invariants (split/merge/collision capture/endpoint alignment) plus a golden signature for meander migration                                             | Verifying `Meanders`/`RiverNetwork` graph operations and migration math                                     |
| `ml/pipeline/PipelineSessionReloadRaceTest.java`     | Concurrency regression: asserts no torn read of the volatile `PipelineSession` triple under concurrent reload                                                           | Verifying `WorldPipeline` session-reload thread-safety                                                      |
