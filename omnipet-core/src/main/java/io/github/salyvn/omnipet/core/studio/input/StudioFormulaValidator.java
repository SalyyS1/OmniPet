package io.github.salyvn.omnipet.core.studio.input;

import java.util.Map;

/** Bounded arithmetic compiler/evaluator with no platform or vendor dependency. */
public final class StudioFormulaValidator {
    private StudioFormulaValidator() {}

    public static CompiledFormula compile(String formula) {
        return FormulaCompiler.compile(formula);
    }

    public static double validate(String formula, Map<String, Double> samples, double minimum, double maximum) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("formula bounds are invalid");
        }
        double result = compile(formula).evaluate(samples);
        if (result < minimum || result > maximum) {
            throw new IllegalArgumentException("formula sample is outside bounds");
        }
        return result;
    }

    public static double validateBounded(
            String formula,
            Map<String, Double> samples,
            double minimum,
            double maximum) {
        return validate(formula, samples, minimum, maximum);
    }
}
