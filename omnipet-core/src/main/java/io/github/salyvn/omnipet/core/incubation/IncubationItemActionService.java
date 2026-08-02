package io.github.salyvn.omnipet.core.incubation;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class IncubationItemActionService {
    private final IncubationItemActionJournal journal;

    public IncubationItemActionService(IncubationItemActionJournal journal) {
        this.journal = Objects.requireNonNull(journal, "incubation item action journal");
    }

    public Optional<IncubationItemActionTransaction> find(UUID token) throws IOException { return journal.find(token); }
    public IncubationItemActionTransaction prepare(IncubationItemActionTransaction transaction) throws IOException {
        return journal.create(transaction);
    }
    public IncubationItemActionTransaction markItemRemoved(UUID token) throws IOException {
        return journal.transition(token, Set.of(IncubationItemActionStage.PREPARED), IncubationItemActionStage.ITEM_REMOVED);
    }
    public IncubationItemActionTransaction commit(UUID token) throws IOException {
        return journal.transition(token,
                Set.of(IncubationItemActionStage.ITEM_REMOVED, IncubationItemActionStage.REFUND_PENDING),
                IncubationItemActionStage.COMMITTED);
    }
    public IncubationItemActionTransaction markRefundPending(UUID token) throws IOException {
        return journal.transition(token, Set.of(IncubationItemActionStage.ITEM_REMOVED),
                IncubationItemActionStage.REFUND_PENDING);
    }
    public IncubationItemActionTransaction markRefunded(UUID token) throws IOException {
        return journal.transition(token, Set.of(IncubationItemActionStage.REFUND_PENDING),
                IncubationItemActionStage.REFUNDED);
    }
    public IncubationItemActionTransaction requireOperatorReview(UUID token) throws IOException {
        return journal.transition(token,
                Set.of(IncubationItemActionStage.PREPARED, IncubationItemActionStage.ITEM_REMOVED,
                        IncubationItemActionStage.REFUND_PENDING, IncubationItemActionStage.COMMITTED),
                IncubationItemActionStage.OPERATOR_REVIEW);
    }
}
