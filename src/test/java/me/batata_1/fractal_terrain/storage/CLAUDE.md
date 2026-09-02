# storage/ (test)

Gates the per-chunk channel fill against the equivalent per-pixel path — both the values and the set of
tiles materialised to produce them — and the chunk-position packing the heightmap caches key on.

## Files

| File                       | What                                                                                                      | When to read                                                        |
| -------------------------- | ----------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `ChunkChannelFillTest.java`| Window fill is bit-identical to the four-corner `getValue` path across tile interiors, both tile-boundary crossings and negative coordinates; and touches exactly the same tile set, which is what catches a `floor + 1` window bound | Changing the heightmap fill, the window bounds, or the upscale loops |
| `IntLongChunkPosConversionTest.java` | `FractalTerrainHeightmapCacheAccessor`'s `(int x, int z)` ↔ packed `long` ↔ `ChunkPos` round-trip, including negative coordinates where sign extension can collide two chunks onto one key | Changing the packed chunk-pos encoding or the heightmap memo's key |
