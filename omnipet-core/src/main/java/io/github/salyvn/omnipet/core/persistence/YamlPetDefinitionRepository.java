package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetDefinitionEnvelope;
import io.github.salyvn.omnipet.core.domain.StableId;
import io.github.salyvn.omnipet.core.migration.legacy.LegacyPetDefinitionReader;

public final class YamlPetDefinitionRepository implements PetDefinitionRepository {
    private final SafeRepositoryPaths paths;
    private final PetDefinitionYamlCodec codec;
    private final AtomicFileStore fileStore;
    private final LegacyPetDefinitionReader legacyReader;
    private final YamlPetDefinitionFiles definitionFiles;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public YamlPetDefinitionRepository(Path root) {
        this.paths = new SafeRepositoryPaths(root);
        this.codec = new PetDefinitionYamlCodec();
        this.fileStore = new AtomicFileStore();
        this.legacyReader = new LegacyPetDefinitionReader();
        this.definitionFiles = new YamlPetDefinitionFiles(paths);
    }

    @Override
    public Optional<PetDefinitionEnvelope> read(String id) throws IOException {
        return withIdLock(id, () -> readUnlocked(id));
    }

    @Override
    public List<String> list() throws IOException {
        return definitionFiles.listIds();
    }

    @Override
    public PetDefinitionEnvelope saveDraft(PetDefinitionDraft draft) throws IOException {
        if (draft == null) throw new IllegalArgumentException("draft is required");
        String id = draft.definition().id();
        return withIdLock(id, () -> {
            Optional<PetDefinitionEnvelope> existing = readUnlocked(id);
            long currentRevision = existing.map(value -> value.definition().revision()).orElse(0L);
            if (currentRevision != draft.expectedRevision()) {
                throw new StaleRevisionException(draft.expectedRevision(), currentRevision);
            }
            long nextRevision = currentRevision + 1;
            PetDefinition source = draft.definition();
            PetDefinition persisted = new PetDefinition(
                    source.id(), nextRevision, source.tier(), source.icon(), source.display(), source.rawNode());
            PetDefinitionEnvelope envelope = new PetDefinitionEnvelope(PetDefinitionEnvelope.CURRENT_SCHEMA_VERSION, persisted);
            Optional<YamlPetDefinitionFiles.DefinitionFile> existingFile = definitionFiles.find(id);
            Path destination = existingFile.isPresent()
                    ? existingFile.orElseThrow().path()
                    : definitionFiles.defaultPath(id);
            fileStore.write(destination, codec.encode(envelope).getBytes(StandardCharsets.UTF_8));
            return envelope;
        });
    }

    @Override
    public void archive(String id) throws IOException {
        withIdLock(id, () -> {
            Optional<YamlPetDefinitionFiles.DefinitionFile> existing = definitionFiles.find(id);
            if (existing.isEmpty()) return null;
            Path archive;
            do {
                archive = paths.resolveArchive(id, UUID.randomUUID().toString());
            } while (Files.exists(archive, LinkOption.NOFOLLOW_LINKS));
            fileStore.moveWithoutReplacing(existing.orElseThrow().path(), archive);
            return null;
        });
    }

    @Override
    public Set<String> referenceScan(String id) throws IOException {
        String expected = StableId.requireValid(id);
        LinkedHashSet<String> references = new LinkedHashSet<>();
        for (String candidate : list()) {
            Optional<PetDefinitionEnvelope> definition = read(candidate);
            if (definition.isPresent() && containsString(definition.orElseThrow().definition().rawNode(), expected)) {
                references.add(candidate);
            }
        }
        return Set.copyOf(references);
    }

    private Optional<PetDefinitionEnvelope> readUnlocked(String id) throws IOException {
        Optional<YamlPetDefinitionFiles.DefinitionFile> existing = definitionFiles.find(id);
        if (existing.isEmpty()) return Optional.empty();
        Path path = existing.orElseThrow().path();
        String yaml = Files.readString(path, StandardCharsets.UTF_8);
        Map<String, Object> raw = YamlDocuments.readMap(yaml);
        int schemaVersion = schemaVersion(raw.getOrDefault("schemaVersion", 1));
        if (schemaVersion < 1 || schemaVersion > PetDefinitionEnvelope.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported pet definition schema version: " + schemaVersion);
        }

        PetDefinitionEnvelope envelope = schemaVersion == 1 && raw.get("general") instanceof Map<?, ?>
                ? legacyReader.read(id, yaml)
                : codec.decode(id, yaml);
        if (schemaVersion < PetDefinitionEnvelope.CURRENT_SCHEMA_VERSION) {
            fileStore.write(path, codec.encode(envelope).getBytes(StandardCharsets.UTF_8));
        }
        return Optional.of(envelope);
    }

    private <T> T withIdLock(String id, IoSupplier<T> operation) throws IOException {
        ReentrantLock lock = locks.computeIfAbsent(StableId.folded(id), ignored -> new ReentrantLock());
        lock.lock();
        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }

    private static int schemaVersion(Object value) {
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException("schemaVersion must be an integer");
        }
        return number.intValue();
    }

    private static boolean containsString(Object value, String expected) {
        if (expected.equals(value)) return true;
        if (value instanceof Map<?, ?> map) return map.values().stream().anyMatch(nested -> containsString(nested, expected));
        if (value instanceof List<?> list) return list.stream().anyMatch(nested -> containsString(nested, expected));
        return false;
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
