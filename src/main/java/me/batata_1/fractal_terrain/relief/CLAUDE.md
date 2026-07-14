# relief/

Decodes the diffusion decoder residual into relief elevation and rock strata. `DecoderChannels` is a
stateless shared decode used by both `ReliefProvider` and `LocalRiverProvider` to avoid a provider cycle.

## Files

| File                   | What                                                                     | When to read                                    |
| ---------------------- | ------------------------------------------------------------------------ | ----------------------------------------------- |
| `ReliefProvider.java`  | Imports carved elevation + decodes residual → `[RELIEF_CHANNELS=7,512,512]` | Relief elevation output, per-tile relief cache |
| `DecoderChannels.java` | Stateless weight-normalized decode of the decoder slice (shared helper)  | Decoder channel normalization, avoiding provider cycle |
| `RockStrata.java`      | Rock-layer strata sampling (spline + noise)                              | Rock/deepslate layering during chunk fill        |
