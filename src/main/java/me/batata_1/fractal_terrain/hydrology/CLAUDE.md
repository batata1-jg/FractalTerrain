# hydrology/

River-network tracing, carving, and per-tile hydrology providers; see `README.md` for the primitives
cache design and coordinate frames.

## Files

| File                            | What                                                                                       | When to read                                                       |
| ------------------------------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------ |
| `README.md`                     | Two-store cache design + memo, coordinate frames, the `buildTile` ordering                 | Onboarding to hydrology, changing tile build order or frames        |
| `GlobalNetworkBuilder.java`     | Traces/relaxes the global network inside a tile; returns the `RiverNetwork`, the pre-carve elevation snapshot, and the boundary-elevation map | Global-network trace math, coarse↔native tile mapping |
| `LocalNetworkBuilder.java`      | Local-trace half of the tile pipeline, symmetric with `GlobalNetworkBuilder`: local drainage trace, boundary-elevation seeding, the three `ChannelElevationAssigner.assign` passes, the shell carve between the first two, and the bed-elevation primitive refresh + bed carve after the third; returns the shell-and-bed-carved padded elevation | Local-network trace math, carve ordering across the three assign passes |
| `LocalDrainageTracer.java`      | Traces the local network off the drainage field and attaches it in place onto the same graph | Local drainage tracing, attach/drop rules, `traceLocalNetworkForTest` |
| `ChannelElevationAssigner.java` | Three-phase bed-elevation propagation; `buildTile` runs it three times per tile             | Channel bed elevations, downstream propagation, topology failures   |
| `Drainage.java`                 | Sink-fill, D8/D4 drainage direction, flow accumulation, `FlowGraph` routing topology        | Drainage/flow math; the shared upstream→downstream walk             |
| `HydrologyTileGeometry.java`    | Shared tile-frame geometry (`GRID=512`, `PAD=1`, `PADDED=514`, `COARSE_PX=256`)             | Tile origins, padding, frame conversions used by all three helpers  |
| `ChannelGeometry.java`          | Width-to-depth law, bed half-width, channel-overlap test                                    | Channel width/shape geometry, `W_REF` calibration                   |

## Subdirectories

| Directory   | What                                                     | When to read                                   |
| ----------- | -------------------------------------------------------- | ---------------------------------------------- |
| `features/` | `HydrologicalPrimitive` interface + the per-feature records and their codec | Reading/extending the primitives index, adding a feature type, primitive persistence |
| `network/`  | The graph itself: `RiverNetwork`, `Channel`, `Endpoint`, `AtomicView`, `ChannelTyper` | Graph topology, the canonical↔atomic seam, stream capture, per-point flow |
| `meanders/` | Point-migration models driven over the injected network (`ChannelMigrator`, `Meanders`, `GradientNetworkRelaxation`) | Meander geometry, gradient relaxation, step ordering |
| `providers/`| `RiverProvider` + `GlobalRiverProvider`: the package's two `Storage`-backed tile caches | Riverprimitive/carved-elevation output, the global coarse network, tile caching, test overrides |
| `profile/`  | Turns the hydrological-primitive index into carve/paint ops   | Per-pixel carve/paint consumed by `world/gen/` |
| `rosgen/`   | Rosgen Level-I classification of each reach from the raw elevation | Stream types, reach slope/entrenchment measurement, the decision key |
