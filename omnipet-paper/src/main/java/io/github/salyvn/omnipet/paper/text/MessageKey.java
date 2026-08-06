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
    EGG_PLACED("hatch.egg-placed",
            "<green>OmniPet: The egg is incubating here.</green> "
                    + "<gray>Break the block to take it back.</gray>"),
    EGG_PLACEMENT_REFUSED("hatch.egg-placement-refused",
            "<red>OmniPet: <detail>.</red>"),
    EGG_RECLAIMED("hatch.egg-reclaimed", "<yellow>OmniPet: You took the egg back.</yellow>"),
    EGG_RECLAIM_FAILED("hatch.egg-reclaim-failed",
            "<red>OmniPet: That egg could not be picked up right now; try again.</red>"),
    EGG_HOLOGRAM_COUNTDOWN("hatch.egg-hologram-countdown",
            "<aqua>Hatching in</aqua> <white><remaining></white>"),
    EGG_HOLOGRAM_READY("hatch.egg-hologram-ready", "<green>Ready! Break to claim</green>"),
    EGG_PLACED_HATCHED("hatch.egg-placed-hatched",
            "<green>OmniPet: <pet> hatched from your placed egg.</green>"),
    EGG_PLACED_VAULT_FULL("hatch.egg-placed-vault-full",
            "<yellow>OmniPet: A placed egg is ready, but your vault is full.</yellow>"),
    EGG_NOT_PLACEABLE("hatch.egg-not-placeable",
            "<red>OmniPet: That egg cannot be placed as a block.</red> "
                    + "<gray>Hold it and run</gray> <white>/pet hatch main</white><gray>.</gray>"),
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
    GUI_TITLE_PLACED_EGG("gui.title.placed-egg", "OmniPet <dark_gray>▸</dark_gray> Egg"),
    GUI_TITLE_MANAGE("gui.title.manage", "OmniPet <dark_gray>▸</dark_gray> Manage"),
    GUI_TITLE_RELEASE("gui.title.release", "OmniPet <dark_gray>▸</dark_gray> Confirm release"),
    GUI_TITLE_ADMIN_TRANSACTIONS("gui.title.admin-transactions",
            "OmniPet <dark_gray>▸</dark_gray> Pending transactions"),
    GUI_TITLE_ADMIN_RECONCILE("gui.title.admin-reconcile",
            "OmniPet <dark_gray>▸</dark_gray> Reconcile transaction"),
    GUI_TITLE_SLOT_SELECT("gui.title.slot-select",
            "OmniPet <dark_gray>▸</dark_gray> Unlock slot <amount>"),
    GUI_TITLE_SLOT_CONFIRM("gui.title.slot-confirm",
            "OmniPet <dark_gray>▸</dark_gray> Confirm slot <amount>"),

    // --- management outcomes -------------------------------------------------------------------
    // A status only reaches the player when the outcome carried no detail of its own. Without these it
    // arrived as a lowercased enum name — "persisted consumption pending" told a player nothing and
    // leaked internal vocabulary into the chat.
    STATUS_REJECTED("status.rejected", "That change was refused."),
    STATUS_PET_NOT_FOUND("status.pet-not-found", "That pet is no longer in your vault."),
    STATUS_UNAUTHORIZED("status.unauthorized", "That pet is not yours."),
    STATUS_STALE_SESSION("status.stale-session", "This menu is out of date; reopen it."),
    STATUS_BUSY("status.busy", "Still working on your last change."),
    STATUS_CONSUMABLE_UNAVAILABLE("status.consumable-unavailable",
            "You are not holding the item that would apply."),
    STATUS_CONSUMPTION_PENDING("status.consumption-pending",
            "Applied, but the item is still being confirmed."),
    STATUS_OUTBOX_PENDING("status.outbox-pending", "Released; your reward is on its way."),
    STATUS_ERROR("status.error", "Something went wrong; try again."),

    // --- first join ----------------------------------------------------------------------------
    // The only guidance a new player gets. Without it nothing tells them the plugin exists: /pet is
    // tab-completable, but only for someone who already suspects there is something to type.
    ONBOARDING_WELCOME("onboarding.welcome",
            "<gray>You have a pet companion system here.</gray>"),
    ONBOARDING_WELCOME_HINT("onboarding.welcome-hint",
            "<yellow>Run <white>/pet</white> to open it.</yellow>"),

    // --- action bar ----------------------------------------------------------------------------
    // The action bar is a second channel, not a louder one: it confirms what just happened without
    // adding a chat line to scroll past. Only moments worth narrating get a key; a repeatable action
    // like changing the vault sort stays sound-only.
    ACTION_BAR_PET_ACTIVATED("action-bar.pet-activated", "<green>Pet summoned</green>"),
    ACTION_BAR_PET_RECALLED("action-bar.pet-recalled", "<gray>Pet recalled</gray>"),
    ACTION_BAR_HATCH_QUEUED("action-bar.hatch-queued", "<yellow>Incubation started</yellow>"),
    ACTION_BAR_HATCH_CLAIMED("action-bar.hatch-claimed", "<green>Hatched! Check your vault</green>"),
    ACTION_BAR_SLOT_UNLOCKED("action-bar.slot-unlocked", "<green>Active slot unlocked</green>"),
    // Staff-facing, but titles rather than audit output: an operator translating the plugin has no
    // reason to be left with six English screens, and a caption cannot distort a receipt.
    GUI_TITLE_STUDIO_TIERS("gui.title.studio-tiers", "OmniPet <dark_gray>▸</dark_gray> Studio"),
    GUI_TITLE_STUDIO_LIST("gui.title.studio-list",
            "OmniPet <dark_gray>▸</dark_gray> Studio <dark_gray>▸</dark_gray> <detail>"),
    GUI_TITLE_STUDIO_EDIT("gui.title.studio-edit",
            "OmniPet <dark_gray>▸</dark_gray> Edit <detail>"),
    GUI_TITLE_STUDIO_ARCHIVE("gui.title.studio-archive",
            "OmniPet <dark_gray>▸</dark_gray> Archive"),
    GUI_TITLE_STUDIO_STATS("gui.title.studio-stats",
            "OmniPet <dark_gray>▸</dark_gray> Stats"),
    GUI_TITLE_STUDIO_STAT("gui.title.studio-stat",
            "OmniPet <dark_gray>▸</dark_gray> <detail>"),

    // --- vault menu --------------------------------------------------------------------------
    GUI_VAULT_PET_LEVEL("gui.vault.pet-level", "<gray>Level</gray> <white><level></white>"),

    // --- nameplate layout -----------------------------------------------------------------------
    // The whole plate floating above an active pet, as one template. Separate keys for with and without
    // the status word, because an operator who turns the status off should not be left with the spacing
    // that was there to separate it. <name>, <level>, and <status> are the parts; anything else on the
    // line is the operator's own. Dropping a placeholder is allowed and simply omits that part, which is
    // how somebody hides the level without touching config.
    // Square brackets mark text that disappears with the level when a pet's level cannot be read, so a
    // pet with a malformed progression node shows no plate furniture rather than a bare "Lv.".
    GUI_PET_NAMEPLATE("gui.pet.nameplate", "<name>[ <dark_gray>Lv.</dark_gray><white><level></white>]"),
    GUI_PET_NAMEPLATE_STATUS("gui.pet.nameplate-status",
            "<name>[ <dark_gray>Lv.</dark_gray><white><level></white>] <status>"),

    // --- nameplate status -----------------------------------------------------------------------
    // Substituted into <status> above, so its owner can see at a glance whether the pet is keeping up.
    // Kept to one short word each: this sits over the pet's head, not in a menu.
    GUI_PET_STATUS_RESTING("gui.pet.status-resting", "<dark_gray>resting</dark_gray>"),
    GUI_PET_STATUS_IDLE("gui.pet.status-idle", "<gray>idle</gray>"),
    GUI_PET_STATUS_FOLLOWING("gui.pet.status-following", "<green>following</green>"),
    GUI_PET_STATUS_DASHING("gui.pet.status-dashing", "<yellow>dashing</yellow>"),
    GUI_VAULT_PET_EXPERIENCE("gui.vault.pet-experience", "<gray>Experience</gray> <white><exp></white>"),
    GUI_VAULT_PET_RARITY("gui.vault.pet-rarity", "<gray>Rarity</gray> <white><status></white>"),
    GUI_VAULT_PET_STATS("gui.vault.pet-stats", "<gray>Stats</gray>"),
    GUI_VAULT_PET_STAT_LINE("gui.vault.pet-stat-line",
            "<dark_gray>·</dark_gray> <gray><stat></gray> <white><amount></white>"),
    GUI_VAULT_PET_STATS_MORE("gui.vault.pet-stats-more",
            "<dark_gray>· and <amount> more</dark_gray>"),
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
    GUI_VAULT_SLOT_LOCKED("gui.vault.slot-locked", "<red>Locked active slot <amount></red>"),
    GUI_VAULT_SLOT_UNLOCKED_COUNT("gui.vault.slot-unlocked-count",
            "<gray>Active slots</gray> <white><amount>/<total></white>"),
    GUI_VAULT_SLOT_COST_LINE("gui.vault.slot-cost-line",
            "<dark_gray>·</dark_gray> <gray><provider></gray> <white><cost></white>"),
    GUI_VAULT_SLOT_LOCKED_HINT("gui.vault.slot-locked-hint",
            "<yellow>Click</yellow> <gray>unlock this slot</gray>"),
    GUI_VAULT_SLOT_LOCKED_PERMISSION("gui.vault.slot-locked-permission",
            "<red>You are not permitted to buy this slot yet</red>"),
    GUI_VAULT_SLOT_ALL_UNLOCKED("gui.vault.slot-all-unlocked",
            "<green>Every configured active slot is unlocked</green>"),
    GUI_VAULT_SLOT_NO_UPGRADE("gui.vault.slot-no-upgrade",
            "<gray>No further slot is offered for sale.</gray>"),
    GUI_VAULT_SLOT_SINGLE_PET("gui.vault.slot-single-pet",
            "<gray>This server allows one active pet.</gray>"),
    GUI_VAULT_PREVIOUS("gui.vault.previous", "<yellow>Previous page</yellow>"),
    GUI_VAULT_NEXT("gui.vault.next", "<yellow>Next page</yellow>"),
    GUI_VAULT_PET_FAVORITE("gui.vault.pet-favorite", "<gold>Favorite</gold>"),
    GUI_VAULT_LAST_PAGE("gui.vault.last-page", "<dark_gray>Last page</dark_gray>"),
    GUI_VAULT_LAST_PAGE_HINT("gui.vault.last-page-hint",
            "<gray>Page <page> of <pages>; there is nothing after this one.</gray>"),
    GUI_VAULT_SORT("gui.vault.sort", "<aqua>Sort: <status></aqua>"),
    GUI_VAULT_SORT_HINT("gui.vault.sort-hint", "<yellow>Click</yellow> <gray>switch to <detail></gray>"),
    GUI_VAULT_FILTER("gui.vault.filter", "<aqua>Show: <status></aqua>"),
    GUI_VAULT_FILTER_HINT("gui.vault.filter-hint", "<yellow>Click</yellow> <gray>switch to <detail></gray>"),
    GUI_VAULT_MATCHED("gui.vault.matched", "<gray>Showing</gray> <white><amount>/<total></white>"),
    GUI_VAULT_EMPTY("gui.vault.empty", "<yellow>You do not own any pets yet</yellow>"),
    GUI_VAULT_EMPTY_HINT("gui.vault.empty-hint", "<gray>Use <white>/pet hatch</white> to start an egg.</gray>"),
    GUI_VAULT_NO_MATCHES("gui.vault.no-matches", "<yellow>No pet matches <status></yellow>"),
    GUI_VAULT_NO_MATCHES_HINT("gui.vault.no-matches-hint",
            "<gray>You own <white><total></white>; click the filter to show all of them.</gray>"),
    GUI_VAULT_OVERFLOW_RELEASE("gui.vault.overflow-release",
            "<gray>Release a pet you no longer want, or</gray>"),
    GUI_VAULT_OVERFLOW_CAPACITY("gui.vault.overflow-capacity",
            "<gray>buy more capacity to store the rest.</gray>"),

    // --- hatch menu --------------------------------------------------------------------------
    GUI_HATCH_PREVIOUS("gui.hatch.previous", "<yellow>Previous hatch: <status></yellow>"),
    GUI_HATCH_PREVIOUS_PET("gui.hatch.previous-pet", "<gray>Resolved pet</gray> <white><pet></white>"),
    GUI_HATCH_PREVIOUS_HINT("gui.hatch.previous-hint", "<gray>A new egg can be started below.</gray>"),

    // --- egg item ----------------------------------------------------------------------------
    GUI_EGG_ITEM_NAME("gui.egg.item-name", "<gold><pet> Egg</gold>"),
    GUI_EGG_ITEM_TIER("gui.egg.item-tier", "<gray>Tier</gray> <white><status></white>"),
    GUI_EGG_ITEM_DURATION("gui.egg.item-duration", "<gray>Incubates in</gray> <white><detail></white>"),
    GUI_EGG_ITEM_HINT("gui.egg.item-hint",
            "<yellow>Hold and run <white>/pet hatch main</white></yellow> <gray>to start</gray>"),
    // Placing an egg has always worked but nothing said so, so the feature was reachable only by
    // accident. The command stays first: it is the path that works anywhere.
    GUI_EGG_ITEM_PLACE_HINT("gui.egg.item-place-hint",
            "<gray>or place it beside a heat source</gray>"),

    // --- placed egg menu ----------------------------------------------------------------------
    // Support items previously worked only on a held incubation, so an egg on the ground could not be
    // hurried at all. Right-clicking it opens this; the item is spent from the hand, never stored here.
    GUI_PLACED_EGG_STATUS("gui.placed-egg.status", "<gold>Incubating</gold>"),
    GUI_PLACED_EGG_REMAINING("gui.placed-egg.remaining",
            "<gray>Time left</gray> <white><remaining></white>"),
    GUI_PLACED_EGG_READY("gui.placed-egg.ready", "<green>Ready — break the egg to claim it</green>"),
    GUI_PLACED_EGG_OWNER_NOTE("gui.placed-egg.owner-note",
            "<gray>Only the player who placed it can hurry it along.</gray>"),
    GUI_PLACED_EGG_MAIN_HAND("gui.placed-egg.main-hand", "<yellow>Main hand</yellow>"),
    GUI_PLACED_EGG_OFF_HAND("gui.placed-egg.off-hand", "<yellow>Off hand</yellow>"),
    GUI_PLACED_EGG_HAND_EMPTY("gui.placed-egg.hand-empty",
            "<gray>Hold an accelerator or an instant hatch.</gray>"),
    GUI_PLACED_EGG_HAND_UNSUPPORTED("gui.placed-egg.hand-unsupported",
            "<gray>That item does nothing for an egg.</gray>"),
    GUI_PLACED_EGG_WOULD_REDUCE("gui.placed-egg.would-reduce",
            "<gray>Takes off</gray> <white><detail></white>"),
    GUI_PLACED_EGG_WOULD_FINISH("gui.placed-egg.would-finish",
            "<green>Finishes this egg at once</green>"),
    GUI_PLACED_EGG_SPEND_HINT("gui.placed-egg.spend-hint",
            "<yellow>Click</yellow> <gray>spend this item</gray>"),
    GUI_PLACED_EGG_REFRESH("gui.placed-egg.refresh", "<yellow>Refresh</yellow>"),
    GUI_PLACED_EGG_REFRESH_HINT("gui.placed-egg.refresh-hint",
            "<gray>Re-reads the countdown and what you are holding.</gray>"),
    PLACED_EGG_NOT_OWNER("placed-egg.not-owner",
            "<yellow>OmniPet: Only the player who placed this egg can hurry it along.</yellow>"),
    PLACED_EGG_GONE("placed-egg.gone", "<yellow>OmniPet: That egg is no longer there.</yellow>"),
    PLACED_EGG_ALREADY_READY("placed-egg.already-ready",
            "<green>OmniPet: That egg is ready; break it to claim your pet.</green>"),
    PLACED_EGG_NOTHING_TO_SPEND("placed-egg.nothing-to-spend",
            "<yellow>OmniPet: You are not holding anything that helps an egg.</yellow>"),
    PLACED_EGG_REDUCED("placed-egg.reduced",
            "<green>OmniPet: Took <detail> off the egg; <remaining> left.</green>"),
    PLACED_EGG_FINISHED("placed-egg.finished",
            "<green>OmniPet: The egg is ready — break it to claim your pet.</green>"),
    PLACED_EGG_SPEND_FAILED("placed-egg.spend-failed",
            "<red>OmniPet: That item could not be spent, so you still have it: <detail>.</red>"),

    // --- admin transaction menu ---------------------------------------------------------------
    // Operator-facing, but these are menu labels rather than the audit trail MessageKey deliberately
    // keeps in Java: an edited YAML file cannot distort a button caption the way it could a receipt.
    GUI_ADMIN_TX_NAME("gui.admin.tx-name", "<white>Slot <amount></white> <gray>- <status></gray>"),
    GUI_ADMIN_TX_PLAYER("gui.admin.tx-player", "<gray>Player</gray> <white><detail></white>"),
    GUI_ADMIN_TX_AMOUNT("gui.admin.tx-amount", "<gray>Amount</gray> <white><detail></white>"),
    GUI_ADMIN_TX_ID("gui.admin.tx-id", "<dark_gray><detail></dark_gray>"),
    GUI_ADMIN_TX_DETAIL("gui.admin.tx-detail", "<gray>Note</gray> <white><detail></white>"),
    GUI_ADMIN_TX_HINT("gui.admin.tx-hint", "<yellow>Click to reconcile this transaction</yellow>"),
    GUI_ADMIN_TX_COUNT("gui.admin.tx-count", "<gray>Showing <amount> transaction(s)</gray>"),
    GUI_ADMIN_NEXT_PAGE("gui.admin.next-page", "<yellow>Next page</yellow>"),
    GUI_ADMIN_REFRESH("gui.admin.refresh", "<yellow>Refresh</yellow>"),
    GUI_ADMIN_BACK("gui.admin.back", "<yellow>Back to the list</yellow>"),
    GUI_ADMIN_DECISION("gui.admin.decision", "<white><status></white>"),
    GUI_ADMIN_DECISION_IRREVERSIBLE("gui.admin.decision-irreversible",
            "<red>This cannot be undone.</red>"),
    GUI_ADMIN_DECISION_CHARGE("gui.admin.decision-charge",
            "<gray>The player was charged; keep the money and grant the slot.</gray>"),
    GUI_ADMIN_DECISION_NO_CHARGE("gui.admin.decision-no-charge",
            "<gray>The player was not charged; grant nothing and close the transaction.</gray>"),
    GUI_ADMIN_DECISION_REFUND("gui.admin.decision-refund",
            "<gray>Return the money to the player and close the transaction.</gray>"),
    GUI_ADMIN_DECISION_SYNC("gui.admin.decision-sync",
            "<gray>Retry only the external entitlement check; no money moves.</gray>"),
    GUI_ADMIN_TX_GONE("gui.admin.tx-gone",
            "<yellow>OmniPet: that transaction was already reconciled; the list has been refreshed.</yellow>"),
    GUI_ADMIN_TX_LOAD_FAILED("gui.admin.tx-load-failed",
            "<red>OmniPet: pending transactions could not be read.</red>"),

    // --- consumable items --------------------------------------------------------------------
    // Every OmniPet consumable is a plain vanilla material, so without a name and a usage line it is
    // indistinguishable from the vanilla item and nothing tells the holder how to redeem it.
    GUI_CANDY_ITEM_NAME("gui.item.candy-name", "<green>OmniPet EXP Candy</green>"),
    GUI_CANDY_ITEM_DETAIL("gui.item.candy-detail", "<gray>Grants</gray> <white><detail> EXP</white>"),
    GUI_CANDY_ITEM_HINT("gui.item.candy-hint",
            "<yellow>Open <white>/pet</white>, right-click a pet, then click EXP candy</yellow>"),
    GUI_BREAKTHROUGH_ITEM_NAME("gui.item.breakthrough-name", "<light_purple>OmniPet Breakthrough Stone</light_purple>"),
    GUI_BREAKTHROUGH_ITEM_DETAIL("gui.item.breakthrough-detail",
            "<gray>Requires level</gray> <white><detail></white>"),
    GUI_BREAKTHROUGH_ITEM_HINT("gui.item.breakthrough-hint",
            "<yellow>Open <white>/pet</white>, right-click a pet, then click Breakthrough</yellow>"),
    GUI_REDUCER_ITEM_NAME("gui.item.reducer-name", "<aqua>OmniPet Hatch Accelerator</aqua>"),
    GUI_REDUCER_ITEM_DETAIL("gui.item.reducer-detail",
            "<gray>Cuts</gray> <white><detail></white> <gray>from the current hatch</gray>"),
    GUI_REDUCER_ITEM_HINT("gui.item.reducer-hint",
            "<yellow>Hold and run <white>/pet hatch use-main</white></yellow>"),
    GUI_INSTANT_ITEM_NAME("gui.item.instant-name", "<gold>OmniPet Instant Hatch</gold>"),
    GUI_INSTANT_ITEM_DETAIL("gui.item.instant-detail", "<gray>Finishes the current hatch at once</gray>"),
    GUI_INSTANT_ITEM_HINT("gui.item.instant-hint",
            "<yellow>Hold and run <white>/pet hatch use-main</white></yellow>"),

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
    GUI_HATCH_REDEEM_MAIN("gui.hatch.redeem-main", "<aqua>Use main-hand item</aqua>"),
    GUI_HATCH_REDEEM_MAIN_HINT("gui.hatch.redeem-main-hint",
            "<gray>Applies a hatch accelerator or instant hatch from your selected slot.</gray>"),
    GUI_HATCH_REDEEM_OFF("gui.hatch.redeem-off", "<aqua>Use off-hand item</aqua>"),
    GUI_HATCH_REDEEM_OFF_HINT("gui.hatch.redeem-off-hint",
            "<gray>Applies a hatch accelerator or instant hatch from your off hand.</gray>"),
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
