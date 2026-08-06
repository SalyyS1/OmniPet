package io.github.salyvn.omnipet.paper.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.skill.SkillCastRequest;
import io.github.salyvn.omnipet.core.skill.SkillTargetPolicy;
import io.lumine.mythic.bukkit.MythicBukkit;

class ReflectiveMythicMobsSkillProviderTest {
    @Test
    void probesCatalogAndCastsWithoutVendorLinkageInProductionClass() {
        MythicBukkit.reset();
        Object caster = new Object();
        ReflectiveMythicMobsSkillProvider provider = provider(true, true, caster);

        var catalog = provider.refresh(4);
        var result = provider.cast(request("ember_burst"));

        assertTrue(catalog.health().available());
        assertTrue(catalog.contains("ember_burst"));
        assertTrue(result.succeeded());
        assertEquals(caster, MythicBukkit.inst().getAPIHelper().caster);
    }

    @Test
    void absentProviderAndOffThreadCastFailClosed() {
        ReflectiveMythicMobsSkillProvider absent = provider(false, true, new Object());
        assertFalse(absent.refresh(1).health().available());

        ReflectiveMythicMobsSkillProvider offThread = provider(true, false, new Object());
        offThread.refresh(2);
        assertFalse(offThread.cast(request("ember_burst")).succeeded());
    }

    @Test
    void providerFailureQuarantinesCurrentEpochUntilRefresh() {
        MythicBukkit.reset();
        MythicBukkit.inst().getAPIHelper().fail = true;
        ReflectiveMythicMobsSkillProvider provider = provider(true, true, new Object());
        provider.refresh(7);

        assertFalse(provider.cast(request("ember_burst")).succeeded());
        assertFalse(provider.catalog().health().available());

        MythicBukkit.inst().getAPIHelper().fail = false;
        assertTrue(provider.refresh(8).health().available());
    }

    /**
     * A cast with resolved targets must reach the overload that accepts them.
     *
     * <p>This is the whole of the "skills do not work with MythicMobs" report. The two-argument overload
     * lets MythicMobs pick a target from the caster, and the caster is the owning <em>player</em> — so a
     * pet's attack skill aimed at whatever the player's own targeting rules found, which is usually nothing.
     * The cast succeeded and hit no one, which is indistinguishable from a skill that never fired.
     */
    @Test
    void aTargetedRequestReachesTheOverloadThatCarriesTargetsAndPower() {
        MythicBukkit.reset();
        Object caster = new Object();
        UUID target = UUID.randomUUID();
        Object targetEntity = new Object();
        ReflectiveMythicMobsSkillProvider provider = new ReflectiveMythicMobsSkillProvider(
                getClass().getClassLoader(), () -> true, () -> true,
                ignored -> caster,
                id -> id.equals(target) ? targetEntity : null);
        provider.refresh(1);

        UUID owner = UUID.randomUUID();
        var result = provider.cast(new SkillCastRequest(
                UUID.randomUUID(), owner, owner, UUID.randomUUID(), "ember_burst",
                SkillTargetPolicy.NEAREST_HOSTILE, List.of(target), 2.5, Map.of()));

        assertTrue(result.succeeded());
        assertTrue(MythicBukkit.inst().getAPIHelper().usedTargetedOverload,
                "a resolved target must not be dropped in favour of the provider's own targeter");
        assertEquals(List.of(targetEntity), MythicBukkit.inst().getAPIHelper().entityTargets);
        assertEquals(2.5f, MythicBukkit.inst().getAPIHelper().power);
    }

    /**
     * A target that left the world between resolution and the cast leaves no targets, and the cast falls
     * back to the provider's own targeter rather than passing a hole.
     */
    @Test
    void aTargetThatHasLeftFallsBackToTheUntargetedOverload() {
        MythicBukkit.reset();
        ReflectiveMythicMobsSkillProvider provider = new ReflectiveMythicMobsSkillProvider(
                getClass().getClassLoader(), () -> true, () -> true,
                ignored -> new Object(),
                id -> null);
        provider.refresh(1);

        UUID owner = UUID.randomUUID();
        var result = provider.cast(new SkillCastRequest(
                UUID.randomUUID(), owner, owner, UUID.randomUUID(), "ember_burst",
                SkillTargetPolicy.NEAREST_HOSTILE, List.of(UUID.randomUUID()), 1, Map.of()));

        assertTrue(result.succeeded());
        assertFalse(MythicBukkit.inst().getAPIHelper().usedTargetedOverload);
    }

    private static ReflectiveMythicMobsSkillProvider provider(
            boolean enabled, boolean mainThread, Object caster) {
        return new ReflectiveMythicMobsSkillProvider(
                ReflectiveMythicMobsSkillProviderTest.class.getClassLoader(),
                () -> enabled,
                () -> mainThread,
                ignored -> caster);
    }

    private static SkillCastRequest request(String skill) {
        UUID owner = UUID.randomUUID();
        return new SkillCastRequest(
                UUID.randomUUID(), owner, owner, UUID.randomUUID(), skill,
                SkillTargetPolicy.OWNER, Map.of());
    }
}
