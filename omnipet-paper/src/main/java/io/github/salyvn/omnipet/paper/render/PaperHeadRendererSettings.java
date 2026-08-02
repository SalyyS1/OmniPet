package io.github.salyvn.omnipet.paper.render;

public record PaperHeadRendererSettings(
        double safetyDistance,
        double movementGain,
        double maximumVelocity,
        int interpolationTicks) {
    public PaperHeadRendererSettings {
        if (!Double.isFinite(safetyDistance) || safetyDistance <= 0) {
            throw new IllegalArgumentException("HEAD safety distance must be positive and finite");
        }
        if (!Double.isFinite(movementGain) || movementGain <= 0 || movementGain > 1) {
            throw new IllegalArgumentException("HEAD movement gain must be in (0, 1]");
        }
        if (!Double.isFinite(maximumVelocity) || maximumVelocity <= 0) {
            throw new IllegalArgumentException("HEAD maximum velocity must be positive and finite");
        }
        if (interpolationTicks < 0 || interpolationTicks > 59) {
            throw new IllegalArgumentException("HEAD interpolation ticks must be between 0 and 59");
        }
    }

    public static PaperHeadRendererSettings defaults() {
        return new PaperHeadRendererSettings(8.0, 0.35, 1.2, 3);
    }
}
