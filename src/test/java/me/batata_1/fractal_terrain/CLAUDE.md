# test/ (JUnit 5)

JUnit 5 golden-gate tests, run via `gradle test` (`useJUnitPlatform()`).

## Files

| File                                                | What                                                                                                          | When to read                                                       |
| ---------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `hydrology/GlobalRiverGoldenTest.java`               | Golden-checksum + determinism gate for `GlobalRiverProvider.computeTileForTest` over a synthetic elevation field                                                        | Verifying/re-baselining the global river network build                                                     |
| `hydrology/LocalRiverGoldenTest.java`                | Structural-invariant + determinism gate for `LocalRiverProvider.traceLocalNetworkForTest` over a synthetic single-trunk global network                                  | Verifying the local network trace: trunk attachment, bed monotonicity, tile-edge containment                |
| `hydrology/SpatialIndexCorrectnessGoldenTest.java`   | R-tree-vs-brute-force cross-check + golden checksum over a synthetic `HydrologicalUnit` set                                                                             | Verifying spatial-index query correctness (former `SpatialIndexBenchmark` correctness portion)             |
| `hydrology/meanders/MeandersGoldenTest.java`         | Collision semantics (crossings stay independent, unreachable branches are pruned, endpoint alignment) plus a golden signature for meander migration                     | Verifying `Meanders`/`RiverNetwork` collision handling and migration math                                   |
| `hydrology/meanders/RiverNetworkSeamGoldenTest.java` | Round-trip gate for the canonical↔atomic seam: points, topology and per-point flow survive `viewAtomic()`/`accumulateAndCorrectFlow`/`update` bit-exactly              | Verifying the seam all network mutation flows through                                                       |
| `hydrology/ChannelGeometryTest.java`                 | Gate for channel-geometry math (width/depth/cross-section derivation)                                                                                                    | Verifying channel geometry calculations                                                                     |
| `hydrology/rosgen/RosgenKeyTest.java`                | Gate for `RosgenKey` classification lookup/parsing logic                                                                                                                 | Verifying Rosgen key derivation and lookup table behavior                                                   |
| `hydrology/rosgen/ReachRosgenClassifierTest.java`    | Gate for reach-level Rosgen type classification                                                                                                                          | Verifying `ReachRosgenClassifier` type assignment                                                           |
| `hydrology/rosgen/ReachMetricsSamplerTest.java`      | Gate for per-reach metric sampling feeding the Rosgen classifier                                                                                                         | Verifying `ReachMetricsSampler` metric extraction                                                           |
| `ml/pipeline/PipelineSessionReloadRaceTest.java`     | Concurrency regression: asserts no torn read of the volatile `PipelineSession` triple under concurrent reload                                                           | Verifying `WorldPipeline` session-reload thread-safety                                                      |

Current status: does not compile. `:compileTestJava` fails in `SpatialIndexCorrectnessGoldenTest.java`
(missing `FractalTerrainConfig.maxNativeWidth()`, removed by commit ea43e40 without updating this call
site) — no tests run. The tree is 10 classes / 56 test methods. Prior pass/fail counts (20 tests, 11
passing/8 failing/1 skipped) are historical, predate both this break and the four Rosgen-era classes
added above, and cannot be reproduced until the suite compiles again. See root `CLAUDE.md` "## Test"
for the full account.
