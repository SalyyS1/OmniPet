package io.github.salyvn.omnipet.utils;

public final class CapacityUtils {
	private CapacityUtils() {}

	public static int normalizeCachedCapacity(int cachedCapacity, int globalMaximum) {
		if (globalMaximum < 0) throw new IllegalArgumentException("globalMaximum must not be negative");
		return cachedCapacity >= 0 && cachedCapacity <= globalMaximum ? cachedCapacity : -1;
	}
}
