package io.github.salyvn.omnipet.paper.gui.player;

/**
 * What the vault is currently showing: sort order, filter, and page.
 *
 * <p>Does not survive a command round-trip, and that is accepted. Four navigation paths return to the
 * vault by replaying a command — the slot purchase cancel and success paths, the hub tile, and the
 * management screen's back button — and each rebuilds a fresh vault at {@link #initial()}. Rewiring
 * them to direct controller calls would mean touching the money-handling purchase flow for a display
 * concern, so returning from a pet's management screen resets the sort to favorites-first.
 */
public record VaultViewState(VaultSortOrder sort, VaultFilter filter, int page) {
    public VaultViewState {
        sort = sort == null ? VaultSortOrder.FAVORITES_FIRST : sort;
        filter = filter == null ? VaultFilter.ALL : filter;
        page = Math.max(1, page);
    }

    public static VaultViewState initial() {
        return new VaultViewState(VaultSortOrder.FAVORITES_FIRST, VaultFilter.ALL, 1);
    }

    public static VaultViewState page(int page) {
        return new VaultViewState(VaultSortOrder.FAVORITES_FIRST, VaultFilter.ALL, page);
    }

    public VaultViewState withPage(int next) {
        return new VaultViewState(sort, filter, next);
    }

    /** Advances the sort and returns to page 1, since the pet under the cursor has moved. */
    public VaultViewState cycleSort() {
        return new VaultViewState(sort.next(), filter, 1);
    }

    /** Advances the filter and returns to page 1, since the result set has changed size. */
    public VaultViewState cycleFilter() {
        return new VaultViewState(sort, filter.next(), 1);
    }

    /** True when the player has narrowed the view, which distinguishes "no matches" from "no pets". */
    public boolean filtered() {
        return filter != VaultFilter.ALL;
    }
}
