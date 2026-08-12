# features/ (test)

Gates the primitive-level invariants the carve and the spatial index rely on.

## Files

| File                        | What                                                                     | When to read                                                    |
| --------------------------- | ------------------------------------------------------------------------ | --------------------------------------------------------------- |
| `RiverPrimitiveIdsTest.java`| The packed `ids` long: channel id in the high half, knot index in the low half, and the knot-adjacency test built on it | Changing the `ids` packing, `channelId`/`knotIndex`, or `isKnotAdjacentTo` |
