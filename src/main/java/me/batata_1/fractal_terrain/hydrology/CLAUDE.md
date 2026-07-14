# hydrology/

River-network tracing, carving, and per-tile hydrology providers. `LocalRiverProvider` orchestrates a
dual-store cache (spatial index of hydrological units + carved-elevation tensor); the deterministic math
is factored into the builder/tracer helpers. See repo-root `ARCHITECTURE.md` "Hydrology split" and
"Coordinate frames".

## Files

| File                          | What                                                                             | When to read                                                        |
| ----------------------------- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| `LocalRiverProvider.java`     | Thin orchestrator over the 512-native-px tile dual-store cache (`buildTile`)     | Local river/carve output, tile caching, test override injection     |
| `GlobalRiverProvider.java`    | Coarse-px global river network, caches its own 64×64-coarse-px tiles             | Global network, coarse-frame addressing, `computeTileForTest`       |
| `GlobalNetworkBuilder.java`   | Traces/relaxes/beds the global network inside a tile (functional, per-tile)      | Global-network trace math, coarse↔native tile mapping               |
| `ChannelElevationAssigner.java` | Bed-elevation propagation for a traced network                                 | Channel bed elevations, downstream propagation                      |
| `LocalDrainageTracer.java`    | Detailed local network off the drainage field (functional, per-tile)            | Local drainage tracing, `traceLocalNetworkForTest`                  |
| `PipelinePreprocessing.java`  | Sink-fill, drainage direction, flow accumulation (shared low-level helpers)     | Drainage/flow preprocessing math                                    |
| `HydrologyTileGeometry.java`  | Shared tile-frame geometry (`GRID=512`, `PAD=1`, `PADDED=514`, `COARSE_PX=256`) | Tile origins, padding, frame conversions used by all three helpers  |
| `HydrologicalUnit.java`       | Record for one hydrological-unit entry in the spatial index                     | Reading/extending the units index                                   |
| `ChannelGeometry.java`        | Lower-level channel-geometry helper                                             | Channel width/shape geometry                                        |

## Subdirectories

| Directory   | What                                                        | When to read                                        |
| ----------- | ---------------------------------------------------------- | --------------------------------------------------- |
| `meanders/` | Meander relaxation network (`Meanders`, `RiverNetwork`)    | Meander geometry, per-tile network relaxation       |
| `profile/`  | Turns the hydrological-unit index into carve/paint ops      | Per-pixel carve/paint consumed by `world/gen/`      |
