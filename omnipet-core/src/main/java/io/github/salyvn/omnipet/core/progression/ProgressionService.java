package io.github.salyvn.omnipet.core.progression;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.github.salyvn.omnipet.core.studio.input.CompiledFormula;
import io.github.salyvn.omnipet.core.studio.input.StudioFormulaValidator;

/** Bounded cultivation transitions; callers persist the returned immutable state. */
public final class ProgressionService {
    private static final int MAX_LEVEL_UPS_PER_CALL = 64;
    private final Map<String, ExperienceFormula> compiled = new ConcurrentHashMap<>();

    public ProgressionResult addExperience(
            ProgressionState state,
            double amount,
            ProgressionConfig config,
            String petFormula,
            Map<String, Double> context) {
        Objects.requireNonNull(state, "progression state");
        Objects.requireNonNull(config, "progression config");
        if (!Double.isFinite(amount) || amount < 0) {
            return result(ProgressionResult.Status.INVALID_AMOUNT, state, 0, "experience amount is invalid");
        }
        if (state.level() >= config.maxLevel()) {
            return result(ProgressionResult.Status.ALREADY_MAX_LEVEL, state, 0, "pet is already at maximum level");
        }
        ExperienceFormula formula;
        try {
            formula = resolveFormula(config, petFormula);
        } catch (RuntimeException failure) {
            return result(ProgressionResult.Status.INVALID_FORMULA, state, 0, failure.getMessage());
        }
        Map<String, Double> values = new HashMap<>(config.formulaSamples());
        if (context != null) values.putAll(context);
        int level = state.level();
        double experience = state.experience() + amount;
        int gained = 0;
        try {
            while (level < config.maxLevel() && gained < MAX_LEVEL_UPS_PER_CALL) {
                values.put("level", (double) level);
                double required = formula.required(values);
                if (!Double.isFinite(required) || required <= 0) {
                    return result(ProgressionResult.Status.INVALID_FORMULA, state, 0,
                            "experience formula must return a finite positive value");
                }
                if (experience < required) break;
                experience -= required;
                level++;
                gained++;
            }
        } catch (RuntimeException failure) {
            return result(ProgressionResult.Status.INVALID_FORMULA, state, 0, failure.getMessage());
        }
        if (level >= config.maxLevel() && config.overflowPolicy() == ProgressionConfig.OverflowPolicy.DISCARD) {
            experience = 0;
        }
        ProgressionState next = state.withValues(level, experience, state.evolution(), state.stamina(),
                state.lastStaminaEpochMillis());
        return result(ProgressionResult.Status.APPLIED, next, gained, "experience applied");
    }

    public ProgressionResult breakthrough(
            ProgressionState state,
            int requiredLevel,
            int requiredEvolution,
            ProgressionConfig config) {
        Objects.requireNonNull(state, "progression state");
        Objects.requireNonNull(config, "progression config");
        if (requiredLevel <= 0 || requiredEvolution < 0) {
            return result(ProgressionResult.Status.INVALID_BREAKTHROUGH, state, 0, "breakthrough requirements are invalid");
        }
        if (state.level() < requiredLevel || state.evolution() != requiredEvolution) {
            return result(ProgressionResult.Status.REQUIREMENT_FAILED, state, 0, "breakthrough requirements are not met");
        }
        return result(ProgressionResult.Status.APPLIED,
                state.withValues(state.level(), state.experience(), state.evolution() + 1,
                        state.stamina(), state.lastStaminaEpochMillis()),
                0, "breakthrough applied");
    }

    public ProgressionState regenerateStamina(
            ProgressionState state,
            long nowEpochMillis,
            ProgressionConfig config) {
        Objects.requireNonNull(state, "progression state");
        Objects.requireNonNull(config, "progression config");
        if (nowEpochMillis < state.lastStaminaEpochMillis()) {
            return state;
        }
        double seconds = (nowEpochMillis - state.lastStaminaEpochMillis()) / 1000.0;
        double next = Math.min(config.maxStamina(), state.stamina() + seconds * config.staminaRegenPerSecond());
        return state.withValues(state.level(), state.experience(), state.evolution(), next, nowEpochMillis);
    }

    public ProgressionResult spendStamina(ProgressionState state, double amount, ProgressionConfig config) {
        Objects.requireNonNull(state, "progression state");
        Objects.requireNonNull(config, "progression config");
        if (!Double.isFinite(amount) || amount < 0 || state.stamina() < amount) {
            return result(ProgressionResult.Status.REQUIREMENT_FAILED, state, 0, "not enough stamina");
        }
        return result(ProgressionResult.Status.APPLIED,
                state.withValues(state.level(), state.experience(), state.evolution(), state.stamina() - amount,
                        state.lastStaminaEpochMillis()), 0, "stamina spent");
    }

    private ExperienceFormula resolveFormula(ProgressionConfig config, String petFormula) {
        String formula = petFormula == null || petFormula.isBlank() ? "default" : petFormula.trim();
        if (formula.equals("default")) return config.defaultFormula();
        return compiled.computeIfAbsent(formula, key -> {
            CompiledFormula expression = StudioFormulaValidator.compile(key);
            return expression::evaluate;
        });
    }

    private static ProgressionResult result(ProgressionResult.Status status, ProgressionState state,
                                            int gained, String detail) {
        return new ProgressionResult(status, state, gained, detail);
    }
}
