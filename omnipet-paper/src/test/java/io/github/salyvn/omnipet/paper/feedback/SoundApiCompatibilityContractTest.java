package io.github.salyvn.omnipet.paper.feedback;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards against calling a Paper API whose declaring type changed kind between versions.
 *
 * <p>This exists because of a real production failure. {@code org.bukkit.Sound} is an {@code enum} on
 * Paper 1.21 and 1.21.1 but an {@code interface} from 1.21.11 onward. Code compiled as
 * {@code Sound.valueOf(name)} against the older API emits a {@code Methodref} constant; loading that
 * class on the newer line throws
 * {@code IncompatibleClassChangeError: Method 'org.bukkit.Sound org.bukkit.Sound.valueOf(String)' must
 * be InterfaceMethodref constant} and the plugin fails to enable at all.
 *
 * <p>A compile probe does not catch it — compiling against 1.21.11 succeeds, it merely warns about
 * deprecation. Only the compiled reference kind differs, so the guard has to be on the source calls.
 * `Registry.SOUNDS.get(NamespacedKey)` has an identical shape on both lines and is the safe lookup.
 */
class SoundApiCompatibilityContractTest {
    private static final Path MAIN = Path.of("src/main/java/io/github/salyvn/omnipet/paper");

    @Test
    void noSourceCallsSoundValueOfOrValues() throws IOException {
        List<String> offenders = sources()
                .filter(path -> {
                    String source = stripComments(read(path));
                    return source.contains("Sound.valueOf(") || source.contains("Sound.values(");
                })
                .map(Path::toString)
                .toList();

        assertTrue(offenders.isEmpty(),
                "Sound is an enum on Paper 1.21 and an interface from 1.21.11, so valueOf/values "
                        + "compile to a Methodref that fails at class load with "
                        + "IncompatibleClassChangeError: " + offenders);
    }

    @Test
    void soundNamesResolveThroughTheRegistryInstead() throws IOException {
        String settings = read(MAIN.resolve("feedback/FeedbackSettings.java"));

        assertTrue(settings.contains("SoundResolver"),
                "resolution goes through the seam, not a version-fragile static call");

        String resolver = read(MAIN.resolve("feedback/SoundResolver.java"));
        assertTrue(resolver.contains("Registry.SOUNDS.get("),
                "Registry.SOUNDS.get(NamespacedKey) is shape-identical on both Paper lines");
        assertTrue(resolver.contains("NamespacedKey.fromString("),
                "a malformed key must resolve to null rather than throwing into startup");
    }

    @Test
    void resolutionStillHappensOnceAtLoadAndNotPerClick() throws IOException {
        String service = read(MAIN.resolve("feedback/FeedbackService.java"));

        assertFalse(service.contains("Registry.SOUNDS"),
                "a registry lookup per click would put I/O-shaped work in a click handler");
        assertFalse(service.contains("Sound.valueOf("), service);
    }

    /**
     * The compiled reference kind is what actually fails, so this asserts on bytecode rather than on
     * source text. Any {@code invokevirtual} or {@code invokestatic} against {@code org/bukkit/Sound}
     * is a latent {@code IncompatibleClassChangeError}: the class compiles against the 1.21 enum and
     * throws when loaded on 1.21.11, where {@code Sound} is an interface. Calls must go through
     * {@code Keyed} or {@code Registry}, which are interfaces on both lines.
     */
    @Test
    void noCompiledClassInvokesSoundNonVirtually() throws IOException {
        Path classes = Path.of("build/classes/java/main/io/github/salyvn/omnipet/paper");
        if (!Files.isDirectory(classes)) return;

        List<String> offenders = new ArrayList<>();
        try (var walk = Files.walk(classes)) {
            for (Path candidate : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                for (String reference : constantPoolMethodRefs(Files.readAllBytes(candidate))) {
                    if (reference.startsWith("org/bukkit/Sound.")) {
                        offenders.add(candidate.getFileName() + " -> " + reference);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "a non-interface reference to org.bukkit.Sound fails at class load on Paper 1.21.11+: "
                        + offenders);
    }

    /**
     * Extracts {@code Methodref} (tag 10) targets — deliberately not {@code InterfaceMethodref}
     * (tag 11), which is the safe form — by walking the constant pool once, so no bytecode library is
     * needed.
     */
    private static List<String> constantPoolMethodRefs(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.getInt();
        buffer.getShort();
        buffer.getShort();
        int count = buffer.getShort() & 0xFFFF;
        String[] utf8 = new String[count];
        int[] classNameIndex = new int[count];
        int[][] methodRefs = new int[count][];
        int[][] nameAndType = new int[count][];
        for (int index = 1; index < count; index++) {
            int tag = buffer.get() & 0xFF;
            switch (tag) {
                case 1 -> {
                    byte[] text = new byte[buffer.getShort() & 0xFFFF];
                    buffer.get(text);
                    utf8[index] = new String(text, java.nio.charset.StandardCharsets.UTF_8);
                }
                // Class: a single UTF8 index naming the type.
                case 7 -> classNameIndex[index] = buffer.getShort() & 0xFFFF;
                // Methodref: the unsafe form this test hunts for.
                case 10 -> methodRefs[index] =
                        new int[] {buffer.getShort() & 0xFFFF, buffer.getShort() & 0xFFFF};
                case 12 -> nameAndType[index] =
                        new int[] {buffer.getShort() & 0xFFFF, buffer.getShort() & 0xFFFF};
                // String, MethodType, Module, Package: one index.
                case 8, 16, 19, 20 -> buffer.getShort();
                // MethodHandle: a kind byte plus one index.
                case 15 -> { buffer.get(); buffer.getShort(); }
                // Long and Double occupy two pool slots.
                case 5, 6 -> { buffer.getLong(); index++; }
                // Fieldref, InterfaceMethodref, Integer, Float, Dynamic, InvokeDynamic: two shorts.
                case 9, 11, 3, 4, 17, 18 -> { buffer.getShort(); buffer.getShort(); }
                default -> throw new AssertionError("unexpected constant pool tag " + tag);
            }
        }
        List<String> found = new ArrayList<>();
        for (int index = 1; index < count; index++) {
            int[] reference = methodRefs[index];
            if (reference == null) continue;
            String owner = utf8[classNameIndex[reference[0]]];
            int[] member = nameAndType[reference[1]];
            if (owner != null && member != null) found.add(owner + "." + utf8[member[0]]);
        }
        return found;
    }

    /**
     * Strips comments so a doc block that names the rejected API — as the fix deliberately does, to
     * stop someone reintroducing it — cannot fail the guard that protects it.
     */
    private static String stripComments(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
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
