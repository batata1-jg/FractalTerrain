# profile/

Turns the hydrological-unit index into carve/paint operations: a tile-level valley/floodplain shell
carve run inside `LocalRiverProvider.buildTile`, plus a per-pixel bed-residual carve/paint consumed by
`world/gen/`. The per-pixel bed stage runs, but its trench depth is a hard-coded constant rather than a
cross-section — see `RosgenProfile.riverAreaDelta`.

## Files

| File                          | What                                                                                                                                                  | When to read                                                              |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| `HydrologyProfileCarver.java` | Two carve stages: static `carveRiverShells` (tile-level shell, nearest-unit, in-place) + per-pixel bed-residual carve (`carve`/`carveAtPixel`/`carvePrefetched`) | Carving the valley/floodplain shell, chunk-time carve entry points        |
| `RosgenProfile.java`          | Cross-channel profile by Rosgen type: shell elevation lerp, bed residual, floodplain/influence extents. Only type `A` overrides anything                | Shell/bed elevation laws, floodplain and influence radii, per-type tuning |
| `HydrologyProfile.java`       | `computeForUnit`: per-unit bed-residual delta, faded in over an elliptical footprint around the unit                                                   | Per-pixel bed cross-section math, the elliptical fade weight             |
| `HydrologyProfilePainter.java`| Water-top and channel-membership queries for chunk fill. Fills from the `RIVER_DIFFERENCE` heightmap the bed stage writes                              | Painting river surfaces/banks during chunk fill                          |
