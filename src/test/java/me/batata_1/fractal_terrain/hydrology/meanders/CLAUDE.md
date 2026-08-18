# meanders/ (test)

Goldens for meander relaxation and the `RiverNetwork` canonical↔atomic seam.

## Files

| File                             | What                                                            | When to read                                                    |
| -------------------------------- | --------------------------------------------------------------- | --------------------------------------------------------------- |
| `MeandersGoldenTest.java`        | Bit-exact migration signature + the three stream-capture outcomes | Changing relaxation, migration, or collision handling           |
| `RiverNetworkSeamGoldenTest.java`| Round-trip idempotence of `viewAtomic`/`accumulateAndCorrectFlow`/`update` | Changing the seam, flow derivation, or `update`'s id rules |

Both signatures are bit-exact; read `src/main/java/me/batata_1/fractal_terrain/hydrology/network/README.md`
before re-baselining either.
