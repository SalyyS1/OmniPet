package io.github.salyvn.omnipet.paper.incubation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import io.github.salyvn.omnipet.core.domain.PetDefinition;
import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinition;
import io.github.salyvn.omnipet.core.domain.incubation.EggDefinitionEnvelope;
import io.github.salyvn.omnipet.core.domain.incubation.HatchCandidate;
import io.github.salyvn.omnipet.core.incubation.IncubationDurationParser;
import io.github.salyvn.omnipet.core.persistence.EggDefinitionRepository;
import io.github.salyvn.omnipet.core.persistence.RegistrySnapshotRepository;
import io.github.salyvn.omnipet.core.storage.PetStorageResult;
import io.github.salyvn.omnipet.core.storage.RepositoryPetStorageService;
import io.github.salyvn.omnipet.paper.permission.PaperStorageLimitsResolver;
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

    private final EggDefinitionRepository eggs;
    private final RegistrySnapshotRepository registry;
    private final RepositoryPetStorageService storage;
    private final PaperEggItemCodec codec;
    private final PaperStorageLimitsResolver limits;
    private final Supplier<UUID> petIds;

    public EggAdminController(
            EggDefinitionRepository eggs,
            RegistrySnapshotRepository registry,
            RepositoryPetStorageService storage,
            PaperEggItemCodec codec,
            PaperStorageLimitsResolver limits) {
        this(eggs, registry, storage, codec, limits, UUID::randomUUID);
    }

    EggAdminController(
            EggDefinitionRepository eggs,
            RegistrySnapshotRepository registry,
            RepositoryPetStorageService storage,
            PaperEggItemCodec codec,
            PaperStorageLimitsResolver limits,
            Supplier<UUID> petIds) {
        this.eggs = Objects.requireNonNull(eggs, "egg definition repository");
        this.registry = Objects.requireNonNull(registry, "pet registry");
        this.storage = Objects.requireNonNull(storage, "pet storage service");
        this.codec = Objects.requireNonNull(codec, "egg item codec");
        this.limits = Objects.requireNonNull(limits, "storage limits resolver");
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
        try {
            Player target = requireOnline(values.get(1));
            PetDefinition definition = requireDefinition(values.get(2));
            grant(sender, target, definition);
        } catch (IOException | IllegalArgumentException failure) {
            sender.sendMessage("OmniPet: pet grant failed - " + detail(failure) + ".");
        }
    }

    /**
     * Creates the egg that lets a freshly-saved definition be obtained in game.
     *
     * <p>Returns the ID when one was written, or empty when a catalog entry already existed. An
     * existing entry is never overwritten: an operator may have tuned its duration or candidate pool
     * by hand, and clobbering that to re-add a single candidate would destroy their work.
     */
    public java.util.Optional<String> createCompanionEgg(PetDefinition definition) throws IOException {
        Objects.requireNonNull(definition, "pet definition");
        String eggId = companionEggId(definition.id());
        if (eggs.read(eggId).isPresent()) return java.util.Optional.empty();
        eggs.save(envelope(eggId, definition, IncubationDurationParser.parseMillis(DEFAULT_DURATION)));
        return java.util.Optional.of(eggId);
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

    private void grant(CommandSender sender, Player target, PetDefinition definition) throws IOException {
        UUID playerId = target.getUniqueId();
        var resolved = limits.resolve(target::hasPermission).limits();
        var snapshot = storage.snapshot(playerId, resolved);
        PetStorageResult result = storage.admit(playerId, snapshot.revision(), pet(definition), resolved);
        if (!result.succeeded()) {
            sender.sendMessage("OmniPet: pet not granted - " + Displays.words(result.status()) + ".");
            return;
        }
        sender.sendMessage("OmniPet: granted " + definition.id() + " to " + target.getName() + ".");
        target.sendMessage(Messages.line(MessageKey.VAULT_REFRESHED));
    }

    /**
     * Builds the instance an admin grant produces.
     *
     * <p>Shaped to match what {@code IncubationPetFactory} writes for a hatched pet, so a granted pet
     * renders and cultivates identically. It has no {@code incubationId} because no incubation
     * happened; {@code source} records that, so the difference is visible in the data instead of
     * looking like a hatch that lost its ID.
     */
    private PetInstance pet(PetDefinition definition) {
        Map<String, Object> hatching = new java.util.LinkedHashMap<>();
        hatching.put("eggId", companionEggId(definition.id()));
        hatching.put("rarityId", definition.tier().name());
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
