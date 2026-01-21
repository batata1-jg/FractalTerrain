package me.batata_1.fractalterrain.ml;//package me.joaopalmeiras.terrenomod.ml;
//
//import java.awt.*;
//import java.awt.image.*;
//import java.util.Vector;
//
//public class RasterRenderedImage implements RenderedImage {
//    private final Raster raster;
//    private final ColorModel colorModel;
//
//    public RasterRenderedImage(Raster raster, ColorModel colorModel) {
//        this.raster = raster;
//        this.colorModel = colorModel;
//    }
//
//    @Override
//    public Raster getData() { return raster; }
//    @Override
//    public Raster getData(Rectangle rect) { return raster; }
//    @Override
//    public WritableRaster copyData(WritableRaster wr) { return (WritableRaster) raster; }
//    @Override
//    public Vector<RenderedImage> getSources() { return null; }
//    @Override
//    public Object getProperty(String name) { return java.awt.Image.UndefinedProperty; }
//    @Override
//    public String[] getPropertyNames() { return null; }
//    @Override
//    public ColorModel getColorModel() { return colorModel; }
//    @Override
//    public SampleModel getSampleModel() { return raster.getSampleModel(); }
//    @Override
//    public int getWidth() { return raster.getWidth(); }
//    @Override
//    public int getHeight() { return raster.getHeight(); }
//    @Override
//    public int getMinX() { return raster.getMinX(); }
//    @Override
//    public int getMinY() { return raster.getMinY(); }
//    @Override
//    public int getNumXTiles() { return 1; }
//    @Override
//    public int getNumYTiles() { return 1; }
//    @Override
//    public int getMinTileX() { return 0; }
//    @Override
//    public int getMinTileY() { return 0; }
//    @Override
//    public int getTileWidth() { return getWidth(); }
//    @Override
//    public int getTileHeight() { return getHeight(); }
//    @Override
//    public int getTileGridXOffset() { return 0; }
//    @Override
//    public int getTileGridYOffset() { return 0; }
//    @Override
//    public Raster getTile(int tileX, int tileY) { return raster; }
//}
