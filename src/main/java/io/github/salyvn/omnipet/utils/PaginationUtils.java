package io.github.salyvn.omnipet.utils;

import java.util.ArrayList;
import java.util.List;

public class PaginationUtils {
	public static int getPageCount(int perPage, int count) {
		if (perPage <= 0) throw new IllegalArgumentException("perPage must be positive");
		if (count < 0) throw new IllegalArgumentException("count must not be negative");
		if (count == 0) return 0;
		return 1 + (count - 1) / perPage;
	}

	public static <T> List<T> paginate(int page, int perPage, List<T> all) {
		if (page < 0) throw new IllegalArgumentException("page must not be negative");
		if (perPage <= 0) throw new IllegalArgumentException("perPage must be positive");
		long from = (long) page * perPage;
		List<T> collected = new ArrayList<>();
		for (int i = 0; i < perPage; i++) {
			long index = from + i;
			collected.add(index < all.size() ? all.get((int) index) : null);
		}
		return collected;
	}
}
