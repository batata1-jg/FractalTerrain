# Plan

## Overview

FractalTerrain traces global (meander) rivers and local (drainage) rivers as two separate representations inside LocalRiverProvider.buildTile: the global RiverNetwork graph, and a detached list of local Channels gated by a per-pixel boolean globalMask. Each is beddedand converted to HydrologicalUnits by its own path (ChannelElevationAssigner.assign for global; a decoded-terrain lerp in collectUnits plus a separate addLocalChannelUnits for local), so bed math diverges and the mask is a second source of truth for the global rivers. The goal is one unified per-tile RiverNetwork in which local rivers are first-class graph members joined to the global meander graph, so a single bed-assignment pass and a single collectUnits serve both, and the pixel mask is dropped entirely.

**Approach**: Land the unification as ordered, individually-green milestones: (1) make QuinticHermiteSpline expose its arc-length t-list and Channel resample bedElevations on that basis (null-safe); (2) extract a reusable insert-specs API from the RiverNetwork constructor; (3) relocate the single bed-assign call out of GlobalNetworkBuilder into buildTile (still global-only, behavior-preserving); (4) switch collectUnits to read assigned bedElevations with an oxbow fallback; (5) KEYSTONE: make the local trace mutate the graph in place, drop rasterizeGlobalMask in favor of proximity-to-global-channel (LOCAL_ATTACH_RADIUS) for both reach-seeding and walk-termination, attach local segments as SOURCE/JUNCTION-split/coast-DRAIN edges, then run ONE assign and ONE collectUnits over the unified graph with crop/shift only at the end; (6) update the debug Stages/harness and stale javadoc. The four JavaExec visual harnesses plus pipelineTest are the regression gate; the local JUnit golden converts to structural invariants while the Meanders and GlobalRiver goldens stay byte-exact.

## Planning Context

### Decision Log

| ID | Decision | Reasoning Chain |
|---|---|---|
| DL-001 | Unify local and global rivers into ONE per-tile RiverNetwork graph; local channels become first-class graph edges joined to the global meander graph. | Two parallel representations (global RiverNetwork + a separate local Channel list gated by a pixel mask) force two bed passes and two collectUnits paths -> divergent bed math and a mask/graph dual source of truth -> collapse both into the single graph so one assign() and one collectUnits serve both. |
| DL-002 | QuinticHermiteSpline.reSample surfaces its arc-length t-list (newT); Channel resamples bedElevations on that identical basis. Reject re-walking arc length inside Channel. | Bed values are per-point scalars aligned to spline points -> if bed is resampled via an independent second arc-length binary search, the two walks can diverge and misalign bed to the new points -> expose the exact newT the geometry resample used so bed and points share one sampling basis (keystone H1). |
| DL-003 | Channel.reSample resamples bedElevations when non-null and no-ops bed when null. | reSample is invoked with bedElevations==null during network construction and every meander relaxation step -> unconditionally resampling bed would NPE or fabricate zeros there -> guard: resample bed only once it has been assigned, leaving the Meanders relaxation path byte-identical (H2). |
| DL-004 | Drop rasterizeGlobalMask entirely; proximity to a global channel replaces BOTH the mask exclusion and the reach seed, derived from a fresh point index over sim.getChannels(). Reject keeping any per-pixel global boolean mask. | A pixel mask is a second representation of the global rivers that must be kept in sync with the graph -> user directive drops the mask -> the local walk terminates when it comes within LOCAL_ATTACH_RADIUS of a global channel (replacing !globalMask) and computeReaches seeds from coast(elev<0) plus cells adjacent to a global channel (replacing the mask seed), both read from a point index built over the graph. |
| DL-005 | traceLocalNetwork mutates the sim network in place (void return); each local segment is inserted as SOURCE -> (split JUNCTION on nearest global channel | coast DRAIN) edges via a reusable insert-specs API extracted from the RiverNetwork constructor. | Local channels must be graph members for a single bed pass to reach them -> the constructor is the only code that mints nodes/edges from specs -> extract that into insertSpecs() callable post-construction, then the tracer attaches each segment: ridge seed as SOURCE, downstream end split()s the nearest global channel to mint a JUNCTION, or terminates at a coast DRAIN (user directive: tracer returns nothing). |
| DL-006 | A single ChannelElevationAssigner.assign runs in buildTile AFTER local insertion over the whole unified graph. GlobalNetworkBuilder.build returns the network plus its boundaryElevByNodeIdx map instead of calling assign itself; buildTile augments that map with local sources and coast drains before the single assign. | assign propagates junction elevations across the graph and floors each path at its terminal drain -> if it runs before locals join, local nodes get datum 0.0 and never propagate -> move the single assign to after insertion, and feed boundaryElevByNodeIdx entries for local ridge sources (decoded terrain at the seed, floored at the coast) and coast drains (bilinear terrain datum) so every path is seeded (H4). |
| DL-007 | collectUnits only resamples geometry and reads Channel.bedElevations; it never invents elevations. It serves global and local uniformly. Oxbows/removedPaths keep an explicit decoded-terrain elevation fallback. | User directive: collectUnits must not invent elevations -> replace the decodedElev max/lerp derivation with a read of the assigned bedElevations resampled onto the width/2 basis via Channel.reSample -> but oxbow/abandoned paths are non-Channel NaN-endpoint splines with no bedElevations, so removing the sampler orphans their elevation -> retain an ElevationSampler solely as the oxbow fallback (H3). |
| DL-008 | Add HydrologyTuning.LOCAL_ATTACH_RADIUS as a first-cut, untuned proximity constant calibrated visually via localRiverTest. Channel.bedElev(t) uses a linear blend on the covering segment (id=floor(t), u=t-id). | The proximity radius replaces the crisp pixel boundary: too small yields parallel double rivers, too large truncates local detail -> its right value needs runtime visual calibration, mirroring the existing Meanders.MAX_MARGIN_FRACTION first-cut-untuned pattern -> ship a documented first-cut constant. bedElevations carry no velocity/acceleration, so bedElev can only blend linearly, using the same segment indexing as sample() per the user directive. |
| DL-009 | buildTile crops to GRID and shifts coordinates only at the very end. The local trace runs in the GRID frame and rejects channels crossing the TRUE tile boundary; local coords are shifted +PAD when inserted into the PADDED-frame graph. | The current crop/shift-then-shift-back dance interleaves frame changes mid-pipeline -> moving the local trace onto the padded graph changes what onBorder means -> keep onBorder testing the true tile boundary (GRID-based) so edge-crossing channels are still rejected, and confine the crop-to-512 plus coordinate shift to a single final step (H5). |
| DL-010 | Behavior-regression gate is the four JavaExec visual harnesses plus pipelineTest. LocalRiverGoldenTest converts from a bit-exact channel checksum to structural invariants; MeandersGoldenTest and GlobalRiverGoldenTest stay bit-exact-passing as guards. Reject re-baselining the local bit-exact checksum. | The redesign intentionally changes local network output and drops the boolean-mask seam the golden checksum was built on, and LOCAL_ATTACH_RADIUS is uncalibrated -> a frozen checksum would demand re-baselining on every calibration and cannot even compile against the new void/mask-free seam -> assert invariants instead (locals attach to the graph, bed monotone non-increasing downstream, determinism across runs); Meanders/GlobalRiver goldens stay byte-exact to guard the null-bed no-op and the untouched coarse path. |
| DL-011 | The commented-out local shell carve in buildTile is left disabled, not silently re-enabled; the straddling-tile-edge floodplain seam risk is documented. | Local networks are traced with no coarse halo, so a local shell can be truncated at the PAD=1 border -> re-enabling the carve would reintroduce that accepted seam artifact unannounced -> keep it disabled and record the risk so a future decision to enable it is explicit. |
| DL-012 | A single shared int[] feature-id counter assigns tile-unique ids to every unit emitted by the unified collectUnits (global, local, oxbow), rather than per-source counters or namespaced id ranges. | After unification global and local units flow through ONE collectUnits over ONE graph -> if each source kept its own counter or id namespace, ids would collide in the shared ImmutableRTree or need cross-source reconciliation -> thread a single monotonically-incrementing shared int[] counter through the one emission pass so every unit gets a distinct tile-unique id with zero cross-source coordination. |
| DL-013 | A local segment whose downstream walk reaches neither a global channel within LOCAL_ATTACH_RADIUS nor a coast DRAIN (i.e. it dead-ends in the tile interior) is DROPPED, not force-attached as a dangling node. | fillSinks runs before the trace, so the drainage field carries no interior pits and a downstream flow walk should normally exit to a coast or reach a global channel -> but a segment can still terminate interior if it leaves riverMask short of the attach radius -> inventing a datum-less dangling DRAIN would make assign() floor that path at 0.0 and corrupt bed; instead drop such orphan segments, and document the fillSinks-implies-no-interior-sink premise so the drop stays a rare edge case rather than a silent policy default. |
| DL-014 | All build/test/verify steps in this plan run through the cached gradle 9.2.1 (not PATH's 8.14), and every Python invocation uses the `py` launcher, not `python3`. | The dev box exposes gradle 8.14 on PATH and has no `python3` alias (MEMORY build-and-run-environment + context.json MUST) -> running the wrong gradle or a missing python3 makes spotlessApply/harness/pipelineTest steps fail spuriously and mask real regressions -> pin every gradle command to the cached 9.2.1 and every script to `py`, so the regression gate (M-003..M-006) actually exercises the code rather than a tooling error. |
| DL-015 | When the grid-frame reach/adjacency seed and the continuous attach-time distance check (nearest global-channel point <= LOCAL_ATTACH_RADIUS) disagree at the radius boundary for a local segment's downstream end, the continuous attach check is authoritative: a segment the attach check rejects is dropped per DL-013, never force-attached, even if the grid reach seed marked it reachable. | The reach seed is computed on integer grid cells while attachment uses a continuous nearest-point distance, so the two can disagree by up to a cell at the radius boundary -> if the grid seed 'wins', a segment could be routed to split() with no global point actually within radius, crashing or minting a dangling junction -> make the continuous distance check the single source of truth for attachment so boundary disagreements resolve to a clean DL-013 drop rather than an unattached/dangling edge. |

### Constraints

- MUST: no behavior regression provable via existing JavaExec harnesses (globalRiverTest, localRiverTest, meandersTest, pipelineTest).
- MUST: run gradle spotlessApply before committing (palantirJavaFormat enforced by build).
- MUST: build via the cached gradle 9.2.1 (not PATH 8.14); the Python launcher is py, not python3 (see DL-014).
- MUST: collectUnits must NOT invent elevations -- only resample geometry and read Channel.bedElevations (user directive).
- MUST: traceLocalNetwork returns nothing -- it mutates the sim network in place (user directive).
- MUST: rasterizeGlobalMask dropped entirely -- no per-pixel global boolean mask anywhere (user decision: drop the mask entirely).
- SHOULD: keep RiverNetwork per-tile, single-threaded, with no cross-tile shared state (existing invariant).

## Invisible Knowledge

### System

Keystone H1: bedElevations must be resampled on the SAME newT arc-length basis the geometry resample used, or bed misaligns to points -- hence QuinticHermiteSpline.reSample must surface newT rather than each consumer re-walking arc length. H2: Channel.reSample is called with bedElevations==null in the RiverNetwork constructor, LocalDrainageTracer channel build, and every Meanders step, so bed resample must no-op when null. H3: oxbow/abandoned removedPaths are non-Channel NaN-endpoint splines with no bedElevations, so dropping the decoded-elevation sampler orphans their elevation -- keep an explicit fallback. H4: the single assign must run AFTER local insertion and boundaryElevByNodeIdx must gain entries for local ridge sources and coast drains, or locals get datum 0.0. H5: the local tracer hardcodes GRID in onBorder/neighborIndex; onBorder must keep testing the TRUE tile boundary, not the padded edge. Drop-the-mask means proximity to a global channel (LOCAL_ATTACH_RADIUS) replaces both the !globalMask exclusion (walk termination) and the mask reach-seed (adjacency), read from a fresh point index over sim.getChannels() -- the transient Meanders collision QuadTree is cleared each step and cannot be reused. A single shared int[] feature-id counter keeps global+local units in one tile-unique id space. GlobalRiverGoldenTest exercises only the coarse GlobalRiverProvider path (computeTileForTest), so relocating assign() does not touch it; only LocalRiverGoldenTest rides the changed local path.

### Invariants

- RiverNetwork is per-tile, single-threaded, no cross-tile shared state.
- The network is a dendritic in-tree: every node has at most one outgoing edge; junctions are minted only via split().
- HydrologicalUnit record shape and the ImmutableRTree index type are unchanged.
- collectUnits never invents elevations -- it only resamples geometry and reads Channel.bedElevations (oxbow fallback aside).
- traceLocalNetwork returns nothing; it mutates the sim network in place.
- No per-pixel global boolean mask exists anywhere after this work.
- Consecutive units resample spacing stays <= half the narrowest taper width so influence discs never gap.
- The GlobalNetworkBuilder owned-cell topology and gate-jitter/relax constants are unchanged.

### Tradeoffs

- LOCAL_ATTACH_RADIUS ships as a first-cut untuned constant (mirrors Meanders.MAX_MARGIN_FRACTION): too small yields parallel double rivers, too large truncates local detail -- calibrated visually via localRiverTest.
- LocalRiverGoldenTest trades a brittle bit-exact checksum for structural invariants because local output legitimately changes and the proximity radius is uncalibrated.
- The local shell carve stays disabled (as it already is), leaving the straddling-tile-edge floodplain seam accepted rather than silently re-enabling it.
- bedElev(t) is a linear blend (bed carries no velocity/acceleration), not a quintic evaluation, even though it borrows sample()'s segment indexing.

## Milestones

### Milestone 1: Spline/Channel bed-resample foundation

**Files**: src/main/java/me/batata_1/fractal_terrain/math/spline/QuinticHermiteSpline.java, src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/Channel.java

**Flags**: needs-rationale

**Requirements**:

- QuinticHermiteSpline exposes the arc-length t-list its reSample builds; Channel gains bedElev(t) and resamples bedElevations on that same t-basis when present
- no-op when null.

**Acceptance Criteria**:

- MeandersGoldenTest and GlobalRiverGoldenTest pass byte-identically (bed path is null-no-op on both); a Channel.reSample on a channel with bedElevations produces one bed value per resampled point aligned to that point.

#### Code Intent

- **CI-M-001-001** `src/main/java/me/batata_1/fractal_terrain/math/spline/QuinticHermiteSpline.java::reSample`: Resampling exposes the arc-length parameter list (newT) it builds by binary search, in addition to the resampled spline, so a caller can resample a parallel per-point array on the identical basis. The existing reSample(samplingDist) keeps returning only the resampled spline with byte-identical geometry; a companion form surfaces the t-list (e.g. a record of {spline, double[] ts}). Callers that only need geometry are unaffected. (refs: DL-002)
- **CI-M-001-002** `src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/Channel.java::reSample`: reSample(samplingDist) resamples the geometry and, when bedElevations is non-null, rebuilds bedElevations by evaluating bedElev(t) at each newT the spline resample used, so bed stays index-aligned to the resampled points. When bedElevations is null it is left null (network construction and meander relaxation are unaffected). bedElev(double t) returns a linear blend of bedElevations[floor(t)] and bedElevations[floor(t)+1] at u = t - floor(t), clamped to the valid segment range, mirroring sample()s segment indexing. (refs: DL-002, DL-003, DL-008)

### Milestone 2: Reusable insert-specs API on RiverNetwork

**Files**: src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/RiverNetwork.java

**Flags**: needs-rationale

**Requirements**:

- Node/edge insertion from NodeSpec/EdgeSpec lists is a reusable method callable after construction; the constructor delegates to it and it returns a local-spec-index to minted-node-id map.

**Acceptance Criteria**:

- MeandersGoldenTest passes byte-identically (constructor path behavior unchanged).

#### Code Intent

- **CI-M-002-001** `src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/RiverNetwork.java::insertSpecs`: A reusable method inserts a batch of NodeSpec and EdgeSpec entries into the live graph (minting Endpoints and Channels, resampling each channel at the given distance, wiring outgoing/incoming and enforcing the single-outflow invariant) and returns the mapping from each supplied node-spec index to the minted node id. The constructor delegates its node/edge construction to this method. Post-construction callers (the local tracer) use the returned map to reference freshly minted nodes. (refs: DL-005)

### Milestone 3: Relocate bed assignment to buildTile; build returns boundary map

**Files**: src/main/java/me/batata_1/fractal_terrain/hydrology/GlobalNetworkBuilder.java, src/main/java/me/batata_1/fractal_terrain/hydrology/LocalRiverProvider.java, src/main/java/me/batata_1/fractal_terrain/hydrology/ChannelElevationAssigner.java

**Flags**: needs-rationale

**Requirements**:

- GlobalNetworkBuilder.build returns the Meanders network together with its boundaryElevByNodeIdx map and no longer invokes assign; LocalRiverProvider.buildTile invokes ChannelElevationAssigner.assign after the global build.

**Acceptance Criteria**:

- GlobalRiverGoldenTest passes; localRiverTest and pipelineTest produce visually unchanged global bed elevations and monotone-downstream node elevations (LocalRiverTest.checkMonotonicElevations reports no violations).

#### Code Intent

- **CI-M-003-001** `src/main/java/me/batata_1/fractal_terrain/hydrology/GlobalNetworkBuilder.java::build`: build returns the relaxed Meanders network together with the boundaryElevByNodeIdx map it accumulated (source/drain node datum), and no longer calls ChannelElevationAssigner.assign. The boundary map is no longer cleared inside build since the caller consumes it. (refs: DL-006)
- **CI-M-003-002** `src/main/java/me/batata_1/fractal_terrain/hydrology/LocalRiverProvider.java::buildTile`: buildTile receives the network and boundary map from GlobalNetworkBuilder.build and invokes ChannelElevationAssigner.assign(network, boundaryElev, base[0]) at the global stage, preserving the prior global bed assignment result. This relocates the assign call site out of the builder without changing the global-only outcome. (refs: DL-006)
- **CI-M-003-003** `src/main/java/me/batata_1/fractal_terrain/hydrology/ChannelElevationAssigner.java::assign`: assign is reachable from LocalRiverProvider.buildTile (package-visible) and operates on whatever network and boundary map it is handed; it carries no assumption that the network is global-only, so a later unified graph is assigned by the same method. (refs: DL-006)

### Milestone 4: collectUnits reads assigned bedElevations

**Files**: src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/RiverNetwork.java

**Flags**: needs-rationale

**Requirements**:

- collectUnits resamples each channel via Channel.reSample and reads bedElevations for RIVER units instead of deriving bed from decoded terrain; removedPaths/oxbows sample decoded terrain as their elevation fallback.

**Acceptance Criteria**:

- At M-004 only GLOBAL channels carry assigned bedElevations because local unification is deferred to M-005
- localRiverTest and pipelineTest render GLOBAL river units whose bed is monotone non-increasing downstream and matches assigned node/junction elevations
- local channels still emit via the unmigrated addLocalChannelUnits/decoded-lerp path and are explicitly out of scope for this milestone bed check
- the bed-monotonicity assertion is scoped to global-only channels and is satisfiable global-only
- oxbow/abandoned features still carry a finite elevation (no NaN units)

#### Code Intent

- **CI-M-004-001** `src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/RiverNetwork.java::collectUnits`: For each active RIVER channel, collectUnits calls Channel.reSample(width/2) and reads the resampled bedElevations directly as each units elevation, without sampling decoded terrain or re-deriving a lerp. For removedPaths (oxbow/abandoned) which have no bedElevations, it samples the supplied ElevationSampler at each point as the elevation fallback. The ElevationSampler parameter is retained solely for that oxbow fallback. Per-point width still lerps start->end and coordinates still subtract the offset. (refs: DL-007, DL-003)
- **CI-M-004-002** `src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/RiverNetwork.java::addFeatureUnits`: The per-feature emitter reads a resampled bed array aligned to the resampled points for graph channels (bed comes from Channel.reSample), and only falls back to the ElevationSampler for features lacking bedElevations. It no longer computes bed as max(decoded,endElev) lerped by fraction for channels. (refs: DL-007)

### Milestone 5: Unify local network into the graph and drop the mask

**Files**: src/main/java/me/batata_1/fractal_terrain/hydrology/LocalDrainageTracer.java, src/main/java/me/batata_1/fractal_terrain/hydrology/LocalRiverProvider.java, src/main/java/me/batata_1/fractal_terrain/config/HydrologyTuning.java, src/test/java/me/batata_1/fractal_terrain/hydrology/LocalRiverGoldenTest.java

**Flags**: needs-rationale, error-handling

**Requirements**:

- traceLocalNetwork mutates the sim network in place with no globalMask parameter
- using a point index over the graph for proximity; rasterizeGlobalMask and addLocalChannelUnits are removed; local segments attach to the graph as SOURCE/JUNCTION(split)/coast-DRAIN edges; HydrologyTuning gains LOCAL_ATTACH_RADIUS; buildTile inserts locals
- augments the boundary map
- runs one assign and one collectUnits over the unified graph
- and crops/shifts only at the end; the golden-test seam feeds a synthetic global network.

**Acceptance Criteria**:

- localRiverTest shows local rivers joining the global channels with no parallel double-river artifact and no local channel crossing the tile edge; pipelineTest completes a full tile; LocalRiverGoldenTest (structural invariants) passes; every source->drain path in the unified network is monotone non-increasing.

#### Code Intent

- **CI-M-005-001** `src/main/java/me/batata_1/fractal_terrain/hydrology/LocalDrainageTracer.java::traceLocalNetwork`: traceLocalNetwork takes the drainage field, elevation, the live RiverNetwork (or a point index over its channels) and mutates that network in place, returning nothing. It builds a point index over the global channel points. computeReaches seeds from coast cells (elev<0) and cells within LOCAL_ATTACH_RADIUS of a global channel point (replacing the mask seed). riverMask excludes cells within LOCAL_ATTACH_RADIUS of a global channel (replacing the !globalMask term). Each surviving segment that stays interior to the true tile boundary is attached: its upstream ridge cell becomes a SOURCE node; its downstream end either splits the nearest global channel (network.split(channelId,pos,redirect) minting a JUNCTION) when a global-channel point lies within LOCAL_ATTACH_RADIUS, or terminates at a coast DRAIN (elev<0); the segment is then inserted as a graph edge via RiverNetwork.insertSpecs with coordinates shifted by +PAD into the padded graph frame. TERMINATION FALLBACK (DL-013/DL-015): a segment whose downstream end finds neither a global-channel point within LOCAL_ATTACH_RADIUS nor a coast drain is discarded and NOT inserted (no dangling edge); the continuous attach-time distance check is authoritative over the grid reach seed at the radius boundary, so a boundary disagreement resolves to a clean drop rather than a force-attach. DEGENERATE INPUTS (error-handling): if the walk yields no surviving segments the method is a no-op and leaves the network unchanged; if the global point index is empty (no global channels), no split target exists so segments can only attach via a coast DRAIN and every non-coast segment is dropped rather than dereferencing a missing nearest point. rasterizeGlobalMask and addLocalChannelUnits are removed. (refs: DL-004, DL-005, DL-008, DL-009, DL-013, DL-015)
- **CI-M-005-002** `src/main/java/me/batata_1/fractal_terrain/config/HydrologyTuning.java::LOCAL_ATTACH_RADIUS`: A constant giving the native-px proximity radius at which a local river is considered to meet a global channel: it gates both the reach seed adjacency and the walk-termination exclusion, and the junction-attachment split. It is a first-cut, untuned value documented as pending visual calibration via localRiverTest, mirroring the MAX_MARGIN_FRACTION first-cut pattern. (refs: DL-008)
- **CI-M-005-003** `src/main/java/me/batata_1/fractal_terrain/hydrology/LocalRiverProvider.java::buildTile`: buildTile builds the global network, then calls the in-place local trace to attach the local network to the same graph, augments boundaryElevByNodeIdx with local ridge SOURCE data (decoded terrain at the seed, floored at the coast datum) and coast DRAIN data (bilinear terrain datum), runs ONE ChannelElevationAssigner.assign over the unified graph, then ONE collectUnits over the unified network to emit all units. Sink-fill/drainage still feed the local trace. DEGENERATE CASE (error-handling): if the local trace attached nothing -- empty trace, or no global channel and no coast reachable so every segment was dropped per DL-013 -- the unified graph is exactly the global graph and the single assign/collectUnits proceed unchanged over global-only members, with no special-casing and no failure. Cropping to GRID and the coordinate shift happen only in a single final step; the shiftUnits round-trip is eliminated where the single collectUnits already emits tile-local coordinates. Harness/verify runs driven by buildTile follow DL-014 (cached gradle 9.2.1, `py` launcher). (refs: DL-006, DL-007, DL-009, DL-011, DL-013, DL-014)
- **CI-M-005-004** `src/test/java/me/batata_1/fractal_terrain/hydrology/LocalRiverGoldenTest.java::localNetworkMatchesGolden`: The headless seam feeds a synthetic global RiverNetwork (a central trunk channel) plus the synthetic drainage/elevation, runs the in-place unified trace, and asserts structural invariants instead of a frozen checksum: at least one local channel attaches to the global trunk (a JUNCTION is minted or a channel ends on the trunk), every source->drain bed sequence is monotone non-increasing, no channel crosses the tile edge, and five runs are bit-identical to each other (determinism). (refs: DL-010)
- **CI-M-005-005** `src/main/java/me/batata_1/fractal_terrain/hydrology/LocalDrainageTracer.java::traceLocalNetwork (edge/failure handling)`: Edge and failure handling for the in-place unified trace. (a) Empty trace: when zero local segments survive the riverMask/reach filtering, the network is left exactly as the global build produced it and the single assign + single collectUnits run on the global-only graph (locals are a no-op, no empty-collection crash). (b) No global channel near a segment: a segment whose downstream end finds no global channel point within LOCAL_ATTACH_RADIUS attaches only if it reaches a coast DRAIN (elev<0). (c) Interior dead-end: a segment reaching neither a global channel within LOCAL_ATTACH_RADIUS nor a coast DRAIN is DROPPED (per DL-013), never inserted as a datum-less dangling node that assign() would floor to 0.0. (d) Tile with no global channels at all still yields a valid unit set from coast-draining locals; the fresh point index built over sim.getChannels() tolerates an empty channel list without NPE. (refs: DL-004, DL-005, DL-013)

### Milestone 6: Harness, Stages, and stale-javadoc cleanup

**Files**: src/main/java/me/batata_1/fractal_terrain/hydrology/LocalRiverProvider.java, src/main/java/me/batata_1/fractal_terrain/debug/tests/LocalRiverTest.java, src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/Channel.java, src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/RiverNetwork.java, src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/Endpoint.java

**Flags**: documentation

**Requirements**:

- The Stages debug struct and LocalRiverTest render local versus global channels from the unified graph; javadoc on Channel.bedElevations
- RiverNetwork.collectUnits
- and Endpoint.elevation describes the unified single-assign/read-bedElevations behavior.

**Acceptance Criteria**:

- All four JavaExec harnesses (globalRiverTest
- localRiverTest
- meandersTest
- pipelineTest) run; gradle spotlessApply leaves the tree clean; no javadoc references the dropped mask or the decoded-elevation lerp.

#### Code Intent

- **CI-M-006-001** `src/main/java/me/batata_1/fractal_terrain/hydrology/LocalRiverProvider.java::Stages`: The Stages debug struct exposes the single unified network and a way to distinguish local from global channels (e.g. a set of local channel ids or a tag), so the harness can still render the two colorings from one graph. (refs: DL-010)
- **CI-M-006-002** `src/main/java/me/batata_1/fractal_terrain/debug/tests/LocalRiverTest.java::dumpTile`: The harness rasterizes local versus global channels from the unified graph using the Stages local/global distinction, and its monotonicity check walks the unified source->drain paths (now including local sources). (refs: DL-010)
- **CI-M-006-003** `src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/Channel.java::bedElevations`: The javadoc on bedElevations states that collectUnits reads it (true after unification) and that Channel.reSample keeps it index-aligned, dropping any claim tying it only to LocalRiverProvider. (refs: DL-007)
- **CI-M-006-004** `src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/RiverNetwork.java::collectUnits`: The collectUnits javadoc describes reading per-point bedElevations (with the decoded-terrain fallback only for oxbows) rather than the removed start->end decoded lerp, and notes the unit list now covers global and local graph members in one pass. (refs: DL-007)

## Execution Waves

- W-001: M-001
- W-002: M-002
- W-003: M-003
- W-004: M-004
- W-005: M-005
- W-006: M-006
