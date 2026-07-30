# models/

## Overview

Wraps ONNX Runtime model loading and asset management for the four models `WorldPipeline` consumes
(coarse, base, decoder, fuzed).

## Design Decisions

**Background-thread load from `init()`, not a static initializer (MUST-2).** `PipelineModels.load()`
starts a daemon thread (`"terrain-diffusion-models"`) that constructs the `PipelineModels` singleton
(loading all four ONNX models) and returns immediately; `awaitLoad()` blocks the calling thread on a
`CountDownLatch` until that background thread finishes or fails. `FractalTerrainInstance.initPipeline()`
calls `load()` then `awaitLoad()` explicitly from `init()`.

The background thread exists so the game/server thread is not blocked for the full model-load duration.
Routing the load through an explicit method rather than a static initializer means a load failure (missing
datapack, missing/corrupt model assets) surfaces as a normal `IllegalStateException` thrown from
`awaitLoad()`, which `init()`'s caller can catch and handle. A static-initializer failure would instead
throw `ExceptionInInitializerError`, which the JVM turns into a permanent `NoClassDefFoundError` on every
future reference to `PipelineModels` — there is no recovery short of restarting the JVM.

`load()` and `awaitLoad()` are both idempotent/re-entrant: `load()` no-ops if a load already
started-or-finished (`INSTANCE != null || loadStarted`), and `awaitLoad()` triggers `load()` itself if
nobody called it yet, so callers do not need to coordinate who calls which first.

## Invariants

- Do not load models (construct `OnnxModel`/`PipelineModels`) from a static initializer or static field
  initializer anywhere in this package — always route through `PipelineModels.load()`/`awaitLoad()`, called
  from an explicit lifecycle method (`init()`).
- `close()` only tears down the singleton if `INSTANCE == this` (guards against a stale/racing instance
  closing a newer one); after `close()`, `loadStarted` and `loadFailure` are reset so a subsequent
  `load()` can run again cleanly.
