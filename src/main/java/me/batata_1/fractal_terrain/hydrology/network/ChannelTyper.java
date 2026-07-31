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

    /**
     * Types for {@code channel}, index-aligned to its <b>current</b> spline points (post-resample), one
     * entry per point. Must never return {@code null} or a shorter array; an individual entry may be
     * {@code null}, meaning no reach covered that point, which {@link RiverNetwork#collectUnits} emits
     * as an untyped unit.
     *
     * <p>An implementation types every point, including the two endpoints. Deciding that a point is a
     * source or a drain rather than a reach — and therefore carries no Rosgen type — belongs to
     * {@link RiverNetwork#collectUnits}, which owns the graph topology; a typer sees only geometry and
     * the raster.
     */
    RosgenType[] typesFor(Channel channel);
}
