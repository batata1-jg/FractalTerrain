# models/

ONNX Runtime wrapper and model-asset management.

## Files

| File                     | What                                                                   | When to read                                        |
| ------------------------ | ---------------------------------------------------------------------- | --------------------------------------------------- |
| `PipelineModels.java`    | Background `load()` + `awaitLoad()` latch; holds the four loaded `OnnxModel` instances (coarse, base, decoder, fuzed) | Model load lifecycle, load-failure surfacing        |
| `OnnxModel.java`         | ONNX Runtime wrapper with single-model GPU-slot VRAM swapping          | Running inference, VRAM offload behavior            |
| `ModelAssetManager.java` | Ensures model assets exist locally and match expected SHA-256 hashes   | Asset download/verification, datapack weights       |
