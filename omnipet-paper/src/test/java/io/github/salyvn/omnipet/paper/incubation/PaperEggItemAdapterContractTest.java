package io.github.salyvn.omnipet.paper.incubation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PaperEggItemAdapterContractTest {
    @Test
    void codecUsesCanonicalAndLegacyPdcWithDurableAmountOneBytes() throws Exception {
        String source = Files.readString(source("PaperEggItemCodec.java"));

        assertTrue(source.contains("\"egg\""));
        assertTrue(source.contains("\"item_nonce\""));
        assertTrue(source.contains("\"item_schema\""));
        assertTrue(source.contains("passivepet:egg"));
        assertTrue(source.contains("normalized.setAmount(1)"));
        assertTrue(source.contains("serializeAsBytes()"));
        assertTrue(source.contains("ItemStack.deserializeBytes(payload)"));
    }

    @Test
    void inventoryScansOnlyStorageAndOffhandAndGuardsEveryOperation() throws Exception {
        String source = Files.readString(source("PaperPlayerEggInventory.java"));

        assertTrue(source.contains("STORAGE_SIZE = 36"));
        assertTrue(source.contains("OFF_HAND_SLOT = 40"));
        assertTrue(source.contains("threadGuard.check()"));
        assertTrue(source.contains("getHeldItemSlot()"));
    }

    private static Path source(String name) {
        Path module = Path.of("src/main/java/io/github/salyvn/omnipet/paper/incubation").resolve(name);
        return Files.exists(module) ? module : Path.of("omnipet-paper").resolve(module);
    }
}
