package io.github.salyvn.omnipet.core.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.salyvn.omnipet.core.domain.DisplayDefinition;
import io.github.salyvn.omnipet.core.domain.HeadIcon;
import io.github.salyvn.omnipet.core.domain.PetTier;
import io.github.salyvn.omnipet.core.persistence.InMemoryRegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.persistence.YamlPetDefinitionRepository;

class PetDefinitionStudioAuditTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void successfulLogicalSaveWritesOneAuditRecord() throws IOException {
        RecordingAuditSink audit = new RecordingAuditSink();
        Fixture fixture = fixture(audit, ignored -> {});
        StudioPetDraft draft = createDraft(fixture.registry());
        UUID key = UUID.randomUUID();

        var first = fixture.service().save(draft, key);
        var retry = fixture.service().save(draft, key);

        assertEquals(first, retry);
        assertEquals(1, audit.entries.size());
        StudioAuditEntry entry = audit.entries.getFirst();
        assertEquals(key, entry.idempotencyKey());
        assertEquals(StudioAuditEntry.Operation.SAVE, entry.operation());
        assertEquals("fox", entry.definitionId());
        assertEquals(first.snapshot().generation(), entry.registryGeneration());
        assertEquals(true, entry.success());
    }

    @Test
    void auditFailureRollsBackDefinitionAndRegistryAndAllowsRetry() throws IOException {
        RecordingAuditSink audit = new RecordingAuditSink();
        audit.failAppend = true;
        Fixture fixture = fixture(audit, ignored -> {});
        StudioPetDraft draft = createDraft(fixture.registry());
        UUID key = UUID.randomUUID();

        assertThrows(IOException.class, () -> fixture.service().save(draft, key));

        assertEquals(false, fixture.definitions().read("fox").isPresent());
        assertEquals(0, fixture.registry().current().generation());
        assertEquals(0, audit.entries.size());

        audit.failAppend = false;
        fixture.service().save(draft, key);

        assertEquals(1, fixture.definitions().read("fox").orElseThrow().definition().revision());
        assertEquals(1, fixture.registry().current().generation());
        assertEquals(1, audit.entries.size());
    }

    @Test
    void activationFailureRollsBackAuditAndDefinition() throws IOException {
        RecordingAuditSink audit = new RecordingAuditSink();
        Fixture fixture = fixture(audit, ignored -> {
            throw new IllegalStateException("activation failed");
        });
        StudioPetDraft draft = createDraft(fixture.registry());

        assertThrows(IllegalStateException.class, () -> fixture.service().save(draft, UUID.randomUUID()));

        assertEquals(false, fixture.definitions().read("fox").isPresent());
        assertEquals(0, fixture.registry().current().generation());
        assertEquals(0, audit.entries.size());
        assertEquals(1, audit.rollbacks);
    }

    private Fixture fixture(
            StudioAuditSink audit,
            java.util.function.Consumer<io.github.salyvn.omnipet.core.persistence.RegistrySnapshot> activation) {
        YamlPetDefinitionRepository definitions = new YamlPetDefinitionRepository(temporaryDirectory.resolve("pets"));
        InMemoryRegistrySnapshotRepository registry = new InMemoryRegistrySnapshotRepository();
        PetDefinitionStudioService service = new PetDefinitionStudioService(
                definitions, registry, activation, List.of(), audit);
        return new Fixture(definitions, registry, service);
    }

    private static StudioPetDraft createDraft(InMemoryRegistrySnapshotRepository registry) {
        return StudioPetDraft.create(
                "fox",
                registry.current().generation(),
                PetTier.C,
                new HeadIcon("TEXTURE_URL", "https://example.invalid/fox.png"),
                new DisplayDefinition(DisplayDefinition.Provider.HEAD, null),
                Map.of("custom", Map.of("kept", true)));
    }

    private record Fixture(
            YamlPetDefinitionRepository definitions,
            InMemoryRegistrySnapshotRepository registry,
            PetDefinitionStudioService service) {}

    private static final class RecordingAuditSink implements StudioAuditSink {
        private final List<StudioAuditEntry> entries = new ArrayList<>();
        private boolean failAppend;
        private int rollbacks;

        @Override
        public StudioAuditReceipt append(StudioAuditEntry entry) throws IOException {
            if (failAppend) throw new IOException("audit unavailable");
            entries.add(entry);
            return () -> {
                entries.remove(entry);
                rollbacks++;
            };
        }
    }
}
