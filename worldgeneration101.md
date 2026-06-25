# World Generation

*Converted from the [Minecraft Wiki — World generation](https://minecraft.wiki/w/World_generation) article.*

**World generation** (sometimes abbreviated **worldgen**) is the procedural generation process *Minecraft* uses to algorithmically generate terrain, biomes, and features, ultimately deciding which blocks are placed where. *Minecraft* worlds are made of 16×16-block-wide chunks stretching the full height of the dimension. Because there are more than 18 quintillion (2⁶⁴ ≈ 18,446,744,073,709,551,616) possible worlds, the game generates them using randomness, algorithms, and some manually built decorations. The benefits of procedural world generation include smaller game file size and practically infinite gameplay possibilities.

---

## Contents

1. [Randomness](#randomness)
2. [Steps](#steps)
3. [Biomes](#biomes)
   - [Overworld](#overworld-biomes)
   - [The Nether](#the-nether-biomes)
   - [The End](#the-end-biomes)
4. [Terrain](#terrain)
   - [3D noise](#3d-noise)
   - [Splines](#splines)
   - [Noise caves](#noise-caves)
   - [Aquifers](#aquifers)
   - [Ore veins](#ore-veins)
5. [Surface](#surface)
6. [Carvers](#carvers)
7. [Structures](#structures)
8. [Features](#features)
9. [Lighting](#lighting)
10. [Mob spawning](#mob-spawning)
11. [Heightmaps](#heightmaps)

---

## Randomness

To generate a different world every time, the game uses random numbers generated from a **seed**. However, pure randomness makes terrain and biomes too chaotic, with no continuity.

To solve this, the game uses gradient noise algorithms, like **Perlin noise**. This makes sure blocks and chunks fit with their neighbors, giving the world both continuity and randomness.

Even though noise looks random and continuous, using it directly still lacks variation — hills and valleys that stand out with large height differences. To solve this, multiple noise functions are generated with different frequencies and amplitudes and then summed, giving a more natural result. These component noise functions are called **octaves**.

- *Pure randomness (white noise) is too chaotic.*
- *Perlin noise is random but continuous.*
- *Multiple octaves are combined to create variation.*

---

## Steps

World generation happens in multiple steps. The game may freeze chunks far from players at an early step for better performance; as a player approaches, those chunks advance through the remaining steps until finished. Incomplete chunks temporarily frozen at a step are called **proto-chunks**, while finished, player-accessible chunks are called **level chunks**.

In *Java Edition*, the generation steps, in order, are:

| Step | Description |
| --- | --- |
| **empty** | The chunk is not yet loaded or generated. |
| **structures_starts** | Calculates starting points for structure pieces. For structures starting in this chunk, the position of all pieces is generated and stored. |
| **structures_references** | References to nearby chunks that contain a structure's starting point are stored. |
| **biomes** | Biomes are determined and stored. No terrain is generated at this stage. |
| **noise** | The base terrain shape and liquid bodies are placed. |
| **surface** | The surface of the terrain is replaced with biome-dependent blocks. |
| **carvers** | Carvers carve parts of the terrain, replacing solid blocks with air. |
| **features** | Features and structure pieces are placed; heightmaps are generated. |
| **initialize_light** | The lighting engine is initialized and light sources are identified. |
| **light** | The lighting engine calculates light levels for blocks. |
| **spawn** | Mobs are spawned. |
| **full** | Generation is done. The proto-chunk becomes a level chunk and all deferred block updates are executed. |

---

## Biomes

### Overworld Biomes

Biome generation in the Overworld is based on **6 parameters**: temperature, humidity (vegetation), continentalness (continents), erosion, weirdness (ridges), and depth. Except for depth, the other 5 parameters depend only on horizontal coordinates.

They form a six-dimensional (6D) space, where each biome occupies multiple intervals. If a location's 6 parameters fall outside all defined biome intervals, the game uses the closest biome interval in the 6D space.

#### Temperature
A noise parameter used only in biome generation (it does not affect terrain). Divided into 5 levels:

| Level | Range |
| --- | --- |
| 0 | −1.0 ~ −0.45 |
| 1 | −0.45 ~ −0.15 |
| 2 | −0.15 ~ 0.2 |
| 3 | 0.2 ~ 0.55 |
| 4 | 0.55 ~ 1.0 |

Note: this temperature *parameter* is not the same as a biome's temperature *property*, though they roughly correspond.

#### Humidity (Vegetation)
A noise parameter used only in biome generation. Divided into 5 levels:

| Level | Range |
| --- | --- |
| 0 | −1.0 ~ −0.35 |
| 1 | −0.35 ~ −0.1 |
| 2 | −0.1 ~ 0.1 |
| 3 | 0.1 ~ 0.3 |
| 4 | 0.3 ~ 1.0 |

#### Continentalness (Continents)
Decides between ocean/beach/land biomes. Higher values are more inland:

| Range | Region |
| --- | --- |
| −1.2 ~ −1.05 | Mushroom fields |
| −1.05 ~ −0.455 | Deep ocean |
| −0.455 ~ −0.19 | Ocean |
| −0.19 ~ −0.11 | Coast |
| −0.11 ~ 0.03 | Near-inland |
| 0.03 ~ 0.3 | Mid-inland |
| 0.3 ~ 1.0 | Far-inland |

#### Erosion
Decides between flat and mountainous terrain. High erosion → flat landscape; low erosion → hilly. Low-erosion areas tend to generate meadows, snowy slopes, stony peaks, jagged peaks, and frozen peaks. Divided into 7 levels:

| Level | Range |
| --- | --- |
| 0 | −1.0 ~ −0.78 |
| 1 | −0.78 ~ −0.375 |
| 2 | −0.375 ~ −0.2225 |
| 3 | −0.2225 ~ 0.05 |
| 4 | 0.05 ~ 0.45 |
| 5 | 0.45 ~ 0.55 |
| 6 | 0.55 ~ 1.0 |

#### Weirdness (Ridges)
Affects whether a biome variant generates and whether terrain generates shattered. When weirdness > 0, the biome becomes "weirder" (e.g. jungle → bamboo jungle, or a taiga with more shattered terrain like a windswept savanna). A biome and its variant often do not appear on the same bank of a river.

The **PV (peaks and valleys**, aka ridges folded) value is calculated from weirdness with the formula:

```
PV = 1 − |(3·|weirdness|) − 2|
```

| PV Range | Level |
| --- | --- |
| −1.0 ~ −0.85 | Valleys |
| −0.85 ~ −0.2 | Low |
| −0.2 ~ 0.2 | Mid |
| 0.2 ~ 0.7 | High |
| 0.7 ~ 1.0 | Peaks |

#### Depth
Not based directly on noise — it corresponds approximately to terrain height. Roughly 0 at the surface, increasing by 1⁄128 (0.0078125) for every block downward. Depth decides whether a surface biome or a cave biome is placed.

**Defined depth values for Overworld biomes:**

| Depth | Additional requirement | Biomes |
| --- | --- | --- |
| D = 0.0 | N/A | Surface biomes |
| D = 0.2 ~ 0.9 | Continentalness = 0.8 ~ 1.0 | Dripstone Caves |
| D = 0.2 ~ 0.9 | Humidity = 0.7 ~ 1.0 | Lush Caves |
| D = 0.2 ~ 0.9 | Weirdness = −1.1 ~ −0.95 *(verify)* | Sulfur Caves *(upcoming: Chaos Cubed)* |
| D = 1.0 | N/A | Surface biomes |
| D = 1.1 | Erosion = −1.0 ~ −0.375 | Deep Dark |

(Lush Caves and Dripstone Caves regions overlap.)

#### Non-inland biomes (by continentalness and temperature)
Not based on humidity, erosion, or weirdness.

| Temperature | Oceans | Deep oceans | Mushroom fields |
| --- | --- | --- | --- |
| T=0 | Frozen Ocean | Deep Frozen Ocean | Mushroom Fields |
| T=1 | Cold Ocean | Deep Cold Ocean | |
| T=2 | Ocean | Deep Ocean | |
| T=3 | Lukewarm Ocean | Deep Lukewarm Ocean | |
| T=4 | Warm Ocean | | |

#### Inland surface biome categories
The inland surface biome is chosen from continentalness, erosion, and PV, which selects one of these **categories**; the specific biome within a category is then determined by temperature, humidity, and weirdness (tables below). The categories are: **beach biomes, badland biomes, middle biomes, plateau biomes, and shattered biomes** (plus rivers in valleys and the various peak/slope/grove biomes at low erosion).

General relationships in the full erosion × PV table:
- **Valleys** at any erosion → River (T>0) or Frozen River (T=0); at E=6, swamps/mangrove swamps appear in near-inland.
- **Low erosion (E=0,1) + High/Peaks PV** → jagged/frozen/stony peaks, snowy slopes, groves, plateau biomes.
- **Low/Stony Shore** appears at coast for E=0,1.
- **High erosion (E=5) + Mid/Peaks PV** → shattered biomes; windswept savanna appears (W>0; T=2,3,4; H=0–3).
- **Beach biomes** appear at coast for E=3–6, Low/Mid PV.

##### Beach biomes (by temperature)
| Temperature | Biome |
| --- | --- |
| T=0 | Snowy Beach |
| T=1,2,3 | Beach |
| T=4 | Desert |

##### Badland biomes (by humidity & weirdness)
| Humidity | Biome |
| --- | --- |
| H=0,1 | Badlands (W<0) / Eroded Badlands (W>0) |
| H=2 | Badlands |
| H=3,4 | Wooded Badlands |

##### Middle biomes (by temperature, humidity, weirdness)
The most extensive inland biomes.

| H \ T | T=0 | T=1 | T=2 | T=3 | T=4 |
| --- | --- | --- | --- | --- | --- |
| H=0 | Snowy Plains (W<0) / Ice Spikes (W>0) | Plains | Flower Forest (W<0) / Sunflower Plains (W>0) | Savanna | Desert |
| H=1 | Snowy Plains | Plains | Plains | Plains | Desert |
| H=2 | Snowy Plains (W<0) / Snowy Taiga (W>0) | Forest | Forest | Forest (W<0) / Plains (W>0) | Desert |
| H=3 | Snowy Taiga | Taiga | Birch Forest (W<0) / Old Growth Birch Forest (W>0) | Jungle (W<0) / Sparse Jungle (W>0) | Desert |
| H=4 | Taiga | Old Growth Spruce Taiga (W<0) / Old Growth Pine Taiga (W>0) | Dark Forest | Jungle (W<0) / Bamboo Jungle (W>0) | Desert |

##### Plateau biomes (by temperature, humidity, weirdness)
Generate at inland high terrain with moderate erosion (e.g. meadows, savanna plateaus).

| H \ T | T=0 | T=1 | T=2 | T=3 | T=4 |
| --- | --- | --- | --- | --- | --- |
| H=0 | Snowy Plains (W<0) / Ice Spikes (W>0) | Meadow (W<0) / Cherry Grove (W>0) | Meadow (W<0) / Cherry Grove (W>0) | Savanna Plateau | Badlands (W<0) / Eroded Badlands (W>0) |
| H=1 | Snowy Plains | Meadow | Meadow | Savanna Plateau | Badlands |
| H=2 | Snowy Plains | Forest (W<0) / Meadow (W>0) | Meadow (W<0) / Forest (W>0) | Forest | Badlands |
| H=3 | Snowy Taiga | Taiga (W<0) / Meadow (W>0) | Meadow (W<0) / Birch Forest (W>0) | Forest | Wooded Badlands |
| H=4 | Snowy Taiga | Old Growth Spruce Taiga (W<0) / Old Growth Pine Taiga (W>0) | Pale Garden | Jungle | Wooded Badlands |

##### Shattered biomes (by temperature, humidity, weirdness)
Generate at inland places with high erosion.

| H \ T | T=0~1 | T=2 | T=3 | T=4 |
| --- | --- | --- | --- | --- |
| H=0~1 | Windswept Gravelly Hills | Windswept Hills | Savanna | Desert |
| H=2 | Windswept Hills | Windswept Hills | Forest (W<0) / Plains (W>0) | Desert |
| H=3 | Windswept Forest | Windswept Forest | Jungle (W<0) / Sparse Jungle (W>0) | Desert |
| H=4 | Windswept Forest | Jungle (W<0) / Bamboo Jungle (W>0) | Jungle | Desert |

### The Nether Biomes

The Nether uses **3 parameters**: temperature, humidity, and offset. Unlike the Overworld, the Nether specifies each biome with a single point. The **offset** parameter is not noise-based — it is always 0 everywhere, so a location's parameter point always lies in the temperature–humidity plane. The closer a biome point's offset is to 0, the greater its advantage during biome generation.

| Biome | Temperature | Humidity | Offset |
| --- | --- | --- | --- |
| Basalt Deltas | −0.5 | 0 | 0.175 |
| Crimson Forest | 0.4 | 0 | 0 |
| Nether Wastes | 0 | 0 | 0 |
| Soul Sand Valley | 0 | −0.5 | 0 |
| Warped Forest | 0 | 0.5 | 0.375 |

### The End Biomes

The End uses only **one** noise parameter: erosion. If the horizontal distance from a chunk's origin to the world origin is less than 1024, the chunk is "The End"; otherwise the biome is determined by erosion.

| Biome | Erosion | Distance |
| --- | --- | --- |
| Small End Islands | −1 ~ −0.21875 | > 1024 |
| End Barrens | −0.21875 ~ −0.0625 | > 1024 |
| End Midlands | −0.0625 ~ 0.25 | > 1024 |
| End Highlands | 0.25 ~ 1 | > 1024 |
| The End | N/A | < 1024 |

In *Bedrock Edition*, these "biomes" are only used for initial terrain and feature generation and are merged into The End after world generation.

---

## Terrain

Terrain shaping determines which blocks are solid and which are filled with air.

### 3D noise

2D noise can only control surface height, making overhangs impossible. To add overhangs and 3D shapes, the game uses a **3D Perlin noise** function that outputs a **density** value for every block. Density > 0 → solid block; otherwise air.

Density is then given a **height bias** and a **base height**:
- **Height bias** "squeezes" the blocks.
- **Base height** is the base of the squeezing process, where density is left unchanged. Changing base height moves the ground up or down.

Per dimension:
- **Overworld:** a single pair of height bias and base height — higher blocks have less density and vice versa. Both are configured by several noises. Notably, *Amplified* worlds tune height bias lower than default so terrain stretches vertically.
- **Nether:** two pairs of base heights create a thick solid ceiling and ground with a hollow space between.
- **End:** parameters are configured to squeeze the map into a big island near the bottom of the dimension.

### Splines

To give dramatic terrain shapes (cliffs, fjords, plateaus), the game uses three 2D noise maps mapped through **splines** to calculate a height **offset** and a vertical stretch **factor**. The same noises are used in biome generation, creating a soft link between biome and terrain (mountainous areas → mountainous biomes; plains biomes are flatter).

- **Continentalness:** larger → higher average terrain height. Mainly differentiates ocean from land.
- **Erosion:** affects inland terrain; mainly creates large flat areas. Higher erosion → lower, flatter terrain.
- **Peaks and valleys (PV):** computed from weirdness; mainly generates peaks and valleys. Higher PV → higher terrain. At low continentalness or high erosion, "Valleys" PV is low enough to generate rivers. At high terrain, negative weirdness produces taller, jagged, pointed peaks. Near erosion level 5, positive weirdness produces shattered, precipitous, craggy inland terrain.

### Noise caves

Noise caves are part of base terrain generation, made with 3D Perlin noises. Three noise maps — **frequency, hollowness, and thickness** — control the process. They come in three forms:

- **Cheese caves:** pocket areas of varying size, generated from the "white" area of a Perlin noise map. *Hollowness* controls their size.
- **Spaghetti caves:** long, narrow winding caves. The boundary between black and white parts of the noise becomes air, producing long wide tunnels. *Thickness* controls their thickness.
- **Noodle caves:** thinner, squigglier spaghetti caves, usually 1–5 blocks wide. *Thickness* controls their thickness.

Additionally, **noise pillars** generate inside big cheese-cave chambers; *frequency* controls pillar frequency and *thickness* controls their thickness.

### Aquifers

Aquifers are liquid systems used in the Overworld to determine the fluid in empty areas. Without them, all empty areas between sea level and Y=−54 would be water; areas below Y=−55 are always lava. Aquifers prevent all caves from flooding. Each position is assigned one of three states:

- **Empty:** always air.
- **Flooded:** as if aquifers didn't exist — air above sea level, fluid below.
- **Local fluid level:** picks a local liquid level, filling below it with liquid and above it with air.

Rules:
- Above the preliminary surface → "Flooded".
- Erosion < −0.22 and depth > 0.9 (only the Deep Dark in vanilla) → always "Empty".
- Otherwise determined by noise: < 0.4 → "Empty"; > 0.8 → "Flooded"; else a local fluid level.
- Near places where the preliminary surface is below sea level, the "Flooded" region extends slightly below the surface; cutoff values decrease linearly from 64 blocks below the preliminary surface upward (at the surface, < −0.8 → "Empty", > −0.3 → "Flooded"). This makes "Flooded" much more common directly below rivers and oceans.
- Local water level is determined in 16×40×16 cells via one noise. Water-vs-lava choice is determined in 64×40×64 cells via a third noise. Areas above Y=−10 always use water.
- **Barriers** separate areas of different liquids (and liquids from air). Barrier height depends on a fourth noise, sometimes letting water or lava spill over.

### Ore veins

Ore veins generate only in the Overworld. Three noises are used: **toggle, ridge, and gap**.

- **Toggle:** always 0 outside Y=−60 to Y=51; inside it can be negative or positive. Negative → attempt iron vein; positive → attempt copper vein (attempts may fail due to configured generation height).
- **Ridge:** always −0.08 if Y is outside the range. If ridge > 0, the block is skipped.
- **Gap:** sets the ore-to-filler ratio (10%–30% per vein). Of non-filler blocks, 98% are normal ore, 2% are raw-ore blocks (Block of Raw Copper / Block of Raw Iron).

The blocks used are hardcoded, though vein size can be changed with data packs.

---

## Surface

After base terrain is generated, the game replaces some blocks with grass, sand, dirt, etc., depending on biome and dimension.

### Overworld (summary of surface rules)

- **Bedrock:** placed at the bottom — *JE* a gradient from Y=−64 (full) up to Y=−59; *BE* noise from Y=−64/−63 (full) up to Y=−60.
- **Default surface:** Grass Block on top (no water above) over Dirt; Coarse Dirt above Y=97 in wooded badlands; Water at the surface for swamps/mangrove swamps.
- **Badlands family:** Terracotta layers — Orange Terracotta, Terracotta, White Terracotta, Red Sand/Red Sandstone, and "Hoodoo" terrain features, depending on height, erosion, and water depth.
- **Frozen oceans:** Air / Ice / Water depending on erosion and cold.
- **Frozen & Jagged Peaks:** Packed Ice, Ice, Snow Block, Stone on steep faces, depending on noise.
- **Snowy Slopes / Grove:** Powder Snow (by noise) or Snow Block, Stone on steep faces.
- **Stony Peaks:** Calcite (within a narrow noise band) or Stone.
- **Stony Shore:** Stone or Gravel by noise.
- **Windswept Hills / Frozen Ocean:** Stone where surface noise ≥ 4/33.
- **Warm Ocean:** Grass Block / Sandstone / Sand.
- **Beaches & Deserts:** Sandstone over Sand.
- **Cave biomes (Dripstone/Sulfur/Legacy Frozen Ocean):** Stone.
- **Windswept Savanna / Shattered Savanna Plateau:** Stone (noise ≥ 7/33), Coarse Dirt (noise ≥ −2/33).
- **Windswept Gravelly Hills:** Stone / Gravel / Grass Block / Dirt depending on surface noise thresholds.
- **Old Growth Pine/Spruce Taiga:** mixes of Stone, Gravel, etc.

*(The full surface table is large and edition-dependent; the above lists the principal results. Many rules differ between Java Edition (JE) and Bedrock Edition (BE).)*

### The Nether (surface)
Surface blocks are biome-dependent — e.g. Netherrack, Soul Sand/Soul Soil in Soul Sand Valley, Crimson/Warped Nylium in the forests, Basalt/Blackstone/Magma in Basalt Deltas — with a Bedrock ceiling and floor.

### The End (surface)
The surface is primarily End Stone.

---

## Carvers

After the surface step, **carvers** cut through the already-generated terrain, replacing solid blocks with air (or water) to produce caves and ravines:

- **Caves:** tunnel-like carved passages.
- **Canyons (ravines):** long, deep carved gorges.

Carvers run separately from noise caves and operate on a per-chunk basis using the world seed, so they connect smoothly across chunk boundaries. Underwater variants of cave and canyon carvers exist for ocean biomes.

---

## Structures

Structures are larger, often manually designed builds (villages, strongholds, temples, mineshafts, ocean monuments, mansions, ruins, etc.) placed via a two-phase process during world generation:

- **structures_starts:** the start position and the layout of all pieces are computed for structures originating in a chunk.
- **structures_references:** chunks store references to nearby chunks that contain structure starts, so a structure spanning multiple chunks is assembled correctly.

Structure placement is governed by configurable rules (spacing, separation, biome restrictions, and a salt/seed) that control how frequently and where each structure type appears. Actual structure blocks are written during the **features** step.

---

## Features

Features are the smaller-scale decorations added after terrain and structures: trees, flowers, grass, ores (besides ore veins), boulders, springs, lakes, dungeons, geodes, and so on.

### Decoration steps
Features are placed in a fixed sequence of decoration steps so that, for example, lakes form before vegetation. The ordered steps include (roughly):

1. Raw generation
2. Lakes
3. Local modifications
4. Underground structures
5. Surface structures
6. Strongholds
7. Underground ores
8. Underground decoration
9. Fluid springs
10. Vegetal decoration
11. Top-layer modification

### Generation
Within each step, features are placed using **placement modifiers** that decide count, rarity, height, and position (e.g. "count," "rarity_filter," "height_range," "in_square," "biome" filter). Because features are added per chunk but can reference neighbors, the generation order is deterministic from the seed, ensuring identical results for the same world.

---

## Lighting

Lighting is computed in two steps:

- **initialize_light:** the lighting engine is initialized and light sources (sky access, glowing blocks) are identified.
- **light:** the engine propagates and calculates the light level for each block.

Minecraft tracks two light types — **sky light** (from the sky, attenuated as it travels down and sideways) and **block light** (from light-emitting blocks). The final light level of a block is the maximum of the two, and this determines visibility and mob spawning eligibility.

---

## Mob spawning

During the **spawn** step, mobs are placed into newly generated chunks according to each biome's spawn lists and the local conditions (light level, surface type, space, and height). This initial world-generation spawn is distinct from the ongoing runtime mob spawning that happens as players explore.

---

## Heightmaps

Heightmaps are generated during the **features** step. A heightmap records, for each (x, z) column in a chunk, the Y level of the highest qualifying block. Minecraft maintains several heightmaps for different purposes, including:

- **WORLD_SURFACE** – highest non-air block.
- **WORLD_SURFACE_WG** – world-generation version of the above.
- **OCEAN_FLOOR** – highest motion-blocking, non-fluid block.
- **OCEAN_FLOOR_WG** – world-generation version of the above.
- **MOTION_BLOCKING** – highest block that blocks motion or contains fluid.
- **MOTION_BLOCKING_NO_LEAVES** – as above but ignoring leaves.

Heightmaps are used for fast lookups during gameplay (e.g. mob spawning, feature placement, and rendering) without scanning entire columns.

---

*Source: Minecraft Wiki, "World generation" — https://minecraft.wiki/w/World_generation (CC BY-NC-SA 3.0). This is a condensed Markdown adaptation; image-only cells and some exhaustive edition-specific surface rules have been summarized.*
