package io.github.salyvn.omnipet.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ProgressionServiceTest {
    private final ProgressionService progression = new ProgressionService();
    private final ProgressionConfig config = new ProgressionConfig(
            3, 100, 10, context -> 100 + context.getOrDefault("level", 1.0) * 50,
            Map.of(), ProgressionConfig.OverflowPolicy.DISCARD);

    @Test
    void experienceCanCrossMultipleBoundedLevels() {
        ProgressionResult result = progression.addExperience(
                ProgressionState.initial(50, 1_000), 350, config, null, Map.of());

        assertEquals(ProgressionResult.Status.APPLIED, result.status());
        assertEquals(3, result.state().level());
        assertEquals(0, result.state().experience());
        assertEquals(2, result.levelsGained());
    }

    @Test
    void formulaOverrideAndInvalidFormulaAreHandledSafely() {
        ProgressionResult override = progression.addExperience(
                ProgressionState.initial(50, 1_000), 60, config, "max(10, level * 20)", Map.of("level", 1.0));
        ProgressionResult invalid = progression.addExperience(
                ProgressionState.initial(50, 1_000), 60, config, "unknown(level)", Map.of("level", 1.0));

        assertEquals(ProgressionResult.Status.APPLIED, override.status());
        assertEquals(3, override.state().level());
        assertEquals(ProgressionResult.Status.INVALID_FORMULA, invalid.status());
        assertEquals(1, invalid.state().level());
    }

    @Test
    void breakthroughAndStaminaRegenerationRemainBounded() {
        ProgressionState state = new ProgressionState(2, 0, 0, 10, 1_000, Map.of());
        ProgressionResult blocked = progression.breakthrough(state, 3, 0, config);
        ProgressionResult applied = progression.breakthrough(state, 2, 0, config);
        ProgressionState regenerated = progression.regenerateStamina(state, 20_000, config);

        assertEquals(ProgressionResult.Status.REQUIREMENT_FAILED, blocked.status());
        assertEquals(ProgressionResult.Status.APPLIED, applied.status());
        assertEquals(1, applied.state().evolution());
        assertEquals(100, regenerated.stamina());
        assertTrue(regenerated.lastStaminaEpochMillis() == 20_000);
    }

    @Test
    void maxLevelOverflowCanBeCarriedWhenConfigured() {
        ProgressionConfig carry = new ProgressionConfig(
                2, 100, 0, ignored -> 10, Map.of(), ProgressionConfig.OverflowPolicy.CARRY);
        ProgressionResult result = progression.addExperience(
                ProgressionState.initial(0, 0), 100, carry, null, Map.of());

        assertEquals(ProgressionResult.Status.APPLIED, result.status());
        assertEquals(2, result.state().level());
        assertEquals(90, result.state().experience());
    }
}
