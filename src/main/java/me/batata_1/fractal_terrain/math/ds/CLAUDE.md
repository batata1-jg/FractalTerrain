# ds/

Spatial-index data structures. `QuadTree` is safe for concurrent reads under its own read/write lock;
immutable variants back per-tile hydrology indexes (see `ARCHITECTURE.md` Invariants).

## Files

| File                          | What                                                        | When to read                                    |
| ----------------------------- | ---------------------------------------------------------- | ----------------------------------------------- |
| `SpatialIndex.java`           | Query interface over indexed shapes                        | Consuming a spatial index                       |
| `QuadTree.java`               | Mutable quadtree with read/write lock                      | Building a quadtree, concurrent-read contract   |
| `ImmutableQuadTree.java`      | Immutable, frozen quadtree                                 | Cached per-tile point indexes                   |
| `ImmutableRTree.java`         | Immutable R-tree (backs the `HydrologicalUnit` index)      | Rectangle/shape range queries per tile          |
| `SpatialIndexShape.java`      | Base shape type indexed by the structures                  | Adding a shape type                             |
| `SpatialIndexPoint.java`      | Point shape                                                | Point queries                                   |
| `SpatialIndexCircle.java`     | Circle shape                                               | Radius queries                                  |
| `SpatialIndexRectangle.java`  | Rectangle shape                                            | Bounding-box queries                            |
| `CoordPoint.java`             | Integer coordinate record                                 | Coordinate keys in indexes                       |
