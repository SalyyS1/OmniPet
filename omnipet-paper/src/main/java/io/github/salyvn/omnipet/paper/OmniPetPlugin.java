package io.github.salyvn.omnipet.paper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import io.github.salyvn.omnipet.core.persistence.FilePlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.FoundationRegistryLoader;
import io.github.salyvn.omnipet.core.persistence.InMemoryRegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.persistence.PetReferenceScanner;
import io.github.salyvn.omnipet.core.persistence.YamlPetDefinitionRepository;
import io.github.salyvn.omnipet.core.migration.legacy.LegacyEggDefinitionsMigrationResult;
import io.github.salyvn.omnipet.core.migration.legacy.LegacyEggDefinitionsMigrator;
import io.github.salyvn.omnipet.paper.command.FoundationCommandContract;
import io.github.salyvn.omnipet.paper.command.OmniPetCommand;
import io.github.salyvn.omnipet.paper.catalog.PaperStatCatalogContext;
import io.github.salyvn.omnipet.paper.catalog.ReflectiveMythicLibStatCatalogSource;
import io.github.salyvn.omnipet.paper.catalog.StatCatalogLifecycleListener;
import io.github.salyvn.omnipet.paper.studio.bukkit.PetStudioController;
import io.github.salyvn.omnipet.paper.studio.bukkit.PetStudioListener;

public final class OmniPetPlugin extends JavaPlugin {
    private PlayerStateRepository playerStates;
    private RegistrySnapshotRepository registry;
    private PetStudioController studio;
    private PaperStatCatalogContext statCatalog;

    @Override
    public void onEnable() {
        try {
            Path dataRoot = getDataFolder().toPath();
            if (Files.exists(dataRoot, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(dataRoot)) {
                throw new IOException("OmniPet data folder cannot be a symbolic link");
            }
            Files.createDirectories(dataRoot);
            migrateLegacyEggDefinitions(dataRoot);
            YamlPetDefinitionRepository definitions = new YamlPetDefinitionRepository(dataRoot.resolve("pets"));
            playerStates = new FilePlayerStateRepository(dataRoot.resolve("data/players"));
            registry = new InMemoryRegistrySnapshotRepository();
            var snapshot = new FoundationRegistryLoader().load(definitions, registry);
            statCatalog = new PaperStatCatalogContext(new ReflectiveMythicLibStatCatalogSource(
                    getServer().getPluginManager()));
            studio = new PetStudioController(this, definitions, registry, java.util.List.of(
                    playerStates::referenceScan,
                    PetReferenceScanner.yamlFiles(java.util.List.of(dataRoot.resolve("eggs.yml")))), statCatalog);
            getServer().getPluginManager().registerEvents(new PetStudioListener(studio), this);
            getServer().getPluginManager().registerEvents(new StatCatalogLifecycleListener(statCatalog), this);
            registerCommands();
            getLogger().info("OmniPet enabled with Pet Studio and " + snapshot.definitions().size() + " definitions.");
        } catch (IOException | RuntimeException failure) {
            getLogger().severe("OmniPet foundation failed to initialize: " + failure.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (studio != null) studio.onDisable();
    }

    private void migrateLegacyEggDefinitions(Path dataRoot) throws IOException {
        Path source = dataRoot.resolve("eggs.yml").toAbsolutePath().normalize();
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(source)) throw new IOException("legacy eggs.yml cannot be a symbolic link");
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) return;

        Path journal = dataRoot.resolve("migration/legacy-eggs-v1.yml").toAbsolutePath().normalize();
        LegacyEggDefinitionsMigrationResult result = new LegacyEggDefinitionsMigrator().migrate(source, journal);
        if (result.journalWritten()) {
            getLogger().info("Migrated legacy egg definitions into a durable migration journal.");
        }
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        FoundationCommandContract.NAME,
                        FoundationCommandContract.ALIASES,
                        new OmniPetCommand(studio)));
    }

    PlayerStateRepository playerStates() {
        return playerStates;
    }

    RegistrySnapshotRepository registry() {
        return registry;
    }
}
