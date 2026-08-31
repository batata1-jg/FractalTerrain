package me.batata_1.fractal_terrain.infinitetensor;

/**
 * The window walk shared by {@link InfiniteTensor#getSlice} and
 * {@link NonIntersectingInfiniteTensor#getSlice}.
 *
 * <p>Geometry only: it decides which windows a pixel range touches and how each one's pixels map
 * into the output, and leaves fetching the window and writing it to the caller. That split is what
 * lets a non-intersecting tensor reuse the arithmetic without joining {@link InfiniteTensor}'s
 * hierarchy — the two differ only in how they obtain a window, and that difference lives in the
 * visitor body.
 */
final class SliceGeometry {

    private SliceGeometry() {}

    /** Receives one intersecting window's geometry. */
    @FunctionalInterface
    interface RegionVisitor {
        /** All three arrays are reused buffers; a visitor that keeps one must copy it. */
        void visit(int[] windowIndex, int[][] dstRegion, int[][] srcRegion);
    }

    /** Visits every window of {@code window} that overlaps {@code pixelRange}, in window-index order. */
    static void forEachIntersection(TensorWindow window, int[][] pixelRange, RegionVisitor visitor) {
        final int n = pixelRange.length;
        final int[] lo = window.getLowestIntersection(pixelRange);
        final int[] hi = window.getHighestIntersection(pixelRange);
        // Reused across windows — iteration is sequential/single-threaded, and each is fully
        // recomputed per window before use, so hoisting these out of the loop is safe.
        final int[][] isect = new int[n][2];
        final int[][] srcRegion = new int[n][2];
        final int[][] dstRegion = new int[n][2];
        InfiniteTensor.iterateWindows(lo, hi, windowIndex -> {
            final int[][] wBounds = window.getBounds(windowIndex);

            // Intersection of the window bounds with the requested pixel range.
            for (int d = 0; d < n; d++) {
                isect[d][0] = Math.max(pixelRange[d][0], wBounds[d][0]);
                isect[d][1] = Math.min(pixelRange[d][1], wBounds[d][1]);
                if (isect[d][0] >= isect[d][1]) return; // no overlap
            }

            for (int d = 0; d < n; d++) {
                srcRegion[d][0] = isect[d][0] - wBounds[d][0];
                srcRegion[d][1] = isect[d][1] - wBounds[d][0];
                dstRegion[d][0] = isect[d][0] - pixelRange[d][0];
                dstRegion[d][1] = isect[d][1] - pixelRange[d][0];
            }

            visitor.visit(windowIndex, dstRegion, srcRegion);
        });
    }
}
