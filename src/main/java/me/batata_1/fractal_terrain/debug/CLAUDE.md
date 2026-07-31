# debug/

Logging facade (`Debug.getLogger`) plus PNG/TIFF visualizers and manual `main()` harnesses.

## Files

| File                          | What                                                                | When to read                                          |
| ----------------------------- | ------------------------------------------------------------------- | ----------------------------------------------------- |
| `Debug.java`                  | `getLogger(Class)` logging facade + debug entry points              | Setting up a logger, debug hooks                      |
| `Infinite3DVisualizer.java`   | 3D visualization of infinite-tensor/generation output               | Visualizing generated volumes                         |
| `InstanceStageDumper.java`    | Dumps per-stage pipeline output for a loaded instance               | Dumping pipeline stage tensors                        |
| `TensorVisualizer.java`       | Renders a `FloatTensor`/infinite tensor to an image                 | Visualizing tensor channels                           |
| `NoiseVisualizer.java`        | Renders noise-sampler output to an image                            | Debugging noise samplers                              |
| `RiverNetworkVisualizer.java` | Renders a riverUnit network to an image                                 | Debugging riverUnit topology                              |
| `HydrologyUnitVisualizer.java`| Renders the hydrological-unit index to an image                     | Debugging the units spatial index                     |
| `SplineVisualizer.java`       | Renders splines to an image                                         | Debugging spline fitting                              |
| `MemoryProfiler.java`         | Heap/memory sampling helper                                         | Profiling memory during generation                    |
| `TiffConverter.java`          | Converts dumps to/from TIFF                                         | Reading/writing TIFF debug output                     |

## Subdirectories

| Directory | What                                             | When to read                                    |
| --------- | ------------------------------------------------ | ----------------------------------------------- |
| `tests/`  | Manual `main()` harnesses run as Gradle `JavaExec` | Running `pipelineTest`/`globalRiverTest`/etc.  |
