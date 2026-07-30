package io.github.salyvn.omnipet.core.persistence;

public final class StaleRevisionException extends IllegalStateException {
    public StaleRevisionException(long expected, long actual) {
        super("stale revision: expected " + expected + " but found " + actual);
    }
}
