package io.github.salyvn.omnipet.core.progression;

import java.io.IOException;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CultivationItemActionService {
    private final CultivationItemActionJournal journal;

    public CultivationItemActionService(CultivationItemActionJournal journal) {
        this.journal = java.util.Objects.requireNonNull(journal, "cultivation action journal");
    }

    public Optional<CultivationItemActionTransaction> find(UUID token) throws IOException { return journal.find(token); }
    public List<CultivationItemActionTransaction> pending(UUID playerId, int limit) throws IOException {
        return journal.scan(playerId, Set.of(
                CultivationItemActionStage.PREPARED,
                CultivationItemActionStage.ITEM_REMOVED,
                CultivationItemActionStage.REFUND_PENDING), limit);
    }
    public List<CultivationItemActionTransaction> operatorReview(UUID playerId, int limit) throws IOException {
        return journal.scan(playerId, Set.of(CultivationItemActionStage.OPERATOR_REVIEW), limit);
    }
    public CultivationItemActionTransaction prepare(CultivationItemActionTransaction transaction) throws IOException {
        return journal.create(transaction);
    }
    public CultivationItemActionTransaction markItemRemoved(UUID token) throws IOException {
        return journal.transition(token, Set.of(CultivationItemActionStage.PREPARED),
                CultivationItemActionStage.ITEM_REMOVED);
    }
    public CultivationItemActionTransaction commit(UUID token) throws IOException {
        return journal.transition(token, Set.of(
                        CultivationItemActionStage.ITEM_REMOVED,
                        CultivationItemActionStage.REFUND_PENDING),
                CultivationItemActionStage.COMMITTED);
    }
    public CultivationItemActionTransaction markRefundPending(UUID token) throws IOException {
        return journal.transition(token, Set.of(CultivationItemActionStage.ITEM_REMOVED),
                CultivationItemActionStage.REFUND_PENDING);
    }
    public CultivationItemActionTransaction markRefunded(UUID token) throws IOException {
        return journal.transition(token, Set.of(CultivationItemActionStage.REFUND_PENDING),
                CultivationItemActionStage.REFUNDED);
    }
    public CultivationItemActionTransaction requireOperatorReview(UUID token) throws IOException {
        return journal.transition(token, Set.of(
                CultivationItemActionStage.PREPARED,
                CultivationItemActionStage.ITEM_REMOVED,
                CultivationItemActionStage.REFUND_PENDING), CultivationItemActionStage.OPERATOR_REVIEW);
    }
}
