package io.github.salyvn.omnipet.core.incubation;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class IncubationItemActionTransactionTest {
    @Test
    void itemContractMustMatchTransactionTypeAndEffect() {
        EggItemIdentity reducer = item(IncubationItemActionType.REDUCE, 30_000);

        assertThrows(IllegalArgumentException.class,
                () -> transaction(reducer, IncubationItemActionType.COMPLETE, 0));
        assertThrows(IllegalArgumentException.class,
                () -> transaction(reducer, IncubationItemActionType.REDUCE, 60_000));
        assertThrows(IllegalArgumentException.class,
                () -> transaction(unboundItem(), IncubationItemActionType.REDUCE, 30_000));
        assertThrows(IllegalArgumentException.class,
                () -> transaction(fractionalEffectItem(), IncubationItemActionType.REDUCE, 30_000));
    }

    private static IncubationItemActionTransaction transaction(
            EggItemIdentity item,
            IncubationItemActionType type,
            long effectMillis) {
        return new IncubationItemActionTransaction(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, item,
                type, effectMillis, IncubationItemActionStage.PREPARED);
    }

    private static EggItemIdentity item(IncubationItemActionType type, long effectMillis) {
        return new EggItemIdentity(
                4, EggInventoryHand.MAIN_HAND, "minecraft:paper", UUID.randomUUID(),
                "a".repeat(64), 1,
                IncubationItemActionItemContract.bind(Map.of("payload", "item"), type, effectMillis));
    }

    private static EggItemIdentity unboundItem() {
        return new EggItemIdentity(
                4, EggInventoryHand.MAIN_HAND, "minecraft:paper", UUID.randomUUID(),
                "a".repeat(64), 1, Map.of("payload", "item"));
    }

    private static EggItemIdentity fractionalEffectItem() {
        return new EggItemIdentity(
                4, EggInventoryHand.MAIN_HAND, "minecraft:paper", UUID.randomUUID(),
                "a".repeat(64), 1, Map.of(
                        IncubationItemActionItemContract.TYPE_KEY, IncubationItemActionType.REDUCE.name(),
                        IncubationItemActionItemContract.EFFECT_MILLIS_KEY, 30_000.5));
    }
}
