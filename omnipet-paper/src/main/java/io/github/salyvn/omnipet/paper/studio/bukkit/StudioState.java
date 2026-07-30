package io.github.salyvn.omnipet.paper.studio.bukkit;

import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.studio.StudioPetDraft;
import io.github.salyvn.omnipet.paper.studio.session.PetStudioSession;
import io.github.salyvn.omnipet.paper.studio.session.StudioViewToken;

final class StudioState {
    final UUID viewerId;
    UUID sessionId;
    StudioViewToken token;
    PetTier tier = PetTier.D;
    int page;
    String filter = "";
    boolean archiveMode;
    String archiveTarget;
    long archiveRevision;
    String archiveHash = "";
    long archiveGeneration;
    StudioPetDraft draft;
    UUID saveKey = UUID.randomUUID();
    UUID archiveKey = UUID.randomUUID();

    StudioState(UUID viewerId, PetStudioSession session) {
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        updateSession(session);
    }

    void updateSession(PetStudioSession session) {
        if (!viewerId.equals(session.viewerId())) throw new IllegalArgumentException("session viewer differs");
        sessionId = session.sessionId();
        token = session.viewToken();
    }

    boolean matches(StudioViewToken candidate) {
        return token != null && token.equals(candidate) && sessionId.equals(candidate.sessionId());
    }
}
