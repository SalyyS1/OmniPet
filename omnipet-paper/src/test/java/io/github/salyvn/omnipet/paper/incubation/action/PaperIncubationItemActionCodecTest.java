package io.github.salyvn.omnipet.paper.incubation.action;

import static io.github.salyvn.omnipet.paper.incubation.action.PaperIncubationActionTestItems.EFFECT;
import static io.github.salyvn.omnipet.paper.incubation.action.PaperIncubationActionTestItems.NONCE;
import static io.github.salyvn.omnipet.paper.incubation.action.PaperIncubationActionTestItems.SCHEMA;
import static io.github.salyvn.omnipet.paper.incubation.action.PaperIncubationActionTestItems.TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.incubation.EggInventoryHand;
import io.github.salyvn.omnipet.core.incubation.IncubationItemActionType;
import io.github.salyvn.omnipet.paper.incubation.ObservedEggStack;
import io.github.salyvn.omnipet.paper.incubation.action.PaperIncubationActionTestItems.FakeItemStack;

class PaperIncubationItemActionCodecTest {
    private static final UUID CREATE_NONCE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CAPTURE_NONCE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void reducerCreationCaptureAndSnapshotRoundTripUseFreshCanonicalIdentity() {
        AtomicReference<ItemStack> restoreTemplate = new AtomicReference<>();
        PaperIncubationItemActionCodec codec = codec(restoreTemplate);
        FakeItemStack created = (FakeItemStack) codec.createReducer(Material.CLOCK, 60_000);
        UUID createdNonce = UUID.fromString((String) created.values().get(NONCE));
        created.setAmount(3);

        CapturedIncubationItemAction captured = codec.capture(created, 4, EggInventoryHand.MAIN_HAND);
        restoreTemplate.set(captured.stack());
        ItemStack restored = codec.restore(captured.identity());
        ObservedEggStack observed = codec.observe(restored, 4).orElseThrow();

        assertEquals(1, created.values().get(SCHEMA));
        assertEquals("REDUCE", created.values().get(TYPE));
        assertEquals(60_000L, created.values().get(EFFECT));
        assertEquals(IncubationItemActionType.REDUCE, captured.type());
        assertEquals(60_000L, captured.effectMillis());
        assertEquals(3, captured.identity().expectedStackAmount());
        assertEquals(CAPTURE_NONCE, captured.identity().itemNonce());
        assertNotEquals(createdNonce, captured.identity().itemNonce());
        assertTrue(observed.identityValid());
        assertEquals(captured.identity().fingerprint(), observed.fingerprint());
        assertEquals(1, restored.getAmount());
    }

    @Test
    void instantHatchUsesZeroEffectAndCaptureRejectsMalformedContracts() {
        AtomicReference<ItemStack> restoreTemplate = new AtomicReference<>();
        PaperIncubationItemActionCodec codec = codec(restoreTemplate);
        FakeItemStack instant = (FakeItemStack) codec.createInstantHatch(Material.NETHER_STAR);
        CapturedIncubationItemAction captured = codec.capture(instant, 40, EggInventoryHand.OFF_HAND);
        FakeItemStack badSchema = instant.clone();
        badSchema.values().put(SCHEMA, 2);
        FakeItemStack badEffect = instant.clone();
        badEffect.values().put(EFFECT, 1L);

        assertEquals(IncubationItemActionType.COMPLETE, captured.type());
        assertEquals(0, captured.effectMillis());
        assertThrows(IllegalArgumentException.class,
                () -> codec.capture(badSchema, 40, EggInventoryHand.OFF_HAND));
        assertThrows(IllegalArgumentException.class,
                () -> codec.capture(badEffect, 40, EggInventoryHand.OFF_HAND));
        assertFalse(codec.observe(badSchema, 40).orElseThrow().identityValid());
        assertFalse(codec.observe(badEffect, 40).orElseThrow().identityValid());
        assertThrows(IllegalArgumentException.class, () -> codec.createReducer(Material.CLOCK, 0));
    }

    private static PaperIncubationItemActionCodec codec(AtomicReference<ItemStack> restoreTemplate) {
        AtomicInteger nonceIndex = new AtomicInteger();
        return new PaperIncubationItemActionCodec(
                SCHEMA,
                TYPE,
                EFFECT,
                NONCE,
                () -> nonceIndex.getAndIncrement() == 0 ? CREATE_NONCE : CAPTURE_NONCE,
                PaperIncubationActionTestItems.FakeItemStack::new,
                ignored -> restoreTemplate.get().clone());
    }
}
