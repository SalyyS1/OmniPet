package io.github.salyvn.omnipet.paper.incubation.placed;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.plugin.java.JavaPlugin;

import io.github.salyvn.omnipet.core.persistence.EggDefinitionRepository;
import io.github.salyvn.omnipet.paper.gui.egg.PlacedEggMenuListener;
import io.github.salyvn.omnipet.paper.incubation.PaperEggItemCodec;
import io.github.salyvn.omnipet.paper.incubation.action.PaperIncubationItemActionCodec;

/**
 * The placed-egg feature, assembled as one unit.
 *
 * <p>Grouped because the coordinator, the view, and the block listener are only correct together: they
 * share one store, and two coordinators would each hold their own and could disagree about whether a
 * block still carries an egg. Assembling them here keeps that invariant in one place rather than spread
 * across plugin startup.
 *
 * <p>What happens when a countdown finishes stays with the caller. Hatching needs the pet registry and
 * the owner's resolved storage limits, neither of which belongs to this package.
 */
public final class PlacedEggServices {
    private final PlacedEggView view;
    private final PlacedEggListener listener;
    private final PlacedEggMenuListener menuListener;

    private PlacedEggServices(
            PlacedEggView view, PlacedEggListener listener, PlacedEggMenuListener menuListener) {
        this.view = view;
        this.listener = listener;
        this.menuListener = menuListener;
    }

    /**
     * Wires the feature. The caller registers {@link #listener()} and {@link #menuListener()}, then calls
     * {@link #start()}.
     *
     * @param onReady runs when a placed egg finishes its countdown, on the main thread
     */
    public static PlacedEggServices open(
            JavaPlugin plugin,
            Path dataRoot,
            EggDefinitionRepository eggDefinitions,
            Consumer<String> warnings,
            BiConsumer<UUID, PlacedEggRecord> onReady) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(dataRoot, "data root");
        Objects.requireNonNull(eggDefinitions, "egg definitions");
        Objects.requireNonNull(warnings, "warning sink");
        Objects.requireNonNull(onReady, "ready handler");

        PlacedEggCoordinator coordinator = new PlacedEggCoordinator(
                new PlacedEggStore(dataRoot.resolve("data/placed-eggs")), eggDefinitions, warnings);
        PlacedEggView view = new PlacedEggView(plugin, coordinator, new PlacedEggHolograms(), onReady);
        // Its own codec instance, matching the held-incubation path: the codec is stateless beyond its
        // namespaced keys, which are derived from the plugin and therefore identical.
        PlacedEggSupportController support = new PlacedEggSupportController(
                coordinator, view, new PaperIncubationItemActionCodec(plugin));
        PlacedEggListener listener = new PlacedEggListener(
                new PaperEggItemCodec(plugin), coordinator, view, support);
        return new PlacedEggServices(view, listener, new PlacedEggMenuListener(support));
    }

    public PlacedEggListener listener() {
        return listener;
    }

    /** Clicks inside the menu that right-clicking a placed egg opens. */
    public PlacedEggMenuListener menuListener() {
        return menuListener;
    }

    /** Drops the record and its hologram once the hatched pet has been admitted. */
    public void consume(PlacedEggRecord record) {
        view.remove(record.key());
        view.consume(record);
    }

    /** The pet a placed egg hatches: its catalog entry's single candidate, or null when unreadable. */
    public static String definitionId(
            EggDefinitionRepository eggDefinitions, PlacedEggRecord record, Consumer<String> warnings) {
        try {
            return eggDefinitions.read(record.eggId())
                    .map(envelope -> envelope.definition().candidates().isEmpty()
                            ? null
                            : envelope.definition().candidates().getFirst().definitionId())
                    .orElse(null);
        } catch (IOException | RuntimeException failure) {
            warnings.accept("could not read the placed egg's definition: " + failure.getMessage());
            return null;
        }
    }

    public void start() {
        view.start();
    }

    public void stop() {
        view.stop();
    }
}
