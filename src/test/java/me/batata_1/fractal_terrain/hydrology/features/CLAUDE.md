# features/ (test)

Gates the primitive-level invariants the carve and the spatial index rely on.

## Files

| File                           | What                                                                     | When to read                                                    |
| ------------------------------ | ------------------------------------------------------------------------ | --------------------------------------------------------------- |
| `HydrologicalFeaturePackTest.java` | The `HydrologicalFeature` family/sub-type long the carve stamps into `Types.RIVER_TYPE`: round-trip at every family, the `-1L` `NONE` sentinel (a zero-filled buffer reads as `RIVER`+`A`), and sub-ordinal sign-extension containment | Changing the packing, adding a `HydrologicalFeature`, or changing what "untouched" means in the type mask |
| `InfluenceCarverDefaultsTest.java` | Confluence/Source/Delta/Waterfall's shell-carve dispatch: all four resolve to `InfluenceCarver.NONE` | Adding a family and deciding whether it should carve the shell |
| `RosgenCarvedPrimitiveCodecTest.java` | Persistence and rectangle geometry shared by every `RosgenCarvedPrimitive`: round trip, byte-size claim, and the `ROSGEN` shell dispatch, over both `RiverPrimitive` and `OxbowLakePrimitive` | Changing `RosgenCarvedPrimitive`'s contract, or either implementer's serialized layout |
| `RadialPrimitiveCodecTest.java` | Persistence and disc index geometry shared by every `RadialPrimitive`: round trip, byte-size claim, MBR/containment, and comparator ordering, over `ConfluencePrimitive`, `SourcePrimitive` and `AbandonedRiverPrimitive` | Changing `RadialPrimitive`'s contract, or any implementer's serialized layout |
| `RadialEmissionTest.java` | What `RiverNetwork` hands the radial family at mint time: the widest-incident-channel sizing rule, the emit-only-if-two-channels-emitted gate, and the NaN-elevation skip | Changing confluence/source sizing, the emission gate, or the elevation-assigned precondition |
