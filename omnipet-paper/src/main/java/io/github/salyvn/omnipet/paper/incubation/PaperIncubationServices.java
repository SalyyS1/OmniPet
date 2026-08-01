package io.github.salyvn.omnipet.paper.incubation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.github.salyvn.omnipet.core.domain.StableId;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinition;
import io.github.salyvn.omnipet.core.incubation.EggEscrowJournal;
import io.github.salyvn.omnipet.core.incubation.EggEscrowRecoveryService;
import io.github.salyvn.omnipet.core.incubation.FileEggEscrowJournal;
import io.github.salyvn.omnipet.core.incubation.ItemEscrowService;
import io.github.salyvn.omnipet.core.incubation.RepositoryHatchService;
import io.github.salyvn.omnipet.core.persistence.EggDefinitionRepository;
import io.github.salyvn.omnipet.core.persistence.PetReferenceScanner;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.YamlEggDefinitionRepository;

public final class PaperIncubationServices {
    private final EggDefinitionRepository eggDefinitions;
    private final EggEscrowJournal eggEscrowJournal;
    private final ItemEscrowService itemEscrow;
    private final RepositoryHatchService hatches;
    private final EggEscrowRecoveryService recovery;
    private final int eggDefinitionCount;
    private final Map<String, Set<String>> eggReferencesByPet;

    private PaperIncubationServices(
            EggDefinitionRepository eggDefinitions,
            EggEscrowJournal eggEscrowJournal,
            PlayerStateRepository playerStates,
            Map<String, EggDefinition> eggCatalog) {
        this.eggDefinitions = eggDefinitions;
        this.eggEscrowJournal = eggEscrowJournal;
        this.itemEscrow = new ItemEscrowService(eggEscrowJournal);
        this.hatches = new RepositoryHatchService(playerStates);
        this.recovery = new EggEscrowRecoveryService();
        this.eggDefinitionCount = eggCatalog.size();
        this.eggReferencesByPet = indexReferences(eggCatalog);
    }

    public static PaperIncubationServices open(Path dataRoot, PlayerStateRepository playerStates) throws IOException {
        if (dataRoot == null) throw new IllegalArgumentException("OmniPet data root is required");
        if (playerStates == null) throw new IllegalArgumentException("player state repository is required");
        Path root = dataRoot.toAbsolutePath().normalize();
        EggDefinitionRepository definitions = new YamlEggDefinitionRepository(root.resolve("eggs"));
        Map<String, EggDefinition> catalog = definitions.loadAll();
        Path escrowRoot = root.resolve("data/egg-escrow");
        prepareEscrowRoot(root, escrowRoot);
        EggEscrowJournal journal = new FileEggEscrowJournal(escrowRoot);
        return new PaperIncubationServices(definitions, journal, playerStates, catalog);
    }

    public EggDefinitionRepository eggDefinitions() { return eggDefinitions; }

    public EggEscrowJournal eggEscrowJournal() { return eggEscrowJournal; }

    public ItemEscrowService itemEscrow() { return itemEscrow; }

    public RepositoryHatchService hatches() { return hatches; }

    public EggEscrowRecoveryService recovery() { return recovery; }

    public int eggDefinitionCount() { return eggDefinitionCount; }

    public PetReferenceScanner petReferences() {
        return definitionId -> {
            String expected = StableId.requireValid(definitionId);
            return eggReferencesByPet.getOrDefault(expected, Set.of());
        };
    }

    private static Map<String, Set<String>> indexReferences(Map<String, EggDefinition> catalog) {
        Map<String, LinkedHashSet<String>> mutable = new LinkedHashMap<>();
        for (var entry : catalog.entrySet()) {
            for (var candidate : entry.getValue().candidates()) {
                mutable.computeIfAbsent(candidate.definitionId(), ignored -> new LinkedHashSet<>())
                        .add("egg:" + entry.getKey());
            }
        }
        Map<String, Set<String>> immutable = new LinkedHashMap<>();
        mutable.forEach((definitionId, references) -> immutable.put(definitionId, Set.copyOf(references)));
        return Map.copyOf(immutable);
    }

    private static void prepareEscrowRoot(Path dataRoot, Path escrowRoot) throws IOException {
        Path current = dataRoot;
        rejectSymbolicLink(current);
        for (Path segment : dataRoot.relativize(escrowRoot)) {
            current = current.resolve(segment);
            rejectSymbolicLink(current);
        }
        if (Files.exists(escrowRoot, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(escrowRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("egg escrow root is not a directory: " + escrowRoot);
        }
        Files.createDirectories(escrowRoot);
    }

    private static void rejectSymbolicLink(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw new IOException("symbolic link is not allowed in egg escrow path: " + path);
        }
    }
}
