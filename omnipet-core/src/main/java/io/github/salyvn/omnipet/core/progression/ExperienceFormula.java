package io.github.salyvn.omnipet.core.progression;

import java.util.Map;

@FunctionalInterface
public interface ExperienceFormula {
    double required(Map<String, Double> context);
}
