package io.github.salyvn.omnipet.paper.config;

import java.util.Objects;

import io.github.salyvn.omnipet.core.progression.ProgressionConfig;
import io.github.salyvn.omnipet.paper.runtime.PaperRuntimeSettings;

public record OmniPetConfig(
        Phase4PaperConfig storage,
        PaperRuntimeSettings runtime,
        ProgressionConfig progression,
        String experienceFormulaSource,
        CultivationItems cultivationItems,
        GuiConfig gui) {
    /** The EXP formula used when {@code progression.defaultExperienceFormula} is absent. */
    public static final String DEFAULT_EXPERIENCE_FORMULA = "100 + level * 25 + evolution * 100";

    public OmniPetConfig {
        storage = Objects.requireNonNull(storage, "storage config");
        runtime = Objects.requireNonNull(runtime, "runtime config");
        progression = Objects.requireNonNull(progression, "progression config");
        // Retained beside the compiled formula because compilation is one-way: ProgressionConfig holds
        // only a lambda, so without the source text encode() could not write back what the operator
        // configured and a migration would silently reset a custom formula to the default.
        experienceFormulaSource = experienceFormulaSource == null || experienceFormulaSource.isBlank()
                ? DEFAULT_EXPERIENCE_FORMULA
                : experienceFormulaSource.trim();
        cultivationItems = Objects.requireNonNull(cultivationItems, "cultivation item config");
        gui = gui == null ? GuiConfig.defaults() : gui;
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
