# features/

The hydrological-feature types stored in the per-tile primitive index, behind one `HydrologicalPrimitive` interface.

## Files

| File                       | What                                                                        | When to read                                                          |
| -------------------------- | --------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `README.md`                     | Why `h`/`w`/`d` sit below the hot/cold line of abstraction                  | Before adding/changing a `h`/`w`/`d` implementation, allocation-cost review |
| `HydrologicalPrimitive.java`    | The interface every feature implements: influence circle, profile, persistence tag. `h`/`w`/`d` (lines 81-85) are below the hot/cold line of abstraction (see `README.md`, root `ARCHITECTURE.md`) | Adding a feature type, extending the primitives index, primitive persistence format |
| `PrimitiveCodec.java`           | Shared serialize/deserialize helpers and the coordinate-contents equality test | Writing a primitive's `serializePrimitive`/`equals`, debugging a round-trip      |
| `RiverPrimitive.java`           | A flowing-channel sample: normal, width, bank elevation, Rosgen type        | Channel cross-section, the reference feature implementation           |
| `SourcePrimitive.java`          | A headwater spring; position only                                           | Source rendering, adding source-specific carve behaviour              |
| `ConfluencePrimitive.java`      | A junction, as one ray per incident arm: angle, width, curvature, Rosgen type, rim elevation | Confluence carve, the angular-bracket blend, junction geometry |
| `DeltaPrimitive.java`           | A river mouth; position only                                                | Delta/mouth behaviour                                                 |
| `WaterfallPrimitive.java`       | A plunge lip and pool; position only                                        | Waterfall behaviour, the `WATERFALL` zone priority                    |
| `OxbowLakePrimitive.java`       | A cutoff meander recorded as standing water                                 | Oxbow recording, `LAKE_BED` zone behaviour                            |
| `AbandonedRiverPrimitive.java`  | A channel pruned by the collision pass                                      | Stream-capture leftovers, abandoned-channel rendering                 |
