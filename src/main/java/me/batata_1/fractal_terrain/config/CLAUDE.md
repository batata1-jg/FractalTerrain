# config/

Configuration split by concern; a backward-compatible facade lives at package root (`FractalTerrainConfig`).

## Files

| File                 | What                                                                    | When to read                                                       |
| -------------------- | ----------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `TensorLayout.java`  | `CH/X/Z` axis indices + per-stage channel counts (ONNX I/O contract)    | Changing tensor axes or channel counts, adding a stage boundary    |
| `DebugConfig.java`   | Debug flags (property-sourced) + visualizer-mode constants              | Adding a debug flag, understanding hot-path flag placement (R-003) |
| `HydrologyTuning.java` | River width/carve-profile law, border/sampling constants              | Tuning river width, carve profile, sampling borders                |
| `ModConfig.java`     | `.properties` load/parse machinery + remaining global scalars           | Adding a config scalar, changing property parsing                  |
