# profile/

The paint half of the hydrology pipeline: what a carved column looks like. See `../carvers/` for the
carve itself — the lattice merge, the dispatch seam, the merge law.

## Files

| File                          | What                                                                                          | When to read                                                              |
| ----------------------------- | ---------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| `README.md`                   | The paint contract, per-Rosgen-type material columns, `RadialProfile`'s three shapes, `ZoneCategory`'s reserved-not-live note, bed-depth limitation | Onboarding to painting, changing per-type materials or shape laws          |
| `HydrologyProfile.java`       | The extension point: `shellElevation` is legacy/unused by the current carve, `riverPaintDepth` the paint half — no zone radius, selection or weight member | Adding a feature type's paint behaviour                                    |
| `SurfaceMaterial.java`        | The Minecraft-free material tokens a profile paints in, plus `DEFER` for a depth left to the vanilla rules | Adding a material a profile can ask for, tracing where a river's blocks are decided |
| `RosgenProfile.java`          | Per-Rosgen-type profile: floodplain length, bed residual, floodplain blend, valley falloff, plus `sampleCrossSection` tabulating that cross-section into the lattice carve's LUT, plus the per-type bed and floodplain material columns `riverPaintDepth` tabulates. Only `A` overrides everything a type needs; `DA` overrides nothing | Bed/floodplain elevation laws, per-type tuning, cross-section LUT tabulation |
| `RadialProfile.java`          | The three radial shape laws (confluence bowl, source cone, abandoned-river shallower bowl) and the two authored band control points (`MARGIN_NORM`, `FLOOD_PLAIN_NORM`) the bed carve bands a disc's distance against; the radial twin of `RosgenProfile` | Adding/tuning a radially-carved feature's shape, changing the bowl or cone law, changing where a disc's bed/floodplain edge sits |
| `DefaultProfile.java`         | The all-defaults profile a feature type uses before it has one of its own                     | Adding a feature type that has no cross-section yet                        |
| `ZoneCategory.java`           | Carve-zone priority enum. **Reserved, not live** — no carve path reads it; the carve blends across every contributing primitive by distance weight, never by zone priority | Giving a feature type a real profile, wiring zone-priority merging         |
| `HydrologyProfilePainter.java`| Water-top and channel-membership queries for chunk fill                                       | Painting river surfaces during chunk fill                                  |
