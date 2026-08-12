# features/ (test)

Gates the primitive-level invariants the carve and the spatial index rely on.

## Files

| File                        | What                                                                     | When to read                                                    |
| --------------------------- | ------------------------------------------------------------------------ | --------------------------------------------------------------- |
| `RiverPrimitiveIdsTest.java`| The packed `ids` long: channel id in the high half, knot index in the low half, and the knot-adjacency test built on it | Changing the `ids` packing, `channelId`/`knotIndex`, or `isKnotAdjacentTo` |
| `ConfluencePrimitiveTest.java` | Junction geometry: angular bracket selection (including wrap past π and across the seam), on-ray blend collapse, radial elevation lerp, `w` falloff, serialization round-trip, degenerate arm counts | Changing arm bracketing, the blend, influence falloff, or the confluence wire format |
