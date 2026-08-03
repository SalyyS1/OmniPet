package io.github.salyvn.omnipet.paper.command;

/**
 * The single declarative description of what {@code /pet} accepts today.
 *
 * <p>Every literal and permission here is transcribed from the live dispatcher in
 * {@link OmniPetCommand#execute} and from each parser's permission constant — not from docs.
 * {@code OmniPetCommandTreeContractTest} asserts every permission is declared in
 * {@code paper-plugin.yml}, so a typo cannot silently hide a branch from everyone.
 *
 * <p>{@code /pet} itself opens the hub. {@code /pet <page>} still opens a vault page directly and is
 * not listed as a separate node, because the bare numeric argument has no literal to suggest.
 */
public final class OmniPetCommandTree {
    private static final CommandSpec ROOT = build();

    private OmniPetCommandTree() {}

    public static CommandSpec root() {
        return ROOT;
    }

    private static CommandSpec build() {
        return CommandSpec.of("pet", "Open the OmniPet hub")
                .permission(AdminPetCommandParser.GENERAL_PERMISSION)
                .playerOnly()
                .child(
                        CommandSpec.of("vault", "Open your pet vault")
                                .args("[page]")
                                .permission(AdminPetCommandParser.GENERAL_PERMISSION)
                                .playerOnly(),
                        hatch(),
                        CommandSpec.of("slot", "Review and buy the next active slot")
                                .permission(AdminPetCommandParser.GENERAL_PERMISSION)
                                .playerOnly(),
                        CommandSpec.of("skill", "Cast an active skill from one of your pets")
                                .args("<pet-uuid>", "<binding-id>")
                                .permission(AdminPetCommandParser.GENERAL_PERMISSION)
                                .playerOnly(),
                        CommandSpec.of("help", "List the commands you can use")
                                .args("[page]")
                                .permission(AdminPetCommandParser.GENERAL_PERMISSION),
                        admin())
                .build();
    }

    private static CommandSpec.Builder hatch() {
        return CommandSpec.of("hatch", "Open the incubation menu")
                .permission(AdminPetCommandParser.GENERAL_PERMISSION)
                .playerOnly()
                .child(
                        leaf("main", "Start incubating the egg in your main hand"),
                        leaf("off", "Start incubating the egg in your off hand"),
                        leaf("claim", "Claim a ready pet into your vault"),
                        leaf("refresh", "Re-read the latest durable incubation state"),
                        leaf("use-main", "Redeem the action item in your main hand"),
                        leaf("use-off", "Redeem the action item in your off hand"));
    }

    private static CommandSpec.Builder leaf(String literal, String description) {
        return CommandSpec.of(literal, description)
                .permission(AdminPetCommandParser.GENERAL_PERMISSION)
                .playerOnly();
    }

    private static CommandSpec.Builder admin() {
        return CommandSpec.of("admin", "Administration commands")
                .group()
                .child(
                        CommandSpec.of("browse", "Open the Pet Studio definition browser")
                                .permission(AdminPetCommandParser.MANAGE_PET_PERMISSION)
                                .playerOnly(),
                        CommandSpec.of("reload", "Reload config, messages, and definitions")
                                .permission("omnipet.admin.reload"),
                        adminItem(),
                        adminEgg(),
                        adminPet(),
                        adminHatch(),
                        adminSkill(),
                        adminCultivation(),
                        adminRelease(),
                        CommandSpec.of("transactions", "List pending slot transactions")
                                .args("[limit]", "[cursor]")
                                .permission(SlotTransactionAdminCommandParser.PERMISSION),
                        CommandSpec.of("reconcile", "Resolve one slot transaction")
                                .args("<transaction-uuid>", "<charge|no-charge|refund|sync>")
                                .permission(SlotTransactionAdminCommandParser.PERMISSION));
    }

    private static CommandSpec.Builder adminItem() {
        // The dispatcher gates every item type on omnipet.admin.item, then additionally requires
        // omnipet.admin.cultivation for the candy and breakthrough types (OmniPetCommand:352-356).
        return CommandSpec.of("item", "Give OmniPet items to an online player")
                .group()
                .child(
                        CommandSpec.of("reducer", "Give incubation time-reducer items")
                                .args("<online-player>", "<seconds>", "[amount]")
                                .permission("omnipet.admin.item"),
                        CommandSpec.of("instant", "Give instant-hatch items")
                                .args("<online-player>", "[amount]")
                                .permission("omnipet.admin.item"),
                        CommandSpec.of("candy", "Give EXP candy")
                                .args("<online-player>", "[amount]")
                                .permission("omnipet.admin.item", "omnipet.admin.cultivation"),
                        CommandSpec.of("breakthrough", "Give breakthrough stones")
                                .args("<online-player>", "[amount]")
                                .permission("omnipet.admin.item", "omnipet.admin.cultivation"));
    }

    private static CommandSpec.Builder adminEgg() {
        return CommandSpec.of("egg", "Distribute and define hatchable eggs")
                .group()
                .child(
                        CommandSpec.of("give", "Give a hatchable egg to an online player")
                                .args("<online-player>", "<egg-id>", "[amount]")
                                .permission("omnipet.admin.egg"),
                        CommandSpec.of("create", "Define an egg that hatches one pet definition")
                                .args("<egg-id>", "<definition-id>", "[duration]")
                                .permission("omnipet.admin.egg"));
    }

    private static CommandSpec.Builder adminPet() {
        // Separate from omnipet.admin.egg: granting a pet skips incubation entirely, so it is a
        // stronger capability than handing out an egg the player still has to hatch.
        return CommandSpec.of("pet", "Grant a pet directly into a player's vault")
                .group()
                .child(
                        CommandSpec.of("give", "Grant a pet, bypassing incubation")
                                .args("<online-player>", "<definition-id>")
                                .permission("omnipet.admin.petgive"));
    }

    private static CommandSpec.Builder adminHatch() {        return CommandSpec.of("hatch", "Inspect and administer incubation state")
                .group()
                .child(
                        CommandSpec.of("inspect", "Show durable incubation state for a player")
                                .args("<player-uuid>")
                                .permission(HatchAdminCommandParser.INSPECT_PERMISSION),
                        CommandSpec.of("reduce", "Reduce remaining incubation time")
                                .args("<player-uuid>", "<incubation-uuid>", "<millis>", "<action-uuid>")
                                .permission(HatchAdminCommandParser.MANAGE_PERMISSION),
                        CommandSpec.of("set", "Set remaining incubation time")
                                .args("<player-uuid>", "<incubation-uuid>", "<millis>", "<action-uuid>")
                                .permission(HatchAdminCommandParser.MANAGE_PERMISSION),
                        CommandSpec.of("complete", "Complete an incubation")
                                .args("<player-uuid>", "<incubation-uuid>", "<action-uuid>")
                                .permission(HatchAdminCommandParser.MANAGE_PERMISSION),
                        CommandSpec.of("cancel", "Cancel an incubation")
                                .args("<player-uuid>", "<incubation-uuid>", "<action-uuid>")
                                .permission(HatchAdminCommandParser.MANAGE_PERMISSION));
    }

    private static CommandSpec.Builder adminSkill() {
        return CommandSpec.of("skill", "Inspect and roll back pending skill reservations")
                .group()
                .child(
                        CommandSpec.of("pending", "List pending skill reservations")
                                .args("<player-uuid>")
                                .permission("omnipet.admin.skill"),
                        CommandSpec.of("rollback", "Roll back one pending reservation")
                                .args("<player-uuid>", "<pet-uuid>", "<action-uuid>")
                                .permission("omnipet.admin.skill"));
    }

    private static CommandSpec.Builder adminCultivation() {
        return CommandSpec.of("cultivation", "Inspect and recover cultivation actions")
                .group()
                .child(
                        CommandSpec.of("pending", "List pending cultivation actions")
                                .args("<player-uuid>", "[limit]")
                                .permission("omnipet.admin.cultivation"),
                        CommandSpec.of("review", "List cultivation actions needing operator review")
                                .args("<player-uuid>", "[limit]")
                                .permission("omnipet.admin.cultivation"),
                        CommandSpec.of("recover", "Recover one cultivation action")
                                .args("<player-uuid>", "<action-uuid>")
                                .permission("omnipet.admin.cultivation"));
    }

    private static CommandSpec.Builder adminRelease() {
        return CommandSpec.of("release", "Inspect and reconcile pet release rewards")
                .group()
                .child(
                        CommandSpec.of("list", "List pending release transactions")
                                .args("[limit]")
                                .permission("omnipet.admin.release"),
                        CommandSpec.of("recover", "Recover one release reward outbox")
                                .args("<player-uuid>", "<transaction-uuid>", "<internal|external>")
                                .permission("omnipet.admin.release"),
                        CommandSpec.of("reconcile", "Reconcile one external release reward")
                                .args("<transaction-uuid>", "<decision>")
                                .permission("omnipet.admin.release"));
    }
}
