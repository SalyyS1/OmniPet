package io.github.salyvn.omnipet.paper.studio.session;

import java.time.Instant;

@FunctionalInterface
public interface StudioClock {
    Instant now();
}
