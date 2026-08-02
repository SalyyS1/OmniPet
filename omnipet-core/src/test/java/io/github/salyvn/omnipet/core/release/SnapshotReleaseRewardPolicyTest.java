package io.github.salyvn.omnipet.core.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.domain.PetInstance;

class SnapshotReleaseRewardPolicyTest {
    private final SnapshotReleaseRewardPolicy policy = new SnapshotReleaseRewardPolicy();

    @Test
    void readsCompactMaterialAndCurrencyRewardsFromPetSnapshot() {
        PetInstance pet = pet(Map.of(
                "mode", "RECYCLE",
                "rewards", Map.of(
                        "materials", Map.of("BONE", 4, "EXPERIENCE_BOTTLE", 2),
                        "currency", Map.of("VAULT", 125.5))));

        ReleaseRewardBundle rewards = policy.calculate(pet);

        assertEquals(2, rewards.internalRewards().size());
        assertEquals(6, rewards.internalRewards().stream().mapToLong(
                ReleaseRewardBundle.InternalReward::amount).sum());
        assertEquals("vault", rewards.externalRewards().getFirst().provider());
        assertEquals(new BigDecimal("125.5"), rewards.externalRewards().getFirst().amount());
    }

    @Test
    void supportsExplicitRewardEntriesAndRejectsInvalidAmounts() {
        PetInstance pet = pet(Map.of("rewards", Map.of(
                "internal", List.of(Map.of(
                        "id", "material", "amount", 3,
                        "payload", Map.of("material", "AMETHYST_SHARD"))),
                "external", List.of(Map.of(
                        "provider", "PLAYER_POINTS", "id", "points", "amount", "7")))));

        ReleaseRewardBundle rewards = policy.calculate(pet);
        assertEquals("AMETHYST_SHARD", rewards.internalRewards().getFirst().payload().get("material"));
        assertEquals(new BigDecimal("7"), rewards.externalRewards().getFirst().amount());

        assertThrows(IllegalArgumentException.class, () -> policy.calculate(
                pet(Map.of("rewards", Map.of("materials", Map.of("BONE", 0))))));
    }

    @Test
    void legacyPetWithoutSnapshotReleasesWithoutInventedRewards() {
        ReleaseRewardBundle rewards = policy.calculate(new PetInstance(
                UUID.randomUUID(), "ember_fox", 1, Map.of(), Map.of()));

        assertEquals(List.of(), rewards.internalRewards());
        assertEquals(List.of(), rewards.externalRewards());
    }

    private static PetInstance pet(Map<String, Object> release) {
        return new PetInstance(
                UUID.randomUUID(), "ember_fox", 1, Map.of("release", release), Map.of());
    }
}
