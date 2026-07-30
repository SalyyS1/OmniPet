package io.github.salyvn.omnipet.core.persistence;

import io.github.salyvn.omnipet.core.domain.PetDefinition;

public record PetDefinitionDraft(PetDefinition definition, long expectedRevision) {
    public PetDefinitionDraft {
        if (definition == null) throw new IllegalArgumentException("definition is required");
        if (expectedRevision < 0) throw new IllegalArgumentException("expected revision cannot be negative");
    }
}
