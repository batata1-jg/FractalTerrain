# storage/

Per-tile in-memory cache with optional disk persistence.

## Files

| File                             | What                                                                    | When to read                                          |
| -------------------------------- | ----------------------------------------------------------------------- | ----------------------------------------------------- |
| `README.md` | Cache/persistence architecture, single-flight, freeze boundary invariants | Changing cache behaviour, persistence format, or eviction |
| `Storage.java`                   | Cache + disk IO; `claimForCompute`/`fetchEntry` single-flight; freezes  | Cache semantics, persistence, eviction, freeze point  |
| `TileKey.java`                   | Immutable `int[]`-tuple cache key with precomputed hash (no boxing)     | Cache keying, scratch-array iteration safety          |
| `Persistable.java`               | Serialize/deserialize contract; default = cache-only (no disk)          | Adding a persistable payload, byte format             |
| `FractalTerrainHeightmapCache.java` | Per-chunk heightmap cache (`getOrCompute`)                           | Heightmap lookups from surface/mixin code             |
| `FractalTerrainHeightmap.java`   | Record of per-chunk heightmap layers (`Types`); `Types.RIVER_TYPE` is a `long[]` packed by `HydrologicalFeature.pack` (family in the high word, sub-type in the low), not a `HydrologicalFeature[]` | Reading heightmap channels (elevation/grad/params); reading/unpacking `RIVER_TYPE` |
| `EntryNotLoadableException.java` | Signals an unloadable/cache-only/corrupt entry (triggers recompute)     | Handling recoverable cache misses                     |
