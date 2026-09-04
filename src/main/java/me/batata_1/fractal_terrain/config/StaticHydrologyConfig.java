package me.batata_1.fractal_terrain.config;

/**
 * Production default {@link HydrologyConfig}, backing every accessor with the still-live
 * {@link HydrologyTuning} statics. An enum singleton rather than a static-instance class: it needs no
 * state of its own, and the enum form rules out a second instance existing by construction.
 */
public enum StaticHydrologyConfig implements HydrologyConfig {
    INSTANCE;

    @Override
    public double getDx() {
        return HydrologyTuning.DX;
    }

    @Override
    public double getResampleDist() {
        return HydrologyTuning.RESAMPLE_DIST;
    }

    @Override
    public double getLocalAttachRadius() {
        return HydrologyTuning.LOCAL_ATTACH_RADIUS;
    }

    @Override
    public double getMinWidth() {
        return HydrologyTuning.MIN_WIDTH;
    }

    @Override
    public double getMaxWidth() {
        return HydrologyTuning.MAX_WIDTH;
    }

    @Override
    public double getWidthFlowScale() {
        return HydrologyTuning.WIDTH_FLOW_SCALE;
    }

    @Override
    public float getFlowThreshold() {
        return HydrologyTuning.FLOW_THRESHOLD;
    }

    @Override
    public float getGradThreshold() {
        return HydrologyTuning.GRAD_THRESHOLD;
    }

    @Override
    public double getMinInfluenceRadius() {
        return HydrologyTuning.MIN_INFLUENCE_RADIUS;
    }

    @Override
    public double getMaxInfluenceRadius() {
        return HydrologyTuning.MAX_INFLUENCE_RADIUS;
    }

    @Override
    public double getInfluenceDepthFactor() {
        return HydrologyTuning.INFLUENCE_DEPTH_FACTOR;
    }

    @Override
    public float getFlowInitialLocal() {
        return HydrologyTuning.FLOW_INITIAL_LOCAL;
    }

    @Override
    public float getFlowInitialGlobal() {
        return HydrologyTuning.FLOW_INITIAL_GLOBAL;
    }

    @Override
    public float getFlowPerCellLocal() {
        return HydrologyTuning.FLOW_PER_CELL_LOCAL;
    }

    @Override
    public float getFlowPerCellGlobal() {
        return HydrologyTuning.FLOW_PER_CELL_GLOBAL;
    }
}
