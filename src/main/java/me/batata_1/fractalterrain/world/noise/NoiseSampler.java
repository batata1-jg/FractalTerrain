package me.batata_1.fractalterrain.world.noise;

import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class NoiseSampler {

    protected static final Set<NoiseSampler> INIT_SET = Collections.synchronizedSet(new HashSet<>());
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

    public abstract float sample(Number x , Number z);

}
