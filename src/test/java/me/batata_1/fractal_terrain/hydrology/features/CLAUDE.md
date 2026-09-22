# features/ (test)

Gates the primitive-level invariants the carve and the spatial index rely on.

## Files

| File                           | What                                                                     | When to read                                                    |
| ------------------------------ | ------------------------------------------------------------------------ | --------------------------------------------------------------- |
| `HydrologicalFeaturePackTest.java` | The `HydrologicalFeature` family/sub-type long the carve stamps into `Types.RIVER_TYPE`: round-trip at every family, the `-1L` `NONE` sentinel (a zero-filled buffer reads as `RIVER`+`A`), and sub-ordinal sign-extension containment | Changing the packing, adding a `HydrologicalFeature`, or changing what "untouched" means in the type mask |
| `InfluenceCarverDefaultsTest.java` | Which families cut a shell cross-section and which contribute none, asserted on the carved buffer: a river cuts a cross-section, confluence/source/delta/waterfall carve nothing | Adding a family and deciding whether it should carve the shell |
| `BedDispatchTest.java` | What each family does when the bed pass reaches it: a `PositionOnlyPrimitive` carves nothing, `OxbowLakePrimitive.carveBed` throws rather than carving silently, and a river's `tabulateBedLut` hook fills the same table `RosgenProfile.sampleCrossSection` would inline | Adding a family's bed-carve dispatch, changing what `OxbowLakePrimitive` does when its bed carve is reached |
| `RosgenCarvedPrimitiveCodecTest.java` | Persistence shared by every `RosgenCarvedPrimitive`: round trip and byte-size claim, over both `RiverPrimitive` and `OxbowLakePrimitive` | Changing `RosgenCarvedPrimitive`'s contract, or either implementer's serialized layout |
| `RadialPrimitiveCodecTest.java` | Persistence and disc index geometry shared by every `RadialPrimitive`: round trip, byte-size claim, MBR/containment, and comparator ordering, over `ConfluencePrimitive`, `SourcePrimitive` and `AbandonedRiverPrimitive` | Changing `RadialPrimitive`'s contract, or any implementer's serialized layout |
| `RadialEmissionTest.java` | What `RiverNetwork` hands the radial family at mint time: the widest-incident-channel sizing rule, the emit-only-if-two-channels-emitted gate, and the NaN-elevation skip | Changing confluence/source sizing, the emission gate, or the elevation-assigned precondition |
