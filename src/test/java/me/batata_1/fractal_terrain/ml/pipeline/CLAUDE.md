# pipeline/ (test)

Tests for the ONNX diffusion pipeline's session lifecycle.

## Files

| File                                | What                                                        | When to read                                            |
| ----------------------------------- | ----------------------------------------------------------- | ------------------------------------------------------- |
| `PipelineSessionReloadRaceTest.java`| Concurrent reload/use of a pipeline session                 | Changing session lifetime, model offload, or reload      |
