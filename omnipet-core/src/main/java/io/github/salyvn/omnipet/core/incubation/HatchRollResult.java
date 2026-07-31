package io.github.salyvn.omnipet.core.incubation;

import io.github.salyvn.omnipet.core.domain.incubation.IncubationOutcome;

public record HatchRollResult(IncubationOutcome outcome) {
    public HatchRollResult {
        if (outcome == null) throw new IllegalArgumentException("incubation outcome is required");
    }
}
