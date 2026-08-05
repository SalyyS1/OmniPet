package io.github.salyvn.omnipet.paper.gui;

import java.util.Objects;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * The single colour grammar every OmniPet menu follows.
 *
 * <p>Colour previously carried no consistent meaning: every lore line was flat gray and titles were
 * uniformly gold, so a player could not tell an affordable option from a blocked one without reading.
 * These constants are the whole vocabulary; renderers pick a role, not a colour.
 */
public final class GuiColors {
    /** Item titles and neutral headings. */
    public static final TextColor TITLE = TextColor.color(0xFFD700);

    /** Positive, active, affordable. */
    public static final NamedTextColor POSITIVE = NamedTextColor.GREEN;

    /** Warning, pending, overflow — actionable but not an error. */
    public static final NamedTextColor WARNING = NamedTextColor.YELLOW;

    /** Blocked, locked, unaffordable. */
    public static final NamedTextColor BLOCKED = NamedTextColor.RED;

    /**
     * An irreversible action the player can take, such as releasing a pet.
     *
     * <p>Shares red with {@link #BLOCKED} deliberately — red means stop and read either way — but is a
     * separate role because the two are opposites: blocked means the click will not work, destructive
     * means it will and cannot be undone. A renderer naming the wrong one still looks right today, so the
     * distinction has to live in the name.
     */
    public static final NamedTextColor DESTRUCTIVE = NamedTextColor.RED;

    /** Unavailable and not worth reading — a control greyed out rather than refused. */
    public static final NamedTextColor DISABLED = NamedTextColor.DARK_GRAY;

    /** Static descriptive lore. */
    public static final NamedTextColor DESCRIPTION = NamedTextColor.GRAY;

    /** Dynamic values inside lore, so numbers stand out from the words around them. */
    public static final NamedTextColor VALUE = NamedTextColor.WHITE;

    /** Section headings inside a lore block. */
    public static final NamedTextColor SECTION = NamedTextColor.DARK_GRAY;

    /** Informational, non-actionable accent — used for identity rows. */
    public static final NamedTextColor ACCENT = NamedTextColor.AQUA;

    private GuiColors() {}

    /** Chooses between the positive and blocked colours. */
    public static NamedTextColor availability(boolean available) {
        return available ? POSITIVE : BLOCKED;
    }

    /** Chooses between the positive and warning colours. */
    public static NamedTextColor health(boolean healthy) {
        return healthy ? POSITIVE : WARNING;
    }

    /** Requires a non-null colour, so a renderer cannot silently fall back to vanilla white. */
    public static NamedTextColor require(NamedTextColor color) {
        return Objects.requireNonNull(color, "color");
    }
}
