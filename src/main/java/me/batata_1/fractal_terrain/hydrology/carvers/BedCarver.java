package me.batata_1.fractal_terrain.hydrology.carvers;

import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.profile.RadialProfile;

/**
 * Every cross-section the bed pass knows how to cut, reached from a primitive's own {@code carveBed}.
 * A footprint shape is a method here rather than a class of its own, so the pass's whole repertoire
 * reads in one place; {@link InfluenceCarver} is the shell pass's twin.
 */
public final class BedCarver {

    private BedCarver() {}

    /**
     * A raw footprint scale remapped onto the banded coordinate the paint side reads. Bed and floodplain
     * assert themselves in the merge, and a consumer classifies against {@link LatticeCarve#BED_EDGE}
     * and {@link LatticeCarve#FLOODPLAIN_EDGE} without access to the primitive.
     */
    // :PERF: six primitive parameters instead of a control-point object; this runs per lattice point,
    // and an object would allocate per primitive and dispatch per point.
    public static double band(
            double raw,
            double marginNorm,
            double floodPlainNorm,
            double bedSlope,
            double floodPlainSlope,
            double outerSlope) {
        if (raw <= marginNorm) return raw * bedSlope;
        if (raw <= floodPlainNorm) return LatticeCarve.BED_EDGE + (raw - marginNorm) * floodPlainSlope;
        return LatticeCarve.FLOODPLAIN_EDGE + (raw - floodPlainNorm) * outerSlope;
    }

    public static void carve(
            HydrologicalPrimitive owner,
            LatticeCarve.BedGrid grid,
            double cx,
            double cz,
            double nx,
            double nz,
            double influenceLen,
            double influenceWidth,
            double marginLen,
            double floodPlainLen,
            float waterSurface,
            long type) {
        final int gridSize = grid.gridSize();
        final double startX = grid.startX();
        final double startZ = grid.startZ();
        final double resolution = grid.resolution();
        final float[] acc = grid.acc();
        final long[] typeMask = grid.typeMask();
        final float[] dist = grid.dist();
        final float[] lut = grid.lut();
        final double[] perpRow = grid.perpRow();
        final double[] perpCol = grid.perpCol();
        final double[] tangRow = grid.tangRow();
        final double[] tangCol = grid.tangCol();
        final float[] elevs = grid.elevs();

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
        final int baseIdx = (int) Math.floor(perpMin * invStep);
        final int n = (int) Math.floor(perpMax * invStep) - baseIdx + 2;
        owner.tabulateBedLut(lut, baseIdx, n, resolution);

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
        // :PERF: reciprocals hoisted per primitive; the merge loop below runs per lattice point and
        // carries no division. A zero denominator means the piece it scales is empty, so the slope is
        // never read and 0 keeps it finite.
        final double bedSlope = marginNorm > 0.0 ? LatticeCarve.BED_EDGE / marginNorm : 0.0;
        final double floodPlainSlope = floodPlainNorm > marginNorm
                ? (LatticeCarve.FLOODPLAIN_EDGE - LatticeCarve.BED_EDGE) / (floodPlainNorm - marginNorm)
                : 0.0;
        final double outerSlope =
                floodPlainNorm < 1.0 ? (1.0 - LatticeCarve.FLOODPLAIN_EDGE) / (1.0 - floodPlainNorm) : 0.0;

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
                final double d = band(raw, marginNorm, floodPlainNorm, bedSlope, floodPlainSlope, outerSlope);
                // Tested on the raw scale rather than the banded one: where floodPlainNorm clamps to 1
                // the band saturates at FLOODPLAIN_EDGE and a point past the rim would read as in-band.
                final double mask = raw <= 1.0 ? 1.0 : 0.0;
                final double t = Math.clamp(((dist[i] - d) / HydrologyTuning.PRIMITIVE_BLEND_STRENGTH + 1) * 0.5, 0, 1);
                final double w = t * t * (3.0 - 2.0 * t) * mask;
                final double f = perp * invStep - baseIdx;
                // Clamped for safety only: mask already zeroes anything out of band, but the branch-free
                // body still evaluates h for those lanes.
                final int i0 = Math.clamp((int) f, 0, n - 2);
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

    /**
     * One disc's contribution, clipped to the lattice points it reaches. The circle admits no affine
     * row/column split, so the distance is computed per cell rather than tabulated per axis.
     */
    public static void carveRadial(
            HydrologicalPrimitive owner,
            LatticeCarve.BedGrid grid,
            double cx,
            double cz,
            double radius,
            float waterSurface) {
        final int gridSize = grid.gridSize();
        final double startX = grid.startX();
        final double startZ = grid.startZ();
        final double resolution = grid.resolution();
        final float[] acc = grid.acc();
        final float[] dist = grid.dist();
        final float[] lut = grid.lut();
        final float[] elevs = grid.elevs();

        // :PERF: conservative AABB clip; floor/ceil so a too-wide range is harmless while a too-narrow
        // one would silently drop carve -- the exact disc test still runs per lattice point.
        final long rowLo = (long) Math.floor((cx - radius - startX) / resolution);
        final long rowHi = (long) Math.ceil((cx + radius - startX) / resolution);
        final long colLo = (long) Math.floor((cz - radius - startZ) / resolution);
        final long colHi = (long) Math.ceil((cz + radius - startZ) / resolution);
        if (rowHi < 0 || rowLo > gridSize - 1 || colHi < 0 || colLo > gridSize - 1) return;
        final int rowMin = (int) Math.max(rowLo, 0);
        final int rowMax = (int) Math.min(rowHi, gridSize - 1);
        final int colMin = (int) Math.max(colLo, 0);
        final int colMax = (int) Math.min(colHi, gridSize - 1);

        // The LUT spans only the radii the clipped box actually reaches. This is what caps n at the
        // grid diagonal: a full-radius table would want radius/resolution entries, which at
        // GRID_RESOLUTION overruns what maxLutLen sizes the buffer for.
        final double x0 = startX + rowMin * resolution, x1 = startX + rowMax * resolution;
        final double z0 = startZ + colMin * resolution, z1 = startZ + colMax * resolution;
        final double nearX = Math.max(0.0, Math.max(x0 - cx, cx - x1));
        final double nearZ = Math.max(0.0, Math.max(z0 - cz, cz - z1));
        final double radMin = Math.sqrt(nearX * nearX + nearZ * nearZ);
        final double farX = Math.max(Math.abs(x0 - cx), Math.abs(x1 - cx));
        final double farZ = Math.max(Math.abs(z0 - cz), Math.abs(z1 - cz));
        final double radMax = Math.min(Math.sqrt(farX * farX + farZ * farZ), radius);
        if (radMin > radMax) return;

        final double invStep = 1.0 / resolution;
        final int baseIdx = (int) Math.floor(radMin * invStep);
        final int n = (int) Math.floor(radMax * invStep) - baseIdx + 2;
        final double invRadius = 1.0 / radius;
        owner.tabulateBedLut(lut, baseIdx, n, resolution);

        // :PERF: slopes hoisted per primitive; the merge loop below runs per lattice point and carries
        // no division. Both control points are constants, so no denominator here can be zero.
        final double bedSlope = LatticeCarve.BED_EDGE / RadialProfile.MARGIN_NORM;
        final double floodPlainSlope = (LatticeCarve.FLOODPLAIN_EDGE - LatticeCarve.BED_EDGE)
                / (RadialProfile.FLOOD_PLAIN_NORM - RadialProfile.MARGIN_NORM);
        final double outerSlope = (1.0 - LatticeCarve.FLOODPLAIN_EDGE) / (1.0 - RadialProfile.FLOOD_PLAIN_NORM);

        for (int row = rowMin; row <= rowMax; row++) {
            final int rowBase = row * gridSize;
            final double ddx = (startX + row * resolution) - cx;
            for (int col = colMin; col <= colMax; col++) {
                final int i = rowBase + col;
                final int a = 3 * i;
                final double ddz = (startZ + col * resolution) - cz;
                // A circle admits no affine row/column split the way a rectangle's two projections do,
                // so the true distance is computed per cell rather than tabulated per axis.
                final double rad = Math.sqrt(ddx * ddx + ddz * ddz);
                final double raw = rad * invRadius;
                final double d = band(
                        raw,
                        RadialProfile.MARGIN_NORM,
                        RadialProfile.FLOOD_PLAIN_NORM,
                        bedSlope,
                        floodPlainSlope,
                        outerSlope);
                // Tested on the raw scale rather than the banded one, as the rectangle carve is: the
                // band saturates at the rim and a point past it would otherwise read as in-band.
                final double mask = raw <= 1.0 ? 1.0 : 0.0;
                final double t = Math.clamp(((dist[i] - d) / HydrologyTuning.PRIMITIVE_BLEND_STRENGTH + 1) * 0.5, 0, 1);
                final double w = t * t * (3.0 - 2.0 * t) * mask;

                final double f = rad * invStep - baseIdx;
                final int i0 = Math.clamp((int) f, 0, n - 2);
                final double sampled = lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0]);
                // Capped against real ambient elevation, so the bowl blends against the ground it stands
                // on rather than against a merged surface an earlier primitive already cut.
                final double h = (elevs != null) ? Math.min(elevs[i], sampled) : sampled;

                dist[i] = (float) ((1 - w) * dist[i] + w * d);
                acc[a] = (float) ((1 - w) * acc[a] + w * h);
                acc[a + 1] = (float) ((1 - w) * acc[a + 1] + w * waterSurface);
                acc[a + 2] = 1 - Math.clamp(dist[i], 0, 1);
            }
        }
    }
}
