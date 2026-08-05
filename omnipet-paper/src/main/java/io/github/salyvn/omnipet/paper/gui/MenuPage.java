package io.github.salyvn.omnipet.paper.gui;

/**
 * One page of a fixed-size grid, with the page index clamped into range.
 *
 * <p>Shared because the same arithmetic was written out in more than one screen, including the
 * off-by-one-proof {@code (size + perPage - 1) / perPage} rounding and the clamp that keeps a stale page
 * index from showing an empty grid after the underlying list shrinks.
 *
 * <p>Cursor-paged screens do not use this. An opaque continuation cursor has no page count and no way
 * back, so presenting it as a page number would be a lie.
 */
public record MenuPage(int index, int pages, int firstItem, int itemCount) {
    /**
     * The page to draw.
     *
     * @param requested the page the viewer last asked for; clamped, so a shrunken list cannot leave them
     *                  stranded past the end
     */
    public static MenuPage of(int totalItems, int perPage, int requested) {
        if (perPage < 1) throw new IllegalArgumentException("a page must hold at least one item");
        int items = Math.max(0, totalItems);
        int pages = Math.max(1, (items + perPage - 1) / perPage);
        int index = Math.max(0, Math.min(requested, pages - 1));
        int first = index * perPage;
        return new MenuPage(index, pages, first, Math.max(0, Math.min(perPage, items - first)));
    }

    /** One past the last item on this page, for an exclusive-end loop or {@code subList}. */
    public int lastItemExclusive() {
        return firstItem + itemCount;
    }

    public boolean hasPrevious() {
        return index > 0;
    }

    public boolean hasNext() {
        return index < pages - 1;
    }

    /** Human-facing page position, counted from one. */
    public String label() {
        return (index + 1) + "/" + pages;
    }
}
