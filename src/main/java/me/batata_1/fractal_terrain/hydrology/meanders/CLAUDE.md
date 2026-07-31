# meanders/

Meander relaxation network; see `README.md` for the per-tile/single-threaded contract.

## Files

| File                | What                                                          | When to read                                        |
| ------------------- | ------------------------------------------------------------ | --------------------------------------------------- |
| `Meanders.java`     | Mutates a riverUnit network into meandering geometry (relaxation) | Meander relaxation algorithm, `meandersTest`        |
| `RiverNetwork.java` | Mutable per-tile graph of channels and endpoints; owns the collision pass | Network topology, node/edge structure, `manageCollisions` |
| `AtomicView.java`   | Per-point view of the graph (every interior spline point is a node); the seam all mutation flows through | Adding nodes/edges, flow accumulation, `viewAtomic`/`update` |
| `Channel.java`      | A single riverUnit channel (edge) in the network                 | Channel segment geometry, connectivity              |
| `Endpoint.java`     | A network node (confluence/source/mouth)                     | Endpoint semantics, degree, connections             |
