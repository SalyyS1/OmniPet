package io.github.salyvn.omnipet.core.studio;

import java.io.IOException;

/** A committed audit write that can remove the exact record during rollback. */
@FunctionalInterface
public interface StudioAuditReceipt {
    void rollback() throws IOException;

    static StudioAuditReceipt noop() {
        return () -> {};
    }
}
