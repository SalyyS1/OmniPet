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
 * <p>The layout comes from {@code gui.pet.nameplate} in the message catalog rather than from code, so the
 * spacing, the ordering, and whether the level or status appear at all are the operator's to write.
 *
 * <p>The status word is not carried on {@link RendererAppearance}. Appearance changes when somebody renames
 * a pet; status changes as the pet walks, so putting it there would make every step look like a rename to
 * the port. It rides the transform instead, and the renderer only rewrites the plate when the word actually
 * changes — a per-tick write would cost a metadata packet per pet to every nearby player for a plate that
 * mostly reads the same.
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
     * <p>Substituted into an operator's template rather than concatenated, so the separator, the word
     * before the level, the ordering, and whether a part appears at all are all things an operator writes
     * in {@code messages.yml} instead of things baked in here.
     *
     * <p>The parts are spliced in as raw MiniMessage and the whole line is parsed once at the end, rather
     * than each part being parsed and inserted as a component. The pet's name is operator-authored
     * MiniMessage too, and parsing the parts separately would mean a colour opened in the template could
     * not close around the name.
     */
    static String text(
            RendererAppearance appearance, PaperHeadRendererSettings settings, PetStatus status) {
        if (!settings.nameplates() || !appearance.named()) return "";
        boolean withStatus = status != null && settings.nameplateStatus();
        String template = Messages.raw(
                withStatus ? MessageKey.GUI_PET_NAMEPLATE_STATUS : MessageKey.GUI_PET_NAMEPLATE);
        // The level segment first, because it decides whether its surrounding text survives at all.
        String rendered = appearance.level() == null
                ? OPTIONAL_SEGMENT.matcher(template).replaceAll("")
                : unwrapOptional(template).replace(LEVEL_PLACEHOLDER, String.valueOf(appearance.level()));
        return rendered
                .replace(NAME_PLACEHOLDER, appearance.displayName())
                .replace(STATUS_PLACEHOLDER, withStatus ? Messages.raw(statusKey(status)) : "")
                .strip();
    }

    /** The placeholders an operator may write into a nameplate template. */
    private static final String NAME_PLACEHOLDER = "<name>";
    private static final String LEVEL_PLACEHOLDER = "<level>";
    private static final String STATUS_PLACEHOLDER = "<status>";

    /**
     * A part of the template that disappears whole when its value is unknown, written {@code [...]}.
     *
     * <p>For the level, which a malformed progression node can leave unreadable. Guessing the extent of the
     * text belonging to a placeholder does not work: stripping {@code <level>} out of
     * {@code Lv.<level>} leaves a bare {@code Lv.} behind, which reads as a bug rather than as an absent
     * level. So the operator marks the extent instead, and the whole bracketed run goes together.
     *
     * <p>Square brackets because MiniMessage gives them no meaning, so no existing message can collide with
     * them. Not nestable, and deliberately so — one optional run is all this needs.
     */
    private static final java.util.regex.Pattern OPTIONAL_SEGMENT =
            java.util.regex.Pattern.compile("\\[[^\\[\\]]*\\]");

    /** Drops the brackets around an optional part, keeping what is inside. */
    private static String unwrapOptional(String template) {
        return template.replace("[", "").replace("]", "");
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
