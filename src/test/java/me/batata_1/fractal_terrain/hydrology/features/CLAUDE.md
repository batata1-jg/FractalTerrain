# features/ (test)

Gates the primitive-level invariants the carve and the spatial index rely on.

## Files

| File                           | What                                                                     | When to read                                                    |
| ------------------------------ | ------------------------------------------------------------------------ | --------------------------------------------------------------- |
| `HydrologicalFeaturePackTest.java` | The `HydrologicalFeature` family/sub-type long the carve stamps into `Types.RIVER_TYPE`: round-trip at every family, the `-1L` `NONE` sentinel (a zero-filled buffer reads as `RIVER`+`A`), and sub-ordinal sign-extension containment | Changing the packing, adding a `HydrologicalFeature`, or changing what "untouched" means in the type mask |
| `HistoricPrimitiveCodecTest.java` | The shed families' payload: type-tagged round trip, the cut step surviving it, the byte-size claim, and the zero-radius window before `resolved` | Changing the shed-feature payload, `HistoricPrimitive`'s geometry, or what `resolved` is allowed to disturb |
