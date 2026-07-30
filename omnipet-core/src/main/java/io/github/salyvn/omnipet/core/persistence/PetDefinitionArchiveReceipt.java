package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;

public interface PetDefinitionArchiveReceipt {
    String definitionId();

    void rollback() throws IOException;
}
