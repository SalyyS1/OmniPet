package io.github.salyvn.omnipet.core.studio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetDefinitionEnvelope;
import io.github.salyvn.omnipet.core.domain.RawNodeValues;
import io.github.salyvn.omnipet.core.persistence.PetDefinitionDraft;
import io.github.salyvn.omnipet.core.persistence.PetDefinitionArchiveReceipt;
import io.github.salyvn.omnipet.core.persistence.PetDefinitionDeleteReceipt;
import io.github.salyvn.omnipet.core.persistence.PetDefinitionRepository;
import io.github.salyvn.omnipet.core.persistence.PetDefinitionWriteReceipt;
import io.github.salyvn.omnipet.core.persistence.PetReferenceScanner;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshot;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotTransaction;

/** Shared staged write/swap boundary used by Studio saves and command reloads. */
public final class PetDefinitionStudioService {
    private static final int IDEMPOTENCY_HISTORY_LIMIT = 512;

    private final PetDefinitionRepository definitions;
    private final RegistrySnapshotRepository snapshots;
    private final RegistrySnapshotTransaction transaction;
    private final Consumer<RegistrySnapshot> activation;
    private final java.util.List<PetReferenceScanner> referenceScanners;
    private final Consumer<StudioAuditEntry> audit;
    private final ReentrantLock transactionLock = new ReentrantLock();
    private final LinkedHashMap<UUID, SaveResult> completedSaves = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, RegistrySnapshot> completedArchives = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, RegistrySnapshot> completedDeletes = new LinkedHashMap<>();

    public PetDefinitionStudioService(
            PetDefinitionRepository definitions,
            RegistrySnapshotRepository snapshots,
            Consumer<RegistrySnapshot> activation) {
        this(definitions, snapshots, activation, java.util.List.of(), ignored -> {});
    }

    public PetDefinitionStudioService(
            PetDefinitionRepository definitions,
            RegistrySnapshotRepository snapshots,
            Consumer<RegistrySnapshot> activation,
            java.util.List<PetReferenceScanner> referenceScanners) {
        this(definitions, snapshots, activation, referenceScanners, ignored -> {});
    }

    public PetDefinitionStudioService(
            PetDefinitionRepository definitions,
            RegistrySnapshotRepository snapshots,
            Consumer<RegistrySnapshot> activation,
            java.util.List<PetReferenceScanner> referenceScanners,
            Consumer<StudioAuditEntry> audit) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.transaction = new RegistrySnapshotTransaction(snapshots);
        this.activation = Objects.requireNonNull(activation, "activation");
        this.referenceScanners = java.util.List.copyOf(referenceScanners == null ? java.util.List.of() : referenceScanners);
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    public SaveResult save(StudioPetDraft draft, UUID idempotencyKey) throws IOException {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (draft.id() == null) throw new IllegalArgumentException("definition ID is required before save");
        validateFields(draft);

        transactionLock.lock();
        try {
            SaveResult completed = completedSaves.get(idempotencyKey);
            if (completed != null) return completed;

            RegistrySnapshot current = snapshots.current();
            if (current.generation() != draft.registryGeneration()) {
                throw new StudioConflictException("registry changed while this draft was open");
            }

            Optional<PetDefinitionEnvelope> existing = definitions.read(draft.id());
            validateBase(draft, existing);
            rejectFoldedCollision(draft, current);
            Map<String, PetDefinition> diskDefinitions = loadAllDefinitions();
            requireDiskMatchesRegistry(current, diskDefinitions);

            long nextRevision = draft.baseRevision() + 1;
            Map<String, Object> persistedRawNode = generatedRawNode(draft, nextRevision);
            PetDefinition persisted = new PetDefinition(
                    draft.id(), nextRevision, draft.tier(), draft.icon(), draft.display(), persistedRawNode);
            Map<String, PetDefinition> candidateDefinitions = new LinkedHashMap<>(diskDefinitions);
            candidateDefinitions.put(draft.id(), persisted);
            RegistrySnapshot staged = snapshots.stage(current.generation() + 1, candidateDefinitions);

            PetDefinition source = new PetDefinition(
                    draft.id(), draft.baseRevision(), draft.tier(), draft.icon(), draft.display(), persistedRawNode);
            PetDefinitionDraft repositoryDraft = new PetDefinitionDraft(source, draft.baseRevision());
            ReceiptHolder receipt = new ReceiptHolder();

            RegistrySnapshot activated;
            try {
                activated = transaction.commit(
                        staged,
                        () -> receipt.write(definitions, repositoryDraft, persisted),
                        receipt::rollback,
                        activation);
            } catch (UncheckedIOException failure) {
                throw failure.getCause();
            }

            SaveResult result = new SaveResult(receipt.persisted(), activated);
            remember(idempotencyKey, result);
            audit.accept(new StudioAuditEntry(Instant.now(), idempotencyKey, StudioAuditEntry.Operation.SAVE,
                    draft.id(), activated.generation(), true, "saved"));
            return result;
        } finally {
            transactionLock.unlock();
        }
    }

    public RegistrySnapshot reload() throws IOException {
        transactionLock.lock();
        try {
            Map<String, PetDefinition> loaded = loadAllDefinitions();
            RegistrySnapshot current = snapshots.current();
            RegistrySnapshot staged = snapshots.stage(current.generation() + 1, loaded);
            RegistrySnapshot result = transaction.commit(staged, () -> {}, () -> {}, activation);
            audit.accept(new StudioAuditEntry(Instant.now(), UUID.randomUUID(), StudioAuditEntry.Operation.RELOAD,
                    "*", result.generation(), true, "reloaded"));
            return result;
        } finally {
            transactionLock.unlock();
        }
    }

    public RegistrySnapshot archive(
            String id,
            long baseRevision,
            String baseSemanticHash,
            long registryGeneration,
            UUID idempotencyKey) throws IOException {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        transactionLock.lock();
        try {
            RegistrySnapshot completed = completedArchives.get(idempotencyKey);
            if (completed != null) return completed;
            RemovalContext removal = validateRemoval(id, baseRevision, baseSemanticHash, registryGeneration,
                    "archive confirmation");

            Map<String, PetDefinition> candidate = new LinkedHashMap<>(removal.diskDefinitions());
            candidate.remove(id);
            RegistrySnapshot staged = snapshots.stage(removal.current().generation() + 1, candidate);
            ArchiveHolder receipt = new ArchiveHolder();
            RegistrySnapshot activated;
            try {
                activated = transaction.commit(
                        staged,
                        () -> receipt.archive(definitions, id),
                        receipt::rollback,
                        activation);
            } catch (UncheckedIOException failure) {
                throw failure.getCause();
            }
            completedArchives.put(idempotencyKey, activated);
            audit.accept(new StudioAuditEntry(Instant.now(), idempotencyKey, StudioAuditEntry.Operation.ARCHIVE,
                    id, activated.generation(), true, "archived"));
            while (completedArchives.size() > IDEMPOTENCY_HISTORY_LIMIT) {
                completedArchives.remove(completedArchives.keySet().iterator().next());
            }
            return activated;
        } finally {
            transactionLock.unlock();
        }
    }

    public RegistrySnapshot hardDelete(
            String id,
            String typedConfirmation,
            long baseRevision,
            String baseSemanticHash,
            long registryGeneration,
            UUID idempotencyKey) throws IOException {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (!id.equals(typedConfirmation)) throw new StudioConflictException("hard-delete confirmation must match the exact definition ID");
        transactionLock.lock();
        try {
            RegistrySnapshot completed = completedDeletes.get(idempotencyKey);
            if (completed != null) return completed;
            RemovalContext removal = validateRemoval(id, baseRevision, baseSemanticHash, registryGeneration,
                    "hard-delete confirmation");
            Map<String, PetDefinition> candidate = new LinkedHashMap<>(removal.diskDefinitions());
            candidate.remove(id);
            RegistrySnapshot staged = snapshots.stage(removal.current().generation() + 1, candidate);
            DeleteHolder receipt = new DeleteHolder();
            RegistrySnapshot activated;
            try {
                activated = transaction.commit(staged, () -> receipt.delete(definitions, id), receipt::rollback, activation);
            } catch (UncheckedIOException failure) {
                throw failure.getCause();
            }
            completedDeletes.put(idempotencyKey, activated);
            audit.accept(new StudioAuditEntry(Instant.now(), idempotencyKey, StudioAuditEntry.Operation.HARD_DELETE,
                    id, activated.generation(), true, "hard deleted"));
            while (completedDeletes.size() > IDEMPOTENCY_HISTORY_LIMIT) {
                completedDeletes.remove(completedDeletes.keySet().iterator().next());
            }
            return activated;
        } finally {
            transactionLock.unlock();
        }
    }

    private RemovalContext validateRemoval(String id, long baseRevision, String baseSemanticHash,
                                           long registryGeneration, String operation) throws IOException {
        RegistrySnapshot current = snapshots.current();
        if (current.generation() != registryGeneration) {
            throw new StudioConflictException("registry changed while this " + operation + " was open");
        }
        PetDefinition definition = definitions.read(id)
                .orElseThrow(() -> new StudioConflictException("definition does not exist: " + id))
                .definition();
        if (definition.revision() != baseRevision) {
            throw new StudioConflictException("definition revision changed: " + id);
        }
        if (!StudioPetDraft.semanticHash(definition.rawNode()).equals(baseSemanticHash)) {
            throw new StudioConflictException("definition content changed: " + id);
        }
        Map<String, PetDefinition> diskDefinitions = loadAllDefinitions();
        requireDiskMatchesRegistry(current, diskDefinitions);
        Set<String> references = new java.util.LinkedHashSet<>(definitions.referenceScan(id));
        references.removeIf(reference -> reference.equalsIgnoreCase(id));
        for (PetReferenceScanner scanner : referenceScanners) references.addAll(scanner.references(id));
        if (!references.isEmpty()) {
            throw new StudioConflictException("definition is still referenced by: " + String.join(", ", references));
        }
        return new RemovalContext(current, diskDefinitions);
    }

    private static void validateBase(StudioPetDraft draft, Optional<PetDefinitionEnvelope> existing) {
        if (draft.mode() == StudioPetDraft.Mode.CREATE) {
            if (existing.isPresent()) throw new StudioConflictException("definition already exists: " + draft.id());
            if (draft.baseRevision() != 0 || !draft.baseSemanticHash().isEmpty()) {
                throw new StudioConflictException("create draft has an invalid base revision or hash");
            }
            return;
        }

        PetDefinition current = existing
                .orElseThrow(() -> new StudioConflictException("definition was removed: " + draft.id()))
                .definition();
        if (current.revision() != draft.baseRevision()) {
            throw new StudioConflictException("definition revision changed: " + draft.id());
        }
        String currentHash = StudioPetDraft.semanticHash(current.rawNode());
        if (!currentHash.equals(draft.baseSemanticHash())) {
            throw new StudioConflictException("definition content changed: " + draft.id());
        }
    }

    private static void validateFields(StudioPetDraft draft) {
        String source = draft.icon().source().toUpperCase(Locale.ROOT);
        if (!Set.of("TEXTURE_URL", "BASE64", "HEAD_CATALOG").contains(source)) {
            throw new IllegalArgumentException("unsupported icon.head.source: " + draft.icon().source());
        }
        if (draft.icon().value().equalsIgnoreCase("CHANGE_ME")) {
            throw new IllegalArgumentException("icon.head.value must be configured");
        }
        if (source.equals("TEXTURE_URL")) {
            URI uri;
            try { uri = URI.create(draft.icon().value()); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("icon texture URL is invalid", error); }
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                throw new IllegalArgumentException("icon texture URL must be an absolute HTTP(S) URL");
            }
        } else if (source.equals("BASE64")) {
            validateBase64Texture(draft.icon().value());
        }

        Set<String> statIds = new HashSet<>();
        draft.stats().forEach(stat -> {
            if (!statIds.add(StatLogicalIdentity.key(stat))) {
                throw new IllegalArgumentException("duplicate stat ID: " + stat.id());
            }
        });
        Set<String> rarityIds = new HashSet<>();
        double totalWeight = 0;
        for (RarityBand band : draft.rarityBands()) {
            if (!rarityIds.add(band.id().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("duplicate rarity band: " + band.id());
            }
            totalWeight += band.weight();
        }
        if (!draft.rarityBands().isEmpty() && totalWeight <= 0) {
            throw new IllegalArgumentException("rarity profile must have positive total weight");
        }
        Set<String> skillIds = new HashSet<>();
        draft.skills().forEach(skill -> {
            String key = (skill.provider() + ":" + skill.id()).toLowerCase(Locale.ROOT);
            if (!skillIds.add(key)) throw new IllegalArgumentException("duplicate skill reference: " + key);
        });
    }

    private static void validateBase64Texture(String encoded) {
        if (encoded.length() > 16_384) {
            throw new IllegalArgumentException("icon BASE64 texture is too large");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("icon BASE64 texture is invalid", error);
        }
        try {
            Map<String, Object> root = StrictJsonDocuments.readObject(new String(decoded, StandardCharsets.UTF_8));
            Map<String, Object> textures = requiredMap(root.get("textures"));
            Map<String, Object> skin = requiredMap(textures.get("SKIN"));
            Object rawUrl = skin.get("url");
            if (!(rawUrl instanceof String url) || url.isBlank()) {
                throw new IllegalArgumentException("texture URL is required");
            }
            URI uri = URI.create(url);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("texture URL must be absolute HTTP(S)");
            }
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("icon BASE64 texture payload is not a valid Minecraft texture object", error);
        }
    }

    private static Map<String, Object> requiredMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("texture node must be an object");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, nested) -> result.put(String.valueOf(key), nested));
        return result;
    }

    private static Map<String, Object> generatedRawNode(StudioPetDraft draft, long revision) {
        Map<String, Object> raw = RawNodeValues.mutableMap(draft.rawNode());
        raw.put("schemaVersion", PetDefinitionEnvelope.CURRENT_SCHEMA_VERSION);
        raw.put("definitionId", draft.id());
        Object revisionValue;
        if (revision <= Integer.MAX_VALUE) revisionValue = Integer.valueOf((int) revision);
        else revisionValue = Long.valueOf(revision);
        raw.put("revision", revisionValue);
        Map<String, Object> classification = mutableNested(raw.get("classification"));
        classification.put("tier", draft.tier().name());
        raw.put("classification", classification);
        Map<String, Object> icon = mutableNested(raw.get("icon"));
        Map<String, Object> head = mutableNested(icon.get("head"));
        head.put("source", draft.icon().source());
        head.put("value", draft.icon().value());
        icon.put("head", head);
        raw.put("icon", icon);
        Map<String, Object> display = mutableNested(raw.get("display"));
        display.put("provider", draft.display().provider().name());
        display.put("model", draft.display().model());
        raw.put("display", display);
        return RawNodeValues.immutableMap(raw);
    }

    private static Map<String, Object> mutableNested(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> result.put(String.valueOf(key), RawNodeValues.mutableCopy(nested)));
        }
        return result;
    }

    private static void rejectFoldedCollision(StudioPetDraft draft, RegistrySnapshot current) {
        if (draft.mode() != StudioPetDraft.Mode.CREATE) return;
        String folded = draft.id().toLowerCase(Locale.ROOT);
        current.definitions().keySet().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).equals(folded))
                .findFirst()
                .ifPresent(id -> {
                    throw new StudioConflictException("definition ID conflicts with existing ID: " + id);
                });
    }

    private Map<String, PetDefinition> loadAllDefinitions() throws IOException {
        Map<String, PetDefinition> loaded = new LinkedHashMap<>();
        Map<String, String> foldedIds = new LinkedHashMap<>();
        for (String id : definitions.list()) {
            PetDefinition definition = definitions.read(id)
                    .orElseThrow(() -> new IOException("definition disappeared during registry staging: " + id))
                    .definition();
            String folded = id.toLowerCase(Locale.ROOT);
            String duplicate = foldedIds.putIfAbsent(folded, id);
            if (duplicate != null) throw new IOException("case-folded duplicate definition IDs: " + duplicate + ", " + id);
            loaded.put(id, definition);
        }
        return loaded;
    }

    private static void requireDiskMatchesRegistry(RegistrySnapshot current, Map<String, PetDefinition> disk) {
        if (!current.definitions().keySet().equals(disk.keySet())) {
            throw new StudioConflictException("definition files changed outside the active registry; reload before saving");
        }
        for (Map.Entry<String, PetDefinition> entry : current.definitions().entrySet()) {
            PetDefinition live = entry.getValue();
            PetDefinition stored = disk.get(entry.getKey());
            boolean same = stored != null
                    && live.id().equals(stored.id())
                    && live.revision() == stored.revision()
                    && live.tier() == stored.tier()
                    && live.icon().equals(stored.icon())
                    && live.display().equals(stored.display())
                    && java.util.Arrays.equals(RawNodeValues.semanticBytes(live.rawNode()),
                            RawNodeValues.semanticBytes(stored.rawNode()));
            if (!same) {
                throw new StudioConflictException("definition files changed outside the active registry; reload before saving");
            }
        }
    }

    private void remember(UUID idempotencyKey, SaveResult result) {
        completedSaves.put(idempotencyKey, result);
        while (completedSaves.size() > IDEMPOTENCY_HISTORY_LIMIT) {
            completedSaves.remove(completedSaves.keySet().iterator().next());
        }
    }

    public record SaveResult(PetDefinitionEnvelope definition, RegistrySnapshot snapshot) {
        public SaveResult {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    private static final class ReceiptHolder {
        private PetDefinitionWriteReceipt receipt;

        private void write(
                PetDefinitionRepository definitions,
                PetDefinitionDraft draft,
                PetDefinition expected) {
            try {
                receipt = definitions.saveDraftWithRollback(draft);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            if (!receipt.persisted().definition().equals(expected)) {
                throw new IllegalStateException("repository persisted a different definition than the staged snapshot");
            }
        }

        private PetDefinitionEnvelope persisted() {
            if (receipt == null) throw new IllegalStateException("definition write did not complete");
            return receipt.persisted();
        }

        private void rollback() {
            if (receipt == null) return;
            try {
                receipt.rollback();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }

    private static final class ArchiveHolder {
        private PetDefinitionArchiveReceipt receipt;

        private void archive(PetDefinitionRepository definitions, String id) {
            try {
                receipt = definitions.archiveWithRollback(id);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        private void rollback() {
            if (receipt == null) return;
            try {
                receipt.rollback();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }

    private static final class DeleteHolder {
        private PetDefinitionDeleteReceipt receipt;

        private void delete(PetDefinitionRepository definitions, String id) {
            try {
                receipt = definitions.deleteWithRollback(id);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        private void rollback() {
            if (receipt == null) return;
            try {
                receipt.rollback();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }

    private record RemovalContext(RegistrySnapshot current, Map<String, PetDefinition> diskDefinitions) {}
}
