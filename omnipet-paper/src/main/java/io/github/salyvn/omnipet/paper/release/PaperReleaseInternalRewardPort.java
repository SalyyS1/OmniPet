package io.github.salyvn.omnipet.paper.release;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.release.InternalRewardDeliveryPort;
import io.github.salyvn.omnipet.core.release.ReleaseRewardBundle;

public final class PaperReleaseInternalRewardPort implements InternalRewardDeliveryPort {
    private final ReleaseRewardMailbox mailbox;
    private final PaperReleaseMaterialMapper materials;
    private final ReleaseSyncExecutor sync;
    private final ReleaseInventoryGateway inventory;

    public PaperReleaseInternalRewardPort(
            ReleaseRewardMailbox mailbox,
            PaperReleaseMaterialMapper materials,
            PaperReleaseSyncExecutor sync,
            ReleaseInventoryGateway inventory) {
        this(mailbox, materials, (ReleaseSyncExecutor) sync, inventory);
    }

    PaperReleaseInternalRewardPort(
            ReleaseRewardMailbox mailbox,
            PaperReleaseMaterialMapper materials,
            ReleaseSyncExecutor sync,
            ReleaseInventoryGateway inventory) {
        this.mailbox = Objects.requireNonNull(mailbox, "release reward mailbox");
        this.materials = Objects.requireNonNull(materials, "release material mapper");
        this.sync = Objects.requireNonNull(sync, "release sync executor");
        this.inventory = Objects.requireNonNull(inventory, "release inventory gateway");
    }

    @Override
    public Outcome deliver(
            UUID playerId,
            UUID transactionId,
            List<ReleaseRewardBundle.InternalReward> rewards) {
        List<PaperMaterialReward> mapped;
        try {
            mapped = materials.map(rewards);
        } catch (RuntimeException invalid) {
            return Outcome.failed(detail(invalid, "release reward mapping failed"));
        }
        ReleaseMailboxEntry entry;
        try {
            entry = mailbox.create(new ReleaseMailboxEntry(
                    transactionId, playerId, ReleaseMailboxEntry.State.MAILBOX_PENDING, mapped,
                    "release entitlement accepted"));
        } catch (IOException failure) {
            return Outcome.failed(detail(failure, "release mailbox persistence failed"));
        }
        if (!entry.playerId().equals(playerId) || !entry.rewards().equals(mapped)) {
            return Outcome.failed("release mailbox transaction identity mismatch");
        }
        if (entry.state() == ReleaseMailboxEntry.State.INVENTORY_DELIVERED) {
            return Outcome.delivered("release transaction already delivered");
        }
        if (entry.state() == ReleaseMailboxEntry.State.UNKNOWN_REQUIRES_RECONCILIATION) {
            return Outcome.failed("release mailbox transaction requires reconciliation");
        }
        if (entry.state() == ReleaseMailboxEntry.State.INVENTORY_CLAIMING) {
            return Outcome.failed(markInterrupted(entry).detail());
        }
        MailboxClaimResult claim = claim(entry);
        return claim.status() == MailboxClaimResult.Status.UNKNOWN_REQUIRES_RECONCILIATION
                || claim.status() == MailboxClaimResult.Status.FAILED
                ? Outcome.failed(claim.detail())
                : Outcome.delivered(claim.detail());
    }

    public MailboxClaimResult recoverMailbox(UUID playerId, UUID transactionId) {
        try {
            ReleaseMailboxEntry entry = mailbox.find(transactionId).orElse(null);
            if (entry == null) return result(MailboxClaimResult.Status.NOT_FOUND, null, "release mailbox not found");
            if (!entry.playerId().equals(playerId)) {
                return result(MailboxClaimResult.Status.IDENTITY_MISMATCH, entry,
                        "release mailbox player identity mismatch");
            }
            if (entry.state() == ReleaseMailboxEntry.State.INVENTORY_DELIVERED) {
                return result(MailboxClaimResult.Status.INVENTORY_DELIVERED, entry, "already delivered");
            }
            if (entry.state() == ReleaseMailboxEntry.State.UNKNOWN_REQUIRES_RECONCILIATION) {
                return result(MailboxClaimResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, entry,
                        "release mailbox requires reconciliation");
            }
            if (entry.state() == ReleaseMailboxEntry.State.INVENTORY_CLAIMING) return markInterrupted(entry);
            return claim(entry);
        } catch (IOException failure) {
            return result(MailboxClaimResult.Status.FAILED, null, detail(failure, "mailbox recovery failed"));
        }
    }

    private MailboxClaimResult claim(ReleaseMailboxEntry entry) {
        ReleaseMailboxEntry claiming;
        try {
            claiming = mailbox.update(
                    entry.transactionId(), ReleaseMailboxEntry.State.INVENTORY_CLAIMING,
                    "inventory claim intent persisted");
        } catch (IOException failure) {
            return result(MailboxClaimResult.Status.FAILED, entry,
                    detail(failure, "inventory claim intent persistence failed"));
        }
        ReleaseInventoryClaimResult claim;
        try {
            claim = sync.call(() -> inventory.claim(
                    claiming.playerId(), claiming.transactionId(), claiming.rewards()));
        } catch (Exception | LinkageError failure) {
            return markInterrupted(claiming, detail(failure, "inventory claim result is unknown"));
        }
        try {
            return switch (claim.status()) {
                case DELIVERED, ALREADY_DELIVERED -> {
                    ReleaseMailboxEntry delivered = mailbox.update(
                            entry.transactionId(), ReleaseMailboxEntry.State.INVENTORY_DELIVERED, claim.detail());
                    yield result(MailboxClaimResult.Status.INVENTORY_DELIVERED, delivered, claim.detail());
                }
                case CAPACITY_FULL, PLAYER_OFFLINE -> {
                    ReleaseMailboxEntry pending = mailbox.update(
                            entry.transactionId(), ReleaseMailboxEntry.State.MAILBOX_PENDING, claim.detail());
                    yield result(MailboxClaimResult.Status.MAILBOX_PENDING, pending, claim.detail());
                }
                case AMBIGUOUS -> {
                    ReleaseMailboxEntry unknown = mailbox.update(
                            entry.transactionId(), ReleaseMailboxEntry.State.UNKNOWN_REQUIRES_RECONCILIATION,
                            claim.detail());
                    yield result(MailboxClaimResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, unknown, claim.detail());
                }
            };
        } catch (IOException failure) {
            return result(MailboxClaimResult.Status.FAILED, entry,
                    detail(failure, "mailbox claim acknowledgement failed"));
        }
    }

    private MailboxClaimResult markInterrupted(ReleaseMailboxEntry entry) {
        return markInterrupted(entry, "inventory claim was interrupted; reconciliation required");
    }

    private MailboxClaimResult markInterrupted(ReleaseMailboxEntry entry, String detail) {
        try {
            ReleaseMailboxEntry unknown = mailbox.update(
                    entry.transactionId(), ReleaseMailboxEntry.State.UNKNOWN_REQUIRES_RECONCILIATION, detail);
            return result(MailboxClaimResult.Status.UNKNOWN_REQUIRES_RECONCILIATION, unknown, detail);
        } catch (IOException failure) {
            return result(MailboxClaimResult.Status.FAILED, entry,
                    detail(failure, "inventory claim interruption could not be persisted"));
        }
    }

    private static MailboxClaimResult result(
            MailboxClaimResult.Status status, ReleaseMailboxEntry entry, String detail) {
        return new MailboxClaimResult(status, entry, detail);
    }

    private static String detail(Throwable failure, String fallback) {
        String message = failure.getMessage();
        String detail = message == null || message.isBlank() ? fallback : fallback + ": " + message;
        return detail.length() <= 512 ? detail : detail.substring(0, 512);
    }
}
