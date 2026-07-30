package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;

import io.github.salyvn.omnipet.core.domain.PetDefinitionEnvelope;

/** A committed definition write that can restore the exact previous disk state. */
public interface PetDefinitionWriteReceipt {
    PetDefinitionEnvelope persisted();

    void rollback() throws IOException;
}
