package io.github.salyvn.omnipet.paper.gui.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.core.runtime.InteractionIdentity;

/**
 * Right-click routing, with the off-hand double-open as the headline case.
 *
 * <p>{@link PlayerInteractEntityEvent} fires once per hand. Without the hand filter one right-click
 * opens the management screen twice, and the second open trips the controller's concurrency guard, so
 * the player sees "Another management request is already processing." on every legitimate click. A
 * burst test would not catch this — it has to be exercised per hand.
 */
class PetInteractListenerTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID petId = UUID.randomUUID();
    private final UUID entityId = UUID.randomUUID();
    private final Map<UUID, InteractionIdentity> index = new HashMap<>();
    private final List<UUID> opened = new ArrayList<>();

    @Test
    void oneRightClickOpensExactlyOneManagementScreenAcrossBothHands() {
        index.put(entityId, new InteractionIdentity(owner, petId, 4));
        PetInteractListener listener = listener();
        Player player = player(owner);

        // Paper delivers both of these for a single click.
        PlayerInteractEntityEvent mainHand = event(player, entityId, EquipmentSlot.HAND);
        PlayerInteractEntityEvent offHand = event(player, entityId, EquipmentSlot.OFF_HAND);
        listener.onRightClick(mainHand);
        listener.onRightClick(offHand);

        assertEquals(List.of(petId), opened, "the off-hand event must be filtered, not handled twice");
        assertTrue(mainHand.isCancelled());
        assertFalse(offHand.isCancelled(), "a filtered event must be left entirely alone");
    }

    @Test
    void clickingYourOwnPetOpensItsManagementScreen() {
        index.put(entityId, new InteractionIdentity(owner, petId, 1));

        PlayerInteractEntityEvent event = event(player(owner), entityId, EquipmentSlot.HAND);
        listener().onRightClick(event);

        assertEquals(List.of(petId), opened);
        assertTrue(event.isCancelled(), "our own entity must not also perform its vanilla interaction");
    }

    @Test
    void clickingANonOmniPetEntityIsLeftUncancelledSoOtherPluginsAreUnaffected() {
        // The index is empty: this is a vanilla mob or another plugin's entity.
        PlayerInteractEntityEvent event = event(player(owner), UUID.randomUUID(), EquipmentSlot.HAND);

        listener().onRightClick(event);

        assertTrue(opened.isEmpty());
        assertFalse(event.isCancelled(), "cancelling an unknown entity would break other plugins");
    }

    @Test
    void aStaleRendererGenerationResolvesAsNotOurs() {
        // The lookup rejects a leftover entry from a previous render, so the listener never sees it.
        PetInteractListener listener = new PetInteractListener(id -> Optional.empty(), this::open);

        PlayerInteractEntityEvent event = event(player(owner), entityId, EquipmentSlot.HAND);
        listener.onRightClick(event);

        assertTrue(opened.isEmpty());
        assertFalse(event.isCancelled());
    }

    @Test
    void clickingAnotherPlayersPetOpensNothing() {
        index.put(entityId, new InteractionIdentity(owner, petId, 1));
        UUID stranger = UUID.randomUUID();

        PlayerInteractEntityEvent event = event(player(stranger), entityId, EquipmentSlot.HAND);
        listener().onRightClick(event);

        assertTrue(opened.isEmpty(), "a pet belongs to its owner");
        // Still cancelled: it is our entity, so its vanilla interaction stays suppressed regardless of
        // who clicked it.
        assertTrue(event.isCancelled());
    }

    @Test
    void onlyThePlainInteractEventIsRegistered() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/salyvn/omnipet/paper/gui/pet/PetInteractListener.java"));

        // PlayerInteractAtEntityEvent has its own HandlerList despite extending
        // PlayerInteractEntityEvent, so handling both would deliver two events for one click. The
        // class comment names it as the rejected option, so only the code is checked here.
        assertFalse(source.contains("import org.bukkit.event.player.PlayerInteractAtEntityEvent"),
                "the At-variant must not be imported, let alone handled");
        assertFalse(source.contains("(PlayerInteractAtEntityEvent"), "no handler may take the At-variant");
        assertEquals(1, count(source, "@EventHandler"), "one handler, one event");
        assertTrue(source.contains("event.getHand() != EquipmentSlot.HAND"),
                "the hand filter is the guard this phase exists to get right");
    }

    @Test
    void theCoordinatorDelegatesLookupWithoutKeepingASecondIndex() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/salyvn/omnipet/paper/runtime/PaperPetRuntimeCoordinator.java"));

        assertTrue(source.contains("interactions.resolve(entityId)"), "petFor must delegate");
        // A parallel map could disagree with the index the activation service maintains, and an
        // earlier design that copied the identity would have dropped rendererGeneration entirely.
        assertFalse(source.contains("Map<UUID, InteractionIdentity>"), "no second index may exist");
        assertFalse(source.contains("synchronized Optional<InteractionIdentity> petFor"),
                "resolve is already synchronized; locking again adds nothing");
    }

    @Test
    void noParallelEntityIndexWasIntroducedAnywhere() throws IOException {
        try (var walk = Files.walk(Path.of("src/main/java"))) {
            List<String> offenders = walk
                    .filter(path -> path.getFileName().toString().equals("PetEntityRef.java")
                            || path.getFileName().toString().equals("PetEntityIndex.java"))
                    .map(Path::toString)
                    .toList();
            assertTrue(offenders.isEmpty(), "InteractionIndex already ships: " + offenders);
        }
    }

    @Test
    void ridingRemainsUnshippped() throws IOException {
        try (var walk = Files.walk(Path.of("src/main/java"))) {
            List<Path> withRideService = walk
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains("RideService"))
                    .toList();
            assertTrue(withRideService.isEmpty(),
                    "entity click ships; riding stays deferred: " + withRideService);
        }
    }

    private PetInteractListener listener() {
        return new PetInteractListener(id -> Optional.ofNullable(index.get(id)), this::open);
    }

    private void open(Player player, UUID pet) {
        opened.add(pet);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new AssertionError(error);
        }
    }

    private static int count(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static PlayerInteractEntityEvent event(Player player, UUID clickedId, EquipmentSlot hand) {
        return new PlayerInteractEntityEvent(player, entity(clickedId), hand);
    }

    /** Minimal stand-ins: this repo has no MockBukkit and hand-rolls proxies for Bukkit interfaces. */
    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "toString" -> "player:" + id;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException("Player." + method.getName());
                });
    }

    private static Entity entity(UUID id) {
        return (Entity) Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class<?>[] {Entity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "toString" -> "entity:" + id;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException("Entity." + method.getName());
                });
    }
}
