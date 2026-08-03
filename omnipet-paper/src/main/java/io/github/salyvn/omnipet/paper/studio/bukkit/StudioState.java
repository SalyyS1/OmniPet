package io.github.salyvn.omnipet.paper.studio.bukkit;

import java.util.Objects;
import java.util.UUID;

import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.catalog.CatalogSnapshot;
import io.github.salyvn.omnipet.core.catalog.StatCatalogEntry;
import io.github.salyvn.omnipet.core.studio.StatModifierType;
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
    String statFilter = "";
    int statPage;
    /** The stat whose modifier is being chosen, or {@code null} outside the modifier flow. */
    String pendingStatId;
    /** The modifier chosen by click, consumed by the two-token {@code min max} prompt. */
    StatModifierType pendingModifier;
    CatalogSnapshot<StatCatalogEntry> statSnapshot;
    boolean archiveMode;
    String archiveTarget;
    long archiveRevision;
    String archiveHash = "";
    long archiveGeneration;
    StudioPetDraft draft;
    UUID saveKey = UUID.randomUUID();
    UUID archiveKey = UUID.randomUUID();
    UUID hardDeleteKey = UUID.randomUUID();

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

    /**
     * Clears the pending stat selection.
     *
     * <p>Called whenever a stat flow completes, is cancelled, or the session closes, so a modifier
     * chosen for one stat can never be applied to the next one.
     */
    void clearPendingStat() {
        pendingStatId = null;
        pendingModifier = null;
    }
}
