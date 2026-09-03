# ds/

Spatial-index data structures: mutable `QuadTree`/`SpatialHashGrid` and immutable, build-once
`ImmutableQuadTree`/`ImmutableRTree` variants.

## Files

| File                          | What                                                        | When to read                                    |
| ----------------------------- | ---------------------------------------------------------- | ----------------------------------------------- |
| `README.md` | Index-choice rationale, the `ImmutableQuadTree` alignment bug, locking invariants | Choosing an index, debugging dropped points, before adding a mutation path |
| `SpatialIndex.java`           | Query interface over indexed shapes                        | Consuming a spatial index                       |
| `QuadTree.java`               | Mutable quadtree with read/write lock                      | Building a quadtree, concurrent-read contract   |
| `SpatialHashGrid.java`        | Mutable bucketed point index, no lock                      | Live insert/remove interleaved with circle queries |
| `ImmutableQuadTree.java`      | Immutable, frozen quadtree                                 | Cached per-tile point indexes                   |
| `ImmutableRTree.java`         | Immutable R-tree (backs the `HydrologicalPrimitive` index)      | Rectangle/shape range queries per tile          |
| `SpatialIndexShape.java`      | Base shape type indexed by the structures                  | Adding a shape type                             |
| `SpatialIndexPoint.java`      | Point shape                                                | Point queries                                   |
| `SpatialIndexCircle.java`     | Circle shape                                               | Radius queries                                  |
| `SpatialIndexRectangle.java`  | Axis-aligned rectangle shape                               | Bounding-box queries                            |
| `SpatialIndexRotatedRectangle.java` | Oriented rectangle shape (center/angle/length/width) | Queries by a footprint aligned to a bearing     |
| `CoordPoint.java`             | Integer coordinate record                                 | Coordinate keys in indexes                       |
