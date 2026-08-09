# features/

The hydrological-feature types stored in the per-tile primitive index, behind one `HydrologicalPrimitive` interface.

## Files

| File                       | What                                                                        | When to read                                                          |
| -------------------------- | --------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `HydrologicalPrimitive.java`    | The interface every feature implements: influence circle, profile, persistence tag | Adding a feature type, extending the primitives index, primitive persistence format |
| `PrimitiveCodec.java`           | Shared serialize/deserialize helpers and the coordinate-contents equality test | Writing a primitive's `serializePrimitive`/`equals`, debugging a round-trip      |
| `RiverPrimitive.java`           | A flowing-channel sample: normal, width, bank elevation, Rosgen type        | Channel cross-section, the reference feature implementation           |
| `SourcePrimitive.java`          | A headwater spring; position only                                           | Source rendering, adding source-specific carve behaviour              |
| `DeltaPrimitive.java`           | A river mouth; position only                                                | Delta/mouth behaviour                                                 |
| `WaterfallPrimitive.java`       | A plunge lip and pool; position only                                        | Waterfall behaviour, the `WATERFALL` zone priority                    |
| `OxbowLakePrimitive.java`       | A cutoff meander recorded as standing water                                 | Oxbow recording, `LAKE_BED` zone behaviour                            |
| `AbandonedRiverPrimitive.java`  | A channel pruned by the collision pass                                      | Stream-capture leftovers, abandoned-channel rendering                 |
