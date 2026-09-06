package io.github.salyvn.omnipet.paper.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.paper.OmniPetPlugin;

class PaperRuntimeProductionWiringTest {
    @Test
    void pluginOwnsStartsReloadsAndDisablesTheOneCoordinator() throws Exception {
        Field runtime = OmniPetPlugin.class.getDeclaredField("petRuntime");
        assertEquals(PaperPetRuntimeCoordinator.class, runtime.getType());
        String source = Files.readString(source(
                "src/main/java/io/github/salyvn/omnipet/paper/OmniPetPlugin.java"));

        // The renderer settings travel with the runtime settings, so both renderers are tuned from the
        // same config rather than one of them silently keeping its built-in numbers, and idle play travels
        // beside them so the whole runtime is configured from one place. Whitespace-collapsed so a
        // line-wrap of the call does not read as a wiring change.
        String collapsed = source.replaceAll("\\s+", " ");
        assertTrue(collapsed.contains(
                "petRuntime = PaperRuntimeBootstrap.create( this, activeConfig.runtime(), "
                        + "activeConfig.render(), activeConfig.idlePlay())"), collapsed);
        assertTrue(source.contains("new PaperRuntimeSnapshotPublisher(petRuntime, registry)"));
        assertTrue(source.contains("new PlayerStorageLifecycleListener("));
        assertTrue(source.contains("managementServices::onJoin"));
        assertTrue(source.contains("petRuntime.start()"));
        assertTrue(source.contains("petRuntime.disable()"));
        assertTrue(source.contains("petRuntime.reload(registry.current())"));
    }

    @Test
    void lifecycleListenerCoversEveryRuntimeCleanupAndRefreshBoundary() throws Exception {
        String source = Files.readString(source(
                "src/main/java/io/github/salyvn/omnipet/paper/player/PlayerStorageLifecycleListener.java"));

        for (String handler : java.util.List.of(
                "onJoin(PlayerJoinEvent",
                "onKick(PlayerKickEvent",
                "onQuit(PlayerQuitEvent",
                "onTeleport(PlayerTeleportEvent",
                "onWorldChanged(PlayerChangedWorldEvent",
                "onDeath(PlayerDeathEvent",
                "onRespawn(PlayerRespawnEvent")) {
            assertTrue(source.contains(handler), handler);
        }
        assertTrue(source.contains("runtime.ownerQuit(ownerId)"));
        assertTrue(source.contains("runtime.ownerWorldChanged(ownerId)"));
        assertTrue(source.contains("controller.reconcile(event.getPlayer())"));
    }

    private static Path source(String modulePath) {
        Path local = Path.of(modulePath);
        return Files.exists(local) ? local : Path.of("omnipet-paper").resolve(local);
    }
}
