package io.github.salyvn.omnipet.paper.release;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReleaseRewardMailbox {
    Optional<ReleaseMailboxEntry> find(UUID transactionId) throws IOException;

    ReleaseMailboxEntry create(ReleaseMailboxEntry entry) throws IOException;

    ReleaseMailboxEntry update(UUID transactionId, ReleaseMailboxEntry.State state, String detail) throws IOException;

    List<ReleaseMailboxEntry> list(int limit) throws IOException;
}
