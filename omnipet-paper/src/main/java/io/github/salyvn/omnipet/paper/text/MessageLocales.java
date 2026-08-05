package io.github.salyvn.omnipet.paper.text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Chooses which text file to load: a language pack, or the operator's own {@code messages.yml}.
 *
 * <p>Layered rather than replacing what was there. {@code messages.yml} is generated on first start and
 * many servers will already have one edited in place; making it disappear in favour of a {@code lang/}
 * directory would silently discard that work. So it stays the operator's file and always wins, while a
 * language pack supplies a translated baseline underneath.
 *
 * <p>Resolution, highest priority first:
 * <ol>
 *   <li>{@code messages.yml} — whatever the operator wrote
 *   <li>{@code lang/<locale>.yml} — the shipped or operator-supplied pack for the configured locale
 *   <li>the {@link MessageKey} defaults, which are English
 * </ol>
 *
 * <p>A key missing from one layer falls through to the next, so a half-finished translation shows
 * translated lines where it has them and English everywhere else rather than failing to load.
 */
public final class MessageLocales {
    /** The locale used when none is configured. Its text is the built-in defaults. */
    public static final String DEFAULT_LOCALE = "en";

    private MessageLocales() {}

    /**
     * Builds the catalog for one locale.
     *
     * @param dataRoot the plugin's data folder
     * @param locale   the configured locale tag; blank means {@link #DEFAULT_LOCALE}
     * @param warnings receives one line per ignored entry, per layer
     */
    public static MessageCatalog load(Path dataRoot, String locale, Consumer<String> warnings)
            throws IOException {
        Objects.requireNonNull(dataRoot, "data root");
        Objects.requireNonNull(warnings, "warning sink");
        MessageCatalog language = languagePack(dataRoot, normalize(locale), warnings);
        MessageCatalog operator = MessageCatalog.load(dataRoot.resolve("messages.yml"), warnings);
        return operator.withFallback(language);
    }

    /** The file a locale reads from, whether or not it exists. */
    public static Path packFile(Path dataRoot, String locale) {
        return dataRoot.resolve("lang").resolve(normalize(locale) + ".yml");
    }

    /**
     * Writes {@code lang/<locale>.yml} for every bundled translation that is not already on disk.
     *
     * <p>Never overwrites: an operator who edited a shipped pack keeps their edits across upgrades, the
     * same rule {@code messages.yml} and the egg catalog already follow.
     */
    public static void writeBundledPacks(Path dataRoot, Consumer<String> warnings) throws IOException {
        Objects.requireNonNull(dataRoot, "data root");
        for (String locale : BundledLanguagePacks.available()) {
            Path file = packFile(dataRoot, locale);
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) continue;
            String document = BundledLanguagePacks.read(locale);
            if (document == null) continue;
            Files.createDirectories(file.getParent());
            Files.writeString(file, document, StandardCharsets.UTF_8);
            warnings.accept("wrote language pack lang/" + locale + ".yml");
        }
    }

    /** The pack for a locale, or an all-defaults catalog when there is none. */
    private static MessageCatalog languagePack(Path dataRoot, String locale, Consumer<String> warnings)
            throws IOException {
        if (locale.equals(DEFAULT_LOCALE)) return MessageCatalog.defaults();
        Path file = packFile(dataRoot, locale);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            // Not an error: an operator naming a locale nobody has translated yet gets English, which
            // is more useful than refusing to start.
            warnings.accept("no language pack at lang/" + locale + ".yml; using the built-in English text");
            return MessageCatalog.defaults();
        }
        return MessageCatalog.load(file, message -> warnings.accept("lang/" + locale + ".yml: " + message));
    }

    private static String normalize(String locale) {
        if (locale == null || locale.isBlank()) return DEFAULT_LOCALE;
        // Lowercased and dash-separated, so "vi-VN", "vi_VN", and "VI" all name one file.
        return locale.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
