# biome/

Climate+relief → vanilla biome parameters. `ClimateVariableTransform` is a thin facade preserving the
pre-split signature (M-011); `BiomeProvider` is the separate per-tile builder/density-function wiring.

## Files

| File                            | What                                                                 | When to read                                       |
| ------------------------------- | -------------------------------------------------------------------- | -------------------------------------------------- |
| `ClimateVariableTransform.java` | Thin public facade forwarding to `ClimateToBiomeTransformer` (M-011) | Legacy transform entry; new code uses the transformer |
| `ClimateToBiomeTransformer.java`| Samples climate+relief and produces biome parameters                 | Transform math, shore/parameter wiring             |
| `BiomeParameterClassifier.java` | `is…(value)` band predicates over parameter values                   | Parameter band classification                      |
| `ShoreDistanceCalculator.java`  | Distance-to-shore upscaling                                          | Shore-distance computation                         |
| `BiomeProvider.java`            | Per-tile biome builder + density-function wiring for the biome source | Tile origins, density functions, biome assignment  |

## Subdirectories

| Directory     | What                                                    | When to read                                |
| ------------- | ------------------------------------------------------- | ------------------------------------------- |
| `parameters/` | Biome-parameter enums (relocated in M-011)              | Parameter levels/bands                       |
| `source/`     | `FractalTerrainBiomeSource` (vanilla `BiomeSource`)     | Registering/serving the biome source         |
