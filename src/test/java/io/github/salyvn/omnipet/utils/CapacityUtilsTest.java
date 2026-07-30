package io.github.salyvn.omnipet.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CapacityUtilsTest {
	@Test
	void keepsValuesInsideTheCurrentGlobalLimit() {
		assertEquals(0, CapacityUtils.normalizeCachedCapacity(0, 100));
		assertEquals(100, CapacityUtils.normalizeCachedCapacity(100, 100));
	}

	@Test
	void invalidatesPersistedValuesAfterAConfiguredLimitReduction() {
		assertEquals(-1, CapacityUtils.normalizeCachedCapacity(1000, 100));
		assertEquals(-1, CapacityUtils.normalizeCachedCapacity(-2, 100));
	}

	@Test
	void rejectsAnInvalidGlobalLimit() {
		assertThrows(IllegalArgumentException.class, () -> CapacityUtils.normalizeCachedCapacity(0, -1));
	}
}
