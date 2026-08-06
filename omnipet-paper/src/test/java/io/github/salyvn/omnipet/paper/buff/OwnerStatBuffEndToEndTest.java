package io.github.salyvn.omnipet.paper.buff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.buff.PetStatBuff;
import io.github.salyvn.omnipet.core.buff.PetStatBuffProjection;
import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;

/**
 * The whole stat path, from what the Studio writes into a pet to what MythicLib ends up holding.
 *
 * <p>This is the test that was missing, and its absence is why "pet stats do nothing" survived three
 * rounds of fixes. Every existing test checked one link: the projection read the right node, the adapter
 * bound the right methods, the coordinator was called on activation. Each passed. The chain still did
 * nothing, because the ID handed over at the end was not the ID MythicLib answers to, and nothing compared
 * the two ends.
 *
 * <p>So these assertions are deliberately about the <em>outcome</em>: after activation, is there a modifier
 * on the stat instance a player's damage is actually read from? A test that asked "was reconcile called"
 * would have passed throughout the entire bug.
 */
class OwnerStatBuffEndToEndTest {
    private static final String VENDOR_ID = "ATTACK_DAMAGE";
    /** What the Studio's stat picker actually writes: its own namespaced, lower-cased form. */
    private static final String STUDIO_ID = "mythiclib:attack_damage";

    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void loadPlayerData() {
        MMOPlayerData.reset();
        MMOPlayerData.load(owner);
    }

    /**
     * The headline case: a pet whose stat came from the Studio picker moves the player's real stat.
     *
     * <p>The modifier has to land on the instance keyed by MythicLib's own name. Landing on one keyed by
     * {@code mythiclib:attack_damage} is exactly the failure that looked like success.
     */
    @Test
    void aStudioAuthoredStatReachesTheStatMythicLibActuallyReads() {
        PetInstance pet = pet(Map.of(
                "id", STUDIO_ID,
                "vendorStatId", VENDOR_ID,
                "modifierType", "FLAT",
                "value", 7.0));
        ReflectiveMythicLibBuffPort port = port();

        MythicLibBuffResult result = port.reconcile(
                owner, PetStatBuffProjection.readActive(List.of(pet), List.of(pet.id())));

        assertEquals(MythicLibBuffResult.Status.APPLIED, result.status(), result.detail());
        assertEquals(1, result.applied());
        assertEquals(0, result.skipped());

        StatInstance real = statMap().getInstance(VENDOR_ID);
        assertEquals(1, real.installed().size(),
                "the modifier has to be on the stat MythicLib reads, not on a namespaced orphan");
        assertEquals(7.0, real.installed().iterator().next().getValue());
    }

    /** And nothing is left on the namespaced name — the orphan that made the bug invisible. */
    @Test
    void nothingIsInstalledOnTheNamespacedOrphan() {
        PetInstance pet = pet(Map.of(
                "id", STUDIO_ID, "vendorStatId", VENDOR_ID, "modifierType", "FLAT", "value", 7.0));

        port().reconcile(owner, PetStatBuffProjection.readActive(List.of(pet), List.of(pet.id())));

        assertFalse(statMap().touched().contains(STUDIO_ID),
                "asking MythicLib for the namespaced ID at all is the bug: getInstance mints an orphan");
    }

    /** An operator who typed the namespaced form by hand, with no picker metadata, gets the same result. */
    @Test
    void aHandTypedNamespacedIdAlsoReachesTheRealStat() {
        PetInstance pet = pet(Map.of(
                "id", STUDIO_ID, "modifierType", "FLAT", "value", 4.0));

        MythicLibBuffResult result = port().reconcile(
                owner, PetStatBuffProjection.readActive(List.of(pet), List.of(pet.id())));

        assertEquals(1, result.applied());
        assertEquals(4.0, statMap().getInstance(VENDOR_ID).installed().iterator().next().getValue());
    }

    /** A stat authored directly against MythicLib's vocabulary must keep working untouched. */
    @Test
    void aBareVendorIdStillWorks() {
        PetInstance pet = pet(Map.of("id", VENDOR_ID, "modifierType", "FLAT", "value", 3.0));

        assertEquals(1, port().reconcile(
                owner, PetStatBuffProjection.readActive(List.of(pet), List.of(pet.id()))).applied());
        assertEquals(3.0, statMap().getInstance(VENDOR_ID).installed().iterator().next().getValue());
    }

    /** Only active pets grant stats: a stored pet's numbers must not reach its owner. */
    @Test
    void aStoredPetGrantsNothing() {
        PetInstance active = pet(Map.of(
                "id", STUDIO_ID, "vendorStatId", VENDOR_ID, "modifierType", "FLAT", "value", 7.0));
        PetInstance stored = pet(Map.of(
                "id", STUDIO_ID, "vendorStatId", VENDOR_ID, "modifierType", "FLAT", "value", 99.0));

        List<PetStatBuff> buffs = PetStatBuffProjection.readActive(
                List.of(active, stored), List.of(active.id()));

        assertEquals(1, buffs.size());
        port().reconcile(owner, buffs);
        assertEquals(7.0, statMap().getInstance(VENDOR_ID).installed().iterator().next().getValue(),
                "the stored pet's 99 must not be what landed");
    }

    /** Recalling the pet takes its stats back off the player. */
    @Test
    void recallingThePetRemovesItsStatFromThePlayer() {
        PetInstance pet = pet(Map.of(
                "id", STUDIO_ID, "vendorStatId", VENDOR_ID, "modifierType", "FLAT", "value", 7.0));
        ReflectiveMythicLibBuffPort port = port();
        port.reconcile(owner, PetStatBuffProjection.readActive(List.of(pet), List.of(pet.id())));

        // Same pet still owned, no longer active — which is what deactivation publishes.
        port.reconcile(owner, PetStatBuffProjection.readActive(List.of(pet), List.of()));

        assertTrue(statMap().getInstance(VENDOR_ID).installed().isEmpty(),
                "a recalled pet must not keep buffing its owner");
    }

    /** Two active pets both count, rather than one silently replacing the other. */
    @Test
    void twoActivePetsBothGrantTheirStat() {
        PetInstance first = pet(Map.of(
                "id", STUDIO_ID, "vendorStatId", VENDOR_ID, "modifierType", "FLAT", "value", 5.0));
        PetInstance second = pet(Map.of(
                "id", STUDIO_ID, "vendorStatId", VENDOR_ID, "modifierType", "FLAT", "value", 2.0));

        MythicLibBuffResult result = port().reconcile(owner, PetStatBuffProjection.readActive(
                List.of(first, second), List.of(first.id(), second.id())));

        assertEquals(2, result.applied());
        assertEquals(2, statMap().getInstance(VENDOR_ID).installed().size(),
                "each pet's modifier is keyed by its own instance ID, so both must survive");
    }

    /** A misspelled stat ID is refused and counted, which is what makes the log line appear. */
    @Test
    void aMisspelledStatIsReportedRatherThanSilentlyOrphaned() {
        PetInstance pet = pet(Map.of("id", "ATTACK_DAMGE", "modifierType", "FLAT", "value", 7.0));

        MythicLibBuffResult result = port().reconcile(
                owner, PetStatBuffProjection.readActive(List.of(pet), List.of(pet.id())));

        assertEquals(0, result.applied());
        assertEquals(1, result.skipped(), "an unknown ID has to be visible, not installed onto an orphan");
    }

    private io.lumine.mythic.lib.api.stat.StatMap statMap() {
        return MMOPlayerData.getOrNull(owner).getStatMap();
    }

    private ReflectiveMythicLibBuffPort port() {
        return new ReflectiveMythicLibBuffPort(
                getClass().getClassLoader(), () -> true, ignored -> true);
    }

    private static PetInstance pet(Map<String, Object> stat) {
        return new PetInstance(
                UUID.randomUUID(), "wolf", 1,
                Map.of("stats", List.of(new java.util.LinkedHashMap<>(stat))),
                Map.of());
    }
}
