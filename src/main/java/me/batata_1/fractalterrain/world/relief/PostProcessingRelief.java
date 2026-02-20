package me.batata_1.fractalterrain.world.relief;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.mojang.datafixers.util.Pair;
import me.batata_1.fractalterrain.ml.Models;
import me.batata_1.fractalterrain.ml.diffusion.Stages;
import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.storage.StorageInterface;
import me.batata_1.fractalterrain.storage.Tile;
import me.batata_1.fractalterrain.storage.TileRegion;
import me.batata_1.fractalterrain.world.ContinentalScaleMapProvider;
import me.batata_1.fractalterrain.world.gen.densityfunction.FractalTerrainDensityFunctionTypes;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static me.batata_1.fractalterrain.FractalTerrainInstance.ENV;
import static me.batata_1.fractalterrain.ml.tensorProviders.GaussianNoisePatchProvider.sampleNoise;
import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.util.DebugTensors.isNan;
import static me.batata_1.fractalterrain.util.DebugTensors.seeTensor;

import static me.batata_1.fractalterrain.util.MlUtil.*;

public class PostProcessingRelief {

    private final DecodeAndFinish decodeAndFinish;
    private final OrtSession average;
    private final OrtSession take_coarse_grad;

    private final EntryStorage<Tile> final_tiles;
    private final EntryStorage<Tile> final_raw_tiles;

    public PostProcessingRelief(FractalTerrainDensityFunctionTypes.RefinedElevation.Settings settings) {
        this.decodeAndFinish = new DecodeAndFinish(settings);
        take_coarse_grad = Models.getOrCreateModel("ml_util/take_coarse_grad");
        average = Models.getOrCreateModel("ml_util/average");
        final_raw_tiles = new EntryStorage<>("final_raw",Tile::new,64, (xz) -> {
            try {
                OnnxTensor t = (OnnxTensor) take_coarse_grad.run(
                        Map.of("x",(OnnxTensor) average.run(
                                Map.of("x", decodeAndFinish.getCoarseTilesAsTensor(xz.getFirst(), xz.getSecond())
                                        )).get(0)
                        )).get(0);
                return new Tile(t);
            } catch (OrtException | IOException | ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        final_tiles = new EntryStorage<>("final",Tile::new,512,(xz) -> {
            int x =  xz.getFirst()<<1;
            int z = xz.getSecond()<<1;
            try {
                OnnxTensor t = (OnnxTensor) average.run(Map.of("x",decodeAndFinish.getTilesAsTensor(x,z))).get(0);
                seeTensor(t,"final" + (x>>1) +" " +(z>>1),false,1);
                return new Tile(t);
            } catch (ExecutionException | IOException | InterruptedException | OrtException e) {
                throw new RuntimeException(e);
            }
        });
    }


    public float getElev(Pair<Integer,Integer> xz) {
        return getValue(xz,1);
    }

    public float getRefinedGrad(Pair<Integer,Integer> xz) {
        return getValue(xz,2);
    }

    public float getBlurredGrad(Pair<Integer,Integer> xz) {
        return getValue(xz,3);
    }

    private float getValue(Pair<Integer,Integer> xz ,int ch){
        try {
            return final_tiles.getValue(xz,ch);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    // global coords -> correct to get coarse value
    private Pair<Integer,Integer> convertCoarse(Pair<Integer,Integer> xz) {
        return xz;
    }

    private float getCoarseValue(Pair<Integer,Integer> xz , int ch) {
        try {
            return final_raw_tiles.getValue(convertCoarse(xz),ch);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public float getContinentalElev(Pair<Integer,Integer> xz) {
        return getElev(xz);
    }

    public float getRawGrad(Pair<Integer,Integer> xz) {
//        if(final_raw_tiles.inBorder(convertCoarse(xz))) return Gradients.entryGradMagnitude(convertCoarse(xz).getFirst(), convertCoarse(xz).getSecond(),0,final_raw_tiles);
//        return getCoarseValue(xz,1);
        return (float) Math.tanh(getBlurredGrad(xz)/1000.0);
    }

    public float getRawTemp(Pair<Integer,Integer> xz) {

        double val = ContinentalScaleMapProvider.sampleTemperature(convertCoarse(xz).getFirst(), convertCoarse(xz).getSecond());
        return (float) val;
    }

    public float getRawTempSTD(Pair<Integer,Integer> xz) {
        return getCoarseValue(xz,3);
    }

    public float getRawPrecip(Pair<Integer,Integer> xz) {
        return (float) ContinentalScaleMapProvider.samplePrecipitation(convertCoarse(xz).getFirst(),convertCoarse(xz).getSecond());
    }

    public float getRawPrecipSTD(Pair<Integer,Integer> xz) {
        return getCoarseValue(xz,5);
    }

    public static class DecodeAndFinish extends StorageInterface {

        private final OrtSession finisher;
        private final OrtSession to_height;
        private final OrtSession decoder;
        private final Stages.LatentStage latent;
        private final FractalTerrainDensityFunctionTypes.RefinedElevation.Settings settings;

        public DecodeAndFinish(FractalTerrainDensityFunctionTypes.RefinedElevation.Settings settings) {
            super(new EntryStorage<>("decoder", TileRegion::new,256),256*256*2, new long[]{2,256,256});
            this.settings = settings;

            Stages.LatentStage.initModels();
            latent = new Stages.LatentStage();
            decoder = Models.getOrCreateModel("models/run_decoder");
            to_height = Models.getOrCreateModel("ml_util/to_elevation");
            finisher = Models.getOrCreateModel("ml_util/finisher");

        }

        @Override
        public OnnxTensor[] runInference(int x, int z) throws ExecutionException, OrtException, InterruptedException, IOException {

            var xz = Pair.of(x,z);

            var inputs = Map.of(
                    "latents",latent.getTilesAsTensor(x,z),
                    "noise",sampleNoise(xz,new long[]{1,512,512},4)
            );

            inputs = Map.of(
                    "residual_init",(OnnxTensor) decoder.run(inputs).get(0),
                    "latents_init",latent.getTilesAsTensor(x,z)
            );

            var out_to_h = (OnnxTensor) to_height.run(inputs).get(0);
            LOGGER.info("out shappeee {}",out_to_h.getInfo().getShape());
            LOGGER.info("printf settins: {}",settings);
            inputs = Map.of(
                    "x",out_to_h,
                    "alpha",OnnxTensor.createTensor(ENV,settings.alpha()),
                    "beta",OnnxTensor.createTensor(ENV,settings.beta()),
                    "gamma",OnnxTensor.createTensor(ENV,settings.gamma()),
                    "grad_blur",OnnxTensor.createTensor(ENV,settings.grad_blur())
            );

            var out = (OnnxTensor) finisher.run(inputs).get(0);
            isNan(out);
            return slice(out);
        }

        public OnnxTensor getCoarseTilesAsTensor(int x , int z) throws IOException, ExecutionException, OrtException, InterruptedException {
            return latent.getCoarseModel().getTilesAsTensor(x,z);
        }


    }
}
