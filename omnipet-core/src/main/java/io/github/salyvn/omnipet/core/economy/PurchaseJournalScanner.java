package io.github.salyvn.omnipet.core.economy;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class PurchaseJournalScanner {
    private static final int MAX_SCAN_LIMIT = 1_000;
    private static final int DEFAULT_MAX_SCANNED_ENTRIES = 10_000;
    private static final int MAX_REPORTED_ISSUES = 20;
    private static final int MAX_INVALID_NAME_CHECKS = 64;

    private final Path root;
    private final PurchaseJournalYamlCodec codec;
    private final int maxScannedEntries;

    PurchaseJournalScanner(Path root, PurchaseJournalYamlCodec codec) {
        this(root, codec, DEFAULT_MAX_SCANNED_ENTRIES);
    }

    PurchaseJournalScanner(Path root, PurchaseJournalYamlCodec codec, int maxScannedEntries) {
        this.root = root;
        this.codec = codec;
        if (maxScannedEntries < 1) throw new IllegalArgumentException("journal scan budget must be positive");
        this.maxScannedEntries = maxScannedEntries;
    }

    PurchaseJournalScanResult scan(Set<SlotPurchaseSagaState> states, int limit) throws IOException {
        return scan(states, limit, null);
    }

    PurchaseJournalScanResult scan(
            Set<SlotPurchaseSagaState> states,
            int limit,
            String cursorText) throws IOException {
        if (states == null || states.isEmpty()) throw new IllegalArgumentException("journal scan states are required");
        if (limit < 1 || limit > MAX_SCAN_LIMIT) {
            throw new IllegalArgumentException("journal scan limit is outside the supported range");
        }
        PurchaseJournalScanCursor cursor = PurchaseJournalScanCursor.parse(cursorText);
        rejectSymbolicLink(root);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return new PurchaseJournalScanResult(List.of(), List.of(), false);
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("purchase journal root is not a directory: " + root);
        }

        IssueCollector issues = new IssueCollector();
        if (cursorText == null) inspectInvalidNames(issues);
        Bucket bucket = loadBucket(cursor.prefix());
        if (bucket.overflowed()) {
            return result(List.of(), issues, cursor.childToken());
        }

        ArrayList<Path> entries = bucket.entries();
        entries.sort(Comparator.comparing(path -> PurchaseJournalScanCursor.compactId(
                path.getFileName().toString())));
        ArrayList<SlotPurchaseTransaction> transactions = new ArrayList<>();
        String nextCursor = null;
        boolean completedBucket = true;
        for (int index = 0; index < entries.size(); index++) {
            Path entry = entries.get(index);
            String name = entry.getFileName().toString();
            String compactId = PurchaseJournalScanCursor.compactId(name);
            if (cursor.afterCompactId() != null
                    && compactId.compareTo(cursor.afterCompactId()) <= 0) {
                continue;
            }
            SlotPurchaseTransaction transaction = readEntry(entry, name, issues);
            if (transaction == null || !states.contains(transaction.state())) continue;
            transactions.add(transaction);
            if (transactions.size() == limit) {
                completedBucket = index == entries.size() - 1;
                nextCursor = completedBucket
                        ? cursor.nextPrefixToken()
                        : cursor.continuationToken(compactId);
                break;
            }
        }
        if (transactions.size() < limit || completedBucket && nextCursor == null) {
            nextCursor = cursor.nextPrefixToken();
        }
        return result(transactions, issues, nextCursor);
    }

    private PurchaseJournalScanResult result(
            List<SlotPurchaseTransaction> transactions,
            IssueCollector issues,
            String nextCursor) {
        boolean truncated = nextCursor != null || issues.truncated() || issues.omittedCount() > 0;
        return new PurchaseJournalScanResult(
                transactions,
                issues.reported(),
                truncated,
                issues.omittedCount(),
                issues.truncated(),
                nextCursor);
    }

    private Bucket loadBucket(String prefix) throws IOException {
        ArrayList<Path> entries = new ArrayList<>(Math.min(maxScannedEntries + 1, 1_024));
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(
                root,
                new PurchaseJournalScanCursor(prefix, null).uuidGlob())) {
            for (Path entry : paths) {
                entries.add(entry);
                if (entries.size() > maxScannedEntries) return new Bucket(entries, true);
            }
        }
        return new Bucket(entries, false);
    }

    private void inspectInvalidNames(IssueCollector issues) throws IOException {
        int budget = Math.min(maxScannedEntries, MAX_INVALID_NAME_CHECKS);
        int examined = 0;
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(root, "*.yml")) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                if (examined >= budget) {
                    issues.markTruncated();
                    break;
                }
                Path entry = iterator.next();
                examined++;
                String name = entry.getFileName().toString();
                if (PurchaseJournalScanCursor.compactId(name) == null) {
                    issues.add(name, "journal filename is not a canonical transaction UUID");
                }
            }
        }
    }

    private SlotPurchaseTransaction readEntry(
            Path entry,
            String name,
            IssueCollector issues) {
        try {
            rejectSymbolicLink(entry);
            if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("entry is not a regular file");
            }
            UUID expectedId = UUID.fromString(name.substring(0, name.length() - 4));
            SlotPurchaseTransaction transaction = codec.decode(PurchaseJournalFileReader.readUtf8(entry));
            if (!expectedId.equals(transaction.transactionId())) {
                throw new IOException("transaction ID does not match filename");
            }
            return transaction;
        } catch (IOException | RuntimeException failure) {
            String detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            issues.add(name, detail);
            return null;
        }
    }

    private static void rejectSymbolicLink(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw new IOException("symbolic link is not allowed: " + path);
        }
    }

    private static String limited(String detail) {
        return detail.length() <= 512 ? detail : detail.substring(0, 512);
    }

    private static String limitedEntryName(String name) {
        if (name == null || name.isBlank()) return "<unreadable>";
        return name.length() <= 128 ? name : name.substring(0, 125) + "...";
    }

    private record Bucket(ArrayList<Path> entries, boolean overflowed) {}

    private static final class IssueCollector {
        private final ArrayList<PurchaseJournalIssue> reported = new ArrayList<>();
        private int omittedCount;
        private boolean scanTruncated;

        void add(String entryName, String detail) {
            if (reported.size() < MAX_REPORTED_ISSUES) {
                reported.add(new PurchaseJournalIssue(limitedEntryName(entryName), limited(detail)));
            } else {
                omittedCount++;
            }
        }

        void markTruncated() {
            scanTruncated = true;
        }

        List<PurchaseJournalIssue> reported() {
            return reported;
        }

        int omittedCount() {
            return omittedCount;
        }

        boolean truncated() {
            return scanTruncated;
        }
    }
}
