# providers/

The two `Storage`-backed hydrology providers: the coarse global river network and the per-tile
hydrological-primitive/carved-elevation pipeline. See `README.md` for the tensor channel layouts and
the store-ownership split; `../README.md` for the `buildTile` stage ordering.

## Files

| File                       | What                                                                                                                   | When to read                                                                          |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `README.md`                | `global_river` channel + arrow-bit layout, store-ownership and cache-cap split, the coarse↔native frame boundary       | Reading/writing global-river channels, decoding arrows, adding a store or a test override |
| `RiverProvider.java`       | Owner of the `primitives` and `hydrology_relief` tile stores plus the cross-store memo; `buildTile` is the shared pipeline | Riverprimitive/carved-elevation output, tile caching, build ordering, test overrides   |
| `GlobalRiverProvider.java` | Coarse-px global riverPrimitive network, caches its own 64×64-coarse-px tiles                                          | Global network, coarse-frame addressing, `computeTileForTest`                          |
