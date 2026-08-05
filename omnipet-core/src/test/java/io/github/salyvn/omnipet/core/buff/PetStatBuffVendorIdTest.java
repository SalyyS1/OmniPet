package io.github.salyvn.omnipet.core.buff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;

/**
 * The stat ID a pet's buffs are published under, which is the whole reason pet stats did nothing.
 *
 * <p>The Studio's stat picker writes the logical ID it minted — {@code mythiclib:attack_damage} — and keeps
 * the provider's own name in {@code vendorStatId}. The adapter then asked MythicLib for a stat called
 * {@code mythiclib:attack_damage}, which MythicLib has never registered. It answered with a fresh orphan
 * instance rather than null, so the modifier installed successfully onto a stat nothing reads: the vault
 * listed the stat, the adapter reported it applied, and the player's numbers never moved. No warning could
 * fire, because from the adapter's side nothing had gone wrong.
 *
 * <p>These tests are about the ID that leaves the projection. That is the only place the translation can
 * happen without the buff port having to know about the Studio.
 */
class PetStatBuffVendorIdTest {
    @Test
    void theVendorIdIsPreferredOverTheNamespacedLogicalId() {
        List<PetStatBuff> buffs = PetStatBuffProjection.read(pet(Map.of(
                "id", "mythiclib:attack_damage",
                "vendorStatId", "ATTACK_DAMAGE",
                "modifierType", "FLAT",
                "value", 5.0)));

        assertEquals(1, buffs.size());
        assertEquals("ATTACK_DAMAGE", buffs.getFirst().statId(),
                "MythicLib knows ATTACK_DAMAGE; it has never heard of mythiclib:attack_damage");
    }

    /** An operator who typed the namespaced form by hand gets the same treatment. */
    @Test
    void theNamespaceIsStrippedWhenNoVendorIdWasRecorded() {
        List<PetStatBuff> buffs = PetStatBuffProjection.read(pet(Map.of(
                "id", "mythiclib:max_health", "modifierType", "RELATIVE", "value", 12.0)));

        assertEquals("MAX_HEALTH", buffs.getFirst().statId());
    }

    /**
     * The namespace match is case-insensitive, and what remains is upper-cased.
     *
     * <p>Upper-casing matters: the catalog lower-cases the vendor's name when it mints the logical ID, so
     * stripping alone would produce {@code attack_speed} for a stat MythicLib registers as
     * {@code ATTACK_SPEED} — the same miss, one letter-case later.
     */
    @Test
    void theNamespaceMatchIsCaseInsensitiveAndTheRemainderIsUpperCased() {
        List<PetStatBuff> buffs = PetStatBuffProjection.read(pet(Map.of(
                "id", "MythicLib:Attack_Speed", "modifierType", "FLAT", "value", 1.0)));

        assertEquals("ATTACK_SPEED", buffs.getFirst().statId());
    }

    /**
     * A bare ID is passed through untouched.
     *
     * <p>That is a stat authored directly against the provider's own vocabulary, which is exactly what
     * MythicLib wants. Upper-casing or otherwise rewriting it would break the setups that already worked.
     */
    @Test
    void anIdWithNoNamespaceIsLeftExactlyAsAuthored() {
        List<PetStatBuff> buffs = PetStatBuffProjection.read(pet(Map.of(
                "id", "ATTACK_DAMAGE", "modifierType", "FLAT", "value", 3.0)));

        assertEquals("ATTACK_DAMAGE", buffs.getFirst().statId());
    }

    /** Another provider's namespace is not ours to strip. */
    @Test
    void aForeignNamespaceIsNotStripped() {
        List<PetStatBuff> buffs = PetStatBuffProjection.read(pet(Map.of(
                "id", "someplugin:power", "modifierType", "FLAT", "value", 2.0)));

        assertEquals("someplugin:power", buffs.getFirst().statId());
    }

    @Test
    void aBlankVendorIdFallsBackToStrippingTheNamespace() {
        List<PetStatBuff> buffs = PetStatBuffProjection.read(pet(Map.of(
                "id", "mythiclib:armor", "vendorStatId", "   ", "modifierType", "FLAT", "value", 4.0)));

        assertEquals("ARMOR", buffs.getFirst().statId());
    }

    /** The value, type, and pet identity are untouched by the translation. */
    @Test
    void translatingTheIdChangesNothingElseAboutTheBuff() {
        PetInstance instance = pet(Map.of(
                "id", "mythiclib:attack_damage",
                "vendorStatId", "ATTACK_DAMAGE",
                "modifierType", "ADDITIVE_MULTIPLIER",
                "value", -2.5));

        PetStatBuff buff = PetStatBuffProjection.read(instance).getFirst();

        assertEquals(instance.id(), buff.petInstanceId());
        assertEquals("ADDITIVE_MULTIPLIER", buff.modifierType());
        assertEquals(-2.5, buff.value());
    }

    /** A stat whose ID is nothing but the namespace has no vendor name left, so it is dropped. */
    @Test
    void aNamespaceWithNoIdBehindItIsDroppedRatherThanPublishedBlank() {
        assertTrue(PetStatBuffProjection.read(pet(Map.of(
                "id", "mythiclib:", "modifierType", "FLAT", "value", 1.0))).isEmpty());
    }

    private static PetInstance pet(Map<String, Object> stat) {
        return new PetInstance(
                UUID.randomUUID(), "wolf", 1,
                Map.of("stats", List.of(new java.util.LinkedHashMap<>(stat))),
                Map.of());
    }
}
