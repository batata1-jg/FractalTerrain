# test/ (JUnit 5)

JUnit 5 golden-gate tests, run via `gradle test` (`useJUnitPlatform()`).

## Subdirectories

| Directory    | What                                                          | When to read                                                       |
| ------------ | ------------------------------------------------------------- | ------------------------------------------------------------------ |
| `hydrology/` | Global/local river goldens, spatial index, channel geometry   | Verifying or re-baselining river generation and the units index    |
| `ml/`        | ONNX pipeline session-lifecycle regression                    | Verifying session reload and model offload thread-safety           |
| `math/`      | Empty stub; no tests yet                                      | Never — add a `CLAUDE.md` here once it holds code                  |

## Status

Does not compile. `:compileTestJava` fails in `hydrology/SpatialIndexCorrectnessGoldenTest.java`
(missing `FractalTerrainConfig.maxNativeWidth()`, removed by commit ea43e40 without updating this call
site), so no tests run. The tree is 10 classes / 56 test methods.

Prior pass/fail counts (20 tests, 11 passing / 8 failing / 1 skipped) are historical: they predate both
this break and the four Rosgen-era classes, and cannot be reproduced until the suite compiles again.
See the root `CLAUDE.md` "## Test" for the full account.
