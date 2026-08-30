# resources/

Mod metadata, mixin/accesswidener config, worldgen datapack JSON, and bundled assets.

## Files

| File                              | What                                                                | When to read                                    |
| --------------------------------- | ------------------------------------------------------------------- | ----------------------------------------------- |
| `fabric.mod.json`                 | Fabric mod manifest: entrypoints, mixins, dependencies              | Adding an entrypoint, dependency, or mixin file |
| `fractal_terrain.mixins.json`     | Mixin registration (main source set)                                | Registering a new mixin class                   |
| `fractal_terrain.accesswidener`   | Access-widener rules for vanilla worldgen internals                 | Widening access to a vanilla member             |
| `terrain-diffusion-mc.properties` | Runtime config `.properties` read by `ModConfig`                    | Changing default runtime flags/scalars          |

## Subdirectories

| Directory | What                                                          | When to read                                              |
| --------- | ------------------------------------------------------------- | --------------------------------------------------------- |
| `data/`   | Datapack JSON under `fractal_terrain/`, `minecraft/`, `terrablender/` — see the table below | Editing generation shape, world preset, dimension bounds, tags |
| `assets/` | Client assets under `fractal_terrain/`: `lang/en_us.json`, `icon.png`, `ml_util/fuzed.onnx` (bundled post-process model) | Translations, mod icon, bundled ONNX helper                |

### Datapack files

| File                                                          | What                                                                  | When to read                                   |
| ------------------------------------------------------------- | --------------------------------------------------------------------- | ---------------------------------------------- |
| `data/fractal_terrain/worldgen/noise_settings/fractal_terrain.json` | Noise settings: sea level, noise router slots, surface rule           | Editing generation shape, surface banding      |
| `data/fractal_terrain/worldgen/world_preset/fractal_terrain.json`   | World preset bundling the overworld dimension                        | Changing what the preset ships                 |
| `data/fractal_terrain/dimension_type/overworld.json`          | Overworld dimension type (height/build limits)                        | Changing world height range                    |
| `data/minecraft/tags/worldgen/world_preset/normal.json`       | `replace: true` override of vanilla's `normal` tag — re-lists the vanilla presets plus `fractal_terrain` | Registering the preset into vanilla world-type selection, or restoring a preset the override dropped |
| `data/terrablender/tags/dimension_type/overworld_regions.json` | Opts `fractal_terrain:overworld` into TerraBlender region-based biome placement | Changing which dimensions TerraBlender manages |
