package io.github.salyvn.omnipet.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * SnakeYAML's {@code Yaml} is not thread-safe, and the plugin reads YAML from async tasks while the
 * main thread reads too. These loads run against the shared entry points from many threads at once
 * and assert every decode comes back exactly as written — a shared-instance race corrupts decodes
 * rather than failing loudly, so the assertions are the point.
 */
class YamlDocumentsConcurrencyTest {
    private static final int THREADS = 8;
    private static final int ROUNDS = 200;

    @Test
    void concurrentReadsOfTheSameAndDistinctDocumentsStayExact() throws Exception {
        String shared = """
                slots: 5
                pets:
                  ember:
                    level: 3
                    stats: { attack: 12.5 }
                """;
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int thread = 0; thread < THREADS; thread++) {
                int id = thread;
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                        for (int round = 0; round < ROUNDS; round++) {
                            Map<String, Object> decodedShared = YamlDocuments.readMap(shared);
                            assertEquals(5, decodedShared.get("slots"),
                                    "shared document drifted on thread " + id);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> pets = (Map<String, Object>) decodedShared.get("pets");
                            @SuppressWarnings("unchecked")
                            Map<String, Object> ember = (Map<String, Object>) pets.get("ember");
                            assertEquals(3, ember.get("level"),
                                    "nested value drifted on thread " + id);

                            String distinct = "worker: " + id + "\nround: " + round + "\n";
                            Map<String, Object> decodedDistinct = YamlDocuments.readMap(distinct);
                            assertEquals(id, decodedDistinct.get("worker"),
                                    "distinct document crossed threads");
                            assertEquals(round, decodedDistinct.get("round"),
                                    "distinct document crossed rounds");

                            String roundTrip = YamlDocuments.writeMap(decodedDistinct);
                            assertEquals(id, YamlDocuments.readMap(roundTrip).get("worker"),
                                    "write/read round trip drifted on thread " + id);
                        }
                    } catch (Throwable failure) {
                        failures.add(failure);
                    }
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertTrue(failures.isEmpty(),
                () -> "concurrent YAML use failed: " + failures.get(0));
    }
}
