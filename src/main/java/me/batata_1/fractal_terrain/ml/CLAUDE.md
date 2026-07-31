# ml/

ONNX diffusion pipeline and model loading; the pipeline is a JVM-lifetime object.

## Files

| File        | What                                                          | When to read                                                |
| ----------- | ------------------------------------------------------------- | ----------------------------------------------------------- |
| `README.md` | How inference is split across stages and why ONNX was chosen  | Onboarding to the diffusion pipeline, cross-cutting concerns |

## Subdirectories

| Directory         | What                                                            | When to read                                          |
| ----------------- | -------------------------------------------------------------- | ----------------------------------------------------- |
| `pipeline/`       | `WorldPipeline` orchestrator + coarse/latent/decoder/climate stages | Diffusion inference, stage math, reload-race session |
| `models/`         | ONNX Runtime wrapper, model-asset download/verify, model loading | Model loading, VRAM slot swapping, asset hashes       |
| `tensorProviders/`| Deterministic tile-seeded Gaussian noise (Python-matched)      | Noise patches matching `world_pipeline`               |
