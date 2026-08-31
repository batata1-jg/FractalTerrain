# infinitetensor/ (test)

Gates slice assembly: which windows a pixel range touches, and that a bulk read agrees with the
per-pixel read it replaces.

## Files

| File                                        | What                                                                                          | When to read                                                                 |
| ------------------------------------------- | --------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `SliceGeometryTest.java`                    | Characterises the window walk: which windows are visited, and the src/dst region arithmetic per visit, including tile crossings, negative indices, and the overlapping-window case | Changing window intersection or region mapping, adding a `getSlice` caller |
| `NonIntersectingInfiniteTensorSliceTest.java`| `getSlice` equals `getValue` pixel-for-pixel across tile boundaries and negative coordinates; a slice touches only the tiles it overlaps; `cacheLimitBytes` bounds the cache on the insert path | Changing bulk reads, the tile-touch set, or a cache budget |
