package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;

/** Restores exact definition and backup bytes when a hard-delete activation fails. */
public interface PetDefinitionDeleteReceipt {
    String definitionId();

    void rollback() throws IOException;
}
