# ml/

ONNX diffusion pipeline and model loading. The pipeline is a JVM-lifetime object (outlives any single
world); model load happens in `init()`, not a static initializer (MUST-2, see `ARCHITECTURE.md`).

## Subdirectories

| Directory         | What                                                            | When to read                                          |
| ----------------- | -------------------------------------------------------------- | ----------------------------------------------------- |
| `pipeline/`       | `WorldPipeline` orchestrator + coarse/latent/decoder/climate stages | Diffusion inference, stage math, reload-race session |
| `models/`         | ONNX Runtime wrapper, model-asset download/verify, model loading | Model loading, VRAM slot swapping, asset hashes       |
| `tensorProviders/`| Deterministic tile-seeded Gaussian noise (Python-matched)      | Noise patches matching `world_pipeline`               |
