# profile/

Turns the hydrological-primitive index into carve/paint operations consumed by `world/gen/`; see `README.md`
for the two carve stages and the bed-depth limitation.

## Files

| File                          | What                                                                                          | When to read                                                              |
| ----------------------------- | ---------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| `README.md`                   | The two carve stages, the `ZoneCategory` reserved-not-live note, bed-depth limitation         | Onboarding to carving, changing stage responsibilities                     |
| `HydrologyProfileInprinter.java` | Shell carve (`carveRiverShells`) and the chunk-level primitive prefetch (`prefetchChunk`) that feeds the per-pixel blend in `PopulateNoiseStep`; also hosts `sampleNearestChannel`, which has no `src/main` caller | Carving the valley shell, chunk-time prefetch, checking `sampleNearestChannel`'s live status |
| `HydrologyProfile.java`       | The extension point: `shellElevation` only — the zone radii/selection/weight members were removed with the zone-priority merge | Adding a feature type's carve behaviour                                    |
| `RosgenProfile.java`          | Per-Rosgen-type profile: floodplain length, bed residual, floodplain blend, valley falloff. Only `A` overrides everything a type needs; `DA` overrides nothing | Bed/floodplain elevation laws, per-type tuning                             |
| `DefaultProfile.java`         | The all-defaults profile a feature type uses before it has one of its own                     | Adding a feature type that has no cross-section yet                        |
| `ZoneCategory.java`           | Carve-zone priority enum. **Reserved, not live** — no carve path reads it; both carve stages now blend across every contributing primitive by weight instead of zone priority | Giving a feature type a real profile, reviving zone-priority merging       |
| `HydrologyProfilePainter.java`| Water-top and channel-membership queries for chunk fill                                       | Painting river surfaces during chunk fill                                  |
