# profile/

Turns the hydrological-primitive index into carve/paint operations consumed by `world/gen/`; see `README.md`
for the shell/bed split across the two lattices and the bed-depth limitation.

## Files

| File                          | What                                                                                          | When to read                                                              |
| ----------------------------- | ---------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| `README.md`                   | The one merge law both lattices share, where each carve call site sits, the `ZoneCategory` reserved-not-live note, bed-depth limitation | Onboarding to carving, changing stage responsibilities                     |
| `RiverInfluenceCarve.java`    | The stateless carve: `computeRiverGrid` (the LUT-backed lattice merge both the padded-tile shell and the per-chunk bed run), `carveRiverInfluence` wrapping it for the shell, and the thread-local `GridBuffers` each call site sizes | Carving the valley shell or the bed, the merge law, buffer sizing, adding a call site |
| `HydrologyProfileInprinter.java` | The one `RiverProvider`-bound member: `prefetchChunk`, the single per-chunk influence query `PopulateNoiseStep` runs before the bed carve. Holds no carve math — that is in `RiverInfluenceCarve`, kept apart so the `providers` import here cannot cycle back onto it | Chunk-time prefetch, the provider→carve dependency direction |
| `HydrologyProfile.java`       | The extension point: `shellElevation` is the carve half of its contract, `riverPaintDepth` the paint half — no zone radius, selection or weight member | Adding a feature type's carve behaviour                                    |
| `SurfaceMaterial.java`        | The Minecraft-free material tokens a profile paints in, plus `DEFER` for a depth left to the vanilla rules | Adding a material a profile can ask for, tracing where a river's blocks are decided |
| `RosgenProfile.java`          | Per-Rosgen-type profile: floodplain length, bed residual, floodplain blend, valley falloff, plus `sampleCrossSection` tabulating that cross-section into the lattice carve's LUT, plus the per-type bed and floodplain material columns `riverPaintDepth` tabulates. Only `A` overrides everything a type needs; `DA` overrides nothing | Bed/floodplain elevation laws, per-type tuning, cross-section LUT tabulation |
| `RadialProfile.java`          | The two radial shape laws: the confluence's parabolic bowl and the source's cone; the radial twin of `RosgenProfile` | Adding/tuning a radially-carved feature's shape, changing the bowl or cone law |
| `DefaultProfile.java`         | The all-defaults profile a feature type uses before it has one of its own                     | Adding a feature type that has no cross-section yet                        |
| `ZoneCategory.java`           | Carve-zone priority enum. **Reserved, not live** — no carve path reads it; the carve blends across every contributing primitive by distance weight, never by zone priority | Giving a feature type a real profile, wiring zone-priority merging         |
| `HydrologyProfilePainter.java`| Water-top and channel-membership queries for chunk fill                                       | Painting river surfaces during chunk fill                                  |
