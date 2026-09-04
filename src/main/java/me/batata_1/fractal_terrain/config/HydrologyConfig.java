package me.batata_1.fractal_terrain.config;

/**
 * Injectable view over the {@link HydrologyTuning} constants that drive trace/carve geometry (flow
 * accumulation → width → influence radius → carve). A curated subset, not all ~50 constants — Rosgen
 * classification thresholds stay static-only; see {@code HydrologyTuning} and this package's README.
 */
public interface HydrologyConfig {

    double getDx();

    double getResampleDist();

    double getLocalAttachRadius();

    double getMinWidth();

    double getMaxWidth();

    double getWidthFlowScale();

    float getFlowThreshold();

    float getGradThreshold();

    double getMinInfluenceRadius();

    double getMaxInfluenceRadius();

    double getInfluenceDepthFactor();

    float getFlowInitialLocal();

    float getFlowInitialGlobal();

    float getFlowPerCellLocal();

    float getFlowPerCellGlobal();

    default double widthFromFlow(double rawFlow) {
        final double lawWidth = getWidthFlowScale() * Math.sqrt(rawFlow);
        return Math.clamp(lawWidth, getMinWidth(), getMaxWidth());
    }

    default double influence(double width) {
        return Math.clamp(width * 5, getMinInfluenceRadius(), getMaxInfluenceRadius());
    }

    default double influence(double width, double deltaElev) {
        return Math.clamp(
                getInfluenceDepthFactor() * Math.sqrt((deltaElev + 1)) * width,
                getMinInfluenceRadius(),
                getMaxInfluenceRadius());
    }

    default float[] flowFromHumidity(float[] humidity, boolean isGlobal) {
        final float[] res = humidity.clone();
        for (int px = 0; px < humidity.length; px++) {
            res[px] /= 1000.0f;
            if (isGlobal) res[px] *= getFlowPerCellGlobal();
            else res[px] *= getFlowPerCellLocal();
        }
        return res;
    }
}
