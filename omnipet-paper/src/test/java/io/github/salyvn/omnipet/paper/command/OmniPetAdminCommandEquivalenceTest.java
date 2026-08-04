package io.github.salyvn.omnipet.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.economy.SlotReconciliationDecision;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/**
 * {@code /petadmin ...} and {@code /pet admin ...} must stay the same command.
 *
 * <p>Two spellings of one feature is exactly the situation that drifts: a permission tightened on one path
 * and not the other, or an area reachable from one and missing from the other. Both route through the same
 * {@link AdminCommandRouter}, and these tests hold that by driving the same inputs through both entry
 * points and comparing what the targets actually received.
 */
class OmniPetAdminCommandEquivalenceTest {
    @Test
    void bothSpellingsForwardTheSameTransactionPage() {
        RecordingTransactions viaPet = new RecordingTransactions();
        RecordingTransactions viaPetAdmin = new RecordingTransactions();

        command(viaPet).execute(source(permitted()), new String[] {"admin", "transactions", "7", "v1.a.start"});
        adminCommand(viaPetAdmin).execute(source(permitted()), new String[] {"transactions", "7", "v1.a.start"});

        assertEquals(viaPet.limit, viaPetAdmin.limit);
        assertEquals(viaPet.cursor, viaPetAdmin.cursor);
        assertEquals(7, viaPetAdmin.limit);
        assertEquals("v1.a.start", viaPetAdmin.cursor);
    }

    @Test
    void bothSpellingsForwardTheSameReconcileDecision() {
        UUID transactionId = UUID.randomUUID();
        RecordingTransactions viaPet = new RecordingTransactions();
        RecordingTransactions viaPetAdmin = new RecordingTransactions();

        command(viaPet).execute(source(permitted()),
                new String[] {"admin", "reconcile", transactionId.toString(), "refund"});
        adminCommand(viaPetAdmin).execute(source(permitted()),
                new String[] {"reconcile", transactionId.toString(), "refund"});

        assertEquals(transactionId, viaPetAdmin.transactionId);
        assertEquals(viaPet.decision, viaPetAdmin.decision);
        assertEquals(SlotReconciliationDecision.REFUND_CONFIRMED, viaPetAdmin.decision);
    }

    @Test
    void bothSpellingsRefuseTheSameMissingPermission() {
        // A permission tightened on one path and not the other is the drift this pins.
        RecordingTransactions viaPet = new RecordingTransactions();
        RecordingTransactions viaPetAdmin = new RecordingTransactions();
        CommandSender petSender = senderWithPermissions("omnipet.general");
        CommandSender adminSender = senderWithPermissions("omnipet.general");

        command(viaPet).execute(source(petSender), new String[] {"admin", "transactions"});
        adminCommand(viaPetAdmin).execute(source(adminSender), new String[] {"transactions"});

        assertNull(viaPet.cursor, "the unpermitted sender must not reach the target");
        assertNull(viaPetAdmin.cursor);
        assertEquals(messages(petSender), messages(adminSender),
                "both spellings must refuse with the same wording");
    }

    @Test
    void anUnknownAdminAreaIsReportedRatherThanSilentlyIgnored() {
        CommandSender sender = permitted();

        adminCommand(new RecordingTransactions()).execute(source(sender), new String[] {"nonsense"});

        assertTrue(messages(sender).stream().anyMatch(line -> line.contains("unknown admin command")),
                messages(sender).toString());
    }

    @Test
    void petAdminIsVisibleToAnAdministratorAndHiddenFromAPlainPlayer() {
        OmniPetAdminCommand command = adminCommand(new RecordingTransactions());

        assertTrue(command.canUse(senderWithPermissions("omnipet.admin.reload")));
        assertTrue(command.canUse(senderWithPermissions("omnipet.admin.release")));
        org.junit.jupiter.api.Assertions.assertFalse(
                command.canUse(senderWithPermissions("omnipet.general")),
                "a plain player has no administration to reach");
    }

    private static OmniPetCommand command(SlotTransactionAdminTarget transactions) {
        return new OmniPetCommand(null, null, transactions, null, () -> false);
    }

    private static OmniPetAdminCommand adminCommand(SlotTransactionAdminTarget transactions) {
        return new OmniPetAdminCommand(command(transactions));
    }

    private static CommandSourceStack source(CommandSender sender) {
        return (CommandSourceStack) Proxy.newProxyInstance(
                OmniPetAdminCommandEquivalenceTest.class.getClassLoader(),
                new Class<?>[] {CommandSourceStack.class},
                (proxy, method, arguments) -> method.getName().equals("getSender")
                        ? sender
                        : defaultValue(method.getReturnType()));
    }

    private static final java.util.Map<CommandSender, List<String>> SENT = new java.util.HashMap<>();

    private static List<String> messages(CommandSender sender) {
        return SENT.getOrDefault(sender, List.of());
    }

    private static CommandSender permitted() {
        return senderWithPermissions("*");
    }

    private static CommandSender senderWithPermissions(String... permissions) {
        Set<String> granted = new HashSet<>(Set.of(permissions));
        List<String> sent = new ArrayList<>();
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(
                OmniPetAdminCommandEquivalenceTest.class.getClassLoader(),
                new Class<?>[] {CommandSender.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission", "isPermissionSet" -> granted.contains("*")
                            || granted.contains((String) arguments[0]);
                    case "sendMessage" -> {
                        if (arguments[0] instanceof String text) sent.add(text);
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                });
        SENT.put(sender, sent);
        return sender;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == void.class) return null;
        return 0;
    }

    /** Records what the transaction target was asked to do, so both paths can be compared. */
    private static final class RecordingTransactions implements SlotTransactionAdminTarget {
        private int limit;
        private String cursor;
        private UUID transactionId;
        private SlotReconciliationDecision decision;

        @Override
        public void list(CommandSender sender, int limit, String cursor) {
            this.limit = limit;
            this.cursor = cursor;
        }

        @Override
        public void reconcile(CommandSender sender, UUID transactionId, SlotReconciliationDecision decision) {
            this.transactionId = transactionId;
            this.decision = decision;
        }
    }
}
