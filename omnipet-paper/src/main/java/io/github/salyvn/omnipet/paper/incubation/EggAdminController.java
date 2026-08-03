package io.github.salyvn.omnipet.paper.incubation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinition;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinitionEnvelope;
import io.github.salyvn.omnipet.core.domain.incubation.HatchCandidate;
import io.github.salyvn.omnipet.core.incubation.IncubationDurationParser;
import io.github.salyvn.omnipet.core.persistence.EggDefinitionRepository;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.persistence.StaleRevisionException;
import io.github.salyvn.omnipet.core.storage.PetStorageLimits;
import io.github.salyvn.omnipet.core.storage.PetStorageResult;
import io.github.salyvn.omnipet.core.storage.RepositoryPetStorageService;
import io.github.salyvn.omnipet.paper.permission.PaperStorageLimitsResolver;
import io.github.salyvn.omnipet.paper.task.PerPlayerTaskQueue;
import io.github.salyvn.omnipet.paper.text.Displays;
import io.github.salyvn.omnipet.paper.text.MessageKey;
import io.github.salyvn.omnipet.paper.text.Messages;

/**
 * Operator distribution for eggs and pets.
 *
 * <p>Fills the gap that made a Studio-created pet unreachable: no command minted eggs, and the item
 * codec could only read one, so there was no in-game path from a pet definition to a pet in a vault.
 *
 * <p>Output here is operator-facing audit text, deliberately left in the plugin rather than moved to
 * {@code messages.yml}, matching the other admin controllers. The egg item a player receives is
 * catalog-owned and does go through {@link Messages}.
 */
public final class EggAdminController {
    /** The default incubation time for an auto-created egg. */
    public static final String DEFAULT_DURATION = "1h";

    /**
     * How many times a grant re-reads the revision after losing a race.
     *
     * <p>Matches the vault's reconcile retry. A grant races anything that bumps the player's revision —
     * a vault toggle, a hatch tick, a slot purchase — and each attempt re-reads, so a bounded retry
     * converges without looping against a genuinely conflicting workload.
     */
    private static final int GRANT_ATTEMPTS = 3;

    private final EggDefinitionRepository eggs;
    private final RegistrySnapshotRepository registry;
    private final RepositoryPetStorageService storage;
    private final PaperEggItemCodec codec;
    private final PaperStorageLimitsResolver limits;
    private final PerPlayerTaskQueue tasks;
    private final Consumer<Runnable> mainDispatcher;
    private final Supplier<UUID> petIds;

    public EggAdminController(
            EggDefinitionRepository eggs,
            RegistrySnapshotRepository registry,
            RepositoryPetStorageService storage,
            PaperEggItemCodec codec,
            PaperStorageLimitsResolver limits,
            PerPlayerTaskQueue tasks,
            JavaPlugin plugin) {
        this(eggs, registry, storage, codec, limits, tasks,
                task -> plugin.getServer().getScheduler().runTask(plugin, task), UUID::randomUUID);
    }

    EggAdminController(
            EggDefinitionRepository eggs,
            RegistrySnapshotRepository registry,
            RepositoryPetStorageService storage,
            PaperEggItemCodec codec,
            PaperStorageLimitsResolver limits,
            PerPlayerTaskQueue tasks,
            Consumer<Runnable> mainDispatcher,
            Supplier<UUID> petIds) {
        this.eggs = Objects.requireNonNull(eggs, "egg definition repository");
        this.registry = Objects.requireNonNull(registry, "pet registry");
        this.storage = Objects.requireNonNull(storage, "pet storage service");
        this.codec = Objects.requireNonNull(codec, "egg item codec");
        this.limits = Objects.requireNonNull(limits, "storage limits resolver");
        this.tasks = Objects.requireNonNull(tasks, "player task queue");
        this.mainDispatcher = Objects.requireNonNull(mainDispatcher, "main-thread dispatcher");
        this.petIds = Objects.requireNonNull(petIds, "pet id supplier");
    }

    /**
     * A controller that can only write the catalog.
     *
     * <p>{@link #createCompanionEgg} touches nothing but the catalog, while the command paths need a
     * live server for player lookup and item creation. This lets the companion-egg decision — which ID,
     * pointing at what, and when to decline — be tested without one, instead of the alternative of
     * relaxing the null checks that keep the command paths honest.
     */
    static EggAdminController catalogOnly(EggDefinitionRepository eggs) {
        return new EggAdminController(eggs);
    }

    private EggAdminController(EggDefinitionRepository eggs) {
        this.eggs = Objects.requireNonNull(eggs, "egg definition repository");
        this.registry = null;
        this.storage = null;
        this.codec = null;
        this.limits = null;
        this.tasks = null;
        this.mainDispatcher = null;
        this.petIds = UUID::randomUUID;
    }

    /** {@code /pet admin egg give|create ...} */
    public void eggCommand(CommandSender sender, List<String> arguments) {
        Objects.requireNonNull(sender, "sender");
        requireMainThread();
        List<String> values = List.copyOf(arguments == null ? List.of() : arguments);
        try {
            String action = values.isEmpty() ? "" : values.getFirst().toLowerCase(Locale.ROOT);
            switch (action) {
                case "give" -> give(sender, values);
                case "create" -> create(sender, values);
                default -> eggUsage(sender);
            }
        } catch (IOException | IllegalArgumentException failure) {
            sender.sendMessage("OmniPet: egg command failed - " + detail(failure) + ".");
        }
    }

    /** {@code /pet admin pet give <online-player> <definition-id>} */
    public void petCommand(CommandSender sender, List<String> arguments) {
        Objects.requireNonNull(sender, "sender");
        requireMainThread();
        List<String> values = List.copyOf(arguments == null ? List.of() : arguments);
        if (values.size() != 3 || !values.getFirst().equalsIgnoreCase("give")) {
            sender.sendMessage("OmniPet: use /pet admin pet give <online-player> <definition-id>.");
            return;
        }
        Player target;
        PetDefinition definition;
        try {
            // Resolved here, on the main thread: player lookup and permission checks are Bukkit calls
            // and must not follow the repository work onto a worker.
            target = requireOnline(values.get(1));
            definition = requireDefinition(values.get(2));
        } catch (IllegalArgumentException failure) {
            sender.sendMessage("OmniPet: pet grant failed - " + detail(failure) + ".");
            return;
        }
        UUID playerId = target.getUniqueId();
        String targetName = target.getName();
        var resolved = limits.resolve(target::hasPermission).limits();
        if (!tasks.submit(playerId, () -> {
            try {
                PetStorageResult result = grant(playerId, definition, resolved);
                onMain(() -> {
                    if (!result.succeeded()) {
                        sender.sendMessage("OmniPet: pet not granted - " + Displays.words(result.status()) + ".");
                        return;
                    }
                    sender.sendMessage("OmniPet: granted " + definition.id() + " to " + targetName + ".");
                    Player online = Bukkit.getPlayer(playerId);
                    if (online != null) online.sendMessage(Messages.line(MessageKey.VAULT_REFRESHED));
                });
            } catch (StaleRevisionException stale) {
                // Exhausted the retries: another accepted task keeps winning the revision. Reporting a
                // retryable conflict is honest; a raw IllegalStateException here would unwind into the
                // command dispatcher and read as a plugin crash.
                onMain(() -> sender.sendMessage(
                        "OmniPet: pet not granted - " + targetName + "'s data changed during the grant; try again."));
            } catch (IOException | RuntimeException failure) {
                onMain(() -> sender.sendMessage("OmniPet: pet grant failed - " + detail(failure) + "."));
            }
        })) {
            sender.sendMessage("OmniPet: pet grant could not be queued; service is shutting down.");
        }
    }

    /**
     * Creates the egg that lets a freshly-saved definition be obtained in game.
     *
     * <p>An existing entry is never overwritten: an operator may have tuned its duration or candidate
     * pool by hand, and clobbering that to re-add a single candidate would destroy their work.
     *
     * <p>Tier is the exception, because it is a consistency constraint rather than tuning — the hatch
     * roller refuses an egg whose tier disagrees with its candidate. Changing a pet's tier after its
     * egg exists therefore makes that egg unhatchable, and the failure would otherwise surface only
     * when a player tried to use it, as a queued hatch that never completes. That case is reported
     * back to the operator instead of being silently skipped.
     *
     * @return what happened, so the caller can tell the operator
     */
    public CompanionEgg createCompanionEgg(PetDefinition definition) throws IOException {
        Objects.requireNonNull(definition, "pet definition");
        String eggId = companionEggId(definition.id());
        var existing = eggs.read(eggId);
        if (existing.isPresent()) {
            return existing.get().definition().tier() == definition.tier()
                    ? new CompanionEgg(eggId, CompanionEgg.Outcome.ALREADY_PRESENT)
                    : new CompanionEgg(eggId, CompanionEgg.Outcome.TIER_MISMATCH);
        }
        eggs.save(envelope(eggId, definition, IncubationDurationParser.parseMillis(DEFAULT_DURATION)));
        return new CompanionEgg(eggId, CompanionEgg.Outcome.CREATED);
    }

    /** What {@link #createCompanionEgg} did, and which egg it was about. */
    public record CompanionEgg(String eggId, Outcome outcome) {
        public enum Outcome {
            /** A new catalog entry was written. */
            CREATED,
            /** An entry already existed and is consistent with the definition. */
            ALREADY_PRESENT,
            /**
             * An entry exists but its tier no longer matches the definition, so the hatch roller will
             * refuse it. The operator has to re-create it with {@code /pet admin egg create}.
             */
            TIER_MISMATCH
        }
    }

    /** The catalog ID an auto-created egg uses for a pet definition. */
    public static String companionEggId(String definitionId) {
        return definitionId + "_egg";
    }

    private void give(CommandSender sender, List<String> values) throws IOException {
        if (values.size() < 3 || values.size() > 4) {
            eggUsage(sender);
            return;
        }
        Player target = requireOnline(values.get(1));
        String eggId = values.get(2);
        EggDefinition definition = eggs.read(eggId)
                .orElseThrow(() -> new IllegalArgumentException("unknown egg definition: " + eggId))
                .definition();
        int amount = values.size() > 3 ? positive(values.get(3), "amount") : 1;
        if (amount > 36) throw new IllegalArgumentException("amount cannot exceed 36");

        // One slot per egg: each carries its own nonce, so they must never merge into a stack.
        List<Integer> free = emptySlots(target, amount);
        if (free.size() < amount) {
            sender.sendMessage("OmniPet: " + target.getName() + " has only " + free.size()
                    + " empty slot(s); nothing was delivered.");
            return;
        }
        for (int index = 0; index < amount; index++) {
            target.getInventory().setItem(free.get(index), item(definition));
        }
        sender.sendMessage("OmniPet: delivered " + amount + " " + eggId + " egg(s) to " + target.getName() + ".");
    }

    private void create(CommandSender sender, List<String> values) throws IOException {
        if (values.size() < 3 || values.size() > 4) {
            eggUsage(sender);
            return;
        }
        String eggId = values.get(1);
        PetDefinition definition = requireDefinition(values.get(2));
        long millis = IncubationDurationParser.parseMillis(values.size() > 3 ? values.get(3) : DEFAULT_DURATION);
        boolean replacing = eggs.read(eggId).isPresent();
        eggs.save(envelope(eggId, definition, millis));
        sender.sendMessage("OmniPet: " + (replacing ? "replaced" : "created") + " egg " + eggId
                + " hatching " + definition.id() + " in " + IncubationDurationParser.formatMillis(millis)
                + ". Give it with /pet admin egg give <player> " + eggId + ".");
    }

    /**
     * Admits the pet, re-reading the revision when it loses a race.
     *
     * <p>Runs on the player's task queue, so it is serialized against that player's other accepted
     * work rather than competing with it for the file lock. Reading the revision and admitting under
     * it are two separate lock acquisitions, so anything that bumps the revision in between — a vault
     * toggle, a hatch tick, a slot purchase — makes the admit stale; each attempt observes again.
     */
    private PetStorageResult grant(UUID playerId, PetDefinition definition, PetStorageLimits resolved)
            throws IOException {
        StaleRevisionException lastConflict = null;
        for (int attempt = 0; attempt < GRANT_ATTEMPTS; attempt++) {
            try {
                var snapshot = storage.snapshot(playerId, resolved);
                return storage.admit(playerId, snapshot.revision(), pet(definition), resolved);
            } catch (StaleRevisionException stale) {
                lastConflict = stale;
            }
        }
        throw lastConflict;
    }

    private void onMain(Runnable task) {
        mainDispatcher.accept(task);
    }

    /**
     * Builds the instance an admin grant produces.
     *
     * <p>Shaped like what {@code IncubationPetFactory} writes for a hatched pet, so a granted pet
     * renders and cultivates the same way, with two deliberate omissions. There is no
     * {@code incubationId} because no incubation happened, and no {@code rarityId} because that field
     * holds a rarity <em>band</em> ID drawn from a roll that never ran — a pet tier is a different
     * namespace, so writing one there would be read as an unknown band and scored as the weakest
     * rarity, making an S-tier grant level at common-pet cost. Both readers already treat the field as
     * optional. {@code source} records the provenance, so the difference is visible in the data rather
     * than looking like a hatch that lost its ID.
     */
    private PetInstance pet(PetDefinition definition) {
        Map<String, Object> hatching = new java.util.LinkedHashMap<>();
        hatching.put("eggId", companionEggId(definition.id()));
        hatching.put("source", "admin-grant");
        Map<String, Object> components = new java.util.LinkedHashMap<>();
        components.put("hatching", hatching);
        components.put("stats", List.of());
        components.put("appearance", Map.of(
                "provider", "HEAD",
                "fallbackHeadSource", definition.icon().source(),
                "fallbackHeadValue", definition.icon().value()));
        return new PetInstance(
                petIds.get(), definition.id(), definition.revision(), components, Map.of());
    }

    private ItemStack item(EggDefinition definition) {
        List<Component> lore = new ArrayList<>();
        lore.add(Messages.line(MessageKey.GUI_EGG_ITEM_TIER,
                Messages.of("status", Displays.of(definition.tier()))));
        lore.add(Messages.line(MessageKey.GUI_EGG_ITEM_DURATION,
                Messages.of("detail", IncubationDurationParser.formatMillis(definition.baseActiveMillis()))));
        lore.add(Component.empty());
        lore.add(Messages.line(MessageKey.GUI_EGG_ITEM_HINT));
        return codec.create(
                definition.id(),
                Material.TURTLE_EGG,
                Messages.line(MessageKey.GUI_EGG_ITEM_NAME,
                        Messages.of("pet", Displays.identifier(definition.id()))),
                lore);
    }

    private static EggDefinitionEnvelope envelope(String eggId, PetDefinition definition, long millis) {
        return new EggDefinitionEnvelope(
                EggDefinitionEnvelope.CURRENT_SCHEMA_VERSION,
                new EggDefinition(
                        eggId,
                        definition.tier(),
                        millis,
                        List.of(new HatchCandidate(definition.id(), 1.0, Map.of())),
                        Map.of()));
    }

    private PetDefinition requireDefinition(String definitionId) {
        PetDefinition definition = registry.current().definitions().get(definitionId);
        if (definition == null) throw new IllegalArgumentException("unknown pet definition: " + definitionId);
        return definition;
    }

    private static List<Integer> emptySlots(Player target, int wanted) {
        List<Integer> free = new ArrayList<>();
        // Snapshotted once: getStorageContents() copies the whole inventory on every call, so reading
        // it in the loop condition would clone it once per slot examined.
        ItemStack[] contents = target.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && free.size() < wanted; slot++) {
            ItemStack existing = contents[slot];
            if (existing == null || existing.getType() == Material.AIR) free.add(slot);
        }
        return free;
    }

    private static Player requireOnline(String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null || !target.isOnline()) {
            throw new IllegalArgumentException("player is not online: " + name);
        }
        return target;
    }

    private static int positive(String raw, String label) {
        int value = Integer.parseInt(raw);
        if (value < 1) throw new IllegalArgumentException(label + " must be positive");
        return value;
    }

    private static void eggUsage(CommandSender sender) {
        sender.sendMessage("OmniPet: use /pet admin egg give <online-player> <egg-id> [amount] "
                + "or /pet admin egg create <egg-id> <definition-id> [duration].");
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("egg administration requires the Paper main thread");
        }
    }

    private static String detail(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
