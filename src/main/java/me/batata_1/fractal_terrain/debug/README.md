# debug/

## Overview

`Debug.getLogger` is the intended logging entry point for the whole codebase
(`LoggerFactory.getLogger("fractal_terrain/" + clazz.getName())`), plus PNG/TIFF visualizers and manual
`main()` debug harnesses.

## Design Decisions

**The logging facade is not universally adopted.** `Debug.getLogger` is the intended convention, but
adoption is roughly even across the codebase: about as many classes call `LoggerFactory.getLogger`
directly (`ModConfig`, `ChannelElevationAssigner`, `LocalDrainageTracer`,
`ImmutableRTree`, `PipelineModels`, most `debug/tests/` harnesses including `GlobalRiverTest`,
`RiverTest`, `MeandersTest`, and `SpatialIndexBenchmark`, …) as go through the facade. The migration
that introduced `Debug.getLogger` moved files to it opportunistically rather than exhaustively, so its
presence is not a reliable signal of a file's age or importance. Do not assume a class uses the facade —
check its own logger field initializer before assuming logging behavior (log name prefix, in particular)
matches the facade's `"fractal_terrain/" + className` convention. New code should prefer
`Debug.getLogger`.
