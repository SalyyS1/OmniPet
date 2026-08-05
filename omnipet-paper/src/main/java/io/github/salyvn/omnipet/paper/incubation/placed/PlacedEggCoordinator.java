package io.github.salyvn.omnipet.paper.incubation.placed;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import io.github.salyvn.omnipet.core.domain.incubation.EggDefinitionEnvelope;
import io.github.salyvn.omnipet.core.persistence.EggDefinitionRepository;

/**
 * Placing, ticking, and reclaiming eggs that incubate in the world.
 *
 * <p>The escrow saga is deliberately untouched. Escrow exists to prove a <em>held</em> item was paid for;
 * a placed egg is an item the player still owns, sitting in a block instead of a slot, so it gets its own
 * durable record. That separation is the point: placing an egg cannot reach the code holding players' paid
 * items and money.
 *
 * <p>No duplication is possible because {@link PlacedEggStore} is keyed by block and the record carries
 * the egg's own nonce: placing writes exactly one record, breaking deletes it before returning the item,
 * and the item returned is the snapshot taken at placement rather than a freshly minted egg.
 */
public final class PlacedEggCoordinator {
    /** The eight blocks touching a placed egg on its own level, plus above and below. */
    private static final List<BlockFace> NEIGHBOURS = List.of(
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.NORTH_EAST, BlockFace.NORTH_WEST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST,
            BlockFace.UP, BlockFace.DOWN);

    private final PlacedEggStore store;
    private final EggDefinitionRepository eggs;
    private final Consumer<String> warnings;
    /**
     * Every placed egg, loaded once and kept in step with the store.
     *
     * <p>The store is the only writer, so re-reading the whole directory each pass told us nothing we had
     * not just written ourselves — it cost a stat, a size check, a read, and a YAML parse per egg per
     * second on the tick thread. This map is the working copy; disk stays the durable one.
     */
    private final Map<String, PlacedEggRecord> records = new LinkedHashMap<>();
    /**
     * Placement requirements by egg id.
     *
     * <p>Egg definitions are static config, so reading one per egg per second was pure repetition. Cleared
     * on reload, which is the only thing that can change them.
     */
    private final Map<String, PlacementRequirement> requirements = new LinkedHashMap<>();
    /** Keys whose countdown has moved since the last flush. */
    private final Set<String> unflushed = new LinkedHashSet<>();
    private boolean loaded;

    public PlacedEggCoordinator(
            PlacedEggStore store, EggDefinitionRepository eggs, Consumer<String> warnings) {
        this.store = Objects.requireNonNull(store, "placed egg store");
        this.eggs = Objects.requireNonNull(eggs, "egg definition repository");
        this.warnings = Objects.requireNonNull(warnings, "warning sink");
    }

    /**
     * Records an egg placed at a block.
     *
     * @return the placement outcome; only {@link Placement.Status#PLACED} means the block should stay
     */
    public Placement place(Block block, UUID ownerId, String eggId, UUID itemNonce, ItemStack item) {
        Objects.requireNonNull(block, "block");
        try {
            Optional<EggDefinitionEnvelope> envelope = eggs.read(eggId);
            if (envelope.isEmpty()) {
                return Placement.refused("that egg has no catalog entry");
            }
            Map<String, Object> extensions = envelope.get().definition().extensions();
            Optional<PlacementRequirement> requirement = PlacementRequirement.forEgg(extensions);
            if (requirement.isEmpty()) {
                return Placement.refused("this egg cannot be placed; hatch it from your hand");
            }
            if (!requirement.get().satisfiedBy(surroundingBlocks(block))) {
                return Placement.refused("this egg needs to be " + requirement.get().describe());
            }
            String key = PlacedEggRecord.key(
                    block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
            if (store.read(key).isPresent()) {
                return Placement.refused("another egg is already incubating there");
            }
            long total = envelope.get().definition().baseActiveMillis();
            PlacedEggRecord placed = new PlacedEggRecord(
                    eggId, itemNonce, ownerId,
                    block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                    total, total, snapshot(item), Map.of());
            // Written immediately, not deferred: this is the player's item becoming a block, and losing it
            // to a crash would lose the item. Only the countdown is allowed to be behind disk.
            store.save(placed);
            records.put(placed.key(), placed);
            return Placement.placed(total);
        } catch (IOException | RuntimeException failure) {
            warnings.accept("could not record a placed egg at " + describe(block) + ": " + failure.getMessage());
            return Placement.refused("that egg could not be placed right now");
        }
    }

    /** The record at a block, if any. Answered from the working copy once it has been loaded. */
    public Optional<PlacedEggRecord> at(Block block) {
        String key = PlacedEggRecord.key(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (loaded) return Optional.ofNullable(records.get(key));
        try {
            Optional<PlacedEggRecord> found = store.read(key);
            found.ifPresent(record -> records.put(record.key(), record));
            return found;
        } catch (IOException | RuntimeException failure) {
            warnings.accept("could not read the placed egg at " + describe(block) + ": " + failure.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Consumes the record and hands back the exact egg that was placed.
     *
     * <p>Deletes first, then returns the item: the record is the single owner, so it must stop existing
     * before the item exists again. Failing that order is how a break/place cycle would duplicate an egg.
     */
    public Optional<ItemStack> reclaim(PlacedEggRecord record) {
        try {
            if (!store.delete(record.key())) return Optional.empty();
            forget(record.key());
            return Optional.of(item(record));
        } catch (IOException | RuntimeException failure) {
            warnings.accept("could not reclaim the placed egg " + record.key() + ": " + failure.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Credits elapsed time when the requirement still holds, and reports whether it now reads ready.
     *
     * <p>The countdown moves in memory and reaches disk on {@link #flush}. Losing at most one flush
     * interval of progress on a crash is the deliberate trade for not fsyncing twice per egg per second on
     * the tick thread; the record itself — the player's item — is written the moment the egg is placed and
     * is never at risk.
     */
    public Optional<PlacedEggRecord> tick(Block block, PlacedEggRecord record, long elapsedMillis) {
        if (elapsedMillis <= 0 || record.ready()) return Optional.empty();
        try {
            PlacementRequirement requirement = requirement(record.eggId());
            if (requirement == null) return Optional.empty();
            // The heat can be removed after placement, so the requirement is re-checked rather than
            // trusted from placement time.
            if (!requirement.satisfiedBy(surroundingBlocks(block))) return Optional.empty();
            PlacedEggRecord advanced = record.withRemaining(record.remainingMillis() - elapsedMillis);
            records.put(advanced.key(), advanced);
            unflushed.add(advanced.key());
            // A ready egg is about to be acted on, so its state has to be durable before that happens.
            if (advanced.ready()) flush();
            return Optional.of(advanced);
        } catch (RuntimeException failure) {
            warnings.accept("could not advance the placed egg " + record.key() + ": " + failure.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Writes every countdown that has moved since the last call.
     *
     * <p>Called on a slow cadence, when an egg becomes ready, and at shutdown — not per tick.
     */
    public void flush() {
        if (unflushed.isEmpty()) return;
        for (String key : List.copyOf(unflushed)) {
            PlacedEggRecord record = records.get(key);
            if (record == null) {
                unflushed.remove(key);
                continue;
            }
            try {
                store.save(record);
                unflushed.remove(key);
            } catch (IOException | RuntimeException failure) {
                // Left in the set so the next flush retries rather than silently losing the progress.
                warnings.accept("could not persist the placed egg " + key + ": " + failure.getMessage());
            }
        }
    }

    /** The cached requirement for an egg definition, or null when the definition cannot be read. */
    private PlacementRequirement requirement(String eggId) {
        PlacementRequirement cached = requirements.get(eggId);
        if (cached != null) return cached;
        try {
            Optional<EggDefinitionEnvelope> envelope = eggs.read(eggId);
            if (envelope.isEmpty()) return null;
            PlacementRequirement resolved =
                    PlacementRequirement.from(envelope.get().definition().extensions());
            requirements.put(eggId, resolved);
            return resolved;
        } catch (IOException | RuntimeException failure) {
            warnings.accept("could not read the egg definition " + eggId + ": " + failure.getMessage());
            return null;
        }
    }

    /** Drops the definition cache. Called when the egg catalog is reloaded. */
    public void invalidateDefinitions() {
        requirements.clear();
    }

    /**
     * Deletes a record whose pet has been granted.
     *
     * <p>Separate from {@link #reclaim} because nothing is handed back: the egg became a pet, so the
     * record is simply consumed.
     */
    public void consume(PlacedEggRecord record) {
        try {
            store.delete(record.key());
        } catch (IOException | RuntimeException failure) {
            warnings.accept("could not consume the placed egg " + record.key() + ": " + failure.getMessage());
        } finally {
            // Dropped even when the delete failed: the pet has been granted, so replaying this record
            // would hand out a second one. A leftover file is an operator problem, a duplicate pet is not.
            forget(record.key());
        }
    }

    /** Drops a record from the working copy and cancels any pending write for it. */
    private void forget(String key) {
        records.remove(key);
        unflushed.remove(key);
    }

    /**
     * Every placed egg, from memory after the first call.
     *
     * <p>The disk scan happens once. Repeating it each pass re-read and re-parsed files this class had
     * just written itself, which is what made a hundred placed eggs cost hundreds of reads a second.
     */
    public PlacedEggStore.Scan all() {
        if (!loaded) {
            try {
                PlacedEggStore.Scan scan = store.scanAll();
                for (PlacedEggRecord record : scan.records()) records.put(record.key(), record);
                loaded = true;
                // Issues are reported from the one real scan; later calls have none to report.
                return scan;
            } catch (IOException | RuntimeException failure) {
                warnings.accept("could not scan placed eggs: " + failure.getMessage());
                return new PlacedEggStore.Scan(List.of(), List.of(), false);
            }
        }
        return new PlacedEggStore.Scan(List.copyOf(records.values()), List.of(), false);
    }

    /** The block names around a placed egg, as the server reports them. */
    private static List<String> surroundingBlocks(Block block) {
        return NEIGHBOURS.stream()
                .map(block::getRelative)
                .map(neighbour -> neighbour.getType().getKey().getKey())
                .toList();
    }

    static String snapshot(ItemStack item) {
        ItemStack one = item.clone();
        one.setAmount(1);
        return Base64.getEncoder().encodeToString(one.serializeAsBytes());
    }

    static ItemStack item(PlacedEggRecord record) {
        ItemStack restored = ItemStack.deserializeBytes(Base64.getDecoder().decode(record.itemSnapshot()));
        restored.setAmount(1);
        return restored;
    }

    private static String describe(Block block) {
        return block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    /** Where a placed egg's hologram sits: centred on the block, just above it. */
    public static Location hologramLocation(Block block) {
        return block.getLocation().add(0.5, 1.1, 0.5);
    }

    /** What happened when a player tried to place an egg. */
    public record Placement(Status status, String reason, long totalMillis) {
        public enum Status { PLACED, REFUSED }

        static Placement placed(long totalMillis) {
            return new Placement(Status.PLACED, "", totalMillis);
        }

        static Placement refused(String reason) {
            return new Placement(Status.REFUSED, reason, 0);
        }

        public boolean allowed() {
            return status == Status.PLACED;
        }
    }
}
