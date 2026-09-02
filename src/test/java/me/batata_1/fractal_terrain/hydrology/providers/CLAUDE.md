# providers/ (test)

Gates the caching policy `RiverProvider` layers over its two `Storage`-backed stores.

## Files

| File                             | What                                                                                              | When to read                                                       |
| -------------------------------- | -------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| `RecentTileCachePolicyTest.java` | The `recentTiles` memo's eviction policy over a bare `Long2ObjectLinkedOpenHashMap`: a read moves an entry to the front so it survives the next eviction, and a miss leaves the map untouched | Changing the memo's capacity, its access ordering, or swapping `getAndMoveToFirst` for a plain `get` |
