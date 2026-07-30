# pipeline/

## Overview

`WorldPipeline` is the orchestrator for the diffusion inference pipeline: it owns the three diffusion
stages (`CoarseStage`, `LatentStage`, `DecoderStage`) plus `ClimateProvider`, all extracted from a
previously-monolithic `WorldPipeline` into separate collaborators (M-009). Each collaborator is
constructor-injected with its upstream model(s), a `Supplier<PipelineSession>` bound to
`WorldPipeline.currentSession()`, and (for `LatentStage`/`DecoderStage`) the upstream tensor it consumes.

## Architecture

`WorldPipeline` holds the reload-scoped state behind one field, `private volatile PipelineSession session`,
and exposes it to every stage only through the `currentSession()` supplier — no stage reads `seed`,
`syntheticMapFactory`, or `tau` as independent fields. `updateInstance` (a world reload / seed change)
builds a whole new `PipelineSession` and assigns it to `session` in a single write.

## Design Decisions

**One immutable `PipelineSession` snapshot per tile/batch, not three independent volatile fields
(MUST-1).** Before this design, `WorldPipeline` held `seed`, `syntheticMapFactory`, and `tau` as three
separate `volatile` fields, and a reload (`updateInstance`) rewrote them in sequence. A worker thread
computing a tile read the three fields independently; if a reload landed between two of those reads, the
worker could observe a torn combination — e.g. an old `syntheticMapFactory` paired with a new `seed`'s
noise draw, since the synthetic map is only valid for the exact seed it was derived from.

The fix bundles the three values into one immutable `record PipelineSession(seed, syntheticMapFactory,
tau)` swapped behind a single `volatile PipelineSession session` field. Each of `CoarseStage`,
`LatentStage`, and `DecoderStage` snapshots the session exactly once per tile/batch
(`final PipelineSession s = sessionSupplier.get();`) and reads all three fields from that one snapshot, so
either the whole old triple or the whole new triple is visible — never a mix. `updateInstance` publishes
the new session in one write, so no worker can observe a half-updated session.

`ClimateProvider` is deliberately the exception: it never reads `PipelineSession` at all. Climate
derivation (windowed lapse-rate regression over the coarse tensor) has no seed-dependent noise draw, so it
has no reload-scoped state to tear in the first place — there was never a bug for it to have.

Regression coverage: `src/test/java/.../ml/pipeline/PipelineSessionReloadRaceTest.java` drives the same
single-volatile-swap mechanism with a headless proxy (no real `SyntheticMapFactory`, since that needs ONNX
assets) — a writer thread reloads repeatedly while four reader threads assert `seed == tau[0]` never tears.

## Invariants

- No stage may read `seed`, `syntheticMapFactory`, or `tau` from `WorldPipeline` independently — always go
  through a `PipelineSession` snapshot taken once per tile/batch via the stage's `sessionSupplier`.
- `updateInstance` must publish a fully-constructed `PipelineSession` in a single assignment to `session`;
  never mutate an in-place session or assign its fields separately.
- Adding a new field to `PipelineSession` extends the atomicity guarantee automatically; adding a new
  reload-scoped field directly to `WorldPipeline` instead (bypassing the record) reintroduces the torn-read
  hazard MUST-1 exists to close.
