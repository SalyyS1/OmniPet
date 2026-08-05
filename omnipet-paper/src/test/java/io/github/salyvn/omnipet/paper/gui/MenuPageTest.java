package io.github.salyvn.omnipet.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * One page of a fixed-size grid.
 *
 * <p>Shared after the same arithmetic appeared in more than one screen. The cases worth pinning are the
 * ones a hand-written copy gets wrong: the rounding at an exact multiple of the page size, and a page
 * index left over from before the list shrank.
 */
class MenuPageTest {
    @Test
    void anEmptyListIsStillOnePageRatherThanZero() {
        MenuPage page = MenuPage.of(0, 45, 0);

        assertEquals(1, page.pages(), "a screen showing nothing still shows page 1 of 1");
        assertEquals(0, page.itemCount());
        assertEquals(0, page.firstItem());
        assertFalse(page.hasPrevious());
        assertFalse(page.hasNext());
        assertEquals("1/1", page.label());
    }

    @Test
    void anExactMultipleDoesNotProduceATrailingEmptyPage() {
        // The rounding an off-by-one gets wrong: 45 items is one full page, not one page plus an empty one.
        assertEquals(1, MenuPage.of(45, 45, 0).pages());
        assertEquals(2, MenuPage.of(46, 45, 0).pages());
        assertEquals(2, MenuPage.of(90, 45, 0).pages());
        assertEquals(3, MenuPage.of(91, 45, 0).pages());
    }

    @Test
    void aStalePageIndexIsClampedInsteadOfShowingAnEmptyGrid() {
        // Archiving definitions can shrink the list under a viewer who is on a later page. Without the
        // clamp they would be left looking at nothing with no way to tell why.
        MenuPage page = MenuPage.of(10, 45, 7);

        assertEquals(0, page.index());
        assertEquals(10, page.itemCount());
        assertEquals("1/1", page.label());
    }

    @Test
    void aNegativePageIsTreatedAsTheFirst() {
        assertEquals(0, MenuPage.of(100, 45, -3).index());
    }

    @Test
    void thePartialLastPageReportsOnlyTheItemsItHas() {
        MenuPage last = MenuPage.of(100, 45, 2);

        assertEquals(2, last.index());
        assertEquals(90, last.firstItem());
        assertEquals(10, last.itemCount());
        assertEquals(100, last.lastItemExclusive());
        assertTrue(last.hasPrevious());
        assertFalse(last.hasNext(), "the last page must not offer a next page");
    }

    @Test
    void aMiddlePageOffersBothDirections() {
        MenuPage middle = MenuPage.of(100, 45, 1);

        assertEquals(45, middle.firstItem());
        assertEquals(45, middle.itemCount());
        assertTrue(middle.hasPrevious());
        assertTrue(middle.hasNext());
        assertEquals("2/3", middle.label());
    }

    @Test
    void aNegativeTotalCannotProduceANegativeItemCount() {
        MenuPage page = MenuPage.of(-5, 45, 0);

        assertEquals(0, page.itemCount());
        assertEquals(1, page.pages());
    }

    @Test
    void aPageMustHoldAtLeastOneItem() {
        assertThrows(IllegalArgumentException.class, () -> MenuPage.of(10, 0, 0));
    }
}
