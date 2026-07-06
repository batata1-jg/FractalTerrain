package me.batata_1.fractal_terrain.math.ds;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/**
 * The simplest {@link SpatialIndexPoint}: a bare coordinate with no payload. Replaces the former direct
 * instantiation of the (now interface) {@code SpatialIndexPoint} class — used by visualizers and spatial
 * helpers that only need positions (e.g. coast-cell indexing, spline rasterization).
 */
public record CoordPoint(double[] coords) implements SpatialIndexPoint {

    @Override
    public double[] getCoords() {
        return coords;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CoordPoint(double[] coords1) && Arrays.equals(coords, coords1);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(coords);
    }

    @Override
    public @NotNull String toString() {
        return Arrays.toString(coords);
    }
}
