# network/

The river graph and every topology or geometry mutation performed on it; see `README.md` for the
canonical↔atomic seam.

## Files

| File                  | What                                                                                   | When to read                                                            |
| --------------------- | --------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `README.md`           | The canonical↔atomic seam, `update` id rules, stream capture, planarization, invariants | Mutating the graph, debugging lost ids or flow, before changing the seam |
| `RiverNetwork.java`   | The graph: channels, nodes, the view seam, stream capture, and `collectPrimitives` — the single entry point every carve and the published index both collect through, taking a coordinate offset, a channel-id filter, a `ChannelTyper` and an influence sampler; also emits `ConfluencePrimitive`/`SourcePrimitive` discs at junction/source endpoints, gated on which incident channels actually emitted a `RiverPrimitive` | Graph topology, `update` id rules, collision handling, emitting primitives    |
| `Centreline.java`     | Cross-section normals from an arc-length stencil that hops across channel boundaries via graph links, instead of a channel's own spline tangent | Changing the normal stencil, junction hop/tie-break rules; consumed by `RiverNetwork.collectPrimitives` and `ReachRosgenClassifier` |
| `AtomicView.java`     | Node-per-spline-point adjacency, plus flow accumulation and edge planarization          | Per-point algorithms, flow derivation, crossing-edge resolution          |
| `Channel.java`        | One directed edge: spline points, per-point flow and bed elevation                      | Channel geometry, resampling, flow/bed interpolation                     |
| `Endpoint.java`       | A graph vertex; SOURCE / JUNCTION / DRAIN and the single-outflow rule                   | Node types, boundary flow seeding, the K1 invariant                      |
| `ChannelTyper.java`   | Interface returning one type per spline point, implemented by the Rosgen classifier     | Adding a typing strategy, the typer/topology responsibility split        |
