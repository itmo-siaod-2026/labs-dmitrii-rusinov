package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HashMapFunctionalTest {

    private CustomHashMap<String, Integer> map;

    @BeforeEach
    void setUp() {
        map = new CustomHashMap<>();
    }

    @Test
    void getOnEmptyMapReturnsNull() {
        assertNull(map.get("missing"));
    }

    @Test
    void putAndGetRoundTrip() {
        map.put("a", 1);
        assertEquals(1, map.get("a"));
    }

    @Test
    void putReturnsPreviousValue() {
        assertNull(map.put("a", 1));
        assertEquals(1, map.put("a", 2));
    }

    @Test
    void putOverwritesExistingKey() {
        map.put("key", 10);
        map.put("key", 20);
        assertEquals(20, map.get("key"));
    }

    @Test
    void getMissingKeyReturnsNull() {
        map.put("a", 1);
        assertNull(map.get("b"));
    }

    @Test
    void sizeReflectsInserts() {
        assertEquals(0, map.size());
        map.put("a", 1);
        assertEquals(1, map.size());
        map.put("b", 2);
        assertEquals(2, map.size());
    }

    @Test
    void sizeDoesNotGrowOnUpdate() {
        map.put("a", 1);
        map.put("a", 2);
        assertEquals(1, map.size());
    }

    @Test
    void clearRemovesAllEntries() {
        map.put("a", 1);
        map.put("b", 2);
        map.clear();
        assertEquals(0, map.size());
        assertNull(map.get("a"));
        assertNull(map.get("b"));
    }

    @Test
    void mapUsableAfterClear() {
        map.put("a", 1);
        map.clear();
        map.put("a", 99);
        assertEquals(99, map.get("a"));
    }

    @Test
    void mergeInsertsWhenKeyAbsent() {
        final Integer result = map.merge("counter", 5, Integer::sum);
        assertEquals(5, result);
        assertEquals(5, map.get("counter"));
    }

    @Test
    void mergeAppliesRemappingForExistingKey() {
        map.put("counter", 10);
        final Integer result = map.merge("counter", 3, Integer::sum);
        assertEquals(13, result);
        assertEquals(13, map.get("counter"));
    }

    @Test
    void mergeReturnsNewValue() {
        map.put("x", 100);
        final Integer returned = map.merge("x", 1, (a, b) -> a - b);
        assertEquals(99, returned);
    }

    @Test
    void nullKeyIsSupported() {
        map.put(null, 42);
        assertEquals(42, map.get(null));
        assertEquals(1, map.size());
    }

    @Test
    void resizePreservesAllEntries() {
        final int count = 200;
        for (int i = 0; i < count; i++)
            map.put("key" + i, i);
        assertEquals(count, map.size());
        for (int i = 0; i < count; i++)
            assertEquals(i, map.get("key" + i));
    }

    @Test
    void keysWithSameHashCodeCoexist() {
        map.put("Aa", 1);
        map.put("BB", 2);
        assertEquals(1, map.get("Aa"));
        assertEquals(2, map.get("BB"));
        assertEquals(2, map.size());
    }

    @Test
    void concurrentPutsProduceNoLostUpdates() throws Exception {
        final int threads = 8;
        final int keysPerThread = 1000;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int base = t * keysPerThread;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < keysPerThread; i++)
                    map.put("k" + (base + i), base + i);
            }));
        }
        for (Future<?> f : futures)
            f.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(threads * keysPerThread, map.size());
        for (int i = 0; i < threads * keysPerThread; i++) {
            assertEquals(i, map.get("k" + i), "key k" + i + " missing");
        }
    }

    @Test
    void concurrentMergeAccumulatesAllIncrements() throws Exception {
        final int threads = 8;
        final int incrementsPerThread = 500;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < incrementsPerThread; i++) {
                    map.merge("counter", 1, Integer::sum);
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures)
            f.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(threads * incrementsPerThread, map.get("counter"));
    }

    @Test
    void concurrentReadsAndWritesDoNotCorrupt() throws Exception {
        final int writerCount = 4;
        final int readerCount = 4;
        final int ops = 500;
        final ExecutorService pool = Executors.newFixedThreadPool(writerCount + readerCount);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger corruptionCount = new AtomicInteger();
        final List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < writerCount; t++) {
            final int base = t * ops;
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < ops; i++)
                    map.put("w" + (base + i), base + i);
                return null;
            }));
        }
        for (int t = 0; t < readerCount; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < ops * writerCount; i++) {
                    final Integer v = map.get("w" + i);
                    if (v != null && !v.equals(i))
                        corruptionCount.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures)
            f.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(0, corruptionCount.get(), "Corrupt reads detected");
    }
}
