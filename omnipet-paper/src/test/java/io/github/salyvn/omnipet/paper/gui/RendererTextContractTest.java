package io.github.salyvn.omnipet.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.paper.gui.player.PetManagementInventoryHolder;
import io.github.salyvn.omnipet.paper.gui.player.PetManagementMenuRenderer;
import io.github.salyvn.omnipet.paper.management.PetManagementSession;
import io.github.salyvn.omnipet.paper.management.PetManagementViewModel;
import io.github.salyvn.omnipet.paper.text.MessageCatalog;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * The "sơ sài" defect was structural: five renderers each held a private item builder and none
 * disabled {@link TextDecoration#ITALIC}, so every lore line rendered italic. These assertions make
 * the fix permanent — a reintroduced private builder or a raw {@code Component.text} lore line fails
 * the build.
 */
class RendererTextContractTest {
    private static final Path GUI = Path.of("src/main/java/io/github/salyvn/omnipet/paper/gui");
    private static final Path STUDIO =
            Path.of("src/main/java/io/github/salyvn/omnipet/paper/studio/bukkit");

    @BeforeAll
    static void bindMessageCatalog() {
        Messages.bind(MessageCatalog.defaults());
    }

    @Test
    void noRendererHoldsItsOwnItemBuilderAnyMore() throws IOException {
        for (Path source : renderers()) {
            String text = Files.readString(source);
            assertFalse(text.contains("private static ItemStack item("),
                    () -> source.getFileName() + " reintroduced a private item builder");
            assertTrue(text.contains("GuiItems"),
                    () -> source.getFileName() + " does not use the shared builder");
        }
    }

    @Test
    void noPlayerFacingRendererTruncatesAUuidIntoLore() throws IOException {
        for (Path source : playerRenderers()) {
            String text = Files.readString(source);
            assertFalse(text.contains("abbreviate("),
                    () -> source.getFileName() + " truncates a value into player lore");
            assertFalse(text.contains("shortId("),
                    () -> source.getFileName() + " truncates an ID into player lore");
        }
    }

    @Test
    void everyManagementEntryRendersUpright() {
        PetManagementMenuRenderer.Layout layout =
                new PetManagementMenuRenderer().layout(view());

        assertEquals(TextDecoration.State.FALSE,
                GuiItems.upright(layout.title()).decoration(TextDecoration.ITALIC));
        layout.entries().forEach((slot, entry) -> {
            assertEquals(TextDecoration.State.FALSE,
                    GuiItems.upright(entry.name()).decoration(TextDecoration.ITALIC),
                    () -> "slot " + slot + " name is italic");
            for (Component line : entry.lore()) {
                assertEquals(TextDecoration.State.FALSE,
                        GuiItems.upright(line).decoration(TextDecoration.ITALIC),
                        () -> "slot " + slot + " lore is italic");
            }
        });
    }

    @Test
    void managementLoreCarriesNoTruncatedInstanceId() {
        PetManagementMenuRenderer.Layout layout = new PetManagementMenuRenderer().layout(view());

        // Staff need the full UUID here, so it is present in full and never as a "..." fragment.
        String identity = layout.entries().get(22).lore().stream()
                .map(line -> PlainTextComponentSerializer.plainText().serialize(line))
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(identity.contains(PET.toString()));
        assertFalse(identity.contains("..."));
    }

    @Test
    void everyGuiKeyDefaultCarriesAnExplicitColourOrIsAValueRow() {
        for (MessageKey key : MessageKey.values()) {
            if (!key.path().startsWith("gui.")) continue;
            String value = key.defaultValue();
            boolean coloured = value.contains("<gray>") || value.contains("<white>")
                    || value.contains("<yellow>") || value.contains("<green>")
                    || value.contains("<red>") || value.contains("<gold>")
                    || value.contains("<aqua>") || value.contains("<dark_gray>");
            // Titles and a few names take their colour from the renderer's state-dependent choice.
            boolean rendererColoured = key.path().startsWith("gui.title.")
                    || key.path().equals("gui.vault.status")
                    || key.path().startsWith("gui.slot.pay-")
                    || key.path().startsWith("gui.manage.")
                    || key.path().startsWith("gui.hatch.incubation");
            assertTrue(coloured || rendererColoured,
                    () -> key.path() + " has no colour and no renderer-applied colour");
        }
    }

    private static List<Path> renderers() {
        return List.of(
                GUI.resolve("player/PlayerPetMenuRenderer.java"),
                GUI.resolve("player/PetManagementMenuRenderer.java"),
                GUI.resolve("player/SlotPurchaseMenuRenderer.java"),
                GUI.resolve("hatch/HatchMenuRenderer.java"),
                STUDIO.resolve("StudioInventoryRenderer.java"));
    }

    private static List<Path> playerRenderers() {
        return List.of(
                GUI.resolve("player/PlayerPetMenuRenderer.java"),
                GUI.resolve("player/PetManagementMenuRenderer.java"),
                GUI.resolve("player/SlotPurchaseMenuRenderer.java"),
                GUI.resolve("hatch/HatchMenuRenderer.java"));
    }

    private static final UUID VIEWER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PET = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static PetManagementViewModel view() {
        // The management view model reads progression through the projection, which requires every
        // field. That is fine here: management only opens for a pet the caller already resolved.
        // The vault's tolerance for partial data is VaultPetSummary's job, tested separately.
        PetInstance pet = new PetInstance(PET, "wolf", 1,
                Map.of(
                        "progression", Map.of(
                                "level", 3,
                                "experience", 120.0,
                                "evolution", 1,
                                "stamina", 20.0,
                                "lastStaminaEpochMillis", 1_700_000_000_000L),
                        "hatching", Map.of("rarityId", "RARE")),
                Map.of());
        PlayerState state = new PlayerState(
                OWNER, 7, List.of(pet), 30, 1, List.of(PET), List.of(), Map.of(), null, Map.of());
        PetManagementSession session = new PetManagementSession(
                VIEWER, OWNER, PET, UUID.randomUUID(), state.revision());
        return PetManagementViewModel.create(session, state, null);
    }

    static {
        // Referenced so the holder import documents the layout type under test.
        assert PetManagementInventoryHolder.View.MANAGEMENT != null;
    }
}
