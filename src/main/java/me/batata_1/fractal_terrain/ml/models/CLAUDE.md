# models/

ONNX Runtime wrapper and model-asset management. Loading runs on a background thread started from
`init()` (MUST-2), not a static initializer.

## Files

| File                     | What                                                                   | When to read                                        |
| ------------------------ | ---------------------------------------------------------------------- | --------------------------------------------------- |
| `PipelineModels.java`    | Background `load()` + `awaitLoad()` latch; holds the loaded sessions   | Model load lifecycle, load-failure surfacing        |
| `OnnxModel.java`         | ONNX Runtime wrapper with single-model GPU-slot VRAM swapping          | Running inference, VRAM offload behavior            |
| `ModelAssetManager.java` | Ensures model assets exist locally and match expected SHA-256 hashes   | Asset download/verification, datapack weights       |
