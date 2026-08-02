package io.github.salyvn.omnipet.core.studio;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/** Transactional destination for Studio audit records. */
@FunctionalInterface
public interface StudioAuditSink {
    /**
     * Appends one record and returns its rollback receipt. Implementations must
     * leave no visible record when this method throws.
     */
    StudioAuditReceipt append(StudioAuditEntry entry) throws IOException;

    static StudioAuditSink noop() {
        return ignored -> StudioAuditReceipt.noop();
    }

    /** Compatibility adapter for existing non-transactional observers. */
    static StudioAuditSink fromConsumer(Consumer<StudioAuditEntry> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        return entry -> {
            consumer.accept(entry);
            return StudioAuditReceipt.noop();
        };
    }
}
