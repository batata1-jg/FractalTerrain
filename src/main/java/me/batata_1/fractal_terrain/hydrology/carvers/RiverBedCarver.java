package me.batata_1.fractal_terrain.hydrology.carvers;

import me.batata_1.fractal_terrain.config.HydrologyTuning;

public interface RiverBedCarver {

    static double band(double raw, double margin, double floodPlain) {
        final double bedSlope = margin > 0.0 ? 0.25 / margin : 0.0;
        if (raw <= margin) return raw * bedSlope;
        final double floodPlainSlope = floodPlain > margin ? 0.25 / (floodPlain - margin) : 0.0;
        if (raw <= floodPlain) return 0.25 + (raw - margin) * floodPlainSlope;
        final double outerSlope = floodPlain < 1.0 ? 0.5 / (1.0 - floodPlain) : 0.0;
        return 0.5 + (raw - floodPlain) * outerSlope;
    }

    static void carve(
            float[] lut,
            int baseIdx,
            float[] acc,
            float[] dist,
            long[] typeMask,
            float[] elevs,
            double[] perpRow,
            double[] tangRow,
            double[] perpCol,
            double[] tangCol,
            double cx,
            double cz,
            double nx,
            double nz,
            double floodPlainLen,
            double marginLen,
            double waterSurface,
            double influenceLen,
            double influenceWidth,
            long type,
            double startX,
            double startZ,
            double resolution,
            int gridSize) {

        final double halfExtentX = influenceLen * Math.abs(nz) + influenceWidth * Math.abs(nx);
        final double halfExtentZ = influenceLen * Math.abs(nx) + influenceWidth * Math.abs(nz);
        final long rowLo = (long) Math.floor((cx - halfExtentX - startX) / resolution);
        final long rowHi = (long) Math.ceil((cx + halfExtentX - startX) / resolution);
        final long colLo = (long) Math.floor((cz - halfExtentZ - startZ) / resolution);
        final long colHi = (long) Math.ceil((cz + halfExtentZ - startZ) / resolution);
        if (rowHi < 0 || rowLo > gridSize - 1 || colHi < 0 || colLo > gridSize - 1) return;
        final int rowMin = (int) Math.max(rowLo, 0);
        final int rowMax = (int) Math.min(rowHi, gridSize - 1);
        final int colMin = (int) Math.max(colLo, 0);
        final int colMax = (int) Math.min(colHi, gridSize - 1);

        // perp is affine in the lattice coordinates, so its extrema over the clipped box are at the four
        // corners. Intersecting with the influence band is what caps the LUT at the grid diagonal.
        final double x0 = startX + rowMin * resolution, x1 = startX + rowMax * resolution;
        final double z0 = startZ + colMin * resolution, z1 = startZ + colMax * resolution;
        final double p00 = nx * (x0 - cx) + nz * (z0 - cz);
        final double p01 = nx * (x0 - cx) + nz * (z1 - cz);
        final double p10 = nx * (x1 - cx) + nz * (z0 - cz);
        final double p11 = nx * (x1 - cx) + nz * (z1 - cz);
        final double perpMin = Math.max(Math.min(Math.min(p00, p01), Math.min(p10, p11)), -influenceWidth);
        final double perpMax = Math.min(Math.max(Math.max(p00, p01), Math.max(p10, p11)), influenceWidth);
        if (perpMin > perpMax) return;

        final double invStep = 1.0 / resolution;

        // :PERF: both projections are affine, so each splits into a row term and a column term; tabulating
        // the two axes costs 2 * gridSize entries and lets the merge rebuild any point with one add.
        for (int row = rowMin; row <= rowMax; row++) {
            final double ddx = (startX + row * resolution) - cx;
            perpRow[row] = nx * ddx;
            tangRow[row] = nz * ddx;
        }
        for (int col = colMin; col <= colMax; col++) {
            final double ddz = (startZ + col * resolution) - cz;
            perpCol[col] = nz * ddz;
            tangCol[col] = -nx * ddz;
        }

        final double invLen = 1.0 / influenceLen;
        final double invWidth = 1.0 / influenceWidth;
        // Control points clamped into [0, 1] and into order. floodPlainLength is a free per-type law:
        // RosgenProfile.E returns less than marginLen at maximum width, a minimum-influence primitive
        // can push its floodplain past its own rim, and the HydrologyProfile default returns exactly
        // marginLen. Each inversion would give the band a negative slope.
        final double marginNorm = Math.min(Math.max(marginLen * invLen, marginLen * invWidth), 1.0);
        final double floodPlainNorm =
                Math.min(Math.max(Math.max(floodPlainLen * invLen, floodPlainLen * invWidth), marginNorm), 1.0);

        for (int row = rowMin; row <= rowMax; row++) {
            final int rowBase = row * gridSize;
            final double perpAtRow = perpRow[row];
            final double tangAtRow = tangRow[row];
            for (int col = colMin; col <= colMax; col++) {
                final int i = rowBase + col;
                final double perp = perpAtRow + perpCol[col];
                final double tang = tangAtRow + tangCol[col];
                // How far the footprint rectangle must be scaled to swallow the point: 1 exactly at the
                // rim, so the recurrence ranks primitives by rectangle penetration, not radial distance.
                final double raw = Math.max(Math.abs(tang) * invLen, Math.abs(perp) * invWidth);
                final double d = band(raw, marginNorm, floodPlainNorm);
                // Tested on the raw scale rather than the banded one: where floodPlainNorm clamps to 1
                // the band saturates at FLOODPLAIN_EDGE and a point past the rim would read as in-band.
                final double mask = raw <= 1.0 ? 1.0 : 0.0;
                final double t = Math.clamp(((dist[i] - d) / HydrologyTuning.PRIMITIVE_BLEND_STRENGTH + 1) * 0.5, 0, 1);
                final double w = t * t * (3.0 - 2.0 * t) * mask;
                final double f = perp * invStep - baseIdx;
                // Clamped for safety only: mask already zeroes anything out of band, but the branch-free
                // body still evaluates h for those lanes.
                final int i0 = Math.clamp((int) f, 0, lut.length - 2);
                final double h = (elevs != null)
                        ? Math.min(elevs[i], lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0]))
                        : lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0]);

                dist[i] = (float) ((1 - w) * dist[i] + w * d);
                final int a = 3 * i;
                acc[a] = (float) ((1 - w) * acc[a] + w * h);
                acc[a + 1] = (float) ((1 - w) * acc[a + 1] + w * waterSurface);
                // Whoever owns the majority of the blend owns the type. With SMOOTH_STEP_DIVISOR at 0.1
                // the weight is a near-hard selector, so this is the true nearest bar a 0.1-wide band.
                typeMask[i] = w > 0.5 ? type : typeMask[i];
                acc[a + 2] = 1 - Math.clamp(dist[i], 0, 1);
            }
        }
    }
}
