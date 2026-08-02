package io.github.salyvn.omnipet.paper.incubation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PaperIncubationSourceContractTest {
    @Test
    void mutationsUseTheSharedNonCoalescingQueue() throws Exception {
        String start = Files.readString(source("PaperIncubationStartSaga.java"));
        String recovery = Files.readString(source("PaperIncubationRecoveryExecutor.java"));
        String coordinator = Files.readString(source("PaperIncubationCoordinator.java"));

        assertTrue(start.contains("tasks.submit(playerId"));
        assertTrue(recovery.contains("tasks.submit(playerId"));
        assertTrue(coordinator.contains("tasks.submit(playerId"));
        assertTrue(!start.contains("submitLatest"));
        assertTrue(!recovery.contains("submitLatest"));
        assertTrue(coordinator.contains("runTaskTimer"));
        assertTrue(coordinator.contains("starts.onQuit(playerId)"));
        assertTrue(recovery.contains("PENDING_STAGES"));
        assertTrue(recovery.contains("eggEscrowJournal().find(player.incubation().id())"));
        assertTrue(!recovery.contains("EnumSet.allOf"));
        assertTrue(coordinator.contains("transaction.stage() != EggEscrowStage.COMMITTED"));
        assertTrue(coordinator.contains("recovery.recover(playerId)"));
        assertTrue(start.contains("recoveryRequest.accept(playerId)"));
        assertTrue(!start.contains("cancelPreparedEscrow"));
        assertTrue(recovery.contains("scheduled.add(transaction.transactionId())"));
        assertTrue(coordinator.contains("tickBaselines.elapsedMillis"));
        assertTrue(coordinator.contains("tickBaselines.commit"));
    }

    private static Path source(String file) {
        Path direct = Path.of("src/main/java/io/github/salyvn/omnipet/paper/incubation").resolve(file);
        return Files.exists(direct)
                ? direct
                : Path.of("omnipet-paper").resolve(direct);
    }
}
