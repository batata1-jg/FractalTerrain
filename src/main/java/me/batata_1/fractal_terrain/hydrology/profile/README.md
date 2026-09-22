# profile/

## Overview

The paint half of the hydrology pipeline: given a carved column, what blocks it should hold. The carve
itself — the lattice merge, the dispatch seam, the merge law — lives in `../carvers/`; this package owns
only `HydrologyProfile`'s paint contract (`riverPaintDepth`), the per-family shape laws
(`RosgenProfile`, `RadialProfile`) those carvers read a cross-section from, and
`HydrologyProfilePainter`, which turns the carve's output into placed water.

## Architecture

**Paint contract.** `HydrologyProfile.riverPaintDepth` tabulates a column of `SurfaceMaterial` tokens
into a caller-owned scratch array and returns how many entries it filled, rather than answering per
block: the caller's 16x16 surface loop sits below the hot/cold line and cannot afford a virtual call per
block. This mirrors `RosgenProfile.sampleCrossSection`, which tabulates a cross-section LUT once per
primitive for the same reason.

Tokens, not blocks, because nothing under `hydrology/` may import `net.minecraft` — that is what lets
the golden suite run as plain JUnit. `world/gen/surfacebuilder/HydrologySurfacePalette` owns the
mapping, including the substitution a token with no vanilla counterpart needs.

`RosgenProfile` splits the law the way `delta` is split: one shared body maps the banded coordinate to a
band, and per-constant `bedColumn`/`floodPlainColumn` hooks name that band's materials. The columns are
`static final` arrays so a constant's override allocates nothing. `DA` overrides neither and takes the
enum-level defaults, the same "overrides nothing" position it holds for the elevation laws.
`DEFAULT_FLOOD_PLAIN` is empty, so a type that has not been given a floodplain material leaves the
valley floor to whatever biome it runs through rather than guessing.

`DEFER` exists so a claimed column need not paint every layer: `C`, `E` and `F` defer their floodplain's
top block, keeping the biome's own grass while replacing the material underneath it.

`HydrologicalFeature.profileFor` resolves a packed `RIVER_TYPE` tag to a profile. It lives on the family
enum rather than on the primitive because the surface path holds a packed tag and never a primitive
instance. Every family but `RIVER` answers `DefaultProfile.INSTANCE`, whose `riverPaintDepth` returns
zero — so a newly added feature type stays as invisible to the surface as it already is to the carve.

**`RadialProfile`** is the radial twin of `RosgenProfile`: same split, where the enum constant owns the
shape and the carve owns the walk. `CONFLUENCE` scours a rounded parabolic floor that holds its depth
well out toward the rim (converging flow); `SOURCE` cuts a linear cone, giving up depth evenly from a
point (a spring notch, not a pool); `ABANDONED_RIVER` uses the same parabola as `CONFLUENCE` scaled to
0.4x depth (a cutoff trace silts shallower than an active pool). `MARGIN_NORM` (0.5) and
`FLOOD_PLAIN_NORM` (0.75) are the two control points the bed carve bands a disc's raw radius scale
against — a disc's bed is its inner half, matching it running to `width()`, twice a channel's painted
bed. Both are authored gates, not measurements, the same way `RosgenProfile`'s per-type constants are.

**Painter** (`HydrologyProfilePainter`): reads `Types.RIVER_DIFFERENCE` — the delta the bed carve
wrote — to compute `riverWaterTop` (fills water down to `reliefHeight - diff` where `diff < 0`)
and tests channel membership (`insideChannel`, `pt` within `width/2` of a primitive) via
`RiverProvider.anyInfluencingPrimitive`.

**`ZoneCategory` is reserved, not live.** No carve path reads it: `HydrologyProfile` carries no
`categoryAt`/`zoneWeight` counterpart, and the distance-weighted blend `../carvers/README.md` describes
is the only merge rule. The enum and the `WATERFALL`/`LAKE_BED` reservations referenced from
`WaterfallPrimitive` and `OxbowLakePrimitive` javadoc remain as the intended home for those feature types
once they grow real profiles.

## Design decisions / known limitations

**A `CONFLUENCE`/`SOURCE`/`ABANDONED_RIVER` cell is not painted.** `HydrologicalFeature.profileFor`
defaults to `DefaultProfile`, whose `riverPaintDepth` returns zero, so a cell the bed carve stamps in
`typeMask` for a radial primitive falls through to the vanilla surface rules rather than the riverbed
materials a `RIVER` cell gets — a deliberate scope cut, not an oversight. In practice this is moot for a
radial primitive specifically: `../carvers/README.md`'s Merge law notes a radial primitive never writes
`typeMask` at all, so the cell it claims carries whatever tag (or none) an earlier primitive left.

**Bed-trench depth is a hard-coded function of width, not a true cross-section.** `RosgenProfile
.delta` computes the bed depth as `FractalTerrainConfig.GLOBAL_SCALE_CORRECTION *
ChannelGeometry.depth(width)`, and `ChannelGeometry.depth` is a pure empirical width-to-depth law with no
dependence on the surrounding terrain, valley shape, or local slope at that point on the channel. Every
Rosgen type's `bedDelta` override reshapes that same width-derived depth across the channel's
cross-section (e.g. `C`'s asymmetric `smoothMax` shelf, `D`'s noise-perturbed braid), but none of them
measure an actual terrain cross-section to carve against — the trench is the same shape and depth
wherever a reach's width is the same, independent of what terrain it cuts through. Treat any visual
"the riverPrimitive doesn't look like it's cutting into this hillside" result as expected behavior of this
design, not a bug in a specific `RosgenProfile` constant.

**Only `RosgenProfile.A` overrides everything a type needs.** `B`, `C`, `D`, `E`, `F`, `G`, `Aa` each
override a subset of `floodPlainLength`/`bedDelta`/`floodPlainDelta`/`valleyDelta`; `DA` overrides
nothing and silently falls back to every enum-level default. A primitive with a `null` `rosgenType` (untyped
— e.g. emitted by a `null` `ChannelTyper`) is coalesced to `RosgenType.A` by `RosgenCarvedPrimitive`'s
default `getProfile()`, the one place `rosgenType` resolves to a profile — shared by `RiverPrimitive` and
`OxbowLakePrimitive`, neither of which overrides it — not left unhandled.

**Every carve uses a hard `min`, not the blended one.** Both lattice carves recover their published
elevation with `Math.min` against ambient — see `../carvers/README.md`'s Cut-only section. No call site
calls `RosgenProfile.smoothMin` — the smooth minimum that would round the rim where a valley cone meets
untouched ground, taking a `lambda` blend-range parameter the caller would have to choose. `smoothMin`
exists on `RosgenProfile` but has no caller anywhere in `src/main` (`smoothMax`, its counterpart, is used
inside `C`'s `bedDelta`). Expect a visible crease at the carve boundary for as long as that holds.
