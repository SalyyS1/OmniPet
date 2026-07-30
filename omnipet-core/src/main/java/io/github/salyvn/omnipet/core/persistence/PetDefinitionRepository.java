package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.salyvn.omnipet.core.domain.PetDefinitionEnvelope;

public interface PetDefinitionRepository {
    Optional<PetDefinitionEnvelope> read(String id) throws IOException;

    List<String> list() throws IOException;

    PetDefinitionEnvelope saveDraft(PetDefinitionDraft draft) throws IOException;

    PetDefinitionWriteReceipt saveDraftWithRollback(PetDefinitionDraft draft) throws IOException;

    void archive(String id) throws IOException;

    PetDefinitionArchiveReceipt archiveWithRollback(String id) throws IOException;

    PetDefinitionDeleteReceipt deleteWithRollback(String id) throws IOException;

    Set<String> referenceScan(String id) throws IOException;
}
