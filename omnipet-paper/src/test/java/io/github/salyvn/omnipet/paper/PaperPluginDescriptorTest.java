package io.github.salyvn.omnipet.paper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class PaperPluginDescriptorTest {
    @Test
    void preservesBrandingBootstrapAndDefaultPermission() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("paper-plugin.yml")) {
            assertNotNull(input);
            String descriptor = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(descriptor.contains("name: OmniPet"));
            assertTrue(descriptor.contains("authors: [SalyVn]"));
            assertTrue(descriptor.contains("main: io.github.salyvn.omnipet.paper.OmniPetPlugin"));
            assertTrue(descriptor.contains("omnipet.general:"));
            assertTrue(descriptor.contains("default: true"));
        }
    }
}
