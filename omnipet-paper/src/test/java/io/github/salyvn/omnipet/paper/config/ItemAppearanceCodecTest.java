package io.github.salyvn.omnipet.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Operator-configurable item appearance.
 *
 * <p>Appearance is cosmetic, so the loader is lenient: a bad value degrades that one key and warns,
 * rather than stopping players receiving items. It is also bounded, because the durable egg snapshot
 * refuses a serialized stack over 8 KiB and lore is the one part an operator can grow without limit.
 */
class ItemAppearanceCodecTest {
    private final ItemAppearanceCodec codec = new ItemAppearanceCodec();
    private final List<String> warnings = new ArrayList<>();

    @Test
    void anAbsentBlockIsTheBuiltInAppearance() {
        assertEquals(ItemAppearance.defaults(), codec.parse(null, "items.egg", warnings::add));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void readsEveryConfigurableField() {
        ItemAppearance appearance = codec.parse(java.util.Map.of(
                "material", "DRAGON_EGG",
                "customModelData", 1001,
                "glint", true,
                "itemFlags", List.of("HIDE_ATTRIBUTES"),
                "lore", List.of("<gray>Season 3</gray>")), "items.egg", warnings::add);

        assertEquals(Optional.of("DRAGON_EGG"), appearance.material());
        assertEquals(Optional.of(1001), appearance.customModelData());
        assertTrue(appearance.glint());
        assertEquals(List.of("HIDE_ATTRIBUTES"), appearance.itemFlags());
        assertEquals(List.of("<gray>Season 3</gray>"), appearance.extraLore());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void aBadValueDegradesThatKeyAloneAndWarns() {
        ItemAppearance appearance = codec.parse(java.util.Map.of(
                "material", 42,
                "customModelData", -1,
                "glint", "yes",
                "unknownKey", true), "items.egg", warnings::add);

        assertEquals(ItemAppearance.defaults(), appearance, "every bad value falls back");
        assertEquals(4, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("unknownKey")), warnings.toString());
    }

    @Test
    void loreIsBoundedSoAMintedItemCanStillBeCaptured() {
        // An egg's durable snapshot is rejected over 8 KiB. Truncating with a warning beats minting an
        // egg that looks fine and then cannot be paid into escrow.
        List<String> tooMany = new ArrayList<>();
        for (int line = 0; line < ItemAppearance.MAX_EXTRA_LORE_LINES + 5; line++) tooMany.add("line " + line);

        ItemAppearance appearance = codec.parse(
                java.util.Map.of("lore", tooMany), "items.egg", warnings::add);

        assertEquals(ItemAppearance.MAX_EXTRA_LORE_LINES, appearance.extraLore().size());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("lines")), warnings.toString());
    }

    @Test
    void anOverlongLoreLineIsTruncatedRatherThanRejected() {
        String overlong = "x".repeat(ItemAppearance.MAX_LORE_LINE_LENGTH + 50);

        ItemAppearance appearance = codec.parse(
                java.util.Map.of("lore", List.of(overlong)), "items.egg", warnings::add);

        assertEquals(ItemAppearance.MAX_LORE_LINE_LENGTH, appearance.extraLore().getFirst().length());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("characters")), warnings.toString());
    }

    @Test
    void configuredAppearanceSurvivesALegacyMigrationRoundTrip() {
        // encode() is what a migration writes back. An appearance the operator tuned must not be
        // silently dropped on upgrade, the same property the gui: section needs.
        OmniPetConfigLoader loader = new OmniPetConfigLoader();
        OmniPetConfig original = loader.parse(shipped().replace(
                "items:\n",
                "items:\n  egg:\n    material: DRAGON_EGG\n    customModelData: 1001\n    glint: true\n"))
                .config();
        assertEquals(Optional.of("DRAGON_EGG"), original.appearances().egg().material());

        OmniPetConfig reloaded = loader.parse(loader.encode(original)).config();

        assertEquals(original.appearances(), reloaded.appearances());
        assertEquals(Optional.of(1001), reloaded.appearances().egg().customModelData());
        assertTrue(reloaded.appearances().egg().glint());
    }

    @Test
    void theShippedConfigConfiguresNoAppearanceAtAll() {
        // Every appearance block ships commented out, so a stock install must read as all-defaults and
        // encode must not then write empty sections back into the file.
        OmniPetConfigLoader loader = new OmniPetConfigLoader();

        OmniPetConfig config = loader.parse(shipped(), warnings::add).config();

        assertEquals(OmniPetConfig.ItemAppearances.defaults(), config.appearances());
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertFalse(loader.encode(config).contains("customModelData"),
                "an unconfigured appearance must not be written back");
    }

    /**
     * The shipped config, with line endings normalised to LF.
     *
     * <p>Normalised because the tests anchor their injections on {@code "\n"}: on a CRLF checkout the
     * anchor would silently fail to match, {@code replace} would be a no-op, and the assertion would
     * compare against a value that was never injected.
     */
    private static String shipped() {
        try (var input = ItemAppearanceCodecTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
