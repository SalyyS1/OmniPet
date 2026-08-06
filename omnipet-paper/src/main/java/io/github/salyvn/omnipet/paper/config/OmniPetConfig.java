package io.github.salyvn.omnipet.paper.config;

import java.util.Objects;

import io.github.salyvn.omnipet.core.progression.ProgressionConfig;
import io.github.salyvn.omnipet.paper.render.PaperHeadRendererSettings;
import io.github.salyvn.omnipet.paper.runtime.PaperRuntimeSettings;

public record OmniPetConfig(
        Phase4PaperConfig storage,
        PaperRuntimeSettings runtime,
        PaperHeadRendererSettings render,
        ProgressionConfig progression,
        String experienceFormulaSource,
        io.github.salyvn.omnipet.core.progression.KillExperienceRules killExperience,
        CultivationItems cultivationItems,
        ItemAppearances appearances,
        GuiConfig gui) {
    /** The EXP formula used when {@code progression.defaultExperienceFormula} is absent. */
    public static final String DEFAULT_EXPERIENCE_FORMULA = "100 + level * 25 + evolution * 100";

    public OmniPetConfig {
        storage = Objects.requireNonNull(storage, "storage config");
        runtime = Objects.requireNonNull(runtime, "runtime config");
        render = render == null ? PaperHeadRendererSettings.defaults() : render;
        progression = Objects.requireNonNull(progression, "progression config");
        // Absent means off, so an existing config keeps behaving as it did until an operator opts in.
        killExperience = killExperience == null
                ? io.github.salyvn.omnipet.core.progression.KillExperienceRules.disabled()
                : killExperience;
        // Retained beside the compiled formula because compilation is one-way: ProgressionConfig holds
        // only a lambda, so without the source text encode() could not write back what the operator
        // configured and a migration would silently reset a custom formula to the default.
        experienceFormulaSource = experienceFormulaSource == null || experienceFormulaSource.isBlank()
                ? DEFAULT_EXPERIENCE_FORMULA
                : experienceFormulaSource.trim();
        cultivationItems = Objects.requireNonNull(cultivationItems, "cultivation item config");
        appearances = appearances == null ? ItemAppearances.defaults() : appearances;
        gui = gui == null ? GuiConfig.defaults() : gui;
    }

    /**
     * Operator-chosen appearance for each OmniPet item.
     *
     * <p>Grouped rather than added as five loose fields so a new item is one entry here instead of a
     * change to every constructor call. All presentation; none of it touches escrow identity.
     */
    public record ItemAppearances(
            ItemAppearance egg,
            ItemAppearance experienceCandy,
            ItemAppearance breakthroughStone,
            ItemAppearance hatchReducer,
            ItemAppearance instantHatch) {
        public ItemAppearances {
            egg = orDefault(egg);
            experienceCandy = orDefault(experienceCandy);
            breakthroughStone = orDefault(breakthroughStone);
            hatchReducer = orDefault(hatchReducer);
            instantHatch = orDefault(instantHatch);
        }

        public static ItemAppearances defaults() {
            return new ItemAppearances(null, null, null, null, null);
        }

        private static ItemAppearance orDefault(ItemAppearance value) {
            return value == null ? ItemAppearance.defaults() : value;
        }
    }

    public record CultivationItems(
            String experienceMaterial,
            double experienceAmount,
            String breakthroughMaterial,
            int breakthroughRequiredLevel,
            int breakthroughRequiredEvolution) {
        public CultivationItems {
            experienceMaterial = required(experienceMaterial, "experience material");
            breakthroughMaterial = required(breakthroughMaterial, "breakthrough material");
            if (!Double.isFinite(experienceAmount) || experienceAmount <= 0 || experienceAmount > 1_000_000_000) {
                throw new IllegalArgumentException("cultivation experience amount is outside the supported range");
            }
            if (breakthroughRequiredLevel < 1 || breakthroughRequiredLevel > 1_000_000
                    || breakthroughRequiredEvolution < 0 || breakthroughRequiredEvolution > 1_000_000) {
                throw new IllegalArgumentException("breakthrough item requirements are outside the supported range");
            }
        }

        private static String required(String value, String label) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
            return value.trim().toUpperCase(java.util.Locale.ROOT);
        }
    }
}
