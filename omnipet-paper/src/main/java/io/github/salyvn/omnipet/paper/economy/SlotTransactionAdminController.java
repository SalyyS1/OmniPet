package io.github.salyvn.omnipet.paper.economy;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.economy.ExternalEntitlementResult;
import io.github.salyvn.omnipet.core.economy.PurchaseJournalIssue;
import io.github.salyvn.omnipet.core.economy.PurchaseJournalScanResult;
import io.github.salyvn.omnipet.core.economy.SlotPurchaseReconciliationService;
import io.github.salyvn.omnipet.core.economy.SlotPurchaseResult;
import io.github.salyvn.omnipet.core.economy.SlotReconciliationDecision;
import io.github.salyvn.omnipet.core.economy.SlotUnlockService;
import io.github.salyvn.omnipet.paper.config.Phase4PaperConfig;
import io.github.salyvn.omnipet.paper.command.SlotTransactionAdminTarget;
import io.github.salyvn.omnipet.paper.entitlement.SlotEntitlementSynchronizer;

/** Keeps journal scans and manual reconciliation off the tick thread. */
public final class SlotTransactionAdminController implements SlotTransactionAdminTarget {
    private static final int MAX_ISSUE_MESSAGES = 20;

    private final JavaPlugin plugin;
    private final SlotPurchaseReconciliationService reconciliation;
    private final SlotUnlockService purchases;
    private final SlotEntitlementSynchronizer entitlements;
    /** One full menu page. The chest holds 45 rows above its control row. */
    private static final int MENU_PAGE_SIZE = 45;
    /**
     * How far the confirm screen scans to re-find its row.
     *
     * <p>Bounded like every other journal read, and read from the page the row was shown on: scanning
     * from the start of the journal instead would never reach a row on the second page, and would
     * report a still-pending transaction as already reconciled.
     */
    private static final int MENU_SCAN_LIMIT = 50;
    private final io.github.salyvn.omnipet.paper.gui.admin.AdminTransactionMenuRenderer menus =
            new io.github.salyvn.omnipet.paper.gui.admin.AdminTransactionMenuRenderer();
    private final AtomicInteger activeTasks = new AtomicInteger();
    private final Object idleMonitor = new Object();
    private volatile boolean shuttingDown;
    private volatile Phase4PaperConfig.ActiveSlots activeSlots;

    public SlotTransactionAdminController(
            JavaPlugin plugin,
            SlotPurchaseReconciliationService reconciliation,
            SlotUnlockService purchases,
            SlotEntitlementSynchronizer entitlements,
            Phase4PaperConfig.ActiveSlots activeSlots) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.reconciliation = Objects.requireNonNull(reconciliation, "reconciliation service");
        this.purchases = Objects.requireNonNull(purchases, "slot unlock service");
        this.entitlements = Objects.requireNonNull(entitlements, "slot entitlement synchronizer");
        this.activeSlots = Objects.requireNonNull(activeSlots, "active slot config");
    }

    public void updateActiveSlots(Phase4PaperConfig.ActiveSlots next) {
        activeSlots = Objects.requireNonNull(next, "active slot config");
    }

    public void list(CommandSender sender, int limit) {
        list(sender, limit, null);
    }

    @Override
    public void list(CommandSender sender, int limit, String cursor) {
        Objects.requireNonNull(sender, "sender");
        if (limit < 1 || limit > 50) {
            sender.sendMessage("OmniPet: transaction list limit must be between 1 and 50.");
            return;
        }
        submit(sender, () -> {
            PurchaseJournalScanResult scan = reconciliation.pending(limit, cursor);
            sendMain(sender, formatListMessages(scan, limit));
        });
    }

    static List<String> formatListMessages(PurchaseJournalScanResult scan, int limit) {
        Objects.requireNonNull(scan, "journal scan result");
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("transaction list limit is invalid");
        ArrayList<String> messages = new ArrayList<>();
        messages.add("OmniPet: " + Math.min(scan.transactions().size(), limit)
                + " transaction(s) require review"
                + (scan.nextCursor() != null ? " (page continues)." : "."));
        scan.transactions().stream().limit(limit).forEach(transaction -> messages.add(
                "- " + transaction.transactionId() + " player=" + transaction.playerId()
                        + " slot=" + transaction.slot() + " provider=" + transaction.amount().provider()
                        + " state=" + transaction.state()));
        int reportedIssues = Math.min(scan.issues().size(), MAX_ISSUE_MESSAGES);
        for (int index = 0; index < reportedIssues; index++) {
            PurchaseJournalIssue issue = scan.issues().get(index);
            messages.add("- unreadable " + issue.entryName() + ": " + issue.detail());
        }
        int omittedIssues = scan.omittedIssueCount()
                + Math.max(0, scan.issues().size() - reportedIssues);
        if (omittedIssues > 0) {
            messages.add("- " + omittedIssues + " additional unreadable journal issue(s) omitted.");
        }
        if (scan.issuesTruncated()) {
            messages.add("- unreadable journal discovery was truncated; additional issues may exist.");
        }
        if (scan.nextCursor() != null) {
            messages.add("OmniPet: next cursor " + scan.nextCursor()
                    + "; use /pet admin transactions " + limit + " " + scan.nextCursor() + ".");
        }
        return List.copyOf(messages);
    }

    @Override
    public void reconcile(
            CommandSender sender,
            UUID transactionId,
            SlotReconciliationDecision decision) {
        reconcile(sender, transactionId, decision, null);
    }

    /**
     * Applies one decision, running {@code afterApplied} on the main thread once the journal write has
     * finished.
     *
     * <p>The callback exists so a caller that wants to show the result — the menu reopening its list —
     * observes the decision instead of racing it.
     */
    public void reconcile(
            CommandSender sender,
            UUID transactionId,
            SlotReconciliationDecision decision,
            Runnable afterApplied) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(transactionId, "transaction id");
        Objects.requireNonNull(decision, "reconciliation decision");
        String actor = sender.getName();
        submit(sender, () -> {
            SlotPurchaseResult result;
            try {
                if (decision == SlotReconciliationDecision.ENTITLEMENT_SYNC_RETRY) {
                    result = synchronizeExternalEntitlement(transactionId, actor);
                } else {
                    result = reconciliation.reconcile(transactionId, decision, actor);
                    if (result.status() == SlotPurchaseResult.Status.ENTITLEMENT_SYNC_PENDING) {
                        result = synchronizeExternalEntitlement(transactionId, actor);
                    }
                }
            } finally {
                // Runs even when the decision failed: the caller's view is stale either way, and the
                // failure itself is already reported by submit().
                if (afterApplied != null) runMain(afterApplied);
            }
            sendMain(sender, "OmniPet: reconciliation " + result.status().name().toLowerCase()
                    + " - " + result.detail());
            plugin.getLogger().info("Slot transaction " + transactionId + " reconciled as "
                    + decision + " by " + actor + ": " + result.status());
        });
    }

    private SlotPurchaseResult synchronizeExternalEntitlement(UUID transactionId, String actor) throws IOException {
        return purchases.synchronizeExternalEntitlement(transactionId, transaction -> {
            var sync = entitlements.grant(
                    transaction.playerId(),
                    transaction.slot(),
                    activeSlots.entitlement());
            String detail = limited(sync.detail() + " by " + actor);
            return sync.succeeded()
                    ? ExternalEntitlementResult.completed(detail)
                    : ExternalEntitlementResult.pending(detail);
        });
    }

    private static String limited(String detail) {
        return detail.length() <= 512 ? detail : detail.substring(0, 512);
    }

    public void close() {
        shuttingDown = true;
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        synchronized (idleMonitor) {
            while (activeTasks.get() > 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    plugin.getLogger().severe("Timed out waiting for slot reconciliation tasks during disable.");
                    return;
                }
                try {
                    Duration wait = Duration.ofNanos(remaining);
                    idleMonitor.wait(Math.max(1, wait.toMillis()));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    plugin.getLogger().severe("Interrupted while waiting for slot reconciliation tasks.");
                    return;
                }
            }
        }
    }

    /**
     * Opens the pending-transaction menu, reading the journal off the main thread.
     *
     * <p>The menu exists to remove the copying: reconciling used to mean reading a UUID out of a chat
     * page and pasting it back with a decision word. The command form stays, because its continuation
     * cursor is an opaque token an operator pastes into a ticket, which no menu replaces.
     */
    public void openMenu(org.bukkit.entity.Player viewer, String cursor) {
        openMenu(viewer, cursor, null);
    }

    /**
     * Opens the pending-transaction menu, reporting a failed journal read through {@code onLoadFailed}.
     *
     * <p>A read that throws leaves no menu open, so without this the operator gets only the generic
     * operation-failed line and no indication that the list they asked for is the thing that is missing.
     * The text belongs to the caller for the same reason the missing-row text does: this class is the
     * audit trail.
     */
    public void openMenu(org.bukkit.entity.Player viewer, String cursor, Runnable onLoadFailed) {
        Objects.requireNonNull(viewer, "viewer");
        submit(viewer, () -> {
            PurchaseJournalScanResult scan;
            try {
                scan = reconciliation.pending(MENU_PAGE_SIZE, cursor);
            } catch (IOException | RuntimeException failure) {
                if (onLoadFailed != null) runMain(onLoadFailed);
                throw failure;
            }
            runMain(() -> {
                if (!viewer.isOnline()) return;
                viewer.openInventory(menus.renderList(viewer, scan, cursor));
            });
        });
    }

    /**
     * Opens the confirm screen for one transaction, re-reading it first.
     *
     * <p>The row is read again rather than carried from the list, so a transaction another operator
     * already reconciled cannot be acted on from a menu that is minutes old. The re-read starts from the
     * cursor the list page was loaded with, so a row on a later page is still found. When it has gone,
     * the list is reopened at the same page instead of silently doing nothing.
     */
    public void openConfirm(
            org.bukkit.entity.Player viewer, UUID transactionId, String cursor, Runnable onMissing) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(transactionId, "transaction id");
        Objects.requireNonNull(onMissing, "missing-row handler");
        submit(viewer, () -> {
            PurchaseJournalScanResult scan = reconciliation.pending(MENU_SCAN_LIMIT, cursor);
            var row = scan.transactions().stream()
                    .filter(candidate -> candidate.transactionId().equals(transactionId))
                    .findFirst()
                    .orElse(null);
            runMain(() -> {
                if (!viewer.isOnline()) return;
                if (row == null) {
                    // Reported by the caller, which owns menu-facing text: this class is the audit
                    // trail, and its output deliberately stays in Java where a YAML edit cannot reach.
                    onMissing.run();
                    openMenu(viewer, cursor);
                    return;
                }
                viewer.openInventory(menus.renderConfirm(viewer, row, cursor));
            });
        });
    }

    /**
     * Applies a decision from the confirm screen, then reopens the list so the operator sees the effect.
     *
     * <p>The reopen is chained onto the decision rather than scheduled alongside it. Scheduling both at
     * once lets the reopen win the race and redraw the row the operator just decided, which reads as the
     * decision having been lost.
     */
    public void reconcileFromMenu(
            org.bukkit.entity.Player viewer,
            UUID transactionId,
            SlotReconciliationDecision decision,
            String cursor) {
        Objects.requireNonNull(viewer, "viewer");
        reconcile(viewer, transactionId, decision, () -> {
            if (viewer.isOnline()) openMenu(viewer, cursor);
        });
    }

    private void runMain(Runnable task) {
        if (!plugin.isEnabled()) return;
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private void submit(CommandSender sender, CheckedTask task) {
        if (shuttingDown) {
            sender.sendMessage("OmniPet: transaction service is shutting down.");
            return;
        }
        activeTasks.incrementAndGet();
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    task.run();
                } catch (IOException | RuntimeException failure) {
                    sendMain(sender, "OmniPet: transaction operation failed - " + failure.getMessage());
                    plugin.getLogger().warning("Slot transaction operation failed: " + failure.getMessage());
                } finally {
                    if (activeTasks.decrementAndGet() == 0) {
                        synchronized (idleMonitor) {
                            idleMonitor.notifyAll();
                        }
                    }
                }
            });
        } catch (RuntimeException failure) {
            activeTasks.decrementAndGet();
            sender.sendMessage("OmniPet: transaction operation could not be scheduled.");
        }
    }

    private void sendMain(CommandSender sender, String message) {
        sendMain(sender, java.util.List.of(message));
    }

    private void sendMain(CommandSender sender, java.util.List<String> messages) {
        if (shuttingDown || !plugin.isEnabled()) return;
        plugin.getServer().getScheduler().runTask(plugin,
                () -> messages.forEach(sender::sendMessage));
    }

    @FunctionalInterface
    private interface CheckedTask {
        void run() throws IOException;
    }
}
