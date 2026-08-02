package io.github.salyvn.omnipet.core.runtime;

/** Dependency-neutral finite vector used by movement and renderer ports. */
public record RuntimeVector(double x, double y, double z) {
    public static final RuntimeVector ZERO = new RuntimeVector(0, 0, 0);

    public RuntimeVector {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("runtime vector must be finite");
        }
    }

    public RuntimeVector add(RuntimeVector other) {
        return new RuntimeVector(x + other.x, y + other.y, z + other.z);
    }

    public RuntimeVector subtract(RuntimeVector other) {
        return new RuntimeVector(x - other.x, y - other.y, z - other.z);
    }

    public RuntimeVector multiply(double factor) {
        if (!Double.isFinite(factor)) throw new IllegalArgumentException("vector factor must be finite");
        return new RuntimeVector(x * factor, y * factor, z * factor);
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public RuntimeVector normalizedOr(RuntimeVector fallback) {
        double length = length();
        return length > 1.0e-9 && Double.isFinite(length) ? multiply(1.0 / length) : fallback;
    }

    public RuntimeVector clampLength(double maximum) {
        if (!Double.isFinite(maximum) || maximum < 0) {
            throw new IllegalArgumentException("maximum vector length must be finite and non-negative");
        }
        double length = length();
        return length <= maximum || length <= 1.0e-9 ? this : multiply(maximum / length);
    }
}
