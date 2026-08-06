package io.github.salyvn.omnipet.paper.render;

import org.bukkit.entity.Entity;

import io.github.salyvn.omnipet.core.runtime.PetStatus;
import io.github.salyvn.omnipet.core.runtime.RendererAppearance;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * The name floating above a rendered pet, and what it is doing.
 *
 * <p>Shared by both renderers so a model pet and a head pet are labelled the same way. Applied to the
 * carrier, which is the entity the display and the interaction ride, so the plate sits above the whole pet
 * however the model is scaled.
 *
 * <p>Parsed as MiniMessage and set through Adventure's {@code customName} rather than the legacy string
 * setter, so an operator can colour a name the way they colour everything else and the colour survives.
 *
 * <p>The status word is appended rather than carried on {@link RendererAppearance}. Appearance changes when
 * somebody renames a pet; status changes as the pet walks, so putting it there would make every step look
 * like a rename to the port. It rides the transform instead, and the renderer only rewrites the plate when
 * the word actually changes — a per-tick write would cost a metadata packet per pet to every nearby player
 * for a plate that mostly reads the same.
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
        apply(carrier, appearance, settings, null);
    }

    /**
     * Shows the plate with the pet's current status, or clears it.
     *
     * @param status what the pet is doing, or null to show the name alone
     */
    static void apply(
            Entity carrier,
            RendererAppearance appearance,
            PaperHeadRendererSettings settings,
            PetStatus status) {
        if (carrier == null) return;
        if (!settings.nameplates() || !appearance.named()) {
            carrier.customName(null);
            carrier.setCustomNameVisible(false);
            return;
        }
        carrier.customName(Messages.operator(text(appearance, settings, status)));
        carrier.setCustomNameVisible(true);
    }

    /**
     * The plate's full text, as MiniMessage.
     *
     * <p>Package-visible so a renderer can compare what it is about to write against what it wrote last and
     * skip the packet when nothing changed.
     *
     * <p>The status is appended as its raw MiniMessage rather than as a rendered component, because the
     * whole string is parsed once at the end — the pet's name is operator-authored MiniMessage too, and
     * parsing the two separately would mean a colour opened in the name could not close in the status.
     */
    static String text(
            RendererAppearance appearance, PaperHeadRendererSettings settings, PetStatus status) {
        if (!settings.nameplates() || !appearance.named()) return "";
        if (status == null || !settings.nameplateStatus()) return appearance.displayName();
        return appearance.displayName() + "  " + Messages.raw(statusKey(status));
    }

    private static MessageKey statusKey(PetStatus status) {
        return switch (status) {
            case RESTING -> MessageKey.GUI_PET_STATUS_RESTING;
            case IDLE -> MessageKey.GUI_PET_STATUS_IDLE;
            case FOLLOWING -> MessageKey.GUI_PET_STATUS_FOLLOWING;
            case DASHING -> MessageKey.GUI_PET_STATUS_DASHING;
        };
    }
}
