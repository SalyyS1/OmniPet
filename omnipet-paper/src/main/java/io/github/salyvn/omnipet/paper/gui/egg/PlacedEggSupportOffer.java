package io.github.salyvn.omnipet.paper.gui.egg;

import java.util.Objects;

/**
 * What one support item in a player's hand would do to the egg they are looking at.
 *
 * <p>Pure and Bukkit-free, so the outcome of every hand state is unit-testable: an empty hand, an ordinary
 * item, a reducer worth less than the time left, a reducer worth more, and an instant-hatch. The renderer
 * turns this into a button and the controller into a decision; neither re-derives it.
 *
 * <p>{@code effectMillis} is the item's own figure, not the time that would actually be credited. A reducer
 * worth an hour against forty minutes of incubation still reports an hour, and {@link #finishes()} says the
 * egg would reach ready. Showing the clamped figure would tell a player their item is smaller than it is.
 */
public record PlacedEggSupportOffer(Kind kind, long effectMillis, long remainingMillis) {
    public enum Kind {
        /** Nothing in that hand. */
        EMPTY,
        /** Something in that hand, but not an OmniPet support item. */
        UNSUPPORTED,
        /** A reducer: credits {@code effectMillis} towards the countdown. */
        REDUCER,
        /** An instant-hatch: finishes the incubation outright. */
        INSTANT
    }

    public PlacedEggSupportOffer {
        Objects.requireNonNull(kind, "support offer kind");
        if (effectMillis < 0) throw new IllegalArgumentException("effect cannot be negative");
        if (remainingMillis < 0) throw new IllegalArgumentException("remaining time cannot be negative");
        if (kind == Kind.REDUCER && effectMillis <= 0) {
            throw new IllegalArgumentException("a reducer must carry a positive effect");
        }
        if (kind != Kind.REDUCER && effectMillis != 0) {
            throw new IllegalArgumentException("only a reducer carries an effect");
        }
    }

    public static PlacedEggSupportOffer empty(long remainingMillis) {
        return new PlacedEggSupportOffer(Kind.EMPTY, 0, remainingMillis);
    }

    public static PlacedEggSupportOffer unsupported(long remainingMillis) {
        return new PlacedEggSupportOffer(Kind.UNSUPPORTED, 0, remainingMillis);
    }

    public static PlacedEggSupportOffer reducer(long effectMillis, long remainingMillis) {
        return new PlacedEggSupportOffer(Kind.REDUCER, effectMillis, remainingMillis);
    }

    public static PlacedEggSupportOffer instant(long remainingMillis) {
        return new PlacedEggSupportOffer(Kind.INSTANT, 0, remainingMillis);
    }

    /** True when spending this item would do something. */
    public boolean usable() {
        return (kind == Kind.REDUCER || kind == Kind.INSTANT) && remainingMillis > 0;
    }

    /** True when spending it would take the egg all the way to ready. */
    public boolean finishes() {
        if (!usable()) return false;
        return kind == Kind.INSTANT || effectMillis >= remainingMillis;
    }

    /**
     * The time this item would actually take off the clock.
     *
     * <p>Clamped, unlike {@link #effectMillis()}: this is what changes, and an egg cannot go below zero.
     */
    public long creditedMillis() {
        if (!usable()) return 0;
        return kind == Kind.INSTANT ? remainingMillis : Math.min(effectMillis, remainingMillis);
    }

    /** What the countdown would read afterwards. */
    public long remainingAfter() {
        return remainingMillis - creditedMillis();
    }
}
