package io.github.salyvn.omnipet.core.studio.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class CompiledFormulaTest {
    @Test
    void oneCompiledTreeEvaluatesMultipleContexts() {
        CompiledFormula formula = StudioFormulaValidator.compile("max(10, level * 20) + clamp(quality, 0, 100)");

        assertEquals(60, formula.evaluate(Map.of("level", 2.0, "quality", 20.0)));
        assertEquals(120, formula.evaluate(Map.of("level", 1.0, "quality", 1000.0)));
    }

    @Test
    void compilationRejectsSyntaxAndEvaluationRejectsMissingOrNonFiniteValues() {
        assertThrows(IllegalArgumentException.class, () -> StudioFormulaValidator.compile("unknown(1)"));
        CompiledFormula formula = StudioFormulaValidator.compile("level / divisor");
        assertThrows(IllegalArgumentException.class, () -> formula.evaluate(Map.of("level", 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> formula.evaluate(Map.of("level", 1.0, "divisor", 0.0)));
    }
}
