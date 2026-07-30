package io.github.salyvn.omnipet.utils;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.serialization.DataResult;

public class ParseUtils {
	private static final Pattern DURATION_REGEX = Pattern.compile("^\\s*(?:(\\d+)[Ww])?\\s*(?:(\\d+)[Dd])?\\s*(?:(\\d+)[Hh])?\\s*(?:(\\d+)[Mm])?\\s*(?:(\\d+)[Ss])?\\s*$");

	public static DataResult<Duration> tryParseDuration(String text) {
		if (text == null) return DataResult.error(() -> "Invalid duration text: null");
		Matcher matcher = DURATION_REGEX.matcher(text);
		if (!matcher.matches() || matcher.group(1) == null && matcher.group(2) == null && matcher.group(3) == null
				&& matcher.group(4) == null && matcher.group(5) == null) {
			return DataResult.error(() -> "Invalid duration text: %s".formatted(text));
		}

		try {
			long weeks = matcher.group(1) != null ? Long.parseLong(matcher.group(1)) : 0L;
			long days = matcher.group(2) != null ? Long.parseLong(matcher.group(2)) : 0L;
			long hours = matcher.group(3) != null ? Long.parseLong(matcher.group(3)) : 0L;
			long minutes = matcher.group(4) != null ? Long.parseLong(matcher.group(4)) : 0L;
			long seconds = matcher.group(5) != null ? Long.parseLong(matcher.group(5)) : 0L;
			long v = Math.multiplyExact(weeks, 7L);
			v = Math.addExact(v, days);
			v = Math.multiplyExact(v, 24L);
			v = Math.addExact(v, hours);
			v = Math.multiplyExact(v, 60L);
			v = Math.addExact(v, minutes);
			v = Math.multiplyExact(v, 60L);
			v = Math.addExact(v, seconds);
			return DataResult.success(Duration.ofSeconds(v));
		} catch (ArithmeticException | NumberFormatException e) {
			return DataResult.error(() -> "Duration is out of range: %s".formatted(text));
		}
	}

	public static Duration parseDuration(String text) {
		return tryParseDuration(text).getOrThrow();
	}

	public static String toString(Duration duration) {
		long seconds = duration.getSeconds();
		long weeks = seconds / (60 * 60 * 24 * 7); seconds -= weeks * (60 * 60 * 24 * 7);
		long days = seconds / (60 * 60 * 24); seconds -= days * (60 * 60 * 24);
		long hours = seconds / (60 * 60); seconds -= hours * (60 * 60);
		long minutes = seconds / 60; seconds -= minutes * 60;
		StringBuilder builder = new StringBuilder();
		if (weeks != 0L) builder.append(weeks).append('w');
		if (days != 0L) builder.append(days).append('d');
		if (hours != 0L) builder.append(hours).append('h');
		if (minutes != 0L) builder.append(minutes).append('m');
		if (builder.isEmpty() || seconds != 0L) builder.append(seconds).append('s');
		return builder.toString();
	}
}
