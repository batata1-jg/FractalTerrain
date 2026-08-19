# features/ (test)

Gates the primitive-level invariants the carve and the spatial index rely on.

## Files

| File                           | What                                                                     | When to read                                                    |
| ------------------------------ | ------------------------------------------------------------------------ | --------------------------------------------------------------- |
| `HydrologicalFeaturePackTest.java` | The `HydrologicalFeature` family/sub-type long the carve stamps into `Types.RIVER_TYPE`: round-trip at every family, the `-1L` `NONE` sentinel (a zero-filled buffer reads as `RIVER`+`A`), and sub-ordinal sign-extension containment | Changing the packing, adding a `HydrologicalFeature`, or changing what "untouched" means in the type mask |
| `ConfluencePrimitiveTest.java` | Junction geometry: angular bracket selection (including wrap past π and across the seam), on-ray blend collapse, radial elevation lerp, `w` falloff, serialization round-trip, degenerate arm counts | Changing arm bracketing, the blend, influence falloff, or the confluence wire format |
