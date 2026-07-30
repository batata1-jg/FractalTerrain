# ml/

## Overview

Owns the ONNX diffusion pipeline (`pipeline/`), the model-loading lifecycle (`models/`), and
seed-deterministic Gaussian noise (`tensorProviders/`). `WorldPipeline` is constructed once and lives for
the JVM's lifetime — it outlives any single world load/unload cycle, unlike the per-world provider graph
in `GenerationContext`.

## Design Decisions

**Model load runs from `init()`, not a static initializer (MUST-2).** `FractalTerrainInstance.initPipeline()`
calls `PipelineModels.load()` (starts a background daemon thread) then `PipelineModels.awaitLoad()` (blocks
on a `CountDownLatch`) explicitly, rather than loading models in a `static { }` block or static field
initializer. This is a deliberate rejection of the more obvious static-init approach: a failure during
static initialization (e.g. missing datapack/model assets) throws `ExceptionInInitializerError`, which the
JVM converts to a permanent `NoClassDefFoundError` on every subsequent reference to the class for the rest
of the JVM's life — unrecoverable without a restart. Routing the load through an explicit method call means
a load failure surfaces as an ordinary `IllegalStateException` from `init()`, which the caller can catch,
log, and potentially retry.

## Invariants

- Model loading must be triggered from `init()` (or equivalent explicit call), never from static
  initialization of any class in `ml/models/`. See `models/README.md` for the mechanism.
