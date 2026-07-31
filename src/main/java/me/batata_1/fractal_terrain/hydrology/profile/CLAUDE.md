# profile/

Turns the hydrological-unit index into carve/paint operations consumed by `world/gen/`; see `README.md`
for the two carve stages and the bed-depth limitation.

## Files

| File                          | What                                                                                                                                                  | When to read                                                              |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| `HydrologyProfileCarver.java` | Two carve stages: static `carveRiverShells` (tile-level shell, nearest-unit, in-place) + per-pixel bed-residual carve (`carve`/`carveAtPixel`/`carvePrefetched`) | Carving the valley/floodplain shell, chunk-time carve entry points        |
| `RosgenProfile.java`          | Cross-channel profile by Rosgen type: shell elevation lerp, bed residual, floodplain/influence extents. Only type `A` overrides anything                | Shell/bed elevation laws, floodplain and influence radii, per-type tuning |
| `HydrologyProfile.java`       | `computeForUnit`: per-unit bed-residual delta, faded in over an elliptical footprint around the unit                                                   | Per-pixel bed cross-section math, the elliptical fade weight             |
| `HydrologyProfilePainter.java`| Water-top and channel-membership queries for chunk fill. Fills from the `RIVER_DIFFERENCE` heightmap the bed stage writes                              | Painting riverUnit surfaces/banks during chunk fill                          |
