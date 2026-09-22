# carvers/

The lattice carve: the shell and bed passes both drive one merge law, dispatched per primitive.

## Files

| File                  | What                                                                                          | When to read                                                              |
| ---------------------- | ---------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| `README.md`            | The two passes, the one merge law, the dispatch seam, the oxbow-throw unreachability evidence   | Onboarding to carving, changing stage responsibilities                     |
| `LatticeCarve.java`     | The stateless orchestration: `carveInfluenceGrid` (shell) and `computeBedGrid` (bed), the thread-local `GridBuffers` each call site sizes, `ShellGrid`/`BedGrid`, and the shared `BED_EDGE`/`FLOODPLAIN_EDGE`/`UNSET_MIN_DIST` constants | Carving the valley shell or the bed, buffer sizing, adding a call site      |
| `BedCarver.java`        | Every cross-section the bed pass knows how to cut — `carve` (rectangle/tangent) and `carveRadial` (disc) — plus the shared `band` remap | Adding a bed footprint shape, changing the banded distance law             |
| `InfluenceCarver.java`  | Every cross-section the shell pass knows how to cut — `carveRosgenInfluence` (rectangle/tangent) and `carveRadialInfluence` (disc) | Adding a shell footprint shape, changing the shell dispatch                |
