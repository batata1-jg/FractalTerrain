# docs/

Offline mirror of Minecraft Wiki world-generation pages, converted to Markdown. Reference material
only — nothing here is FractalTerrain's own documentation.

**Scraped content — never edit directly.** Regenerate instead (see Regenerate below). Every file is
verbatim wiki prose; local edits are lost on the next scrape and diverge from the upstream revision
recorded in each file's header.

**Version caveat:** the wiki documents the *latest* Minecraft release. FractalTerrain targets **Java
Edition 1.20.1**. Each file carries a `⚠ Post-1.20.1 changes` section listing that page's own
History entries newer than 1.20.1 — read it before trusting the body. The `Δ` column below is the
count of those entries; a high count means the page body substantially describes behaviour the mod's
target version does not have. `Δ 0` means the page's History records nothing after 1.20.1, not that
the page is guaranteed accurate.

## Files

### Tooling

| File        | What                                                              | When to read                                    | Δ |
| ----------- | ----------------------------------------------------------------- | ----------------------------------------------- | - |
| `scrape.py` | The scraper that produced every `.md` here; holds the page list    | Adding/removing a page, re-running the scrape   | - |

### Pipeline and terrain

| File                         | What                                                                | When to read                                                        | Δ  |
| ---------------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------- | -- |
| `world-generation.md`        | End-to-end vanilla generation: stages, biome climate parameters, order | Understanding how generation phases fit together, tracing stage order | 0  |
| `custom-world-generation.md` | Entry point for data-pack worldgen; index of the JSON file types     | Orienting in the data-pack format, finding which file controls what  | 0  |
| `terrain-features.md`        | Catalogue of naturally generated terrain shapes and formations       | Identifying a landform, naming terrain output                        | 0  |
| `chunk.md`                   | Chunk concept, generation stages, ticking and loading behaviour      | Working with chunk lifecycle, generation status, loading             | 6  |
| `chunk-format.md`            | On-disk NBT chunk layout, sections, heightmaps, generation status    | Reading/writing region data, debugging saved output                  | 18 |
| `heightmap.md`               | The heightmap types and what each one records                        | Querying surface height, choosing a heightmap type                   | 0  |

### Noise and density

| File                             | What                                                                    | When to read                                                          | Δ |
| -------------------------------- | ------------------------------------------------------------------------ | --------------------------------------------------------------------- | - |
| `noise-settings.md`              | `worldgen/noise_settings` JSON: sea level, aquifers, ore veins, noise router, surface rule | Configuring terrain shape, replacing overworld generation | 2 |
| `density-function.md`            | All density function types and their JSON fields; the core math layer    | Building/reading density functions, understanding terrain shaping      | 6 |
| `noise-router.md`                | The named density-function slots the generator reads                     | Wiring density functions into generation, finding the right slot       | 1 |
| `noise.md`                       | `worldgen/noise` normal-noise parameter files                            | Defining noise octaves/amplitudes                                      | 0 |
| `surface-rule.md`                | Surface rule and surface condition decision tree; all types              | Controlling which block caps the terrain, grass/dirt/badlands banding  | 2 |
| `configured-surface-builder.md`  | **Legacy** pre-1.18 surface builders, superseded by surface rules        | Reading pre-1.18 packs or historical context only                      | 0 |

### Biomes

| File                 | What                                                                | When to read                                                     | Δ  |
| -------------------- | -------------------------------------------------------------------- | ---------------------------------------------------------------- | -- |
| `biome.md`           | Every vanilla biome with climate values, mobs, features, colours      | Looking up a specific biome's properties or generation           | 4  |
| `biome-definition.md`| `worldgen/biome` JSON format: effects, spawners, carvers, features    | Authoring a custom biome, reading biome JSON                     | 9  |
| `biome-tag.md`       | Vanilla biome tags and their members                                 | Gating features/structures by biome tag, resolving `#minecraft:` tags | 12 |

### Features and carvers

| File                    | What                                                                       | When to read                                                       | Δ  |
| ----------------------- | --------------------------------------------------------------------------- | ------------------------------------------------------------------ | -- |
| `feature.md`            | What features are; the decoration stages they run in                        | Understanding feature placement order and stages                   | 0  |
| `configured-feature.md` | Every feature type with full JSON config (largest file here, ~344 KB)       | Configuring ore/tree/lake/spring/vegetation features               | 24 |
| `placed-feature.md`     | Placement modifier types controlling where a configured feature is tried    | Controlling feature count, height, rarity, filtering               | 7  |
| `carver-definition.md`  | `worldgen/configured_carver` JSON: cave and canyon carvers                  | Carving caves/ravines, tuning carver parameters                    | 5  |
| `cave.md`               | Cave and aquifer generation, noise caves vs carver caves                    | Cave/aquifer behaviour, water table interaction                    | 1  |
| `ore.md`                | Ore distribution and vein generation per ore type                           | Ore placement, distribution curves                                 | 1  |
| `riverUnit.md`              | River biome: generation, terrain shape, variants                            | **Hydrology work** — vanilla riverUnit behaviour as a baseline         | 1  |

### Structures

| File                      | What                                                          | When to read                                          | Δ |
| ------------------------- | -------------------------------------------------------------- | ----------------------------------------------------- | - |
| `structure.md`            | Overview of generated structures and where each occurs         | Structure behaviour, which biome hosts what           | 1 |
| `structure-definition.md` | `worldgen/structure` JSON for every structure type            | Authoring/reading a structure definition              | 8 |
| `structure-set.md`        | Placement/spacing/spread of structures across the world        | Controlling structure frequency and distribution      | 0 |
| `template-pool.md`        | Jigsaw template pool JSON                                      | Building jigsaw structures                            | 0 |
| `processor-list.md`       | Structure block processors (rules, replacement, weathering)    | Post-processing placed structure blocks               | 0 |
| `jigsaw-block.md`         | Jigsaw block mechanics and fields                              | Jigsaw connection semantics                           | 2 |

### Dimensions and presets

| File                          | What                                                          | When to read                                              | Δ  |
| ----------------------------- | -------------------------------------------------------------- | --------------------------------------------------------- | -- |
| `dimension-definition.md`     | `dimension/` JSON: generator + biome source wiring            | Registering a dimension, choosing a biome source          | 0  |
| `dimension-type.md`           | `dimension_type/` JSON: height, min_y, logical height, effects | Setting world height bounds and dimension properties      | 11 |
| `world-preset-definition.md`  | World preset JSON bundling dimensions                          | Shipping a selectable world type                          | 0  |
| `superflat.md`                | Superflat layer/preset format                                  | Flat-world configuration                                  | 0  |
| `old-customized.md`           | **Removed** pre-1.13 customized generator and its parameters   | Historical reference for terrain parameter design only    | 0  |

### Seeds and packaging

| File                       | What                                                     | When to read                                          | Δ  |
| -------------------------- | -------------------------------------------------------- | ----------------------------------------------------- | -- |
| `world-seed.md`            | How seeds are parsed and used to derive generation randomness | Seed handling, determinism, reproducing a world   | 0  |
| `anomalous-world-seeds.md` | Known degenerate/special-case seeds                      | Picking test seeds, explaining odd generation         | 0  |
| `data-pack.md`             | Data pack layout, pack format versions, load order       | Packaging worldgen JSON, resolving pack_format issues | 21 |

## Regenerate

Requires `py` with `markdownify` and `beautifulsoup4`:

```
py -m pip install markdownify beautifulsoup4
py docs/scrape.py docs
```

Responses are cached under `cache/` (relative to the working directory); delete it to force a refetch.
Edit the `PAGES` list in `scrape.py` to add or remove pages. Re-scraping rewrites every file and bumps
each recorded revision id, so regenerate all pages at once.
