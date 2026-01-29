package me.batata_1.fractalterrain.ml;//package me.joaopalmeiras.terrenomod.ml;
import static me.batata_1.fractalterrain.references.Reference.LOGGER;


import ai.onnxruntime.*;

import com.twelvemonkeys.imageio.plugins.tiff.TIFFImageWriter;

import net.minecraft.resource.Resource;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

import net.minecraft.util.math.random.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static me.batata_1.fractalterrain.references.Reference.ModID;
import static net.minecraft.util.math.MathHelper.nextGaussian;

public class teste {

    public static void inference(MinecraftServer server) throws OrtException, IOException {
        var env = OrtEnvironment.getEnvironment();

        Identifier modelId = Identifier.of(ModID, "model/teste.onnx");

        Optional<Resource> resource = server.getResourceManager().getResource(modelId);
        InputStream inputStream = resource.get().getInputStream();
        byte[] modelArr = inputStream.readAllBytes();

        OrtSession session = env.createSession(modelArr);

        FloatBuffer bufferedNoise = FloatBuffer.allocate(512);
        for(int i=0 ; i<512 ; i++) {
            bufferedNoise.put( nextGaussian(Random.create(),0,1) );
        }
        bufferedNoise.flip();

        OnnxTensor noise = OnnxTensor.createTensor(env, bufferedNoise, new long[]{1, 512, 1, 1});
        OnnxTensor step = OnnxTensor.createTensor( env, new int[]{5} );
        OnnxTensor alpha = OnnxTensor.createTensor(env , new double[]{1.0});

        bufferedNoise.clear();

        var inputs = Map.of("x",noise , "alpha",alpha);

        try( var out = session.run(inputs) ) {
            LOGGER.info("conseguiu afsdghadsfgaijdfgiuadfgiuadffgiudaiofugiaudfgbiuadfgiu");
            OnnxTensor output = (OnnxTensor) out.get(0);

            LOGGER.info("shape do cara:" + Arrays.toString(output.getInfo().getShape()));

            long[] shape = output.getInfo().getShape(); // [1, 2, 128, 128]
            int channels = (int) shape[1];
            int height = (int) shape[2];
            int width = (int) shape[3];

            // Get the flat float buffer
            FloatBuffer buffer = output.getFloatBuffer();
            float[] flat = new float[channels * height * width];
            buffer.get(flat);

            // We want only tensor[0][0], which corresponds to the first 128x128 block
            float[][] firstChannel = new float[height][width];
            int offset = 0; // start of channel 0

            for (int i = 0; i < height; i++) {
                System.arraycopy(flat, offset + i * width, firstChannel[i], 0, width);
            }

            WritableRaster raster = Raster.createBandedRaster(3,width,height,1, new Point(0,0));

            float max_in_d = 1765.0F;

            for(int i=0 ; i<128 ; i++) {
                for(int j=0 ; j<128 ; j++) {
                    int value = (int) (((firstChannel[i][j] + 1) * max_in_d) / 2);
                    raster.setSample(i, j, 0, value);
                }
            }

            Path path = server.getSavePath(WorldSavePath.ROOT).normalize();
            File outputFile = new File(path + "/tensor_first_channel.tiff");
            LOGGER.info("O caminho eh: {}" , outputFile.getPath());
            TIFFImageWriter writer = (TIFFImageWriter) ImageIO.getImageWritersByFormatName("TIFF").next();

            ColorModel cm = new ComponentColorModel(
                    ColorSpace.getInstance(ColorSpace.CS_GRAY),
                    new int[]{32}, false, false, Transparency.OPAQUE, DataBuffer.TYPE_FLOAT);

            RenderedImage img = new RasterRenderedImage(raster, cm);
            writer.setOutput(ImageIO.createImageOutputStream(outputFile));
            writer.write( null , new IIOImage(img , null , null) , null);
        }
    }

    public static void main(String[] args) throws OrtException {
        LOGGER.info("oiiiiiiii");

    }

}
