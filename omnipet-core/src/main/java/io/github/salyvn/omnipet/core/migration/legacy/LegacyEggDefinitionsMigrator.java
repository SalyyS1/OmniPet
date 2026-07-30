package io.github.salyvn.omnipet.core.migration.legacy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.domain.StableId;
import io.github.salyvn.omnipet.core.persistence.AtomicFileStore;
import io.github.salyvn.omnipet.core.persistence.YamlDocuments;

public final class LegacyEggDefinitionsMigrator {
    public static final int JOURNAL_SCHEMA_VERSION = 1;
    public static final String JOURNAL_KIND = "legacy-egg-definitions";

    private final AtomicFileStore fileStore;

    public LegacyEggDefinitionsMigrator() {
        this(new AtomicFileStore());
    }

    LegacyEggDefinitionsMigrator(AtomicFileStore fileStore) {
        this.fileStore = Objects.requireNonNull(fileStore, "file store is required");
    }

    public LegacyEggDefinitionsMigrationResult migrate(String legacyYaml) {
        Map<String, Object> source = YamlDocuments.readMap(legacyYaml);
        validateDefinitions(source);

        String semanticSha256 = sha256(RawNodeValues.semanticBytes(source));
        Map<String, Map<String, Object>> definitions = canonicalDefinitions(source);
        String payload = YamlDocuments.writeMap(journalPayload(semanticSha256, definitions));
        return new LegacyEggDefinitionsMigrationResult(
                JOURNAL_SCHEMA_VERSION,
                semanticSha256,
                definitions,
                payload,
                false);
    }

    public LegacyEggDefinitionsMigrationResult migrate(Path legacySource, Path journalDestination) throws IOException {
        Path source = Objects.requireNonNull(legacySource, "legacy source is required").toAbsolutePath().normalize();
        Path journal = Objects.requireNonNull(journalDestination, "journal destination is required")
                .toAbsolutePath()
                .normalize();
        rejectSourceAsJournal(source, journal);

        LegacyEggDefinitionsMigrationResult result = migrate(Files.readString(source, StandardCharsets.UTF_8));
        byte[] payload = result.journalPayload().getBytes(StandardCharsets.UTF_8);
        if (hasIdenticalPayload(journal, payload)) return result;

        fileStore.write(journal, payload);
        return result.withJournalWritten(true);
    }

    private static void validateDefinitions(Map<String, Object> source) {
        Set<String> foldedIds = new HashSet<>();
        source.forEach((id, rawDefinition) -> {
            String validId = StableId.requireValid(id);
            if (!foldedIds.add(StableId.folded(validId))) {
                throw new IllegalArgumentException("case-folded duplicate legacy egg ID: " + id);
            }
            if (!(rawDefinition instanceof Map<?, ?> definition)) {
                throw new IllegalArgumentException("legacy egg definition " + id + " must be a map");
            }
            validatePetReferences(id, definition.get("pets"));
        });
    }

    private static void validatePetReferences(String eggId, Object rawPets) {
        if (rawPets == null) return;
        if (!(rawPets instanceof List<?> pets)) {
            throw new IllegalArgumentException("legacy egg definition " + eggId + ".pets must be a list");
        }
        for (int index = 0; index < pets.size(); index++) {
            Object reference = pets.get(index);
            if (!(reference instanceof String petId)) {
                throw new IllegalArgumentException(
                        "legacy egg definition " + eggId + ".pets[" + index + "] must be a stable ID");
            }
            StableId.requireValid(petId);
        }
    }

    private static Map<String, Map<String, Object>> canonicalDefinitions(Map<String, Object> source) {
        LinkedHashMap<String, Map<String, Object>> definitions = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> definitions.put(entry.getKey(), canonicalMap(asMap(entry.getValue()))));
        return definitions;
    }

    private static Map<String, Object> journalPayload(
            String semanticSha256,
            Map<String, Map<String, Object>> definitions) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", JOURNAL_SCHEMA_VERSION);
        payload.put("kind", JOURNAL_KIND);
        payload.put("sourceSemanticSha256", semanticSha256);
        payload.put("eggDefinitions", definitions);
        return payload;
    }

    private static Map<String, Object> canonicalMap(Map<?, ?> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                .forEach(entry -> result.put(String.valueOf(entry.getKey()), canonicalValue(entry.getValue())));
        return result;
    }

    private static Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) return canonicalMap(map);
        if (value instanceof List<?> list) {
            ArrayList<Object> result = new ArrayList<>(list.size());
            list.forEach(item -> result.add(canonicalValue(item)));
            return result;
        }
        return value;
    }

    private static Map<?, ?> asMap(Object value) {
        return (Map<?, ?>) value;
    }

    private static boolean hasIdenticalPayload(Path destination, byte[] expected) throws IOException {
        return Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
                && Arrays.equals(Files.readAllBytes(destination), expected);
    }

    private static void rejectSourceAsJournal(Path source, Path journal) throws IOException {
        if (source.equals(journal)) throw new IllegalArgumentException("journal cannot overwrite the legacy source");
        if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)
                && Files.exists(journal, LinkOption.NOFOLLOW_LINKS)
                && Files.isSameFile(source, journal)) {
            throw new IllegalArgumentException("journal cannot overwrite the legacy source");
        }
    }

    private static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
