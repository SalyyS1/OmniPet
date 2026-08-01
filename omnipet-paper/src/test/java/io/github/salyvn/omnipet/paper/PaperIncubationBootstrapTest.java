package io.github.salyvn.omnipet.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.github.salyvn.omnipet.paper.incubation.PaperIncubationServices;

class PaperIncubationBootstrapTest {
    @Test
    void pluginOwnsAndBindsTheCanonicalIncubationServices() throws Exception {
        Field field = OmniPetPlugin.class.getDeclaredField("incubation");
        assertEquals(PaperIncubationServices.class, field.getType());

        String source = Files.readString(pluginSource());
        assertTrue(source.contains("PaperIncubationServices.open(dataRoot, playerStates)"));
        assertTrue(source.contains("incubation.petReferences()"));
        assertTrue(source.contains("incubation.eggDefinitionCount()"));
    }

    private static Path pluginSource() {
        Path module = Path.of("src/main/java/io/github/salyvn/omnipet/paper/OmniPetPlugin.java");
        return Files.exists(module) ? module : Path.of("omnipet-paper").resolve(module);
    }
}
