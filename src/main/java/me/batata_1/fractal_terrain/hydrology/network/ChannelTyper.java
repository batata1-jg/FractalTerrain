package me.batata_1.fractal_terrain.hydrology.network;

import me.batata_1.fractal_terrain.hydrology.features.RiverUnit.RosgenType;

/**
 * Assigns a Rosgen type to every spline point of every channel. Declared here rather than beside its
 * implementation so the package dependency stays one-way: {@code rosgen} depends on {@code meanders},
 * never the reverse.
 *
 * <p>Two-phase because a type depends on neighbouring channels: {@link RiverNetwork#collectUnits}
 * resamples every channel first, then calls {@link #prepare} once with the whole network, then calls
 * {@link #typesFor} per channel. An implementation that needs no cross-channel context may leave
 * {@link #prepare} empty.
 */
public interface ChannelTyper {

    /** Called once per {@code collectUnits}, after every channel is resampled and before any lookup. */
    void prepare(RiverNetwork network);

    /** One type per current spline point; a null entry means no reach covered that point.
     *  Types every point including endpoints — source/drain is topology, which only
     *  {@link RiverNetwork#collectUnits} can decide. */
    RosgenType[] typesFor(Channel channel);
}
