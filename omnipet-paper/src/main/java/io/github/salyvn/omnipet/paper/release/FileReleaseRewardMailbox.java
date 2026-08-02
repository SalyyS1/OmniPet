package io.github.salyvn.omnipet.paper.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;

import io.github.salyvn.omnipet.core.persistence.AtomicFileStore;

public final class FileReleaseRewardMailbox implements ReleaseRewardMailbox {
    private static final int MAX_FILE_BYTES = 16 * 1024;
    private static final int MAX_SCAN_FILES = 10_000;

    private final Path root;
    private final AtomicFileStore files = new AtomicFileStore();

    public FileReleaseRewardMailbox(Path root) {
        if (root == null) throw new IllegalArgumentException("release mailbox root is required");
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public synchronized Optional<ReleaseMailboxEntry> find(UUID transactionId) throws IOException {
        Path path = path(transactionId);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        return Optional.of(read(path));
    }

    @Override
    public synchronized ReleaseMailboxEntry create(ReleaseMailboxEntry entry) throws IOException {
        Optional<ReleaseMailboxEntry> existing = find(entry.transactionId());
        if (existing.isPresent()) {
            if (!sameEntitlement(existing.get(), entry)) {
                throw new IOException("release mailbox transaction identity mismatch");
            }
            return existing.get();
        }
        write(entry);
        return entry;
    }

    @Override
    public synchronized ReleaseMailboxEntry update(
            UUID transactionId,
            ReleaseMailboxEntry.State state,
            String detail) throws IOException {
        ReleaseMailboxEntry current = find(transactionId)
                .orElseThrow(() -> new IOException("release mailbox transaction not found"));
        ReleaseMailboxEntry updated = current.withState(state, detail);
        write(updated);
        return updated;
    }

    @Override
    public synchronized List<ReleaseMailboxEntry> list(int limit) throws IOException {
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("release mailbox limit must be 1..50");
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return List.of();
        ArrayList<Path> paths = new ArrayList<>();
        try (var stream = Files.list(root)) {
            stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".mailbox"))
                    .limit(MAX_SCAN_FILES + 1L)
                    .forEach(paths::add);
        }
        if (paths.size() > MAX_SCAN_FILES) throw new IOException("release mailbox scan exceeds safe bound");
        paths.sort(Comparator.comparing(path -> path.getFileName().toString()));
        ArrayList<ReleaseMailboxEntry> entries = new ArrayList<>();
        for (Path path : paths) {
            ReleaseMailboxEntry entry = read(path);
            if (entry.state() != ReleaseMailboxEntry.State.INVENTORY_DELIVERED) entries.add(entry);
            if (entries.size() == limit) break;
        }
        return List.copyOf(entries);
    }

    private ReleaseMailboxEntry read(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("release mailbox entry is not a regular file");
        }
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("release mailbox file exceeds 16 KiB");
        String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\\R");
        if (lines.length < 5 || !"schema=1".equals(lines[0])) throw new IOException("invalid release mailbox schema");
        UUID transactionId = UUID.fromString(value(lines[1], "transaction="));
        UUID playerId = UUID.fromString(value(lines[2], "player="));
        ReleaseMailboxEntry.State state = ReleaseMailboxEntry.State.valueOf(value(lines[3], "state="));
        List<PaperMaterialReward> rewards = parseRewards(value(lines[4], "rewards="));
        String detail = lines.length > 5 ? decode(value(lines[5], "detail=")) : "";
        if (!path.getFileName().toString().equals(transactionId + ".mailbox")) {
            throw new IOException("release mailbox filename identity mismatch");
        }
        return new ReleaseMailboxEntry(transactionId, playerId, state, rewards, detail);
    }

    private void write(ReleaseMailboxEntry entry) throws IOException {
        String rewards = entry.rewards().stream()
                .map(reward -> reward.material().name() + ":" + reward.amount())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        String text = "schema=1\ntransaction=" + entry.transactionId()
                + "\nplayer=" + entry.playerId()
                + "\nstate=" + entry.state()
                + "\nrewards=" + rewards
                + "\ndetail=" + encode(entry.detail()) + "\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("release mailbox file exceeds 16 KiB");
        files.write(path(entry.transactionId()), bytes);
    }

    private Path path(UUID transactionId) {
        if (transactionId == null) throw new IllegalArgumentException("release transaction UUID is required");
        Path path = root.resolve(transactionId + ".mailbox").normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("release mailbox path escapes root");
        return path;
    }

    private static List<PaperMaterialReward> parseRewards(String value) throws IOException {
        if (value.isBlank()) return List.of();
        ArrayList<PaperMaterialReward> rewards = new ArrayList<>();
        for (String token : value.split(",")) {
            String[] parts = token.split(":", 2);
            if (parts.length != 2) throw new IOException("invalid release mailbox reward");
            Material material = Material.matchMaterial(parts[0]);
            if (material == null) throw new IOException("unknown release mailbox material");
            rewards.add(new PaperMaterialReward(material, Long.parseLong(parts[1])));
        }
        return List.copyOf(rewards);
    }

    private static String value(String line, String prefix) throws IOException {
        if (!line.startsWith(prefix)) throw new IOException("invalid release mailbox field: " + prefix);
        return line.substring(prefix.length());
    }

    private static boolean sameEntitlement(ReleaseMailboxEntry left, ReleaseMailboxEntry right) {
        return left.transactionId().equals(right.transactionId())
                && left.playerId().equals(right.playerId())
                && left.rewards().equals(right.rewards());
    }

    private static String encode(String value) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) throws IOException {
        try {
            return new String(java.util.Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid release mailbox detail", invalid);
        }
    }
}
