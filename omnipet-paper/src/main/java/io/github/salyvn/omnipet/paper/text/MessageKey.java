package io.github.salyvn.omnipet.paper.text;

import java.util.Objects;

/**
 * Every player-facing text key with its built-in MiniMessage default.
 *
 * <p>This enum is the single source of truth: the shipped {@code messages.yml} resource is generated
 * from these defaults, so the file and the code cannot drift. Removing a key from the file restores
 * its default; the plugin never fails to start because of message text.
 *
 * <p>Operator and console output — transaction pages, opaque cursors, reconcile reasons, offline
 * inspect dumps, and item delivery receipts — is deliberately absent. That output is an audit trail
 * and stays in Java so an edited YAML file cannot distort it.
 *
 * <p>Defaults reproduce today's rendered text exactly, with only the MiniMessage color tag matching
 * the previous {@code NamedTextColor}. Wording changes belong to later phases.
 */
public enum MessageKey {
    VAULT_CHANGE_IN_FLIGHT("vault.change-in-flight", "<yellow>OmniPet: a pet change is already processing.</yellow>"),
    VAULT_REFRESHED("vault.refreshed", "<yellow>OmniPet vault changed; refreshed the page.</yellow>"),
    VAULT_OVERFLOW_RECALLED("vault.overflow-recalled",
            "<yellow>OmniPet recalled <amount> overflow active pet(s).</yellow>"),
    VAULT_MUTATION_REJECTED("vault.mutation-rejected", "<red>OmniPet: <status></red>"),
    VAULT_FAILURE("vault.failure", "<red>OmniPet: <detail>.</red>"),

    // --- hatch -------------------------------------------------------------------------------
    HATCH_USAGE("hatch.usage",
            "<yellow>OmniPet: Use /pet hatch [main|off|claim|refresh|use-main|use-off].</yellow>"),
    HATCH_VIEW_NOT_SCHEDULED("hatch.view-not-scheduled", "<red>OmniPet: Hatch view could not be scheduled.</red>"),
    HATCH_STATE_LOAD_FAILED("hatch.state-load-failed",
            "<red>OmniPet: hatch state could not be loaded.</red>"),
    HATCH_ACTION_ITEMS_UNAVAILABLE("hatch.action-items-unavailable",
            "<red>OmniPet: Incubation action items are unavailable.</red>"),
    HATCH_ACTION_IN_FLIGHT("hatch.action-in-flight", "<yellow>OmniPet: A hatch action is already processing.</yellow>"),
    HATCH_START_QUEUED("hatch.start-queued",
            "<green>OmniPet: Egg start queued; the escrow must commit before time advances.</green>"),
    HATCH_START_NOT_QUEUED("hatch.start-not-queued", "<red>OmniPet: Egg start could not be queued.</red>"),
    HATCH_START_FAILED("hatch.start-failed", "<red>OmniPet: Egg start failed.</red>"),
    HATCH_LIMITS_UNRESOLVED("hatch.limits-unresolved", "<red>OmniPet: Vault limits could not be resolved.</red>"),
    HATCH_NO_INCUBATION("hatch.no-incubation", "<red>OmniPet: No incubation is available.</red>"),
    HATCH_CLAIM_NOT_SCHEDULED("hatch.claim-not-scheduled", "<red>OmniPet: Claim could not be scheduled.</red>"),
    HATCH_CLAIM_FAILED("hatch.claim-failed", "<red>OmniPet: Claim failed: <detail></red>"),
    HATCH_CLAIM_LOCKED("hatch.claim-locked",
            "<red>OmniPet: Egg payment is still being recovered; claim is locked.</red>"),
    HATCH_CLAIMED("hatch.claimed", "<green>OmniPet: Pet claimed into your vault.</green>"),
    HATCH_CLAIM_RESULT("hatch.claim-result", "<green>OmniPet: Claim result: <status></green>"),
    HATCH_CLAIM_RESULT_REJECTED("hatch.claim-result-rejected", "<yellow>OmniPet: Claim result: <status></yellow>"),
    HATCH_ITEM_RESULT("hatch.item-result", "<green>OmniPet: Incubation item: <status>.</green>"),
    HATCH_ITEM_RESULT_PENDING("hatch.item-result-pending", "<yellow>OmniPet: Incubation item: <status>.</yellow>"),
    HATCH_ITEM_NO_INCUBATION("hatch.item-no-incubation", "<yellow>OmniPet: No incubation is active.</yellow>"),
    HATCH_ITEM_FAILED("hatch.item-failed", "<red>OmniPet: Incubation item failed: <detail>.</red>"),

    // --- active slots ------------------------------------------------------------------------
    SLOT_BASELINE_PENDING("slot.baseline-pending",
            "<yellow>OmniPet: Active slot baseline reconciliation is still pending; "
                    + "reopen this menu shortly.</yellow>"),
    SLOT_COUNTS_DIFFER("slot.counts-differ",
            "<red>OmniPet: OmniPet and LuckPerms slot counts differ; reconcile them before "
                    + "another purchase.</red>"),
    SLOT_NO_UPGRADE("slot.no-upgrade", "<yellow>OmniPet: No further active slot upgrade is configured.</yellow>"),
    SLOT_NOT_ELIGIBLE("slot.not-eligible",
            "<red>OmniPet: You do not meet the permission requirement for slot <amount>.</red>"),
    SLOT_ENTITLEMENT_UNAVAILABLE("slot.entitlement-unavailable",
            "<red>OmniPet: Slot entitlement provider unavailable: <detail></red>"),
    SLOT_PROVIDER_UNAVAILABLE("slot.provider-unavailable", "<red>OmniPet: <detail></red>"),
    SLOT_PRICE_CHANGED("slot.price-changed", "<yellow>OmniPet: Slot price changed; reopen the purchase menu.</yellow>"),
    SLOT_REQUIREMENTS_GONE("slot.requirements-gone",
            "<red>OmniPet: Slot purchase requirements are no longer available.</red>"),
    SLOT_PURCHASE_IN_FLIGHT("slot.purchase-in-flight",
            "<yellow>OmniPet: A slot purchase is already processing.</yellow>"),
    SLOT_UNLOCKED("slot.unlocked", "<green>OmniPet: Active slot <amount> unlocked.</green>"),
    SLOT_SYNC_PENDING("slot.sync-pending",
            "<yellow>OmniPet: Slot saved, but entitlement sync needs admin review: <detail></yellow>"),
    SLOT_PURCHASE_REJECTED("slot.purchase-rejected", "<red>OmniPet: Slot purchase <status>: <detail></red>"),
    SLOT_FAILURE("slot.failure", "<red>OmniPet: <detail>.</red>"),

    // --- active skills -----------------------------------------------------------------------
    SKILL_BINDING_REQUIRED("skill.binding-required", "<red>OmniPet: Skill binding ID is required.</red>"),
    SKILL_IN_FLIGHT("skill.in-flight", "<yellow>OmniPet: Another pet skill is still resolving.</yellow>"),
    SKILL_NOT_SCHEDULED("skill.not-scheduled", "<red>OmniPet: Skill request could not be scheduled.</red>"),
    SKILL_REQUIRES_ACTIVE_PET("skill.requires-active-pet",
            "<yellow>OmniPet: Only an active owned pet can cast a skill.</yellow>"),
    SKILL_PET_NOT_OWNED("skill.pet-not-owned", "<yellow>OmniPet: Pet is no longer owned.</yellow>"),
    SKILL_BINDING_NOT_FOUND("skill.binding-not-found",
            "<yellow>OmniPet: Active skill binding was not found.</yellow>"),
    SKILL_PROVIDER_UNAVAILABLE("skill.provider-unavailable",
            "<yellow>OmniPet: Skill provider or skill ID is unavailable.</yellow>"),
    SKILL_CHANCE_MISSED("skill.chance-missed", "<gray>OmniPet: The pet skill chance did not trigger.</gray>"),
    SKILL_REJECTED("skill.rejected", "<yellow>OmniPet: Skill rejected: <status>.</yellow>"),
    SKILL_PREPARE_FAILED("skill.prepare-failed", "<red>OmniPet: Skill preparation failed: <detail>.</red>"),
    SKILL_SUCCEEDED("skill.succeeded", "<green>OmniPet: Pet skill cast succeeded.</green>"),
    SKILL_COMPLETION_NEEDS_REVIEW("skill.completion-needs-review",
            "<red>OmniPet: Skill cast happened, but durable completion requires review: <status>.</red>"),
    SKILL_COMPLETION_NOT_SCHEDULED("skill.completion-not-scheduled",
            "<red>OmniPet: Skill cast happened; durable completion could not be scheduled and "
                    + "requires review.</red>"),
    SKILL_RESERVATION_PENDING("skill.reservation-pending",
            "<red>OmniPet: Skill cast happened; reservation remains pending for recovery: <detail>.</red>"),
    SKILL_ROLLED_BACK("skill.rolled-back", "<yellow>OmniPet: <detail></yellow>"),
    SKILL_ROLLBACK_NEEDS_REVIEW("skill.rollback-needs-review",
            "<red>OmniPet: <detail> Durable rollback needs review: <reason>.</red>"),
    SKILL_ROLLBACK_NOT_SCHEDULED("skill.rollback-not-scheduled",
            "<red>OmniPet: <detail> Durable rollback could not be scheduled.</red>"),
    SKILL_PLAYER_LEFT("skill.player-left", "Player left before the cast; reservation rolled back."),
    SKILL_PROVIDER_FAILED("skill.provider-failed", "Provider failed: <detail>."),
    SKILL_CAST_FAILED("skill.cast-failed", "Skill cast failed: <detail>."),

    // --- commands ----------------------------------------------------------------------------
    COMMAND_FOUNDATION_READY("command.foundation-ready",
            "OmniPet foundation is enabled; pet features are loading in the next release phase.");

    private final String path;
    private final String defaultValue;

    MessageKey(String path, String defaultValue) {
        this.path = path;
        this.defaultValue = defaultValue;
    }

    /** The dotted key used in {@code messages.yml}. */
    public String path() {
        return path;
    }

    /** The built-in MiniMessage string used when the file omits or cannot parse this key. */
    public String defaultValue() {
        return defaultValue;
    }

    /** Resolves a dotted path to its key, or {@code null} when the file carries an unknown key. */
    public static MessageKey byPath(String path) {
        Objects.requireNonNull(path, "message path");
        for (MessageKey key : values()) {
            if (key.path.equals(path)) return key;
        }
        return null;
    }
}
