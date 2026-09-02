# hydrology/

River-network tracing, carving, and per-tile hydrology providers; see `README.md` for the primitives
cache design and coordinate frames.

## Files

| File                            | What                                                                                       | When to read                                                       |
| ------------------------------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------ |
| `README.md`                     | Two-store cache design + memo, coordinate frames, the `buildTile` ordering                 | Onboarding to hydrology, changing tile build order or frames        |
| `GlobalNetworkBuilder.java`     | Traces/relaxes the global network inside a tile, assigns and shell-carves it into its own elevation clone, and returns the graph, the drainage field computed over that clone, the boundary-elevation map, the Rosgen typer and the clone itself | Global-network trace math, coarse↔native tile mapping, why the drainage field sees valleys |
| `LocalNetworkBuilder.java`      | Local-trace half of the tile pipeline, symmetric with `GlobalNetworkBuilder`: seeds boundary elevations, assigns, shell-carves a private clone, then traces the local network off `GlobalNetworkBuilder`'s drainage field against that carved clone and attaches it in place. Returns `void` — the clone is scratch, and the published elevation comes from `RiverProvider.carveRivers` | Local-network trace math, why the local trace walks a carved surface |
| `LocalDrainageTracer.java`      | Traces the local network off the drainage field and attaches it in place onto the same graph | Local drainage tracing, attach/drop rules, `traceLocalNetworkForTest` |
| `ChannelElevationAssigner.java` | Three-phase bed-elevation propagation; one tile build runs it four times (global, local, and twice inside `RiverProvider.carveRivers` — once before the published carve and once against it) | Channel bed elevations, downstream propagation, topology failures   |
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
