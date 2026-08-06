package io.github.salyvn.omnipet.paper.buff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.buff.PetStatBuff;
import io.lumine.mythic.lib.api.player.MMOPlayerData;

/**
 * The MythicLib adapter, against stubs shaped to the real ABI of build 106.
 *
 * <p>Nothing tested this before, and two defects lived in the gap. Both are the same kind: a failure that
 * looks like success from inside the adapter.
 *
 * <p>The first is the quarantine. {@code MMOPlayerData.get} throws for a player MythicLib has not set up
 * yet — its join handler and ours race, and ours can win. The adapter treats any throw as an ABI mismatch
 * and disables itself permanently for <em>every</em> owner, so one player reconciled a moment too early
 * after a restart would silently switch pet stats off server-wide until the next reload.
 *
 * <p>The second is the unknown stat. {@code StatMap.getInstance} is a {@code computeIfAbsent}, so it answers
 * any name with a live instance; a modifier for a stat MythicLib never registered installs perfectly onto an
 * object nothing reads. The adapter counted that as applied and had no way to warn.
 */
class ReflectiveMythicLibBuffPortTest {
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void resetPlayerData() {
        MMOPlayerData.reset();
    }

    @Test
    void aKnownStatIsApplied() {
        MMOPlayerData.load(owner);
        ReflectiveMythicLibBuffPort port = port();

        MythicLibBuffResult result = port.reconcile(owner, List.of(buff("ATTACK_DAMAGE", 5)));

        assertEquals(MythicLibBuffResult.Status.APPLIED, result.status());
        assertEquals(1, result.applied());
        assertEquals(0, result.skipped());
    }

    /**
     * A stat MythicLib never registered is refused, not installed onto an orphan.
     *
     * <p>Refusing is what lets the coordinator warn. Installing it was invisible by construction.
     */
    @Test
    void anUnregisteredStatIsSkippedRatherThanInstalledOntoAnOrphan() {
        MMOPlayerData.load(owner);
        ReflectiveMythicLibBuffPort port = port();

        MythicLibBuffResult result = port.reconcile(
                owner, List.of(buff("mythiclib:attack_damage", 5)));

        assertEquals(MythicLibBuffResult.Status.APPLIED, result.status());
        assertEquals(0, result.applied(), "a stat MythicLib does not know must not count as applied");
        assertEquals(1, result.skipped(), "and it has to be visible as skipped");
    }

    /**
     * A player MythicLib has not loaded yet is reported, and the adapter stays usable.
     *
     * <p>This is the one that mattered: a permanent quarantine here took every other player's stats with it.
     */
    @Test
    void anUnloadedPlayerDoesNotQuarantineTheAdapterForEveryoneElse() {
        ReflectiveMythicLibBuffPort port = port();

        MythicLibBuffResult early = port.reconcile(owner, List.of(buff("ATTACK_DAMAGE", 5)));

        assertEquals(MythicLibBuffResult.Status.UNAVAILABLE, early.status());
        assertFalse(early.detail().isBlank());

        // MythicLib finishes loading them, and the next reconcile works — for this owner and any other.
        MMOPlayerData.load(owner);
        UUID second = UUID.randomUUID();
        MMOPlayerData.load(second);

        assertEquals(MythicLibBuffResult.Status.APPLIED,
                port.reconcile(owner, List.of(buff("ATTACK_DAMAGE", 5))).status());
        assertEquals(MythicLibBuffResult.Status.APPLIED,
                port.reconcile(second, List.of(buff("ATTACK_DAMAGE", 3))).status());
    }

    @Test
    void reconcilingAgainReplacesTheModifierRatherThanStackingIt() {
        MMOPlayerData.load(owner);
        ReflectiveMythicLibBuffPort port = port();

        port.reconcile(owner, List.of(buff("ATTACK_DAMAGE", 5)));
        MythicLibBuffResult second = port.reconcile(owner, List.of(buff("ATTACK_DAMAGE", 9)));

        assertEquals(1, second.applied());
        assertEquals(1, second.removed(), "the previous modifier has to come off first");
    }

    @Test
    void clearingRemovesWhatWasInstalled() {
        MMOPlayerData.load(owner);
        ReflectiveMythicLibBuffPort port = port();
        port.reconcile(owner, List.of(buff("ATTACK_DAMAGE", 5)));

        MythicLibBuffResult cleared = port.clear(owner);

        assertEquals(MythicLibBuffResult.Status.APPLIED, cleared.status());
        assertEquals(0, cleared.applied());
        assertEquals(1, cleared.removed());
    }

    @Test
    void anOfflineOwnerIsReportedRatherThanApplied() {
        MMOPlayerData.load(owner);
        ReflectiveMythicLibBuffPort port = new ReflectiveMythicLibBuffPort(
                getClass().getClassLoader(), () -> true, ignored -> false);

        assertEquals(MythicLibBuffResult.Status.UNAVAILABLE,
                port.reconcile(owner, List.of(buff("ATTACK_DAMAGE", 5))).status());
    }

    @Test
    void mutatingOffTheMainThreadIsRefused() {
        MMOPlayerData.load(owner);
        ReflectiveMythicLibBuffPort port = new ReflectiveMythicLibBuffPort(
                getClass().getClassLoader(), () -> false, ignored -> true);

        assertEquals(MythicLibBuffResult.Status.OFF_THREAD,
                port.reconcile(owner, List.of(buff("ATTACK_DAMAGE", 5))).status());
    }

    /** The adapter binds against the real class names, so a rename upstream surfaces here. */
    @Test
    void theAdapterBindsAgainstTheApiItClaimsToSupport() {
        MMOPlayerData.load(owner);

        MythicLibBuffResult result = port().reconcile(owner, List.of(buff("ATTACK_DAMAGE", 1)));

        assertTrue(result.status() == MythicLibBuffResult.Status.APPLIED,
                "binding failed: " + result.detail());
    }

    private ReflectiveMythicLibBuffPort port() {
        return new ReflectiveMythicLibBuffPort(
                getClass().getClassLoader(), () -> true, ignored -> true);
    }

    private PetStatBuff buff(String statId, double value) {
        return new PetStatBuff(UUID.randomUUID(), statId, "FLAT", value);
    }
}
