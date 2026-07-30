package io.github.salyvn.omnipet.core.studio;

/** Inclusive finite bounds for a generated stat value. */
public record StatRange(double minimum, double maximum) {
    public StatRange {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)) {
            throw new IllegalArgumentException("stat range values must be finite");
        }
        if (minimum > maximum) throw new IllegalArgumentException("stat range minimum must be <= maximum");
    }

    public double min() {
        return minimum;
    }

    public double max() {
        return maximum;
    }
}
