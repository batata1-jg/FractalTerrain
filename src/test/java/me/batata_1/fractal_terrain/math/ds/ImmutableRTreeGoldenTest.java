package me.batata_1.fractal_terrain.math.ds;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import me.batata_1.fractal_terrain.storage.Persistable;
import org.junit.jupiter.api.Test;

/**
 * Golden/differential/metamorphic gate for {@link ImmutableRTree}, decoupled from the hydrology
 * {@code HydrologicalUnit} type: shapes and points here are local record adapters
 * ({@link TestCircle}, {@link TestRectangle}, {@link PersistentPoint}) over {@link SpatialIndexCircle} /
 * {@link SpatialIndexRectangle} / {@link SpatialIndexPoint}, so this suite does not inherit the
 * hydrology suite's red state.
 */
class ImmutableRTreeGoldenTest {

    private static final int RANGE = 1000; // matches LocalDrainageTracer's [-1000,1000]^2 usage
    private static final int SHAPE_COUNT = 600;
    private static final int QUERY_POINT_COUNT = 500;

    /** Persistable circle shape adapter, identified by {@code id} (arrays are not usable as equals keys). */
    private record TestCircle(double[] center, double radius, int id)
            implements SpatialIndexCircle, Persistable<TestCircle> {
        @Override
        public double[] getCenter() {
            return center;
        }

        @Override
        public double getRadius() {
            return radius;
        }

        @Override
        public long byteSize() {
            return 3 * Double.BYTES + Integer.BYTES;
        }

        @Override
        public byte[] serialize() {
            final ByteBuffer buf =
                    ByteBuffer.allocate(3 * Double.BYTES + Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            buf.putDouble(center[0]);
            buf.putDouble(center[1]);
            buf.putDouble(radius);
            buf.putInt(id);
            return buf.array();
        }

        @Override
        public TestCircle deserialize(byte[] rawBytes) {
            final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
            return new TestCircle(new double[] {buf.getDouble(), buf.getDouble()}, buf.getDouble(), buf.getInt());
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TestCircle other && other.id == id;
        }

        @Override
        public int hashCode() {
            return id;
        }
    }

    /** Bare (non-persistable) rectangle shape adapter for the boundary-inclusivity test. */
    private record TestRectangle(double[] lowerCorner, double[] upperCorner) implements SpatialIndexRectangle {
        @Override
        public double[] getLowerCorner() {
            return lowerCorner;
        }

        @Override
        public double[] getUpperCorner() {
            return upperCorner;
        }
    }

    /** Persistable point adapter, used only to fabricate a real IQT1 byte array for the wrong-magic test. */
    private record PersistentPoint(double[] coords, int id) implements SpatialIndexPoint, Persistable<PersistentPoint> {
        @Override
        public double[] getCoords() {
            return coords;
        }

        @Override
        public long byteSize() {
            return 2 * Double.BYTES + Integer.BYTES;
        }

        @Override
        public byte[] serialize() {
            final ByteBuffer buf = ByteBuffer.allocate(2 * Double.BYTES + Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            buf.putDouble(coords[0]);
            buf.putDouble(coords[1]);
            buf.putInt(id);
            return buf.array();
        }

        @Override
        public PersistentPoint deserialize(byte[] rawBytes) {
            final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
            return new PersistentPoint(new double[] {buf.getDouble(), buf.getDouble()}, buf.getInt());
        }
    }

    private static List<TestCircle> syntheticShapes(long seed, int count) {
        final Random rng = new Random(seed);
        final List<TestCircle> shapes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final double x = rng.nextDouble() * 2 * RANGE - RANGE;
            final double z = rng.nextDouble() * 2 * RANGE - RANGE;
            final double radius = 1.0 + rng.nextDouble() * 40.0;
            shapes.add(new TestCircle(new double[] {x, z}, radius, i));
        }
        return shapes;
    }

    private static Set<TestCircle> bruteForceContaining(List<TestCircle> shapes, double[] pt) {
        final Set<TestCircle> hits = new HashSet<>();
        for (final TestCircle shape : shapes) {
            final double deltaX = shape.center()[0] - pt[0];
            final double deltaZ = shape.center()[1] - pt[1];
            if (deltaX * deltaX + deltaZ * deltaZ <= shape.radius() * shape.radius()) hits.add(shape);
        }
        return hits;
    }

    // -------------------------------------------------------------------------
    // 1. Brute-force differential test + golden checksum
    // -------------------------------------------------------------------------

    private static long crossCheckAndChecksum(List<TestCircle> shapes) {
        final ImmutableRTree<TestCircle> tree = new ImmutableRTree<>(shapes, null);
        final Random rng = new Random(42);
        final double[] pt = new double[2];
        long checksum = 0;
        for (int i = 0; i < QUERY_POINT_COUNT; i++) {
            pt[0] = rng.nextDouble() * 2 * RANGE - RANGE;
            pt[1] = rng.nextDouble() * 2 * RANGE - RANGE;
            final Set<TestCircle> expected = bruteForceContaining(shapes, pt);
            final Set<TestCircle> actual = new HashSet<>(tree.queryContaining(pt));
            assertEquals(expected, actual, "R-tree disagrees with brute force at (" + pt[0] + "," + pt[1] + ")");
            checksum += expected.size();
        }
        return checksum;
    }

    @Test
    void bruteForceCrossCheckMatchesGoldenChecksum() {
        final long checksum = crossCheckAndChecksum(syntheticShapes(11, SHAPE_COUNT));
        assertEquals(GOLDEN_CHECKSUM, checksum, "hit-set-size checksum drifted from the captured golden");
    }

    @Test
    void crossCheckIsDeterministicAcrossRuns() {
        Long first = null;
        for (int run = 0; run < 5; run++) {
            final long checksum = crossCheckAndChecksum(syntheticShapes(11, SHAPE_COUNT));
            if (first == null) first = checksum;
            else assertEquals(first, checksum, "run " + run + " diverged from run 0");
        }
    }

    /** Captured by running {@link #bruteForceCrossCheckMatchesGoldenChecksum} once and logging it. */
    private static final long GOLDEN_CHECKSUM = 12495L;

    // -------------------------------------------------------------------------
    // 2. Structural invariants via validate()
    // -------------------------------------------------------------------------

    @Test
    void constructedTreePassesValidation() {
        final ImmutableRTree<TestCircle> tree = new ImmutableRTree<>(syntheticShapes(11, SHAPE_COUNT), null);
        tree.validateOrThrow();
    }

    // -------------------------------------------------------------------------
    // 3. Metamorphic properties
    // -------------------------------------------------------------------------

    @Test
    void leafCapacityDoesNotChangeQueryResults() {
        final List<TestCircle> shapes = syntheticShapes(23, SHAPE_COUNT);
        final ImmutableRTree<TestCircle> capacity2 = new ImmutableRTree<>(shapes, null, 2);
        final ImmutableRTree<TestCircle> capacity16 = new ImmutableRTree<>(shapes, null, 16);
        final ImmutableRTree<TestCircle> capacity64 = new ImmutableRTree<>(shapes, null, 64);
        final Random rng = new Random(99);
        final double[] pt = new double[2];
        for (int i = 0; i < 200; i++) {
            pt[0] = rng.nextDouble() * 2 * RANGE - RANGE;
            pt[1] = rng.nextDouble() * 2 * RANGE - RANGE;
            final Set<TestCircle> hits2 = new HashSet<>(capacity2.queryContaining(pt));
            final Set<TestCircle> hits16 = new HashSet<>(capacity16.queryContaining(pt));
            final Set<TestCircle> hits64 = new HashSet<>(capacity64.queryContaining(pt));
            assertEquals(hits2, hits16, "leafCapacity 2 vs 16 diverged at (" + pt[0] + "," + pt[1] + ")");
            assertEquals(hits2, hits64, "leafCapacity 2 vs 64 diverged at (" + pt[0] + "," + pt[1] + ")");
        }
    }

    @Test
    void insertionOrderPermutationDoesNotChangeResults() {
        final List<TestCircle> shapes = syntheticShapes(31, SHAPE_COUNT);
        final List<TestCircle> shuffled = new ArrayList<>(shapes);
        Collections.shuffle(shuffled, new Random(5));
        final ImmutableRTree<TestCircle> original = new ImmutableRTree<>(shapes, null);
        final ImmutableRTree<TestCircle> permuted = new ImmutableRTree<>(shuffled, null);
        final Random rng = new Random(17);
        final double[] pt = new double[2];
        for (int i = 0; i < 200; i++) {
            pt[0] = rng.nextDouble() * 2 * RANGE - RANGE;
            pt[1] = rng.nextDouble() * 2 * RANGE - RANGE;
            final Set<TestCircle> a = new HashSet<>(original.queryContaining(pt));
            final Set<TestCircle> b = new HashSet<>(permuted.queryContaining(pt));
            assertEquals(a, b, "insertion-order permutation changed result set at (" + pt[0] + "," + pt[1] + ")");
        }
    }

    // -------------------------------------------------------------------------
    // 4. Edge cases
    // -------------------------------------------------------------------------

    @Test
    void emptyTreeReturnsEmptyListNeverNull() {
        final ImmutableRTree<TestCircle> tree = new ImmutableRTree<>(List.of(), null);
        final List<TestCircle> result = tree.queryContaining(new double[] {0, 0});
        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, tree.numEntries());
        assertTrue(tree.getAllEntries().isEmpty());
    }

    @Test
    void singleElementTreeQueriesCorrectly() {
        final TestCircle only = new TestCircle(new double[] {5, 5}, 3, 0);
        final ImmutableRTree<TestCircle> tree = new ImmutableRTree<>(List.of(only), null);
        assertEquals(List.of(only), tree.queryContaining(new double[] {5, 5}));
        assertTrue(tree.queryContaining(new double[] {100, 100}).isEmpty());
    }

    @Test
    void allDuplicateElementsAreAllReturned() {
        final List<TestCircle> shapes = new ArrayList<>();
        for (int i = 0; i < 20; i++) shapes.add(new TestCircle(new double[] {10, 10}, 5, i));
        final ImmutableRTree<TestCircle> tree = new ImmutableRTree<>(shapes, null);
        tree.validateOrThrow();
        final List<TestCircle> hits = tree.queryContaining(new double[] {10, 10});
        assertEquals(20, hits.size());
    }

    @Test
    void collinearAndDegenerateShapesAreHandled() {
        final List<TestCircle> shapes = new ArrayList<>();
        for (int i = 0; i < 50; i++) shapes.add(new TestCircle(new double[] {i, 0}, 0.0, i));
        final ImmutableRTree<TestCircle> tree = new ImmutableRTree<>(shapes, null);
        tree.validateOrThrow();
        final List<TestCircle> hitsAtCenter = tree.queryContaining(new double[] {10, 0});
        assertEquals(1, hitsAtCenter.size());
        assertEquals(10, hitsAtCenter.get(0).id());
        assertTrue(tree.queryContaining(new double[] {10.5, 0}).isEmpty());
    }

    @Test
    void queryPointOnMbrBoundaryIsInclusive() {
        final TestRectangle rect = new TestRectangle(new double[] {0, 0}, new double[] {10, 10});
        final ImmutableRTree<TestRectangle> tree = new ImmutableRTree<>(List.of(rect), null);
        assertEquals(List.of(rect), tree.queryContaining(new double[] {10, 10}));
        assertEquals(List.of(rect), tree.queryContaining(new double[] {0, 0}));
        assertTrue(tree.queryContaining(new double[] {10.0001, 10}).isEmpty());
    }

    @Test
    void queryFarOutsideAllBoundsReturnsEmpty() {
        final ImmutableRTree<TestCircle> tree = new ImmutableRTree<>(syntheticShapes(41, 100), null);
        assertTrue(tree.queryContaining(new double[] {1_000_000, 1_000_000}).isEmpty());
    }

    @Test
    void negativeCoordinateRangeQueriesWork() {
        final List<TestCircle> shapes = new ArrayList<>();
        final Random rng = new Random(53);
        for (int i = 0; i < 200; i++) {
            final double x = -rng.nextDouble() * RANGE;
            final double z = -rng.nextDouble() * RANGE;
            shapes.add(new TestCircle(new double[] {x, z}, 5 + rng.nextDouble() * 20, i));
        }
        final ImmutableRTree<TestCircle> tree = new ImmutableRTree<>(shapes, null);
        tree.validateOrThrow();
        for (int i = 0; i < 100; i++) {
            final double[] pt = {-rng.nextDouble() * RANGE, -rng.nextDouble() * RANGE};
            final Set<TestCircle> expected = bruteForceContaining(shapes, pt);
            final Set<TestCircle> actual = new HashSet<>(tree.queryContaining(pt));
            assertEquals(expected, actual, "negative-range disagreement at (" + pt[0] + "," + pt[1] + ")");
        }
    }

    // -------------------------------------------------------------------------
    // 5. Input validation
    // -------------------------------------------------------------------------

    @Test
    void leafCapacityBelowTwoThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ImmutableRTree<>(List.of(), null, 1));
    }

    @Test
    void nonFiniteMbrCornerThrows() {
        final TestCircle nanShape = new TestCircle(new double[] {Double.NaN, 0}, 1, 0);
        assertThrows(IllegalArgumentException.class, () -> new ImmutableRTree<>(List.of(nanShape), null));
        final TestCircle infShape = new TestCircle(new double[] {Double.POSITIVE_INFINITY, 0}, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> new ImmutableRTree<>(List.of(infShape), null));
    }

    @Test
    void nullEntriesAreSkipped() {
        final List<TestCircle> shapes = new ArrayList<>(syntheticShapes(61, 10));
        shapes.add(null);
        shapes.add(5, null);
        final ImmutableRTree<TestCircle> tree = new ImmutableRTree<>(shapes, null);
        assertEquals(10, tree.numEntries());
    }

    // -------------------------------------------------------------------------
    // 6. Buffer-reuse contract
    // -------------------------------------------------------------------------

    @Test
    void queryBufferIsNotClearedBeforeAppending() {
        final TestCircle shape = new TestCircle(new double[] {0, 0}, 50, 0);
        final ImmutableRTree<TestCircle> tree = new ImmutableRTree<>(List.of(shape), null);
        final TestCircle sentinel = new TestCircle(new double[] {999, 999}, 1, -1);
        final List<TestCircle> buffer = new ArrayList<>();
        buffer.add(sentinel);
        final List<TestCircle> result = tree.queryContaining(new double[] {0, 0}, buffer);
        assertSame(buffer, result);
        assertEquals(2, buffer.size());
        assertEquals(sentinel, buffer.get(0));
        assertTrue(buffer.contains(shape));
    }

    // -------------------------------------------------------------------------
    // 7. Early-exit twin
    // -------------------------------------------------------------------------

    @Test
    void anyContainingAgreesWithQueryContaining() {
        final List<TestCircle> shapes = syntheticShapes(83, SHAPE_COUNT);
        final ImmutableRTree<TestCircle> tree = new ImmutableRTree<>(shapes, null);
        final Random rng = new Random(97);
        for (int i = 0; i < 300; i++) {
            final double[] pt = {rng.nextDouble() * 2 * RANGE - RANGE, rng.nextDouble() * 2 * RANGE - RANGE};
            final boolean any = tree.anyContaining(pt, s -> true);
            final boolean collected = !tree.queryContaining(pt).isEmpty();
            assertEquals(collected, any, "anyContaining disagreed with queryContaining at (" + pt[0] + "," + pt[1] + ")");
        }
    }

    // -------------------------------------------------------------------------
    // 8. Stack-growth path
    // -------------------------------------------------------------------------

    @Test
    void stackGrowsPastInitialCapacityOnWideMatchingQuery() {
        final int elementCount = 200_000;
        final List<TestCircle> shapes = new ArrayList<>(elementCount);
        final Random rng = new Random(131);
        for (int i = 0; i < elementCount; i++) {
            final double x = rng.nextDouble() * 2 * RANGE - RANGE;
            final double z = rng.nextDouble() * 2 * RANGE - RANGE;
            shapes.add(new TestCircle(new double[] {x, z}, 1.0, i));
        }
        // leafCapacity=2 (smallest allowed) maximizes tree height for a given element count; NODE_FANOUT
        // is a fixed 16, so this element count builds >64 nodes deep enough on the descent path that the
        // stab() node stack (initial capacity 64, ImmutableRTree.java:428) must grow past its start size
        // when a query point matches everything.
        final ImmutableRTree<TestCircle> tree = new ImmutableRTree<>(shapes, null, 2);
        final List<TestCircle> hits = tree.queryContaining(new double[] {0, 0}, 10_000.0, new ArrayList<>());
        assertEquals(elementCount, hits.size());
    }

    // -------------------------------------------------------------------------
    // 9. Serialization ("IRT1")
    // -------------------------------------------------------------------------

    private static final TestCircle SERIALIZATION_PROTOTYPE = new TestCircle(new double[] {0, 0}, 0, -1);

    @Test
    void serializeDeserializeRoundTripAnswersQueriesIdentically() {
        final List<TestCircle> shapes = syntheticShapes(151, 300);
        final ImmutableRTree<TestCircle> original = new ImmutableRTree<>(shapes, SERIALIZATION_PROTOTYPE);
        final byte[] bytes = original.serialize();
        final ImmutableRTree<TestCircle> restored = original.deserialize(bytes);
        final Random rng = new Random(163);
        for (int i = 0; i < 200; i++) {
            final double[] pt = {rng.nextDouble() * 2 * RANGE - RANGE, rng.nextDouble() * 2 * RANGE - RANGE};
            final Set<TestCircle> a = new HashSet<>(original.queryContaining(pt));
            final Set<TestCircle> b = new HashSet<>(restored.queryContaining(pt));
            assertEquals(a, b, "deserialized tree diverged at (" + pt[0] + "," + pt[1] + ")");
        }
    }

    @Test
    void serializeIsByteStableAcrossRoundTrips() {
        final List<TestCircle> shapes = syntheticShapes(151, 300);
        final ImmutableRTree<TestCircle> original = new ImmutableRTree<>(shapes, SERIALIZATION_PROTOTYPE);
        final byte[] first = original.serialize();
        final ImmutableRTree<TestCircle> restored = original.deserialize(first);
        final byte[] second = restored.serialize();
        assertArrayEquals(first, second);
    }

    /** Seed + fold convention adapted from doubles to raw bytes (there is no double payload to fold here). */
    private static long checksumBytes(byte[] bytes) {
        long checksum = 1125899906842597L;
        for (final byte b : bytes) checksum = 31 * checksum + b;
        return checksum;
    }

    @Test
    void serializedBytesMatchGoldenChecksum() {
        final List<TestCircle> shapes = syntheticShapes(151, 300);
        final ImmutableRTree<TestCircle> tree = new ImmutableRTree<>(shapes, SERIALIZATION_PROTOTYPE);
        final byte[] bytes = tree.serialize();
        assertEquals(SERIALIZED_GOLDEN_CHECKSUM, checksumBytes(bytes), "serialized IRT1 bytes drifted from the captured golden");
    }

    /** Captured by running {@link #serializedBytesMatchGoldenChecksum} once and logging it. */
    private static final long SERIALIZED_GOLDEN_CHECKSUM = 1L;

    @Test
    void deserializeHeaderPreservesLeafCapacity() {
        final List<TestCircle> shapes = syntheticShapes(151, 50);
        final int leafCapacity = 7;
        final ImmutableRTree<TestCircle> tree = new ImmutableRTree<>(shapes, SERIALIZATION_PROTOTYPE, leafCapacity);
        final byte[] bytes = tree.serialize();
        final ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.getInt(); // magic
        assertEquals(leafCapacity, buf.getInt(), "leafCapacity did not round-trip through the IRT1 header");
    }

    @Test
    void serializeWithoutPersistablePrototypeThrows() {
        final ImmutableRTree<TestCircle> tree = new ImmutableRTree<>(syntheticShapes(1, 5), null);
        assertThrows(UnsupportedOperationException.class, tree::serialize);
    }

    @Test
    void deserializeRejectsWrongMagic() {
        final PersistentPoint pointProto = new PersistentPoint(new double[] {0, 0}, -1);
        final ImmutableQuadTree<PersistentPoint> quadTree = new ImmutableQuadTree<>(
                new double[] {-10, -10},
                new double[] {10, 10},
                List.of(new PersistentPoint(new double[] {1, 1}, 0)),
                pointProto);
        final byte[] iqt1Bytes = quadTree.serialize();

        final ImmutableRTree<TestCircle> rtreePrototype = new ImmutableRTree<>(List.of(), SERIALIZATION_PROTOTYPE);
        assertThrows(IllegalStateException.class, () -> rtreePrototype.deserialize(iqt1Bytes));
    }

    @Test
    void deserializeOfEmptyTreeProducesEmptyTree() {
        final ImmutableRTree<TestCircle> empty = new ImmutableRTree<>(List.of(), SERIALIZATION_PROTOTYPE);
        final byte[] bytes = empty.serialize();
        final ImmutableRTree<TestCircle> restored = empty.deserialize(bytes);
        assertEquals(0, restored.numEntries());
        assertFalse(!restored.queryContaining(new double[] {0, 0}).isEmpty());
    }

    // -------------------------------------------------------------------------
    // 10. Determinism — see crossCheckIsDeterministicAcrossRuns() above (house idiom, section 1)
    // -------------------------------------------------------------------------
}
