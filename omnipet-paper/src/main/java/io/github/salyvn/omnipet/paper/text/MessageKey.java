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
            "OmniPet foundation is enabled; pet features are loading in the next release phase."),

    // --- help --------------------------------------------------------------------------------
    HELP_HEADER("help.header", "<gold>OmniPet commands</gold> <gray>(page <page>/<pages>)</gray>"),
    HELP_LINE("help.line", "<yellow><usage></yellow> <dark_gray>-</dark_gray> <gray><description></gray>"),
    HELP_FOOTER("help.footer", "<gray>More on <yellow>/pet help <page></yellow></gray>"),
    HELP_EMPTY("help.empty", "<yellow>OmniPet: You have no available commands.</yellow>"),
    HELP_PAGE_INVALID("help.page-invalid", "<red>OmniPet: Help page must be a positive integer.</red>"),

    // --- Studio chat prompts -----------------------------------------------------------------
    // Every prompt states what is being edited, the format, an example, and how to abort. The
    // Studio previously captured chat silently, which is why operators reported that nothing worked.
    STUDIO_PROMPT_FIELD("studio.prompt.field", "<gold>OmniPet Studio</gold> <gray>-</gray> <yellow><field></yellow>"),
    STUDIO_PROMPT_FORMAT("studio.prompt.format", "<gray>Format:</gray> <white><format></white>"),
    STUDIO_PROMPT_EXAMPLE("studio.prompt.example", "<gray>Example:</gray> <white><example></white>"),
    STUDIO_PROMPT_CANCEL("studio.prompt.cancel", "<gray>Type <yellow>cancel</yellow> to abort.</gray>"),
    STUDIO_MODIFIER_PROMPT("studio.modifier.prompt",
            "<gold>OmniPet Studio</gold> <gray>-</gray> <yellow><stat></yellow> <gray>(<modifier>)</gray>"),
    STUDIO_MODIFIER_RANGE("studio.modifier.range", "<gray>Values:</gray> <white><detail></white>"),

    // --- GUI titles --------------------------------------------------------------------------
    // Titles carry location context so a player always knows which screen they are on.
    GUI_TITLE_HUB("gui.title.hub", "OmniPet"),
    GUI_TITLE_VAULT("gui.title.vault", "OmniPet <dark_gray>▸</dark_gray> Vault <gray>(<page>/<pages>)</gray>"),
    GUI_TITLE_HATCH("gui.title.hatch", "OmniPet <dark_gray>▸</dark_gray> Hatch"),
    GUI_TITLE_MANAGE("gui.title.manage", "OmniPet <dark_gray>▸</dark_gray> Manage"),
    GUI_TITLE_RELEASE("gui.title.release", "OmniPet <dark_gray>▸</dark_gray> Confirm release"),
    GUI_TITLE_SLOT_SELECT("gui.title.slot-select",
            "OmniPet <dark_gray>▸</dark_gray> Unlock slot <amount>"),
    GUI_TITLE_SLOT_CONFIRM("gui.title.slot-confirm",
            "OmniPet <dark_gray>▸</dark_gray> Confirm slot <amount>"),

    // --- vault menu --------------------------------------------------------------------------
    GUI_VAULT_PET_LEVEL("gui.vault.pet-level", "<gray>Level</gray> <white><level></white>"),
    GUI_VAULT_PET_RARITY("gui.vault.pet-rarity", "<gray>Rarity</gray> <white><status></white>"),
    GUI_VAULT_PET_RECALL("gui.vault.pet-recall", "<yellow>Left-click</yellow> <gray>recall</gray>"),
    GUI_VAULT_PET_ACTIVATE("gui.vault.pet-activate", "<yellow>Left-click</yellow> <gray>activate</gray>"),
    GUI_VAULT_PET_MANAGE("gui.vault.pet-manage",
            "<yellow>Right-click</yellow> <gray>manage, cultivate, or release</gray>"),
    GUI_VAULT_STATUS("gui.vault.status", "Vault status"),
    GUI_VAULT_OWNED("gui.vault.owned", "<gray>Owned</gray> <white><amount>/<total></white>"),
    GUI_VAULT_ACTIVE("gui.vault.active", "<gray>Active</gray> <white><amount>/<total></white>"),
    GUI_VAULT_OVERFLOW("gui.vault.overflow", "<red>Overflow is read-only; no pet was deleted</red>"),
    GUI_VAULT_PROVIDER_NOTE("gui.vault.provider-note",
            "<gray>Slot purchases use an explicit currency choice</gray>"),
    GUI_VAULT_UNLOCK_SLOT("gui.vault.unlock-slot", "<gold>Unlock active slot</gold>"),
    GUI_VAULT_UNLOCK_HINT("gui.vault.unlock-hint",
            "<yellow>Click</yellow> <gray>review configured prices</gray>"),
    GUI_VAULT_PREVIOUS("gui.vault.previous", "<yellow>Previous page</yellow>"),
    GUI_VAULT_NEXT("gui.vault.next", "<yellow>Next page</yellow>"),

    // --- hatch menu --------------------------------------------------------------------------
    GUI_HATCH_PREVIOUS("gui.hatch.previous", "<yellow>Previous hatch: <status></yellow>"),
    GUI_HATCH_PREVIOUS_PET("gui.hatch.previous-pet", "<gray>Resolved pet</gray> <white><pet></white>"),
    GUI_HATCH_PREVIOUS_HINT("gui.hatch.previous-hint", "<gray>A new egg can be started below.</gray>"),
    GUI_HATCH_START_MAIN("gui.hatch.start-main", "<aqua>Start main-hand egg</aqua>"),
    GUI_HATCH_START_MAIN_HINT("gui.hatch.start-main-hint",
            "<gray>Uses the egg in your selected hotbar slot.</gray>"),
    GUI_HATCH_START_OFF("gui.hatch.start-off", "<aqua>Start off-hand egg</aqua>"),
    GUI_HATCH_START_OFF_HINT("gui.hatch.start-off-hint", "<gray>Uses the egg in your off hand.</gray>"),
    GUI_HATCH_INCUBATION("gui.hatch.incubation", "Incubation <dark_gray>-</dark_gray> <status>"),
    GUI_HATCH_PET("gui.hatch.pet", "<gray>Pet</gray> <white><pet></white>"),
    GUI_HATCH_TIER("gui.hatch.tier", "<gray>Tier</gray> <white><status></white>"),
    GUI_HATCH_RARITY("gui.hatch.rarity", "<gray>Rarity</gray> <white><detail></white>"),
    GUI_HATCH_REMAINING("gui.hatch.remaining", "<gray>Remaining</gray> <white><remaining></white>"),
    GUI_HATCH_READY("gui.hatch.ready", "<green>Ready to claim into your vault.</green>"),
    GUI_HATCH_ONLINE_ONLY("gui.hatch.online-only", "<gray>Time advances only while you are online.</gray>"),
    GUI_HATCH_REFRESH("gui.hatch.refresh", "<yellow>Refresh</yellow>"),
    GUI_HATCH_REFRESH_HINT("gui.hatch.refresh-hint",
            "<gray>Read the latest durable incubation state.</gray>"),

    // --- slot purchase menu ------------------------------------------------------------------
    GUI_SLOT_PAY_VAULT("gui.slot.pay-vault", "Pay with Vault"),
    GUI_SLOT_PAY_PLAYERPOINTS("gui.slot.pay-playerpoints", "Pay with PlayerPoints"),
    GUI_SLOT_COST("gui.slot.cost", "<gray>Cost</gray> <white><cost></white>"),
    GUI_SLOT_BALANCE("gui.slot.balance", "<gray>Balance</gray> <white><balance></white>"),
    GUI_SLOT_BALANCE_UNAVAILABLE("gui.slot.balance-unavailable",
            "<red>Balance unavailable: <detail></red>"),
    GUI_SLOT_REVIEW_HINT("gui.slot.review-hint", "<yellow>Click</yellow> <gray>review this purchase</gray>"),
    GUI_SLOT_BACK_TO_VAULT("gui.slot.back-to-vault", "<yellow>Back to vault</yellow>"),
    GUI_SLOT_CONFIRM("gui.slot.confirm", "<green>Confirm purchase</green>"),
    GUI_SLOT_CONFIRM_PROVIDER("gui.slot.confirm-provider", "<gray>Provider</gray> <white><provider></white>"),
    GUI_SLOT_CONFIRM_ONCE("gui.slot.confirm-once",
            "<gray>One click creates one durable transaction.</gray>"),
    GUI_SLOT_CANCEL("gui.slot.cancel", "<red>Cancel</red>"),

    // --- management menu ---------------------------------------------------------------------
    GUI_MANAGE_FAVORITE("gui.manage.favorite", "Favorite"),
    GUI_MANAGE_UNFAVORITE("gui.manage.unfavorite", "Unfavorite"),
    GUI_MANAGE_FAVORITE_HINT("gui.manage.favorite-hint", "<gray>Pin this pet to the top of your vault.</gray>"),
    GUI_MANAGE_LOCK("gui.manage.lock", "Lock pet"),
    GUI_MANAGE_UNLOCK("gui.manage.unlock", "Unlock pet"),
    GUI_MANAGE_LOCKED_HINT("gui.manage.locked-hint", "<gray>Release and destructive actions are disabled.</gray>"),
    GUI_MANAGE_UNLOCKED_HINT("gui.manage.unlocked-hint", "<gray>Protect this pet from release.</gray>"),
    GUI_MANAGE_MOVE_LEFT("gui.manage.move-left", "Move left"),
    GUI_MANAGE_MOVE_RIGHT("gui.manage.move-right", "Move right"),
    GUI_MANAGE_MOVE_TARGET("gui.manage.move-target", "<gray>Target position</gray> <white><amount></white>"),
    GUI_MANAGE_CANDY("gui.manage.candy", "Use EXP candy"),
    GUI_MANAGE_LEVEL("gui.manage.level", "<gray>Level</gray> <white><level></white>"),
    GUI_MANAGE_EXPERIENCE("gui.manage.experience", "<gray>EXP</gray> <white><exp></white>"),
    GUI_MANAGE_CANDY_HINT("gui.manage.candy-hint",
            "<gray>The item is consumed only after the change persists.</gray>"),
    GUI_MANAGE_BREAKTHROUGH("gui.manage.breakthrough", "Breakthrough"),
    GUI_MANAGE_EVOLUTION("gui.manage.evolution", "<gray>Evolution</gray> <white><amount></white>"),
    GUI_MANAGE_BREAKTHROUGH_HINT("gui.manage.breakthrough-hint",
            "<gray>Requirements come from the captured stone.</gray>"),
    GUI_MANAGE_RELEASE("gui.manage.release", "Release pet"),
    GUI_MANAGE_RELEASE_LOCKED("gui.manage.release-locked", "<gray>Unlock this pet first.</gray>"),
    GUI_MANAGE_RELEASE_HINT("gui.manage.release-hint",
            "<gray>Preview the frozen rewards before confirming.</gray>"),
    GUI_MANAGE_REFRESH("gui.manage.refresh", "<aqua>Refresh</aqua>"),
    GUI_MANAGE_REFRESH_HINT("gui.manage.refresh-hint", "<gray>Re-read the latest durable state.</gray>"),
    GUI_MANAGE_BACK("gui.manage.back", "<yellow>Back to vault</yellow>"),
    GUI_MANAGE_DEFINITION("gui.manage.definition", "<gray>Definition</gray> <white><pet></white>"),
    GUI_MANAGE_INSTANCE("gui.manage.instance", "<dark_gray>Instance <detail></dark_gray>"),
    GUI_MANAGE_ACTIVE("gui.manage.active", "<green>Currently active</green>"),
    GUI_MANAGE_STORED("gui.manage.stored", "<gray>Stored in your vault</gray>"),
    GUI_RELEASE_CANCEL("gui.release.cancel", "<yellow>Cancel</yellow>"),
    GUI_RELEASE_CANCEL_HINT("gui.release.cancel-hint", "<gray>No state changes.</gray>"),
    GUI_RELEASE_CONFIRM("gui.release.confirm", "<red>Confirm release</red>"),
    GUI_RELEASE_REWARD_INTERNAL("gui.release.reward-internal",
            "<gray><detail></gray> <white>x<amount></white>"),
    GUI_RELEASE_REWARD_EXTERNAL("gui.release.reward-external",
            "<gray><provider></gray> <white><detail> <amount></white>"),
    GUI_RELEASE_REWARD_NONE("gui.release.reward-none", "<gray>No configured rewards</gray>"),
    GUI_RELEASE_ATOMIC_NOTE("gui.release.atomic-note",
            "<gray>Pet removal and the reward outbox persist together.</gray>"),
    GUI_RELEASE_PREVIEW("gui.release.preview", "<gold>Frozen reward preview</gold>"),
    GUI_RELEASE_REVISION("gui.release.revision", "<dark_gray>Revision <amount></dark_gray>"),
    GUI_RELEASE_TRANSACTION("gui.release.transaction", "<dark_gray>Transaction <detail></dark_gray>"),

    // --- management chat ---------------------------------------------------------------------
    MANAGE_FAILED("manage.failed", "<red>OmniPet: <detail></red>"),
    MANAGE_BUSY("manage.busy", "<yellow>OmniPet: <detail></yellow>"),
    MANAGE_RELEASED("manage.released", "<green>OmniPet: <detail></green>"),

    // --- hub ---------------------------------------------------------------------------------
    HUB_VAULT("hub.vault", "<gold>Pet vault</gold>"),
    HUB_VAULT_OWNED("hub.vault-owned", "<gray>Pets</gray> <white><amount>/<total></white>"),
    HUB_VAULT_ACTIVE("hub.vault-active", "<gray>Active</gray> <white><amount>/<total></white>"),
    HUB_VAULT_OVERFLOW("hub.vault-overflow", "<red>Over capacity by <amount></red>"),
    HUB_VAULT_HINT("hub.vault-hint", "<yellow>Click</yellow> <gray>open your vault</gray>"),
    HUB_HATCH("hub.hatch", "<aqua>Incubation</aqua>"),
    HUB_HATCH_STATUS("hub.hatch-status", "<gray>Status</gray> <white><status></white>"),
    HUB_HATCH_REMAINING("hub.hatch-remaining", "<gray>Remaining</gray> <white><remaining></white>"),
    HUB_HATCH_READY("hub.hatch-ready", "<green>Ready to claim</green>"),
    HUB_HATCH_IDLE("hub.hatch-idle", "<gray>No active incubation</gray>"),
    HUB_HATCH_HINT("hub.hatch-hint", "<yellow>Click</yellow> <gray>open the hatch menu</gray>"),
    HUB_SLOTS("hub.slots", "<gold>Active slots</gold>"),
    HUB_SLOTS_COUNT("hub.slots-count", "<gray>Unlocked</gray> <white><amount></white>"),
    HUB_SLOTS_HINT("hub.slots-hint", "<yellow>Click</yellow> <gray>review the next unlock</gray>"),
    HUB_HELP("hub.help", "<yellow>Commands</yellow>"),
    HUB_HELP_HINT("hub.help-hint", "<gray>List every command you can use.</gray>"),
    HUB_STUDIO("hub.studio", "<light_purple>Pet Studio</light_purple>"),
    HUB_STUDIO_HINT("hub.studio-hint", "<gray>Browse and edit pet definitions.</gray>"),
    HUB_BACK("hub.back", "<yellow>Back to hub</yellow>"),
    HUB_FAILURE("hub.failure", "<red>OmniPet: <detail>.</red>");

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
