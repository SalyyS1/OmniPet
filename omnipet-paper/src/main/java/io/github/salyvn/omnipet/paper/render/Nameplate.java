package io.github.salyvn.omnipet.paper.render;

import org.bukkit.entity.Entity;

import io.github.salyvn.omnipet.core.runtime.RendererAppearance;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * The name floating above a rendered pet.
 *
 * <p>Shared by both renderers so a model pet and a head pet are labelled the same way. Applied to the
 * carrier, which is the entity the display and the interaction ride, so the plate sits above the whole pet
 * however the model is scaled.
 *
 * <p>Parsed as MiniMessage and set through Adventure's {@code customName} rather than the legacy string
 * setter, so an operator can colour a name the way they colour everything else and the colour survives.
 */
final class Nameplate {
    private Nameplate() {}

    /**
     * Shows or clears the plate.
     *
     * <p>Cleared rather than left alone when there is no name, because a pet can lose its name — a player
     * clearing a rename, or an operator switching {@code render.nameplates} off and reloading — and a stale
     * plate would outlive the name it was showing.
     */
    static void apply(Entity carrier, RendererAppearance appearance, PaperHeadRendererSettings settings) {
        if (carrier == null) return;
        if (!settings.nameplates() || !appearance.named()) {
            carrier.customName(null);
            carrier.setCustomNameVisible(false);
            return;
        }
        carrier.customName(Messages.operator(appearance.displayName()));
        carrier.setCustomNameVisible(true);
    }
}
