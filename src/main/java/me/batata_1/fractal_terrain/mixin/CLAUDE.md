# mixin/

SpongePowered mixins/accessors into vanilla worldgen. These are Fabric-instantiated and resolve providers
through `FractalTerrainInstance` (no constructor the mod controls — see `ARCHITECTURE.md` caller migration).

## Files

| File                             | What                                                                | When to read                                   |
| -------------------------------- | ------------------------------------------------------------------- | ---------------------------------------------- |
| `SteepSlopePredicateMixin.java`  | Overrides vanilla steep-slope surface predicate using refined gradient | Surface steep-slope material selection         |
| `PlacedFeatureMixin.java`        | Alters feature placement (vegetation) at `placeWithContext` head    | Vegetation/feature placement gating            |
| `MaterialRuleContextAccessor.java` | `@Invoker` accessor to construct `SurfaceRules.Context`           | Building a surface-rule context                |
| `LevelUtilsMixin.java`           | Empty mixin marker into terrablender `LevelUtils`                   | Terrablender level-utils hook                  |
