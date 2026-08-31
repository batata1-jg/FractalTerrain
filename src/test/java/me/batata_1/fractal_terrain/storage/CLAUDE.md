# storage/ (test)

Gates the per-chunk channel fill against the per-pixel path it replaces — both the values and the set
of tiles materialised to produce them.

## Files

| File                       | What                                                                                                      | When to read                                                        |
| -------------------------- | ----------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `ChunkChannelFillTest.java`| Window fill is bit-identical to the four-corner `getValue` path across tile interiors, both tile-boundary crossings and negative coordinates; and touches exactly the same tile set, which is what catches a `floor + 1` window bound | Changing the heightmap fill, the window bounds, or the upscale loops |
