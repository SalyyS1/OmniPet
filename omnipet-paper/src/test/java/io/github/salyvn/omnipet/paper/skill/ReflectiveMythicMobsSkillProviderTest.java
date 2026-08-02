package io.github.salyvn.omnipet.paper.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
