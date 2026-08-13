package me.batata_1.fractal_terrain.math;

public final class VectorOps {

    private VectorOps() {}

    public static double magnitude(double[] vec) {
        return Math.sqrt(dot(vec, vec));
    }

    public static double distance(double[] vec1, double[] vec2) {
        return Math.sqrt(distanceSquared(vec1, vec2));
    }

    public static double distanceSquared(double[] vec1, double[] vec2) {
        checkLengths(vec1, vec2);
        double resp = 0;
        for (int i = 0; i < vec1.length; i++) resp += (vec1[i] - vec2[i]) * (vec1[i] - vec2[i]);
        return resp;
    }

    public static double dot(double[] vec1, double[] vec2) {
        checkLengths(vec1, vec2);
        double resp = 0;
        for (int i = 0; i < vec1.length; i++) resp += vec1[i] * vec2[i];
        return resp;
    }

    public static double[] add(double[] vec1, double[] vec2) {
        checkLengths(vec1, vec2);
        double[] resp = new double[vec1.length];
        for (int i = 0; i < vec1.length; i++) resp[i] = vec1[i] + vec2[i];
        return resp;
    }

    public static double[] sub(double[] vec1, double[] vec2) {
        checkLengths(vec1, vec2);
        double[] resp = new double[vec1.length];
        for (int i = 0; i < vec1.length; i++) resp[i] = vec1[i] - vec2[i];
        return resp;
    }

    public static double[] normalize(double[] vec) {
        double len = magnitude(vec);
        if (len < 1e-12) return new double[vec.length];
        double[] resp = new double[vec.length];
        for (int i = 0; i < vec.length; i++) resp[i] = vec[i] / len;
        return resp;
    }

    public static double[] scale(double[] vec, double scalar) {
        double[] resp = new double[vec.length];
        for (int i = 0; i < vec.length; i++) resp[i] = vec[i] * scalar;
        return resp;
    }

    // projects a onto b
    public static double[] project(double[] a, double[] b) {
        checkLengths(a, b);
        final double magSqB = dot(b, b);
        if (magSqB < 1e-10) return new double[a.length];
        return scale(b, dot(a, b) / magSqB);
    }

    public static double[] div(double[] vec, double scalar) {
        if (scalar == 0) throw new IllegalArgumentException("division by zero");
        double[] resp = new double[vec.length];
        for (int i = 0; i < vec.length; i++) resp[i] = vec[i] / scalar;
        return resp;
    }

    public static double[] min(double[] vec1, double[] vec2) {
        checkLengths(vec1, vec2);
        double[] resp = new double[vec1.length];
        for (int i = 0; i < vec1.length; i++) resp[i] = Math.min(vec1[i], vec2[i]);
        return resp;
    }

    public static double[] max(double[] vec1, double[] vec2) {
        checkLengths(vec1, vec2);
        double[] resp = new double[vec1.length];
        for (int i = 0; i < vec1.length; i++) resp[i] = Math.max(vec1[i], vec2[i]);
        return resp;
    }

    /**
     * Returns the z-component of the 3D cross product (vec1 × vec2) for 2D vectors.
     * 2D-only by design: reads only indices 0 and 1, no length check (hot path).
     */
    public static double cross2D(double[] vec1, double[] vec2) {
        return vec1[0] * vec2[1] - vec1[1] * vec2[0];
    }

    private static void checkLengths(double[] vec1, double[] vec2) {
        if (vec1.length != vec2.length) throw new IllegalArgumentException("vectors with different lengths");
    }

    /** 2D-only by design: reads only indices 0 and 1, no length check (hot path). */
    public static double[] perpendicular(double[] d) {
        return new double[] {d[1], -d[0]};
    }

    /**
     * Closest point on a segment, as the normalised position along it plus the signed distance to it.
     *
     * <p>Signed by which bank the point falls on, using the same convention as
     * {@code RiverPrimitive.d}: positive where {@code perpendicular(segEnd - segStart)} points, so a
     * caller can hand the value straight to an asymmetric cross-section. The magnitude stays the true
     * distance to the segment, endpoint-clamped included.
     *
     * <p>Writes into {@code outProjection} rather than returning, so the per-pixel carve loop stays
     * allocation-free. 2D-only by design: reads only indices 0 and 1, no length check (hot path).
     */
    public static void projectPointOntoSegment(
            double[] point, double[] segStart, double[] segEnd, double[] outProjection) {
        final double segX = segEnd[0] - segStart[0];
        final double segZ = segEnd[1] - segStart[1];
        final double segLenSq = segX * segX + segZ * segZ;
        final double toPointX = point[0] - segStart[0];
        final double toPointZ = point[1] - segStart[1];
        final double segParam =
                segLenSq < 1e-12 ? 0.0 : Math.clamp((toPointX * segX + toPointZ * segZ) / segLenSq, 0.0, 1.0);
        final double footToPointX = toPointX - segParam * segX;
        final double footToPointZ = toPointZ - segParam * segZ;
        // Read off the infinite line, not the clamped foot: past an endpoint the foot vector swings
        // round to point along the segment, which would flip the bank for no geometric reason. A
        // degenerate segment gives 0 here and falls to the positive bank, matching the on-centreline case.
        final double side = segZ * toPointX - segX * toPointZ;
        final double dist = Math.sqrt(footToPointX * footToPointX + footToPointZ * footToPointZ);
        outProjection[0] = segParam;
        outProjection[1] = side < 0.0 ? -dist : dist;
    }
}
