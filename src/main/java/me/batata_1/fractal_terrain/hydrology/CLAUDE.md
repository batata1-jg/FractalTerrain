# hydrology/

River-network tracing, carving, and per-tile hydrology providers; see `README.md` for the dual-store
cache design and coordinate frames.

## Files

| File                            | What                                                                                       | When to read                                                       |
| ------------------------------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------ |
| `LocalRiverProvider.java`       | Thin orchestrator over the 512-native-px tile dual-store cache; `buildTile` is the pipeline | Local riverPrimitive/carve output, tile caching, build ordering, test overrides |
| `GlobalRiverProvider.java`      | Coarse-px global riverPrimitive network, caches its own 64×64-coarse-px tiles                        | Global network, coarse-frame addressing, `computeTileForTest`      |
| `GlobalNetworkBuilder.java`     | Traces/relaxes the global network inside a tile; returns it plus its boundary-elevation map | Global-network trace math, coarse↔native tile mapping              |
| `LocalDrainageTracer.java`      | Traces the local network off the drainage field and attaches it in place onto the same graph | Local drainage tracing, attach/drop rules, `traceLocalNetworkForTest` |
| `ChannelElevationAssigner.java` | Three-phase bed-elevation propagation; `buildTile` runs it twice per tile                   | Channel bed elevations, downstream propagation, topology failures   |
| `Drainage.java`                 | Sink-fill, D8/D4 drainage direction, flow accumulation, `FlowGraph` routing topology        | Drainage/flow math; the shared upstream→downstream walk             |
| `HydrologyTileGeometry.java`    | Shared tile-frame geometry (`GRID=512`, `PAD=1`, `PADDED=514`, `COARSE_PX=256`)             | Tile origins, padding, frame conversions used by all three helpers  |
| `ChannelGeometry.java`          | Width-to-depth law, bed half-width, channel-overlap test                                    | Channel width/shape geometry, `W_REF` calibration                   |

## Subdirectories

| Directory   | What                                                     | When to read                                   |
| ----------- | -------------------------------------------------------- | ---------------------------------------------- |
| `features/` | `HydrologicalPrimitive` interface + the per-feature records and their codec | Reading/extending the primitives index, adding a feature type, primitive persistence |
| `network/`  | The graph itself: `RiverNetwork`, `Channel`, `Endpoint`, `AtomicView`, `ChannelTyper` | Graph topology, the canonical↔atomic seam, stream capture, per-point flow |
| `meanders/` | Meander relaxation over the network (`Meanders`)         | Meander geometry, per-tile network relaxation  |
| `profile/`  | Turns the hydrological-primitive index into carve/paint ops   | Per-pixel carve/paint consumed by `world/gen/` |
| `rosgen/`   | Rosgen Level-I classification of each reach from the raw elevation | Stream types, reach slope/entrenchment measurement, the decision key |
