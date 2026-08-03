package io.github.salyvn.omnipet.paper.incubation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinition;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinitionEnvelope;
import io.github.salyvn.omnipet.core.domain.incubation.HatchCandidate;
import io.github.salyvn.omnipet.core.incubation.IncubationDurationParser;
import io.github.salyvn.omnipet.core.persistence.EggDefinitionRepository;
import io.github.salyvn.omnipet.core.persistence.YamlEggDefinitionRepository;

/**
 * The companion egg is what closes the gap between creating a pet and being able to obtain one.
 *
 * <p>Driven against the repository directly rather than through the command, because the command needs
 * a live server for {@code Bukkit.getPlayerExact} and {@code new ItemStack}. What matters here is the
 * decision logic: which egg ID, what it points at, and when it declines to write.
 */
class EggCompanionCreationTest {
    @TempDir
    Path root;

    @Test
    void savingAPetCreatesAnEggThatHatchesExactlyThatPet() throws IOException {
        EggDefinitionRepository eggs = new YamlEggDefinitionRepository(root);

        Optional<String> created = controller(eggs).createCompanionEgg(pet("ember_fox", PetTier.B));

        assertEquals(Optional.of("ember_fox_egg"), created);
        EggDefinition definition = eggs.read("ember_fox_egg").orElseThrow().definition();
        assertEquals(List.of("ember_fox"), definition.candidates().stream()
                .map(HatchCandidate::definitionId).toList());
        // Tier follows the pet, so an S-tier pet does not silently become a D-tier egg.
        assertEquals(PetTier.B, definition.tier());
        assertEquals(IncubationDurationParser.parseMillis(EggAdminController.DEFAULT_DURATION),
                definition.baseActiveMillis());
    }

    @Test
    void anExistingEggIsNeverOverwritten() throws IOException {
        EggDefinitionRepository eggs = new YamlEggDefinitionRepository(root);
        // An operator has tuned this by hand: longer duration, two candidates.
        eggs.save(new EggDefinitionEnvelope(1, new EggDefinition(
                "ember_fox_egg",
                PetTier.S,
                Duration.ofDays(2).toMillis(),
                List.of(new HatchCandidate("ember_fox", 3.0, Map.of()),
                        new HatchCandidate("stone_wolf", 1.0, Map.of())),
                Map.of())));

        Optional<String> created = controller(eggs).createCompanionEgg(pet("ember_fox", PetTier.B));

        assertTrue(created.isEmpty(), "an existing entry must be reported as not created");
        EggDefinition kept = eggs.read("ember_fox_egg").orElseThrow().definition();
        assertEquals(2, kept.candidates().size(), "the operator's candidate pool must survive");
        assertEquals(Duration.ofDays(2).toMillis(), kept.baseActiveMillis());
        assertEquals(PetTier.S, kept.tier());
    }

    @Test
    void savingTheSamePetTwiceWritesTheEggOnlyOnce() throws IOException {
        EggDefinitionRepository eggs = new YamlEggDefinitionRepository(root);
        EggAdminController controller = controller(eggs);
        PetDefinition definition = pet("ember_fox", PetTier.D);

        assertTrue(controller.createCompanionEgg(definition).isPresent());
        assertTrue(controller.createCompanionEgg(definition).isEmpty(), "the second save must be a no-op");

        assertEquals(1, eggs.list().size());
    }

    @Test
    void theCompanionIdIsDerivedFromTheDefinitionId() {
        assertEquals("ember_fox_egg", EggAdminController.companionEggId("ember_fox"));
    }

    @Test
    void aFailingCatalogReportsThroughTheExceptionRatherThanSilently() {
        // A repository that cannot write must surface an IOException, so the Studio can tell the
        // operator the pet saved but its egg did not.
        EggDefinitionRepository broken = new EggDefinitionRepository() {
            @Override
            public Optional<EggDefinitionEnvelope> read(String id) {
                return Optional.empty();
            }

            @Override
            public List<String> list() {
                return List.of();
            }

            @Override
            public void save(EggDefinitionEnvelope envelope) throws IOException {
                throw new IOException("catalog is read-only");
            }
        };

        IOException failure = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> controller(broken).createCompanionEgg(pet("ember_fox", PetTier.D)));
        assertEquals("catalog is read-only", failure.getMessage());
    }

    @Test
    void theStudioCreatesEggsOnlyBehindItsConfigSwitch() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/salyvn/omnipet/paper/studio/bukkit/PetStudioController.java"));

        assertTrue(source.contains("GuiSettings.gui().studioAutoCreateEgg()"),
                "an operator with a hand-designed catalog must be able to turn this off");
        // After the save, never inside it: a failing egg must not roll back or obscure the definition.
        assertTrue(source.indexOf("service.save(state.draft") < source.indexOf("createCompanionEgg(player"),
                "the egg is written after the definition, so its failure cannot affect the pet");
        assertTrue(source.contains("if (eggs == null"), "an unbound controller must be a no-op");
    }

    private static EggAdminController controller(EggDefinitionRepository eggs) {
        return EggAdminController.catalogOnly(eggs);
    }

    private static PetDefinition pet(String id, PetTier tier) {
        return new PetDefinition(
                id,
                1,
                tier,
                new HeadIcon("BASE64", "value"),
                new DisplayDefinition(DisplayDefinition.Provider.HEAD, null),
                Map.of());
    }
}
