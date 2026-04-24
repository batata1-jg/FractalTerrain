package me.batata_1.fractalterrain.world.noise;

import com.google.common.hash.Hashing;
import net.minecraft.util.math.random.Random;

public class VoronoiNoiseSampler extends NoiseSampler {

    private long seed = 0;
    protected final float scale;

    private static long getHash(final long[] longs, long seed) {
        for (final long l : longs) {
            seed = Hashing.sha256().hashLong(seed + l).asLong();
        }
        return seed;
    }

    public VoronoiNoiseSampler(float scale, long off) {
        super(off);
        this.scale = scale;
    }

    @Override
    public void initSampler(long seed) {
        this.seed = seed + seedOffset;
        INIT_SET.remove(this);
    }

    protected float distPoint(final double x, final double z, final int i, final int j) {
        final Random r = Random.create(getHash(new long[] {i, j}, seed));
        final double x0 = r.nextDouble();
        final double z0 = r.nextDouble();
        if (x0 > 1 || 0 > x0) throw new RuntimeException("xo out of bounds");
        assert z0 <= 1 && 0 <= z0;
        return (float) ((x - x0 - i) * (x - x0 - i) + (z - z0 - j) * (z - z0 - j));
    }

    @Override
    public float sample(final Number x, final Number z) {
        final double xi = x.doubleValue() / scale;
        final double zi = z.doubleValue() / scale;
        final int xCurSquare = (int) Math.floor(xi);
        final int zCurSquare = (int) Math.floor(zi);
        float curMinDist = 1e9F;
        for (byte i = -1; i <= 1; i++) {
            for (byte j = -1; j <= 1; j++) {
                final float curDist = distPoint(xi, zi, xCurSquare + i, zCurSquare + j);
                if (curDist < curMinDist) {
                    curMinDist = curDist;
                }
            }
        }
        return curMinDist;
    }
}
