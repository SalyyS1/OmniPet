package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.salyvn.omnipet.core.domain.StableId;

/** Scans provider-neutral YAML files such as eggs.yml for definition references. */
@FunctionalInterface
public interface PetReferenceScanner {
    Set<String> references(String definitionId) throws IOException;

    static PetReferenceScanner yamlFiles(List<Path> files) {
        List<Path> inputs = List.copyOf(files == null ? List.of() : files);
        return definitionId -> {
            String expected = StableId.requireValid(definitionId);
            Set<String> result = new LinkedHashSet<>();
            for (Path input : inputs) {
                Path path = input.toAbsolutePath().normalize();
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) continue;
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("reference file is not a regular file: " + path.getFileName());
                }
                Object raw = YamlDocuments.readMap(Files.readString(path, StandardCharsets.UTF_8));
                if (containsString(raw, expected)) result.add("yaml:" + path.getFileName());
            }
            return Set.copyOf(result);
        };
    }

    private static boolean containsString(Object value, String expected) {
        if (expected.equals(value)) return true;
        if (value instanceof java.util.Map<?, ?> map) return map.values().stream().anyMatch(v -> containsString(v, expected));
        if (value instanceof List<?> list) return list.stream().anyMatch(v -> containsString(v, expected));
        return false;
    }
}
