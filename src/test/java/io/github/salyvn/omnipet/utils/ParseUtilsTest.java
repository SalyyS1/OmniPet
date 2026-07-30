package io.github.salyvn.omnipet.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ParseUtilsTest {
	@Test
	void parsesCombinedDurationUnits() {
		assertEquals(Duration.ofSeconds(788645), ParseUtils.parseDuration("1w 2d 3h 4m 5s"));
		assertEquals(Duration.ZERO, ParseUtils.parseDuration("0s"));
	}

	@Test
	void rejectsBlankMalformedAndOverflowingDurations() {
		assertThrows(IllegalStateException.class, () -> ParseUtils.parseDuration(""));
		assertThrows(IllegalStateException.class, () -> ParseUtils.parseDuration("   "));
		assertThrows(IllegalStateException.class, () -> ParseUtils.parseDuration("1 hour"));
		assertThrows(IllegalStateException.class, () -> ParseUtils.parseDuration("999999999999999999999s"));
	}

	@Test
	void formatsDurationsUsingStableConfigSyntax() {
		assertEquals("1w2d3h4m5s", ParseUtils.toString(Duration.ofSeconds(788645)));
		assertEquals("0s", ParseUtils.toString(Duration.ZERO));
	}
}
