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

| Directory                                  | What                                                              | When to read                                  |
| ------------------------------------------ | ---------------------------------------------------------------- | --------------------------------------------- |
| `data/fractal_terrain/worldgen/`           | Worldgen JSON: `noise_settings`, `world_preset`                  | Editing generation shape, world preset        |
| `data/fractal_terrain/dimension_type/`     | Overworld dimension-type JSON (height/build limits)              | Changing world height range                    |
| `data/minecraft/tags/worldgen/`            | Vanilla worldgen tag overrides (world-preset tags)               | Registering the preset into vanilla selection  |
| `assets/fractal_terrain/`                  | `lang/`, `icon.png`, `ml_util/fuzed.onnx` (bundled post-process) | Translations, mod icon, bundled ONNX helper    |
