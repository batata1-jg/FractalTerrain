package me.batata_1.fractal_terrain.noise;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class NoiseSampler {

    protected static final Set<NoiseSampler> INIT_SET = Collections.synchronizedSet(new HashSet<>());
    private static final Logger LOG = LoggerFactory.getLogger(NoiseSampler.class);
    protected final long seedOffset;

    protected NoiseSampler(long seedOffset) {
        this.seedOffset = seedOffset;
    }

    public static synchronized void init(long seed) {
        NoiseSampler[] toInit = INIT_SET.toArray(new NoiseSampler[0]);
        for (var s : toInit) {
            s.initSampler(seed);
        }
    }

    public static synchronized int getInitSetSize() {
        return INIT_SET.size();
    }

    public abstract void initSampler(long seed);

    public abstract float sample(Number x, Number z);
}
