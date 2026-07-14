# chunk/

## Files

| File                                | What                                                            | When to read                                  |
| ----------------------------------- | -------------------------------------------------------------- | --------------------------------------------- |
| `FractalTerrainChunkGenerator.java` | The mod's `ChunkGenerator`: block fill, codec, biome wiring    | Chunk generation flow, registering the generator |
| `FractalTerrainChunkNoiseSampler.java` | `NoiseChunk` subclass feeding the generator                 | Noise-chunk sampling for chunk fill           |
