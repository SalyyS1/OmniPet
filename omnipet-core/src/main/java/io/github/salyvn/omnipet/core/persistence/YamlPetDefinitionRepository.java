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
import java.util.function.Consumer;

import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetDefinitionEnvelope;
import io.github.salyvn.omnipet.core.domain.StableId;
import io.github.salyvn.omnipet.core.migration.legacy.LegacyPetDefinitionReader;

public final class YamlPetDefinitionRepository implements PetDefinitionRepository {
    /**
     * The largest definition file this repository will read into memory. Generous next to a real
     * definition, which is a few kilobytes of YAML, and far below a heap problem.
     */
    private static final int MAX_DEFINITION_BYTES = 1024 * 1024;

    private final SafeRepositoryPaths paths;
    private final PetDefinitionYamlCodec codec;
    private final AtomicFileStore fileStore;
    private final LegacyPetDefinitionReader legacyReader;
    private final YamlPetDefinitionFiles definitionFiles;
    private final KeyedLockRegistry<String> locks = new KeyedLockRegistry<>();

    public YamlPetDefinitionRepository(Path root) {
        this(root, problem -> {});
    }

    /**
     * @param problems where unusable definition filenames are reported. A stray file is skipped rather
     *     than fatal, so without a sink the skip is silent and an operator wonders where their pet went.
     */
    public YamlPetDefinitionRepository(Path root, Consumer<String> problems) {
        this.paths = new SafeRepositoryPaths(root);
        this.codec = new PetDefinitionYamlCodec();
        this.fileStore = new AtomicFileStore();
        this.legacyReader = new LegacyPetDefinitionReader();
        this.definitionFiles = new YamlPetDefinitionFiles(paths, problems);
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
        return saveDraftWithRollback(draft).persisted();
    }

    @Override
    public PetDefinitionWriteReceipt saveDraftWithRollback(PetDefinitionDraft draft) throws IOException {
        if (draft == null) throw new IllegalArgumentException("draft is required");
        String id = draft.definition().id();
        return withIdLock(id, () -> {
            Optional<YamlPetDefinitionFiles.DefinitionFile> sourceFile = definitionFiles.find(id);
            Path sourcePath = sourceFile.map(YamlPetDefinitionFiles.DefinitionFile::path).orElse(null);
            byte[] previousContent = sourcePath == null ? null : Files.readAllBytes(sourcePath);
            Path previousBackupPath = sourcePath == null ? null : AtomicFileStore.backupPath(sourcePath);
            byte[] previousBackup = previousBackupPath != null
                    && Files.isRegularFile(previousBackupPath, LinkOption.NOFOLLOW_LINKS)
                    ? Files.readAllBytes(previousBackupPath)
                    : null;
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
            Path rollbackPath = sourcePath == null ? destination : sourcePath;
            return new PetDefinitionWriteReceipt() {
                @Override
                public PetDefinitionEnvelope persisted() {
                    return envelope;
                }

                @Override
                public void rollback() throws IOException {
                    withIdLock(id, () -> {
                        Optional<PetDefinitionEnvelope> current = readUnlocked(id);
                        long revision = current.map(value -> value.definition().revision()).orElse(0L);
                        if (revision != envelope.definition().revision()) {
                            throw new IOException("definition changed after Studio write; refusing rollback: " + id);
                        }
                        fileStore.restore(rollbackPath, previousContent, previousBackup);
                        return null;
                    });
                }
            };
        });
    }

    @Override
    public void archive(String id) throws IOException {
        archiveWithRollback(id);
    }

    @Override
    public PetDefinitionArchiveReceipt archiveWithRollback(String id) throws IOException {
        return withIdLock(id, () -> {
            Optional<YamlPetDefinitionFiles.DefinitionFile> existing = definitionFiles.find(id);
            if (existing.isEmpty()) throw new IOException("definition does not exist: " + id);
            Path source = existing.orElseThrow().path();
            Path archive;
            do {
                archive = paths.resolveArchive(id, UUID.randomUUID().toString());
            } while (Files.exists(archive, LinkOption.NOFOLLOW_LINKS));
            fileStore.moveWithoutReplacing(source, archive);
            Path archived = archive;
            return new PetDefinitionArchiveReceipt() {
                @Override
                public String definitionId() {
                    return id;
                }

                @Override
                public void rollback() throws IOException {
                    withIdLock(id, () -> {
                        if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                            throw new IOException("definition path was recreated; refusing archive rollback: " + id);
                        }
                        if (!Files.isRegularFile(archived, LinkOption.NOFOLLOW_LINKS)) {
                            throw new IOException("archived definition is unavailable for rollback: " + id);
                        }
                        fileStore.moveWithoutReplacing(archived, source);
                        return null;
                    });
                }
            };
        });
    }

    @Override
    public PetDefinitionDeleteReceipt deleteWithRollback(String id) throws IOException {
        return withIdLock(id, () -> {
            Optional<YamlPetDefinitionFiles.DefinitionFile> existing = definitionFiles.find(id);
            if (existing.isEmpty()) throw new IOException("definition does not exist: " + id);
            Path source = existing.orElseThrow().path();
            Path backup = AtomicFileStore.backupPath(source);
            byte[] sourceContent = Files.readAllBytes(source);
            byte[] backupContent = Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)
                    ? Files.readAllBytes(backup)
                    : null;
            try {
                fileStore.restore(source, null, null);
            } catch (IOException failure) {
                try {
                    fileStore.restore(source, sourceContent, backupContent);
                } catch (IOException restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
                throw failure;
            }
            return new PetDefinitionDeleteReceipt() {
                @Override
                public String definitionId() {
                    return id;
                }

                @Override
                public void rollback() throws IOException {
                    withIdLock(id, () -> {
                        if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)
                                || Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                            throw new IOException("definition path was recreated; refusing delete rollback: " + id);
                        }
                        fileStore.restore(source, sourceContent, backupContent);
                        return null;
                    });
                }
            };
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
        String yaml = BoundedFiles.readString(path, MAX_DEFINITION_BYTES);
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
        return locks.withLock(StableId.folded(id), operation::get);
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
