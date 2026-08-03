package io.github.salyvn.omnipet.paper.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards the deduplications this phase performed, so a future change cannot quietly reintroduce a
 * second copy of a formatter.
 */
class SharedFormatterContractTest {
    private static final Path MAIN = Path.of("src/main/java/io/github/salyvn/omnipet/paper");

    @Test
    void exactlyOneFileFormatsACountdown() throws IOException {
        List<String> owners = sources()
                .filter(path -> read(path).contains("86_400"))
                .map(path -> path.getFileName().toString())
                .toList();

        assertEquals(List.of("Durations.java"), owners,
                "two copies of a countdown format string drift, and one screen ends up counting "
                        + "differently from another");
    }

    @Test
    void exactlyOneFileFormatsADecimal() throws IOException {
        List<String> owners = sources()
                .filter(path -> read(path).contains("stripTrailingZeros"))
                .map(path -> path.getFileName().toString())
                .toList();

        assertEquals(List.of("Durations.java"), owners);
    }

    @Test
    void noControllerKeepsItsOwnEnumToTextConversion() throws IOException {
        List<String> offenders = sources()
                .filter(path -> !path.getFileName().toString().equals("Displays.java"))
                .filter(path -> read(path).contains("name().toLowerCase(java.util.Locale.ROOT).replace")
                        || read(path).contains("name().toLowerCase(Locale.ROOT).replace"))
                .map(Path::toString)
                .toList();

        // Inconsistent capitalisation across call sites was the original symptom: the same status
        // reached players differently depending on which controller reported it.
        assertTrue(offenders.isEmpty(), "enum text belongs to Displays: " + offenders);
    }

    @Test
    void bothCountdownCallSitesUseTheSharedHelper() throws IOException {
        for (String renderer : List.of("gui/hatch/HatchMenuRenderer.java", "gui/hub/HubMenuRenderer.java")) {
            String source = read(MAIN.resolve(renderer));

            assertTrue(source.contains("Durations.countdown("), renderer + " must use the shared helper");
            assertFalse(source.contains("private static String format(long millis)"),
                    renderer + " must not keep its own copy");
        }
    }

    @Test
    void bothDecimalCallSitesUseTheSharedHelper() throws IOException {
        for (String file : List.of(
                "gui/player/PetManagementMenuRenderer.java", "studio/bukkit/StudioStatScreens.java")) {
            assertTrue(read(MAIN.resolve(file)).contains("Durations.decimal("), file);
        }
    }

    private static Stream<Path> sources() throws IOException {
        try (var walk = Files.walk(MAIN)) {
            return walk.filter(path -> path.toString().endsWith(".java")).toList().stream();
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new AssertionError(error);
        }
    }
}
