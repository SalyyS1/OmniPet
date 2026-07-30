package io.github.salyvn.omnipet.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class PaginationUtilsTest {
	@Test
	void calculatesPageCountsForEmptyExactAndPartialPages() {
		assertEquals(0, PaginationUtils.getPageCount(7, 0));
		assertEquals(1, PaginationUtils.getPageCount(7, 1));
		assertEquals(1, PaginationUtils.getPageCount(7, 7));
		assertEquals(2, PaginationUtils.getPageCount(7, 8));
		assertEquals(3, PaginationUtils.getPageCount(7, 21));
	}

	@Test
	void rejectsInvalidPaginationArguments() {
		assertThrows(IllegalArgumentException.class, () -> PaginationUtils.getPageCount(0, 1));
		assertThrows(IllegalArgumentException.class, () -> PaginationUtils.getPageCount(7, -1));
		assertThrows(IllegalArgumentException.class, () -> PaginationUtils.paginate(-1, 2, List.of(1, 2)));
		assertThrows(IllegalArgumentException.class, () -> PaginationUtils.paginate(0, 0, List.of(1, 2)));
	}

	@Test
	void paginatesAndPadsMissingEntries() {
		assertEquals(Arrays.asList(3, 4, null), PaginationUtils.paginate(1, 3, List.of(0, 1, 2, 3, 4)));
	}
}
