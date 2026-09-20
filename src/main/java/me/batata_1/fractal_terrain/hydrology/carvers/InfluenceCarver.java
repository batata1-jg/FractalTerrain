package me.batata_1.fractal_terrain.hydrology.carvers;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.features.RadialPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RosgenCarvedPrimitive;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;

/**
 * Every cross-section the shell pass knows how to cut, reached from a primitive's own
 * {@code carveInfluence}. A footprint shape is a method here rather than a class of its own, so the
 * pass's whole repertoire reads in one place; {@link RiverBedCarver} is the bed pass's twin.
 */
public final class InfluenceCarver {

    private InfluenceCarver() {}

    public static void carveRosgenInfluence(RosgenCarvedPrimitive primitive, RiverInfluenceCarve.ShellGrid grid) {
        final double[] normal = primitive.normal();
        // A null normal has no tangent -- the projection below would NPE.
        if (normal == null) return;
        // A zero-extent primitive (e.g. an OxbowLakePrimitive minted with influence=0, unresolved) makes
        // invLen/invWidth below Infinity and the merge below resolves to NaN written into elevs.
        if (primitive.getLength() <= 0 || primitive.getWidth() <= 0) return;
        // Deferred: elevation is NaN-sentinelled until RiverNetwork.remapHistory resolves it, which no
        // production caller does -- carving the sentinel would cut every unresolved primitive to NaN.
        if (Double.isNaN(primitive.elevation())) return;
        final int gridSize = grid.gridSize();
        final float[] acc = grid.acc();
        final float[] lut = grid.lut();
        final double[] perpRow = grid.perpRow();
        final double[] perpCol = grid.perpCol();
        final double[] tangRow = grid.tangRow();
        final double[] tangCol = grid.tangCol();
        final float[] elevs = grid.elevs();

        final double nx = normal[0], nz = normal[1];
        final double cx = primitive.coord()[0], cz = primitive.coord()[1];
        // Half-extents of the primitive's footprint rectangle: along the flow tangent (nz, -nx), and across
        // it along the normal. Read from the same accessors the spatial index stabs, so a primitive whose
        // rectangle stops being square carves the shape it was indexed under.
        final double influenceLen = primitive.getLength() * 0.5;
        final double influenceWidth = primitive.getWidth() * 0.5;

        // :PERF: conservative AABB clip; floor/ceil so a too-wide range is harmless while a too-narrow one
        // would silently drop carve -- the exact containment test still runs per lattice point.
        final double halfExtentX = influenceLen * Math.abs(nz) + influenceWidth * Math.abs(nx);
        final double halfExtentZ = influenceLen * Math.abs(nx) + influenceWidth * Math.abs(nz);
        final long rowLo = (long) Math.floor(cx - halfExtentX);
        final long rowHi = (long) Math.ceil(cx + halfExtentX);
        final long colLo = (long) Math.floor(cz - halfExtentZ);
        final long colHi = (long) Math.ceil(cz + halfExtentZ);
        if (rowHi < 0 || rowLo > gridSize - 1 || colHi < 0 || colLo > gridSize - 1) return;
        final int rowMin = (int) Math.max(rowLo, 0);
        final int rowMax = (int) Math.min(rowHi, gridSize - 1);
        final int colMin = (int) Math.max(colLo, 0);
        final int colMax = (int) Math.min(colHi, gridSize - 1);

        // perp is affine in the lattice coordinates, so its extrema over the clipped box are at the four
        // corners. Intersecting with the influence band is what caps the LUT at the grid diagonal.
        final double x0 = rowMin, x1 = rowMax, z0 = colMin, z1 = colMax;
        final double p00 = nx * (x0 - cx) + nz * (z0 - cz);
        final double p01 = nx * (x0 - cx) + nz * (z1 - cz);
        final double p10 = nx * (x1 - cx) + nz * (z0 - cz);
        final double p11 = nx * (x1 - cx) + nz * (z1 - cz);
        final double perpMin = Math.max(Math.min(Math.min(p00, p01), Math.min(p10, p11)), -influenceWidth);
        final double perpMax = Math.min(Math.max(Math.max(p00, p01), Math.max(p10, p11)), influenceWidth);
        if (perpMin > perpMax) return;

        final int baseIdx = (int) Math.floor(perpMin);
        final int n = (int) Math.floor(perpMax) - baseIdx + 2;

        final double width = primitive.width();
        final double curvature = primitive.curvature();
        // The assigner picks a bed elevation from the uncarved field, so a primitive whose neighbours have
        // already cut through this point would sit above them and fill rather than cut. Capping against
        // the surface merged so far keeps the influence carve cut-only.
        final double elevation = primitive.elevation();
        final RosgenProfile profile = (RosgenProfile) primitive.getProfile();
        final long seed = primitive.seed();
        final double floodPlainLen = profile.floodPlainLength(width);
        final double marginLen = width / 2;
        final double depth = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depth(width);
        profile.sampleCrossSection(lut, n, 1.0, baseIdx, seed, elevation, floodPlainLen, marginLen, depth, curvature);
        for (int i = 0; i < lut.length; i++) if (lut[i] < elevation) lut[i] = (float) elevation;

        // :PERF: both projections are affine, so each splits into a row term and a column term; tabulating
        // the two axes costs 2 * gridSize entries and lets the merge rebuild any point with one add.
        for (int row = rowMin; row <= rowMax; row++) {
            final double ddx = row - cx;
            perpRow[row] = nx * ddx;
            tangRow[row] = nz * ddx;
        }
        for (int col = colMin; col <= colMax; col++) {
            final double ddz = col - cz;
            perpCol[col] = nz * ddz;
            tangCol[col] = -nx * ddz;
        }

        final double invLen = 1.0 / influenceLen;
        final double invWidth = 1.0 / influenceWidth;
        final double floodPlainNormLen = Math.max(floodPlainLen * invLen, floodPlainLen * invWidth);
        final double invFlNormLenSlope = 1.0 / (1 - floodPlainNormLen);
        final double invFlNormLen = 1.0 / floodPlainNormLen;
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
                final double d = Math.max(Math.abs(tang) * invLen, Math.abs(perp) * invWidth);
                final double dd = 0.5
                        * (d > floodPlainNormLen ? (d - floodPlainNormLen) * invFlNormLenSlope + 1 : d * invFlNormLen);
                final double f = perp - baseIdx;
                // Clamped for safety only: mask already zeroes anything out of band, but the branch-free
                // body still evaluates h for those lanes.
                final int i0 = Math.clamp((int) f, 0, n - 2);

                final int a = 3 * i;
                final double h = lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0]);
                final float testeW = dd < 0.5 ? 1 : (float) (1 - Math.clamp(dd * 2 - 1, 0, 1));
                elevs[i] = (float) Math.min(elevs[i], acc[a] * (1 - testeW) + h * testeW);
            }
        }
    }

    public static void carveRadialInfluence(RadialPrimitive primitive, RiverInfluenceCarve.ShellGrid grid) {
        final double cx = primitive.coord()[0], cz = primitive.coord()[1];
        final double radius = primitive.getRadius();
        if (radius <= 0) return;
        // Deferred: elevation is NaN-sentinelled until RiverNetwork.remapHistory resolves it, which no
        // production caller does -- carving the sentinel would cut every unresolved primitive to NaN.
        if (Double.isNaN(primitive.elevation())) return;
        final int gridSize = grid.gridSize();
        final float[] acc = grid.acc();
        final float[] lut = grid.lut();
        final float[] elevs = grid.elevs();

        final long rowLo = (long) Math.floor(cx - radius);
        final long rowHi = (long) Math.ceil(cx + radius);
        final long colLo = (long) Math.floor(cz - radius);
        final long colHi = (long) Math.ceil(cz + radius);
        if (rowHi < 0 || rowLo > gridSize - 1 || colHi < 0 || colLo > gridSize - 1) return;
        final int rowMin = (int) Math.max(rowLo, 0);
        final int rowMax = (int) Math.min(rowHi, gridSize - 1);
        final int colMin = (int) Math.max(colLo, 0);
        final int colMax = (int) Math.min(colHi, gridSize - 1);

        final double x0 = rowMin, x1 = rowMax, z0 = colMin, z1 = colMax;
        final double nearX = Math.max(0.0, Math.max(x0 - cx, cx - x1));
        final double nearZ = Math.max(0.0, Math.max(z0 - cz, cz - z1));
        final double radMin = Math.sqrt(nearX * nearX + nearZ * nearZ);
        final double farX = Math.max(Math.abs(x0 - cx), Math.abs(x1 - cx));
        final double farZ = Math.max(Math.abs(z0 - cz), Math.abs(z1 - cz));
        final double radMax = Math.min(Math.sqrt(farX * farX + farZ * farZ), radius);
        if (radMin > radMax) return;

        final int baseIdx = (int) Math.floor(radMin);
        final int n = (int) Math.floor(radMax) - baseIdx + 2;

        final double elevation = primitive.elevation();
        final double invRadius = 1.0 / radius;
        final double depth = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depth(primitive.width());
        primitive.getRadialProfile().sampleRadialSection(lut, n, 1.0, baseIdx, elevation, invRadius, depth);

        for (int row = rowMin; row <= rowMax; row++) {
            final int rowBase = row * gridSize;
            final double ddx = row - cx;
            for (int col = colMin; col <= colMax; col++) {
                final int i = rowBase + col;
                final double ddz = col - cz;
                final double rad = Math.sqrt(ddx * ddx + ddz * ddz);
                if (rad > radius) continue; // outside the disc -- the AABB clip is conservative
                final double d = rad * invRadius;
                // Same inner/outer taper shape as the rosgen carve's blend -- full profile depth near
                // the centre, tapering to ambient by the rim. d is already <= 1 here (the loop above
                // continues past radius), so unlike carveRosgenInfluence's dd this needs no clamp.
                final double f = rad - baseIdx;
                final int i0 = Math.clamp((int) f, 0, n - 2);

                final int a = 3 * i;
                final double h = lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0]);
                final float testeW = d < 0.5 ? 1 : (float) (1 - Math.clamp(d * 2 - 1, 0, 1));
                elevs[i] = (float) Math.min(elevs[i], acc[a] * (1 - testeW) + h * testeW);
            }
        }
    }
}
