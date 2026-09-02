# features/

The hydrological-feature types stored in the per-tile primitive index, behind one `HydrologicalPrimitive` interface.

## Files

| File                       | What                                                                        | When to read                                                          |
| -------------------------- | --------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `README.md`                     | Why a feature type's carve data must stay allocation-free, and what replaced the per-primitive `h`/`w`/`d` hot path | Adding a feature type, allocation-cost review of the primitive collect/merge path |
| `HydrologicalPrimitive.java`    | The interface every feature implements: influence circle, profile, persistence tag, the `comparator` the lattice carve requires, and the `HydrologicalFeature` family enum that mints primitives and packs the type mask | Adding a feature type, extending the primitives index, primitive persistence format, the pack/unpack type-mask encoding |
| `PrimitiveCodec.java`           | Shared serialize/deserialize helpers and the coordinate-contents equality test | Writing a primitive's `serializePrimitive`/`equals`, debugging a round-trip      |
| `PositionOnlyPrimitive.java`    | Package-private mixin for the position-only features: fixed-radius disc, `DefaultProfile`, no cross-section | Adding a position-only feature, changing the shared disc radius or blend                     |
| `RiverPrimitive.java`           | A flowing-channel sample: normal, width, bank elevation, Rosgen type        | Channel cross-section, the reference feature implementation           |
| `SourcePrimitive.java`          | A headwater spring; position only                                           | Source rendering, adding source-specific carve behaviour              |
| `DeltaPrimitive.java`           | A river mouth; position only                                                | Delta/mouth behaviour                                                 |
| `WaterfallPrimitive.java`       | A plunge lip and pool; position only                                        | Waterfall behaviour, the `WATERFALL` zone priority                    |
| `OxbowLakePrimitive.java`       | A cutoff meander recorded as standing water                                 | Oxbow recording, `LAKE_BED` zone behaviour                            |
| `AbandonedRiverPrimitive.java`  | A channel pruned by the collision pass                                      | Stream-capture leftovers, abandoned-channel rendering                 |
| `elev_fix.txt`                  | Checked-in Claude Code transcript of the carve-ordering redesign. Not source; not compiled | Never edit; read only to recover why the tile-level bed carve was dropped |
