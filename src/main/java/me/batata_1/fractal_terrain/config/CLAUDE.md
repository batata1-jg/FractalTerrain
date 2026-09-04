# config/

Configuration split by concern; a backward-compatible facade lives at package root (`FractalTerrainConfig`).

## Files

| File                 | What                                                                    | When to read                                                       |
| -------------------- | ----------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `README.md`          | Hydrology calibration status, failure signatures, tuning invariants     | Changing any `HydrologyTuning` value, diagnosing bad river output   |
| `TensorLayout.java`  | `CH/X/Z` axis indices + per-stage channel counts (ONNX I/O contract)    | Changing tensor axes or channel counts, adding a stage boundary    |
| `DebugConfig.java`   | Debug flags (property-sourced) + visualizer-mode constants              | Adding a debug flag, understanding hot-path flag placement (R-003) |
| `HydrologyTuning.java` | River width/carve-profile law, border/sampling constants              | Tuning riverPrimitive width, carve profile, sampling borders                |
| `HydrologyConfig.java` | Injectable interface over a curated ~15-constant subset of `HydrologyTuning` (trace/carve math, not Rosgen thresholds) | Injecting hydrology tuning instead of reading `HydrologyTuning` statically |
| `StaticHydrologyConfig.java` | Enum-singleton `HydrologyConfig` backed by the live `HydrologyTuning` statics; the production default | Wiring the default config into `GenerationContext` or a test |
| `ModConfig.java`     | `.properties` load/parse machinery + remaining global scalars           | Adding a config scalar, changing property parsing                  |
