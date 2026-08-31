package me.batata_1.fractal_terrain.infinitetensor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Characterises the window walk lifted out of {@code InfiniteTensor.getSlice}. Locks the src/dst
 * region arithmetic so a later caller cannot silently change which pixels a slice reads.
 */
class SliceGeometryTest {

    /** One visit, flattened: window index then dst/src bounds per dimension. */
    private static String record(int[] wi, int[][] dst, int[][] src) {
        final StringBuilder sb = new StringBuilder("w=");
        for (int v : wi) sb.append(v).append(',');
        sb.append(" dst=");
        for (int[] d : dst) sb.append(d[0]).append("..").append(d[1]).append(',');
        sb.append(" src=");
        for (int[] s : src) sb.append(s[0]).append("..").append(s[1]).append(',');
        return sb.toString();
    }

    private static List<String> walk(TensorWindow window, int[] start, int[] end) {
        final List<String> visits = new ArrayList<>();
        SliceGeometry.forEachIntersection(
                window, InfiniteTensor.buildRange(start, end), (wi, dst, src) -> visits.add(record(wi, dst, src)));
        return visits;
    }

    @Test
    void singleWindowSliceMapsSrcAndDstDirectly() {
        final TensorWindow window = new TensorWindow(new int[] {2, 8, 8});
        final List<String> visits = walk(window, new int[] {0, 2, 3}, new int[] {1, 5, 6});
        assertEquals(List.of("w=0,0,0, dst=0..1,0..3,0..3, src=0..1,2..5,3..6,"), visits);
    }

    @Test
    void sliceCrossingATileBoundaryVisitsBothWindows() {
        final TensorWindow window = new TensorWindow(new int[] {2, 8, 8});
        final List<String> visits = walk(window, new int[] {0, 6, 0}, new int[] {1, 10, 2});
        assertEquals(
                List.of(
                        "w=0,0,0, dst=0..1,0..2,0..2, src=0..1,6..8,0..2,",
                        "w=0,1,0, dst=0..1,2..4,0..2, src=0..1,0..2,0..2,"),
                visits);
    }

    @Test
    void negativeCoordinatesWalkNegativeWindowIndices() {
        final TensorWindow window = new TensorWindow(new int[] {2, 8, 8});
        final List<String> visits = walk(window, new int[] {0, -2, 0}, new int[] {1, 2, 1});
        assertEquals(
                List.of(
                        "w=0,-1,0, dst=0..1,0..2,0..1, src=0..1,6..8,0..1,",
                        "w=0,0,0, dst=0..1,2..4,0..1, src=0..1,0..2,0..1,"),
                visits);
    }

    @Test
    void overlappingWindowsVisitTheSameOutputPixelTwice() {
        // stride < size: the overlap InfiniteTensor's additive accumulation exists for.
        final TensorWindow window = new TensorWindow(new int[] {1, 8, 8}, new int[] {1, 4, 4});
        final List<String> visits = walk(window, new int[] {0, 4, 4}, new int[] {1, 6, 6});
        assertEquals(
                List.of(
                        "w=0,0,0, dst=0..1,0..2,0..2, src=0..1,4..6,4..6,",
                        "w=0,0,1, dst=0..1,0..2,0..2, src=0..1,4..6,0..2,",
                        "w=0,1,0, dst=0..1,0..2,0..2, src=0..1,0..2,4..6,",
                        "w=0,1,1, dst=0..1,0..2,0..2, src=0..1,0..2,0..2,"),
                visits);
    }
}
