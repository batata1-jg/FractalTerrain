# pipeline/

Diffusion inference: `WorldPipeline` orchestrates four extracted stage collaborators.

## Files

| File                          | What                                                                | When to read                                                    |
| ----------------------------- | ------------------------------------------------------------------- | --------------------------------------------------------------- |
| `WorldPipeline.java`          | Orchestrator; owns the `volatile PipelineSession`, coarse-slice API | Pipeline entry, session swap on reload, coarse tensor access    |
| `CoarseStage.java`            | 20-step DPM-Solver++ → coarse climate/elevation tensor              | Coarse diffusion; needs manual `pipelineTest` on change         |
| `LatentStage.java`            | 2 flow-matching steps → latent tensor                               | Latent stage math; manual `pipelineTest` on change              |
| `DecoderStage.java`           | 1 flow-matching step + fused post-process → relief residual         | Decoder stage math; manual `pipelineTest` on change             |
| `ClimateProvider.java`        | Windowed lapse-rate regression over coarse → `getClimate(x,z,elev)` | Climate sampling from coarse tensor                             |
| `PipelineSession.java`        | Immutable `record(seed, syntheticMapFactory, tau)` snapshot         | MUST-1 reload-race; adding session-scoped state                 |
| `EDMScheduler.java`           | Diffusion schedule + step update (shared helper)                    | Noise schedule, step coefficients                              |
| `SyntheticMapFactory.java`    | Seed-derived synthetic conditioning map (needs ONNX assets)         | Conditioning map generation                                    |
| `WorldPipelineModelConfig.java` | Loads model constants from `world_pipeline_config.json`           | Swapping models without recompiling, means/stds/resolution      |
