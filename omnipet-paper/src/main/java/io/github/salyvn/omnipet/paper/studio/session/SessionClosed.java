package io.github.salyvn.omnipet.paper.studio.session;

import java.util.UUID;

public record SessionClosed(UUID sessionId, UUID viewerId, SessionCloseReason reason) {}
