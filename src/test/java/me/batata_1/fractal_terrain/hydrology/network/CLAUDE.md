# network/ (test)

## Files

| File                    | What                                                                                     | When to read                                                        |
| ------------------------ | ----------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `CentrelineTest.java`   | Gates `Centreline.normalAt`: wedged-channel through-direction, one-sided source stencil, junction tie-break determinism | Changing the cross-section normal stencil, hop-across-junction rules, or the flow tie-break |
| `RiverNetworkHistoryTest.java` | The history deque: what a cutoff sheds, the age window that evicts it, `remapHistory` resolution, and the frame shift on emission | Changing what the network records as history, the eviction window, or how shed primitives reach `collectPrimitives` |
