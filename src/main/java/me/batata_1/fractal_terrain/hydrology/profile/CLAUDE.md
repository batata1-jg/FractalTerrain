# profile/

Turns the hydrological-primitive index into carve/paint operations consumed by `world/gen/`; see `README.md`
for the two carve stages and the bed-depth limitation.

## Files

| File                          | What                                                                                          | When to read                                                              |
| ----------------------------- | ---------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| `README.md`                   | The two carve stages, zone resolution, bed-depth limitation                                   | Onboarding to carving, changing stage responsibilities                     |
| `HydrologyProfileInprinter.java` | Both carve stages: the tile-level shell carve and the per-pixel bed refinement              | Carving the valley shell, chunk-time carve entry points, zone merging      |
| `NearestChannelSample.java`   | One channel's cross-section at a point, sampled at the centreline foot; carves itself in     | Changing bed/rim geometry, debugging one-distance-per-channel carve        |
| `HydrologyProfile.java`       | The extension point: zone radii, zone selection, zone weight, shell elevation                 | Adding a feature type's carve behaviour, per-zone weighting                |
| `RosgenProfile.java`          | Per-Rosgen-type profile: shell lerp, bed residual, floodplain and influence extents. Only `A` overrides anything | Shell/bed elevation laws, floodplain and influence radii, per-type tuning |
| `DefaultProfile.java`         | The all-defaults profile a feature type uses before it has one of its own                     | Adding a feature type that has no cross-section yet                        |
| `ZoneCategory.java`           | Carve-zone priority enum. **Reserved, not live** — no carve path reads it since the single-nearest-channel carve replaced the zone merge | Giving a feature type a real profile, reviving zone-priority merging       |
| `HydrologyProfilePainter.java`| Water-top and channel-membership queries for chunk fill                                       | Painting river surfaces during chunk fill                                  |
