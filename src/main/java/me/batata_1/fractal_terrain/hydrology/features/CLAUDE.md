# features/

The hydrological-feature types stored in the per-tile unit index, behind one `HydrologicalUnit` interface.

## Files

| File                       | What                                                                        | When to read                                                          |
| -------------------------- | --------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `HydrologicalUnit.java`    | The interface every feature implements: influence circle, profile, persistence tag | Adding a feature type, extending the units index, unit persistence format |
| `UnitCodec.java`           | Shared serialize/deserialize helpers and the coordinate-contents equality test | Writing a unit's `serializeUnit`/`equals`, debugging a round-trip      |
| `RiverUnit.java`           | A flowing-channel sample: normal, width, bank elevation, Rosgen type        | Channel cross-section, the reference feature implementation           |
| `SourceUnit.java`          | A headwater spring; position only                                           | Source rendering, adding source-specific carve behaviour              |
| `DeltaUnit.java`           | A river mouth; position only                                                | Delta/mouth behaviour                                                 |
| `WaterfallUnit.java`       | A plunge lip and pool; position only                                        | Waterfall behaviour, the `WATERFALL` zone priority                    |
| `OxbowLakeUnit.java`       | A cutoff meander recorded as standing water                                 | Oxbow recording, `LAKE_BED` zone behaviour                            |
| `AbandonedRiverUnit.java`  | A channel pruned by the collision pass                                      | Stream-capture leftovers, abandoned-channel rendering                 |
