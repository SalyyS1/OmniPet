package io.github.salyvn.omnipet.core.persistence;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.github.salyvn.omnipet.core.domain.StableId;

final class YamlPetDefinitionFiles {
    private final SafeRepositoryPaths paths;

    YamlPetDefinitionFiles(SafeRepositoryPaths paths) {
        this.paths = paths;
    }

    Optional<DefinitionFile> find(String id) throws IOException {
        String validId = StableId.requireValid(id);
        String foldedId = StableId.folded(validId);
        List<DefinitionFile> matches = new ArrayList<>();
        for (DefinitionFile file : scan()) {
            if (StableId.folded(file.id()).equals(foldedId)) matches.add(file);
        }
        if (matches.size() > 1) throw ambiguousDefinition(matches.get(0), matches.get(1));
        if (matches.isEmpty()) return Optional.empty();
        DefinitionFile match = matches.get(0);
        if (!match.id().equals(validId)) {
            throw new IOException("case-folded pet definition ID collision: " + validId + " conflicts with " + match.id());
        }
        return Optional.of(match);
    }

    List<String> listIds() throws IOException {
        LinkedHashMap<String, DefinitionFile> filesByFoldedId = new LinkedHashMap<>();
        for (DefinitionFile file : scan()) {
            String folded = StableId.folded(file.id());
            DefinitionFile duplicate = filesByFoldedId.putIfAbsent(folded, file);
            if (duplicate != null) throw ambiguousDefinition(duplicate, file);
        }
        List<String> ids = new ArrayList<>();
        filesByFoldedId.values().forEach(file -> ids.add(file.id()));
        ids.sort(Comparator.naturalOrder());
        return List.copyOf(ids);
    }

    Path defaultPath(String id) throws IOException {
        return paths.resolveId(id, ".yml");
    }

    private List<DefinitionFile> scan() throws IOException {
        if (!Files.isDirectory(paths.root(), LinkOption.NOFOLLOW_LINKS)) return List.of();
        List<DefinitionFile> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(paths.root())) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                String fileName = path.getFileName().toString();
                String lower = fileName.toLowerCase(Locale.ROOT);
                int extensionLength;
                if (lower.endsWith(".yaml")) {
                    extensionLength = 5;
                } else if (lower.endsWith(".yml")) {
                    extensionLength = 4;
                } else {
                    continue;
                }
                String id = StableId.requireValid(fileName.substring(0, fileName.length() - extensionLength));
                files.add(new DefinitionFile(id, paths.requireSafe(path)));
            }
        }
        return files;
    }

    private static IOException ambiguousDefinition(DefinitionFile first, DefinitionFile second) {
        return new IOException("ambiguous pet definition files for case-folded ID " + first.id() + ": "
                + first.path().getFileName() + " and " + second.path().getFileName());
    }

    record DefinitionFile(String id, Path path) {}
}
