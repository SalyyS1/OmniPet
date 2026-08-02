package io.github.salyvn.omnipet.paper.release;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.release.ExternalReconciliation;
import io.github.salyvn.omnipet.core.release.ExternalReleaseResult;
import io.github.salyvn.omnipet.core.release.ReleaseOutboxEntry;
import io.github.salyvn.omnipet.core.release.ReleaseOutboxDeliveryService;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;

public final class ReleaseAdminController {
    private final ReleaseRewardMailbox mailbox;
    private final PaperReleaseInternalRewardPort internal;
    private final PaperReleaseExternalRewardPort external;
    private final ReleaseOutboxDeliveryService delivery;
    private final PlayerStateRepository players;

    public ReleaseAdminController(
            ReleaseRewardMailbox mailbox,
            PaperReleaseInternalRewardPort internal,
            ReleaseOutboxDeliveryService delivery) {
        this(mailbox, internal, null, delivery, null);
    }

    public ReleaseAdminController(
            ReleaseRewardMailbox mailbox,
            PaperReleaseInternalRewardPort internal,
            PaperReleaseExternalRewardPort external,
            ReleaseOutboxDeliveryService delivery) {
        this(mailbox, internal, external, delivery, null);
    }

    public ReleaseAdminController(
            ReleaseRewardMailbox mailbox,
            PaperReleaseInternalRewardPort internal,
            PaperReleaseExternalRewardPort external,
            ReleaseOutboxDeliveryService delivery,
            PlayerStateRepository players) {
        this.mailbox = Objects.requireNonNull(mailbox, "release mailbox");
        this.internal = Objects.requireNonNull(internal, "internal release delivery");
        this.external = external;
        this.delivery = Objects.requireNonNull(delivery, "release outbox delivery");
        this.players = players;
    }

    public ReleaseAdminResult execute(ReleaseAdminCommand command) {
        if (command == null) return failed("release admin command is required");
        try {
            return switch (command) {
                case ReleaseAdminCommand.ListPending list -> list(list.limit());
                case ReleaseAdminCommand.Recover recover -> recover(recover);
                case ReleaseAdminCommand.Reconcile reconcile -> reconcile(reconcile);
            };
        } catch (IOException | RuntimeException failure) {
            return failed(detail(failure));
        }
    }

    private ReleaseAdminResult list(int limit) throws IOException {
        List<ReleaseMailboxEntry> entries = mailbox.list(limit);
        ArrayList<String> messages = new ArrayList<>();
        messages.add("OmniPet: " + entries.size() + " release mailbox transaction(s) require review.");
        entries.forEach(entry -> messages.add("- " + entry.transactionId()
                + " player=" + entry.playerId() + " state=" + entry.state()
                + " rewards=" + entry.rewards().size()));
        appendCoreOutboxes(messages, limit);
        return completed(messages);
    }

    private void appendCoreOutboxes(List<String> messages, int limit) throws IOException {
        if (players == null) return;
        int remaining = limit;
        for (UUID playerId : players.playerIds(10_000)) {
            if (remaining == 0) break;
            List<ReleaseOutboxEntry> pending = delivery.pending(playerId, remaining);
            for (ReleaseOutboxEntry entry : pending) {
                messages.add("- core " + entry.transactionId() + " player=" + playerId
                        + " internal=" + entry.internalState() + " external=" + entry.externalState());
                remaining--;
                if (remaining == 0) break;
            }
        }
    }

    private ReleaseAdminResult recover(ReleaseAdminCommand.Recover command) throws IOException {
        if (command.channel() == ReleaseAdminCommand.Channel.INTERNAL) {
            MailboxClaimResult result = internal.recoverMailbox(command.playerId(), command.transactionId());
            return completed("OmniPet: internal release recovery " + result.status() + " - " + result.detail());
        }
        ExternalReleaseResult result = delivery.recoverExternal(command.playerId(), command.transactionId());
        if (external != null && result.entry() != null
                && result.entry().externalState()
                        == io.github.salyvn.omnipet.core.release.ReleaseOutboxEntry.ExternalState.PENDING) {
            result = delivery.attemptExternal(command.playerId(), command.transactionId(), external);
        }
        return completed("OmniPet: external release recovery " + result.status() + " - " + result.detail());
    }

    private ReleaseAdminResult reconcile(ReleaseAdminCommand.Reconcile command) throws IOException {
        return switch (command.decision()) {
            case EXTERNAL_DELIVERED, EXTERNAL_NOT_DELIVERED -> {
                ExternalReconciliation decision = command.decision() == ReleaseAdminCommand.Decision.EXTERNAL_DELIVERED
                        ? ExternalReconciliation.CONFIRM_DELIVERED
                        : ExternalReconciliation.CONFIRM_NOT_DELIVERED;
                ExternalReleaseResult result = delivery.reconcileExternal(
                        command.playerId(), command.transactionId(), decision, "operator reconciliation");
                yield completed("OmniPet: external release reconciliation " + result.status());
            }
            case MAILBOX_PENDING, INVENTORY_DELIVERED -> {
                ReleaseMailboxEntry current = mailbox.find(command.transactionId()).orElse(null);
                if (current == null) yield failed("release mailbox transaction not found");
                if (!current.playerId().equals(command.playerId())) yield failed("release mailbox identity mismatch");
                ReleaseMailboxEntry.State state = command.decision() == ReleaseAdminCommand.Decision.MAILBOX_PENDING
                        ? ReleaseMailboxEntry.State.MAILBOX_PENDING
                        : ReleaseMailboxEntry.State.INVENTORY_DELIVERED;
                ReleaseMailboxEntry saved = mailbox.update(
                        command.transactionId(), state, "operator reconciliation");
                yield completed("OmniPet: mailbox release reconciliation " + saved.state());
            }
        };
    }

    private static ReleaseAdminResult completed(String message) {
        return completed(List.of(message));
    }

    private static ReleaseAdminResult completed(List<String> messages) {
        return new ReleaseAdminResult(ReleaseAdminResult.Status.COMPLETED, messages);
    }

    private static ReleaseAdminResult failed(String message) {
        return new ReleaseAdminResult(ReleaseAdminResult.Status.FAILED, List.of("OmniPet: " + message));
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
