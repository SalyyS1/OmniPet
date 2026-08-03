package io.github.salyvn.omnipet.paper.studio.bukkit;

import java.util.Locale;
import java.util.Objects;

import io.github.salyvn.omnipet.core.studio.StatModifierType;

/**
 * Human-readable names and one-line explanations for the three stat modifier types.
 *
 * <p>The stat prompt previously demanded a bare {@code FLAT|RELATIVE|ADDITIVE_MULTIPLIER} token with
 * no hint of what any of them meant, which is why operators reported that neither words nor numbers
 * worked. Each modifier now carries a label and a concrete example.
 */
final class StatModifierPresentation {
    private StatModifierPresentation() {}

    /** Title-case label, e.g. {@code Additive multiplier}. */
    static String label(StatModifierType modifier) {
        Objects.requireNonNull(modifier, "modifier");
        String words = modifier.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** What the modifier does, phrased for someone who has never seen the enum. */
    static String explanation(StatModifierType modifier) {
        return switch (modifier) {
            case FLAT -> "Adds a fixed amount to the stat";
            case RELATIVE -> "Adds a percentage of the base value";
            case ADDITIVE_MULTIPLIER -> "Stacks additively with other multipliers";
        };
    }

    /** A concrete example of the effect, so the choice is unambiguous. */
    static String example(StatModifierType modifier) {
        return switch (modifier) {
            case FLAT -> "10 becomes +10 attack damage";
            case RELATIVE -> "10 becomes +10%";
            case ADDITIVE_MULTIPLIER -> "0.1 adds +0.1 to the total multiplier";
        };
    }

    /** The value range hint shown in the follow-up chat prompt. */
    static String rangeHint(StatModifierType modifier) {
        return switch (modifier) {
            case FLAT -> "whole or decimal numbers, minimum first";
            case RELATIVE -> "percentages as plain numbers, so 10 means 10%";
            case ADDITIVE_MULTIPLIER -> "decimal multipliers, so 0.1 means +10%";
        };
    }

    /** The comma-joined labels used in stat lore, replacing {@code Set.toString()}. */
    static String labels(java.util.Collection<StatModifierType> modifiers) {
        Objects.requireNonNull(modifiers, "modifiers");
        if (modifiers.isEmpty()) return "none";
        StringBuilder joined = new StringBuilder();
        for (StatModifierType modifier : modifiers) {
            if (!joined.isEmpty()) joined.append(", ");
            joined.append(label(modifier));
        }
        return joined.toString();
    }
}
