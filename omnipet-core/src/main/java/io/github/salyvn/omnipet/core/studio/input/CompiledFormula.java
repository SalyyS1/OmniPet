package io.github.salyvn.omnipet.core.studio.input;

import java.util.Map;

@FunctionalInterface
public interface CompiledFormula {
    double evaluate(Map<String, Double> variables);
}
