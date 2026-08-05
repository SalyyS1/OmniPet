package io.github.salyvn.omnipet.paper.text;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Translations shipped inside the jar.
 *
 * <p>Copied out to {@code lang/<locale>.yml} on first start so an operator can edit them, and never
 * overwritten afterwards — the same rule {@code messages.yml} and the egg catalog already follow, so an
 * upgrade cannot discard someone's corrections.
 *
 * <p>English is absent by design: it is the {@link MessageKey} defaults, and shipping a second copy
 * would create exactly the drift the generated {@code messages.yml} exists to avoid.
 */
public final class BundledLanguagePacks {
    private static final List<String> LOCALES = List.of("vi");

    private BundledLanguagePacks() {}

    /** The locales this build carries a translation for. */
    public static List<String> available() {
        return LOCALES;
    }

    public static boolean bundles(String locale) {
        return locale != null && LOCALES.contains(locale.trim().toLowerCase(Locale.ROOT));
    }

    /** The pack's YAML, or null when this build does not carry that locale. */
    public static String read(String locale) throws IOException {
        if (!bundles(locale)) return null;
        String resource = "lang/" + locale.trim().toLowerCase(Locale.ROOT) + ".yml";
        try (InputStream input = BundledLanguagePacks.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) return null;
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
