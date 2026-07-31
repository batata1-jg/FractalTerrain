# meanders/

Meander relaxation over the river network; see `README.md` for the per-tile/single-threaded contract.
The graph itself lives in `../network/`.

## Files

| File                | What                                                                        | When to read                                                  |
| ------------------- | ---------------------------------------------------------------------------- | -------------------------------------------------------------- |
| `README.md`         | Per-tile single-threaded contract, relaxation ordering                      | Changing relaxation or reusing a simulation across tiles       |
| `Meanders.java`     | Relaxes network geometry into meanders; owns the pre-carve elevation snapshot and `collectUnits` | Meander migration, border damping, unit emission, `meandersTest` |
