package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RadialPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RosgenCarvedPrimitive;

/**
 * Which shell-carve algorithm a primitive uses, replacing the old {@code instanceof} dispatch in
 * {@link RiverInfluenceCarve}'s shell pass. Scoped to the shell/influence pass only — the bed pass
 * ({@link RiverInfluenceCarve#computeRiverGrid}) keeps its own, untouched dispatch.
 */
public enum InfluenceCarver {

    /** Rectangle/tangent cross-section, shared by every {@link RosgenCarvedPrimitive}. */
    ROSGEN {
        @Override
        public void carveInfluence(HydrologicalPrimitive primitive, RiverInfluenceCarve.ShellGrid grid) {
            RiverInfluenceCarve.carveRosgenInfluence((RosgenCarvedPrimitive) primitive, grid);
        }
    },

    /** Radial cross-section, for a {@link RadialPrimitive} that opts into shell carving. */
    RADIAL {
        @Override
        public void carveInfluence(HydrologicalPrimitive primitive, RiverInfluenceCarve.ShellGrid grid) {
            RiverInfluenceCarve.carveRadialInfluence((RadialPrimitive) primitive, grid);
        }
    },

    /** This primitive contributes no shell influence — today's behaviour for every family the
     *  shell pass does not carve. */
    NONE {
        @Override
        public void carveInfluence(HydrologicalPrimitive primitive, RiverInfluenceCarve.ShellGrid grid) {}
    };

    public abstract void carveInfluence(HydrologicalPrimitive primitive, RiverInfluenceCarve.ShellGrid grid);
}
