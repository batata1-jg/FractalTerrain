# profile/

Turns the hydrological-unit index into carve/paint operations: a tile-level valley/floodplain shell
carve run inside `LocalRiverProvider.buildTile`, plus a per-pixel bed-residual carve/paint consumed by
`world/gen/`. The per-pixel bed stage is currently inert — see `HydrologyProfile.java`.

## Files

| File                          | What                                                                                                                                                  | When to read                                                              |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| `HydrologyProfileCarver.java` | Two carve stages: static `carveRiverShells` (tile-level shell, nearest-unit, in-place) + per-pixel bed-residual carve (`carve`/`carveAtPixel`/`carvePrefetched`) | Carving the valley/floodplain shell, chunk-time carve entry points        |
| `RosgenProfile.java`          | Cross-channel profile by Rosgen type: shell elevation lerp, bed residual, floodplain/influence extents. Only type `A` overrides anything                | Shell/bed elevation laws, floodplain and influence radii, per-type tuning |
| `HydrologyProfile.java`       | `computeForUnit`: per-unit bed-residual delta. **Currently a no-op** — body commented out                                                              | Per-pixel bed cross-section math, re-enabling the bed stage               |
| `HydrologyProfilePainter.java`| Water-top and channel-membership queries for chunk fill. Places no water while the bed stage is inert                                                  | Painting river surfaces/banks during chunk fill                          |
