# test/ (JUnit 5)

The deterministic golden gate (`gradle test`, `useJUnitPlatform()`). Each test drives the exact
production code path over a synthetic seeded fixture with no ONNX dependency, so a divergence in the
deterministic hydrology/math fails immediately. The diffusion half has no automated gate — see
`ARCHITECTURE.md` "Testing stance".

## Files (by subpackage)

| Test                                              | Covers                                                          |
| ------------------------------------------------- | -------------------------------------------------------------- |
| `hydrology/GlobalRiverGoldenTest.java`            | `GlobalRiverProvider` per-tile pipeline (`computeTileForTest`)  |
| `hydrology/LocalRiverGoldenTest.java`             | `LocalRiverProvider` local-network trace (`traceLocalNetworkForTest`) |
| `hydrology/SpatialIndexCorrectnessGoldenTest.java`| Spatial-index correctness (former `SpatialIndexBenchmark`)     |
| `hydrology/meanders/MeandersGoldenTest.java`      | `Meanders` relaxation                                          |
| `ml/pipeline/PipelineSessionReloadRaceTest.java`  | MUST-1 reload-race regression (single-volatile session swap)   |
