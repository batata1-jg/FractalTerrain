# meanders/

Point-migration models driven over the river network; see `README.md` for the per-tile/single-threaded
contract. The graph itself lives in `../network/` and is injected, not owned.

## Files

| File                              | What                                                                                                  | When to read                                                             |
| --------------------------------- | ----------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| `README.md`                       | Per-tile single-threaded contract, relaxation ordering                                                | Changing relaxation or reusing a simulation across tiles                   |
| `ChannelMigrator.java`            | The shared per-step driver: resample → migrate → resolve endpoints → cutoffs → collisions, plus border damping | Adding a migration model, changing step ordering, debug step dumps |
| `Meanders.java`                   | The Ikeda-Parker-Sawai curvature-driven migration rule, gradient-attenuated. Runs in `RiverProvider.computeTile` | Meander migration math, gradient attenuation, `meandersTest`               |
| `GradientNetworkRelaxation.java`  | The terrain-driven rule: slides points down the decoded elevation gradient. Runs in `GlobalNetworkBuilder.build` | Global-network relaxation, gradient sampling                          |

