# features/

## Overview

`HydrologicalPrimitive.h`/`w`/`d` (`HydrologicalPrimitive.java:81-85`) and every implementation
(`RiverPrimitive`, `ConfluencePrimitive`, `SourcePrimitive`, `DeltaPrimitive`, `WaterfallPrimitive`,
`OxbowLakePrimitive`, `AbandonedRiverPrimitive`) sit below this repo's hot/cold line of abstraction (root
`ARCHITECTURE.md`, "Hot/cold line of abstraction"): they are called from `PopulateNoiseStep`'s per-column
inner loop, which runs 256 times per chunk for every chunk generated, plus everything those methods call
in turn (`HydrologyProfile`/`RosgenProfile`/`DefaultProfile`, `VectorOps`).

## Invariants

- No heap allocation inside `h`/`w`/`d` or their call graph — no `new`, no boxing, no iterator/stream
  allocation.
- The `Object... args` varargs on `h`/`w` already allocates an array per call; do not add a second
  varargs or boxed-object parameter to any implementation.
- New feature types follow the same rule: an implementation with an allocating `h`/`w`/`d` degrades every
  chunk generated, not just the tiles that contain that feature type.
- Mark any allocation that looks avoidable but is intentional with `:PERF: [what]; [why]`
  (`.claude/conventions/intent-markers.md`) rather than leaving it for a reviewer to flag.
