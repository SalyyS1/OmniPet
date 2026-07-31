package io.github.salyvn.omnipet.paper.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.paper.OmniPetPlugin;
import io.github.salyvn.omnipet.paper.task.PerPlayerTaskQueue;

class PlayerTaskOwnershipTest {
    @Test
    void pluginOwnsTheQueueAndBothControllersRequireInjection() throws Exception {
        Field pluginQueue = OmniPetPlugin.class.getDeclaredField("playerTasks");
        assertEquals(PerPlayerTaskQueue.class, pluginQueue.getType());

        assertInjectedQueue(PlayerPetController.class, "taskQueue");
        assertInjectedQueue(PlayerSlotPurchaseController.class, "taskQueue");
    }

    @Test
    void controllerReadKeysAreNamespacedAcrossFeatures() throws Exception {
        assertEquals("vault:view", constant(PlayerPetController.class, "VAULT_VIEW_TASK"));
        assertEquals("slot:view", constant(PlayerSlotPurchaseController.class, "VIEW_TASK"));
    }

    @Test
    void storageReconcileUsesMutationSubmissionAndHasNoCoalesceKey() throws Exception {
        String source = Files.readString(controllerSource());

        assertFalse(source.contains("LIMIT_RECONCILE_TASK"));
        int reconcile = source.indexOf("public void reconcile(Player player)");
        int release = source.indexOf("public void release(UUID playerId)", reconcile);
        String method = source.substring(reconcile, release);
        assertTrue(method.contains("taskQueue.submit(playerId"));
        assertFalse(method.contains("submitLatest"));
    }

    private static void assertInjectedQueue(Class<?> controller, String fieldName) throws Exception {
        Field field = controller.getDeclaredField(fieldName);
        assertEquals(PerPlayerTaskQueue.class, field.getType());
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertTrue(Arrays.stream(controller.getConstructors())
                .allMatch(constructor -> Arrays.asList(constructor.getParameterTypes())
                        .contains(PerPlayerTaskQueue.class)));
    }

    private static String constant(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static Path controllerSource() {
        Path module = Path.of("src/main/java/io/github/salyvn/omnipet/paper/player/PlayerPetController.java");
        return Files.exists(module)
                ? module
                : Path.of("omnipet-paper").resolve(module);
    }
}
