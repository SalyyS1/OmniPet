package io.github.salyvn.omnipet.paper.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.economy.EconomyProvider;

class EconomyProviderPluginDependenciesTest {
    private final EconomyProviderPluginDependencies dependencies = new EconomyProviderPluginDependencies(Map.of(
            EconomyProvider.VAULT, Set.of("Vault", "Essentials"),
            EconomyProvider.PLAYER_POINTS, Set.of("PlayerPoints")));

    @Test
    void unrelatedPluginDoesNotInvalidateHealthyProviders() {
        assertEquals(Set.of(), dependencies.affectedBy("WorldEdit"));
    }

    @Test
    void serviceOwnerInvalidatesOnlyItsProvider() {
        assertEquals(Set.of(EconomyProvider.VAULT), dependencies.affectedBy("essentials"));
        assertEquals(Set.of(EconomyProvider.PLAYER_POINTS), dependencies.affectedBy("PLAYERPOINTS"));
    }
}
