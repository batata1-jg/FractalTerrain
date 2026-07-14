# client/

Client-only source set: the client mod initializer and the Fabric data generator entrypoint.

## Files

| File                              | What                                                        | When to read                                |
| --------------------------------- | ----------------------------------------------------------- | ------------------------------------------- |
| `FractalterrainClient.java`       | `ClientModInitializer` (client startup; currently empty)    | Adding client-side init (rendering, screens) |
| `FractalterrainDataGenerator.java`| `DataGeneratorEntrypoint` for `gradle runDatagen`           | Adding datagen providers/packs               |
