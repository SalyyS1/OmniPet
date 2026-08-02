package io.github.salyvn.omnipet.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.economy.SlotReconciliationDecision;
import io.papermc.paper.command.brigadier.CommandSourceStack;

class OmniPetCommandDispatchTest {
    @Test
    void executeForwardsTransactionListLimitAndCursor() {
        RecordingTransactions transactions = new RecordingTransactions();
        OmniPetCommand command = command(transactions);

        command.execute(source(permittedSender()), new String[] {
                "admin", "transactions", "7", "v1.a.start"
        });

        assertEquals(7, transactions.limit);
        assertEquals("v1.a.start", transactions.cursor);
        assertNull(transactions.transactionId);
    }

    @Test
    void executeDispatchesEveryReconciliationDecision() {
        LinkedHashMap<String, SlotReconciliationDecision> decisions = new LinkedHashMap<>();
        decisions.put("charge", SlotReconciliationDecision.CHARGE_CONFIRMED);
        decisions.put("no-charge", SlotReconciliationDecision.NO_CHARGE_CONFIRMED);
        decisions.put("refund", SlotReconciliationDecision.REFUND_CONFIRMED);
        decisions.put("sync", SlotReconciliationDecision.ENTITLEMENT_SYNC_RETRY);

        for (Map.Entry<String, SlotReconciliationDecision> entry : decisions.entrySet()) {
            RecordingTransactions transactions = new RecordingTransactions();
            UUID transactionId = UUID.randomUUID();

            command(transactions).execute(source(permittedSender()), new String[] {
                    "admin", "reconcile", transactionId.toString(), entry.getKey()
            });

            assertEquals(transactionId, transactions.transactionId);
            assertEquals(entry.getValue(), transactions.decision);
        }
    }

    @Test
    void executeDispatchesHatchActionOnlyForValidPlayerSyntax() {
        RecordingHatches hatches = new RecordingHatches();
        RecordingTransactions transactions = new RecordingTransactions();
        OmniPetCommand command = command(hatches, transactions);

        command.execute(source(playerSender()), new String[] {"hatch", "off"});

        assertEquals("off", hatches.action);

        hatches.action = null;
        command.execute(source(playerSender()), new String[] {"hatch", "main", "extra"});
        assertNull(hatches.action);

        command.execute(source(permittedSender()), new String[] {"hatch", "main"});
        assertNull(hatches.action);
    }

    @Test
    void executeDispatchesActiveSkillAndAdminRecoverySyntax() {
        RecordingSkills skills = new RecordingSkills();
        OmniPetCommand command = new OmniPetCommand(
                null, null, null, null, null, skills, skills,
                new RecordingTransactions(), null, () -> false);
        UUID petId = UUID.randomUUID();

        command.execute(source(playerSender()), new String[] {"skill", petId.toString(), "active_one"});
        assertEquals(petId, skills.petId);
        assertEquals("active_one", skills.bindingId);

        UUID playerId = UUID.randomUUID();
        command.execute(source(senderWithPermissions("omnipet.admin.skill")), new String[] {
                "admin", "skill", "pending", playerId.toString()
        });
        assertEquals(List.of("pending", playerId.toString()), skills.adminArguments);
    }

    @Test
    void cultivationItemRequiresBothPermissionsAndDispatchesBeforeIncubation() {
        RecordingItemTarget incubation = new RecordingItemTarget();
        RecordingCultivationTarget cultivation = new RecordingCultivationTarget();
        OmniPetCommand command = command(incubation, cultivation, null);

        command.execute(source(senderWithPermissions("omnipet.admin.item")), new String[] {
                "admin", "item", "candy", "Player", "2"
        });
        assertNull(cultivation.arguments);
        assertNull(incubation.arguments);

        command.execute(source(senderWithPermissions("omnipet.admin.cultivation")), new String[] {
                "admin", "item", "candy", "Player", "2"
        });
        assertNull(cultivation.arguments);
        assertNull(incubation.arguments);

        command.execute(source(senderWithPermissions(
                "omnipet.admin.item", "omnipet.admin.cultivation")), new String[] {
                "admin", "item", "candy", "Player", "2"
        });
        assertEquals(List.of("candy", "Player", "2"), cultivation.arguments);
        assertNull(incubation.arguments);
    }

    @Test
    void unsupportedCultivationItemFallsBackToIncubationItemController() {
        RecordingItemTarget incubation = new RecordingItemTarget();
        RecordingCultivationTarget cultivation = new RecordingCultivationTarget();
        OmniPetCommand command = command(incubation, cultivation, null);

        command.execute(source(senderWithPermissions("omnipet.admin.item")), new String[] {
                "admin", "item", "reducer", "Player", "30", "2"
        });

        assertEquals(List.of("reducer", "Player", "30", "2"), incubation.arguments);
        assertNull(cultivation.arguments);
    }

    @Test
    void releaseAdministrationRequiresPermissionAndForwardsArgumentTail() {
        RecordingReleaseAdmin release = new RecordingReleaseAdmin();
        OmniPetCommand command = command(null, null, release);
        UUID playerId = UUID.randomUUID();

        command.execute(source(senderWithPermissions()), new String[] {
                "admin", "release", "pending", playerId.toString()
        });
        assertNull(release.arguments);

        command.execute(source(senderWithPermissions("omnipet.admin.release")), new String[] {
                "admin", "release", "pending", playerId.toString()
        });
        assertEquals(List.of("pending", playerId.toString()), release.arguments);
    }

    @Test
    void cultivationRecoveryRequiresPermissionAndForwardsArgumentTail() {
        RecordingCultivationAdmin cultivation = new RecordingCultivationAdmin();
        OmniPetCommand command = command(null, null, null, cultivation);
        UUID playerId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();

        command.execute(source(senderWithPermissions()), new String[] {
                "admin", "cultivation", "recover", playerId.toString(), actionId.toString()
        });
        assertNull(cultivation.arguments);

        command.execute(source(senderWithPermissions("omnipet.admin.cultivation")), new String[] {
                "admin", "cultivation", "recover", playerId.toString(), actionId.toString()
        });
        assertEquals(List.of("recover", playerId.toString(), actionId.toString()), cultivation.arguments);
    }

    @Test
    void executeDispatchesOfflineHatchAdministrationFromConsole() {
        RecordingHatchAdmin hatchAdmin = new RecordingHatchAdmin();
        OmniPetCommand command = command(hatchAdmin);
        CommandSourceStack console = source(senderWithPermissions(
                HatchAdminCommandParser.INSPECT_PERMISSION,
                HatchAdminCommandParser.MANAGE_PERMISSION));
        UUID playerId = UUID.randomUUID();
        UUID incubationId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();

        command.execute(console, new String[] {"admin", "hatch", "inspect", playerId.toString()});
        assertEquals("inspect", hatchAdmin.operation);
        assertEquals(playerId, hatchAdmin.playerId);

        command.execute(console, new String[] {
                "admin", "hatch", "reduce", playerId.toString(), incubationId.toString(), "500", actionId.toString()
        });
        assertEquals("reduce", hatchAdmin.operation);
        assertEquals(500, hatchAdmin.millis);

        command.execute(console, new String[] {
                "admin", "hatch", "set", playerId.toString(), incubationId.toString(), "0", actionId.toString()
        });
        assertEquals("set", hatchAdmin.operation);
        assertEquals(0, hatchAdmin.millis);

        command.execute(console, new String[] {
                "admin", "hatch", "complete", playerId.toString(), incubationId.toString(), actionId.toString()
        });
        assertEquals("complete", hatchAdmin.operation);

        command.execute(console, new String[] {
                "admin", "hatch", "cancel", playerId.toString(), incubationId.toString(), actionId.toString()
        });
        assertEquals("cancel", hatchAdmin.operation);
        assertEquals(incubationId, hatchAdmin.incubationId);
        assertEquals(actionId, hatchAdmin.actionId);
    }

    @Test
    void executeChecksInspectAndMutationPermissionsSeparately() {
        UUID playerId = UUID.randomUUID();
        UUID incubationId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        RecordingHatchAdmin inspectTarget = new RecordingHatchAdmin();
        OmniPetCommand inspectCommand = command(inspectTarget);
        CommandSourceStack inspectOnly = source(senderWithPermissions(HatchAdminCommandParser.INSPECT_PERMISSION));

        inspectCommand.execute(inspectOnly, new String[] {"admin", "hatch", "inspect", playerId.toString()});
        assertEquals("inspect", inspectTarget.operation);
        inspectTarget.operation = null;
        inspectCommand.execute(inspectOnly, new String[] {
                "admin", "hatch", "complete", playerId.toString(), incubationId.toString(), actionId.toString()
        });
        assertNull(inspectTarget.operation);

        RecordingHatchAdmin manageTarget = new RecordingHatchAdmin();
        OmniPetCommand manageCommand = command(manageTarget);
        CommandSourceStack manageOnly = source(senderWithPermissions(HatchAdminCommandParser.MANAGE_PERMISSION));
        manageCommand.execute(manageOnly, new String[] {"admin", "hatch", "inspect", playerId.toString()});
        assertNull(manageTarget.operation);
        manageCommand.execute(manageOnly, new String[] {
                "admin", "hatch", "cancel", playerId.toString(), incubationId.toString(), actionId.toString()
        });
        assertEquals("cancel", manageTarget.operation);
    }

    @Test
    void executeDoesNotDispatchInvalidOfflineHatchArguments() {
        RecordingHatchAdmin hatchAdmin = new RecordingHatchAdmin();
        OmniPetCommand command = command(hatchAdmin);

        command.execute(source(permittedSender()), new String[] {
                "admin", "hatch", "reduce", UUID.randomUUID().toString(), "bad", "-1", "bad"
        });

        assertNull(hatchAdmin.operation);
    }

    @Test
    void outerCommandGateAcceptsEitherOfflineHatchPermission() {
        OmniPetCommand command = command(new RecordingHatchAdmin());

        assertTrue(command.canUse(senderWithPermissions(HatchAdminCommandParser.INSPECT_PERMISSION)));
        assertTrue(command.canUse(senderWithPermissions(HatchAdminCommandParser.MANAGE_PERMISSION)));
        assertTrue(command.canUse(senderWithPermissions(AdminPetCommandParser.GENERAL_PERMISSION)));
        assertTrue(command.canUse(senderWithPermissions("omnipet.admin.cultivation")));
        assertTrue(command.canUse(senderWithPermissions("omnipet.admin.release")));
        assertFalse(command.canUse(senderWithPermissions()));
        assertNull(command.permission());
    }

    private static OmniPetCommand command(SlotTransactionAdminTarget transactions) {
        return new OmniPetCommand(null, null, transactions, null, () -> false);
    }

    private static OmniPetCommand command(
            PlayerHatchCommandTarget hatches,
            SlotTransactionAdminTarget transactions) {
        return new OmniPetCommand(null, null, hatches, transactions, null, () -> false);
    }

    private static OmniPetCommand command(HatchAdminCommandTarget hatchAdmin) {
        return new OmniPetCommand(null, null, null, hatchAdmin, new RecordingTransactions(), null, () -> false);
    }

    private static OmniPetCommand command(
            ItemCommandTarget incubation,
            CultivationItemCommandTarget cultivation,
            ReleaseAdminCommandTarget release) {
        return command(incubation, cultivation, release, null);
    }

    private static OmniPetCommand command(
            ItemCommandTarget incubation,
            CultivationItemCommandTarget cultivation,
            ReleaseAdminCommandTarget release,
            CultivationAdminCommandTarget cultivationAdmin) {
        return new OmniPetCommand(
                null, null, null, null, incubation, cultivation, null, null, release, cultivationAdmin,
                new RecordingTransactions(), null, () -> false);
    }

    private static CommandSourceStack source(CommandSender sender) {
        return (CommandSourceStack) Proxy.newProxyInstance(
                OmniPetCommandDispatchTest.class.getClassLoader(),
                new Class<?>[] {CommandSourceStack.class},
                (proxy, method, arguments) -> method.getName().equals("getSender") ? sender : defaultValue(method.getReturnType()));
    }

    private static CommandSender permittedSender() {
        return senderWithPermissions("*");
    }

    private static CommandSender senderWithPermissions(String... permissions) {
        Set<String> granted = new HashSet<>(Set.of(permissions));
        return (CommandSender) Proxy.newProxyInstance(
                OmniPetCommandDispatchTest.class.getClassLoader(),
                new Class<?>[] {CommandSender.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission", "isPermissionSet" -> granted.contains("*")
                            || granted.contains((String) arguments[0]);
                    case "getName" -> "Console";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Player playerSender() {
        UUID playerId = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(
                OmniPetCommandDispatchTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission", "isPermissionSet", "isOnline" -> true;
                    case "getName" -> "Player";
                    case "getUniqueId" -> playerId;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class) return null;
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        throw new IllegalArgumentException("unsupported primitive: " + type);
    }

    private static final class RecordingTransactions implements SlotTransactionAdminTarget {
        private int limit;
        private String cursor;
        private UUID transactionId;
        private SlotReconciliationDecision decision;

        @Override
        public void list(CommandSender sender, int requestedLimit, String requestedCursor) {
            limit = requestedLimit;
            cursor = requestedCursor;
        }

        @Override
        public void reconcile(
                CommandSender sender,
                UUID requestedTransactionId,
                SlotReconciliationDecision requestedDecision) {
            transactionId = requestedTransactionId;
            decision = requestedDecision;
        }
    }

    private static final class RecordingHatches implements PlayerHatchCommandTarget {
        private String action;

        @Override
        public void command(Player player, String requestedAction) {
            action = requestedAction;
        }
    }

    private static final class RecordingSkills implements PlayerSkillCommandTarget, SkillAdminCommandTarget {
        private UUID petId;
        private String bindingId;
        private List<String> adminArguments;

        @Override
        public void cast(Player player, UUID requestedPetId, String requestedBindingId) {
            petId = requestedPetId;
            bindingId = requestedBindingId;
        }

        @Override
        public void command(CommandSender sender, List<String> arguments) {
            adminArguments = List.copyOf(arguments);
        }
    }

    private static final class RecordingItemTarget implements ItemCommandTarget {
        private List<String> arguments;

        @Override
        public void command(CommandSender sender, List<String> requestedArguments) {
            arguments = List.copyOf(requestedArguments);
        }
    }

    private static final class RecordingCultivationTarget implements CultivationItemCommandTarget {
        private List<String> arguments;

        @Override
        public boolean supports(List<String> requestedArguments) {
            return !requestedArguments.isEmpty()
                    && (requestedArguments.getFirst().equalsIgnoreCase("candy")
                    || requestedArguments.getFirst().equalsIgnoreCase("breakthrough"));
        }

        @Override
        public void command(CommandSender sender, List<String> requestedArguments) {
            arguments = List.copyOf(requestedArguments);
        }
    }

    private static final class RecordingReleaseAdmin implements ReleaseAdminCommandTarget {
        private List<String> arguments;

        @Override
        public void command(CommandSender sender, List<String> requestedArguments) {
            arguments = List.copyOf(requestedArguments);
        }
    }

    private static final class RecordingCultivationAdmin implements CultivationAdminCommandTarget {
        private List<String> arguments;

        @Override
        public void command(CommandSender sender, List<String> requestedArguments) {
            arguments = List.copyOf(requestedArguments);
        }
    }

    private static final class RecordingHatchAdmin implements HatchAdminCommandTarget {
        private String operation;
        private UUID playerId;
        private UUID incubationId;
        private long millis;
        private UUID actionId;

        @Override
        public void inspect(CommandSender sender, UUID requestedPlayerId) {
            record("inspect", requestedPlayerId, null, 0, null);
        }

        @Override
        public void reduce(
                CommandSender sender,
                UUID requestedPlayerId,
                UUID requestedIncubationId,
                long requestedMillis,
                UUID requestedActionId) {
            record("reduce", requestedPlayerId, requestedIncubationId, requestedMillis, requestedActionId);
        }

        @Override
        public void setRemaining(
                CommandSender sender,
                UUID requestedPlayerId,
                UUID requestedIncubationId,
                long requestedMillis,
                UUID requestedActionId) {
            record("set", requestedPlayerId, requestedIncubationId, requestedMillis, requestedActionId);
        }

        @Override
        public void complete(
                CommandSender sender,
                UUID requestedPlayerId,
                UUID requestedIncubationId,
                UUID requestedActionId) {
            record("complete", requestedPlayerId, requestedIncubationId, 0, requestedActionId);
        }

        @Override
        public void cancel(
                CommandSender sender,
                UUID requestedPlayerId,
                UUID requestedIncubationId,
                UUID requestedActionId) {
            record("cancel", requestedPlayerId, requestedIncubationId, 0, requestedActionId);
        }

        private void record(
                String requestedOperation,
                UUID requestedPlayerId,
                UUID requestedIncubationId,
                long requestedMillis,
                UUID requestedActionId) {
            operation = requestedOperation;
            playerId = requestedPlayerId;
            incubationId = requestedIncubationId;
            millis = requestedMillis;
            actionId = requestedActionId;
        }
    }
}
