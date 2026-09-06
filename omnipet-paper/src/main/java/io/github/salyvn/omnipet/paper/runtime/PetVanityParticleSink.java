package io.github.salyvn.omnipet.paper.runtime;

import java.util.UUID;

import io.github.salyvn.omnipet.core.runtime.RuntimeVector;

/**
 * Where a playing pet's vanity particles go.
 *
 * <p>A seam rather than a direct Bukkit call inside the engine, so the tick loop stays testable without a
 * server and an operator who turns the feature off gets a no-op rather than a branch on every pet.
 */
@FunctionalInterface
public interface PetVanityParticleSink {
    /** Does nothing; the wiring installs this when idle play is disabled. */
    PetVanityParticleSink NONE = (ownerId, position) -> {};

    /**
     * @param ownerId  the owner whose world the burst is drawn in
     * @param position where the playing pet is this tick
     */
    void emit(UUID ownerId, RuntimeVector position);
}
