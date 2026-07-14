# profile/

Turns the hydrological-unit index into carve/paint operations: a tile-level valley/floodplain shell
carve (two passes — global units, then local units — on the shared buffer, both within the single
once-per-tile `LocalRiverProvider.buildTile` flow) plus a per-pixel bed-residual carve/paint consumed
by `world/gen/`.

## Files

| File                          | What                                                       | When to read                                     |
| ----------------------------- | ---------------------------------------------------------- | ------------------------------------------------ |
| `HydrologyProfileCarver.java` | Two carve stages: static `carveRiverShells` (tile-level shell, min-composite) + per-pixel bed-residual carve (`carve`/`carveAtPixel`/`carvePrefetched`) | Carving the valley/floodplain shell, carving the per-pixel bed trench |
| `HydrologyProfilePainter.java`| Paints per-pixel hydrology (water/bank) blocks             | Painting river surfaces/banks during chunk fill  |
| `HydrologyProfile.java`       | `computeForUnit`: per-unit bed-residual delta anchored on the already-carved shell | Per-pixel bed cross-section math |
| `RosgenProfile.java`          | Cross-channel profile by Rosgen type: shell floor (bank − freeboard), lens mask, bed residual (shell − depth) | Shell/bed elevation laws, lens-mask geometry, floodplain/influence extents |
