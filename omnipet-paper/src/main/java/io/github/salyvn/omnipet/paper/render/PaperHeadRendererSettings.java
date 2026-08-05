package io.github.salyvn.omnipet.paper.render;

/**
 * How a rendered pet moves and how far it may drift before it is snapped back.
 *
 * <p>Shared by both renderers. The ModelEngine adapter used to carry its own copies of the gain, the
 * velocity ceiling, and the safety distance, which meant tuning one renderer silently left the other
 * alone.
 */
public record PaperHeadRendererSettings(
        double safetyDistance,
        double movementGain,
        double maximumVelocity,
        int interpolationTicks,
        double maximumLeanDegrees,
        boolean nameplates) {
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
        // Zero disables the lean outright, which is what an operator who wants the old flat look sets.
        if (!Double.isFinite(maximumLeanDegrees) || maximumLeanDegrees < 0 || maximumLeanDegrees > 45) {
            throw new IllegalArgumentException("HEAD maximum lean must be between 0 and 45 degrees");
        }
    }

    public static PaperHeadRendererSettings defaults() {
        return new PaperHeadRendererSettings(8.0, 0.35, 1.2, 3, 12.0, true);
    }

    /**
     * Movement settings with nameplates left at their default.
     *
     * <p>Kept so retuning movement does not force a caller to restate a cosmetic switch it has no opinion
     * about, and so the existing five-argument call sites keep meaning what they meant.
     */
    public PaperHeadRendererSettings(
            double safetyDistance,
            double movementGain,
            double maximumVelocity,
            int interpolationTicks,
            double maximumLeanDegrees) {
        this(safetyDistance, movementGain, maximumVelocity, interpolationTicks, maximumLeanDegrees, true);
    }
}
