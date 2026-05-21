package org.example;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 2)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class HashMapBenchmark {

    private static final int KEY_RANGE = 1_000;

    private CustomHashMap<Integer, Integer> customMap;
    private Map<Integer, Integer> syncHashMap;
    private ConcurrentHashMap<Integer, Integer> concurrentHashMap;

    @Setup
    public void setup() {
        customMap = new CustomHashMap<>();
        syncHashMap = Collections.synchronizedMap(new HashMap<>());
        concurrentHashMap = new ConcurrentHashMap<>();
        for (int i = 0; i < KEY_RANGE; i++) {
            customMap.put(i, i);
            syncHashMap.put(i, i);
            concurrentHashMap.put(i, i);
        }
    }

    @Benchmark
    @Threads(1)
    public void singlePut_Custom(Blackhole bh) {
        final int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        bh.consume(customMap.put(k, k));
    }

    @Benchmark
    @Threads(1)
    public void singlePut_HashMap(Blackhole bh) {
        final int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        bh.consume(syncHashMap.put(k, k));
    }

    @Benchmark
    @Threads(1)
    public void singlePut_ConcurrentHashMap(Blackhole bh) {
        final int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        bh.consume(concurrentHashMap.put(k, k));
    }

    @Benchmark
    @Threads(1)
    public void singleGet_Custom(Blackhole bh) {
        bh.consume(customMap.get(ThreadLocalRandom.current().nextInt(KEY_RANGE)));
    }

    @Benchmark
    @Threads(1)
    public void singleGet_HashMap(Blackhole bh) {
        bh.consume(syncHashMap.get(ThreadLocalRandom.current().nextInt(KEY_RANGE)));
    }

    @Benchmark
    @Threads(1)
    public void singleGet_ConcurrentHashMap(Blackhole bh) {
        bh.consume(concurrentHashMap.get(ThreadLocalRandom.current().nextInt(KEY_RANGE)));
    }

    @Benchmark
    @Threads(8)
    public void concPut_Custom(Blackhole bh) {
        final int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        bh.consume(customMap.put(k, k));
    }

    @Benchmark
    @Threads(8)
    public void concPut_HashMap(Blackhole bh) {
        final int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        bh.consume(syncHashMap.put(k, k));
    }

    @Benchmark
    @Threads(8)
    public void concPut_ConcurrentHashMap(Blackhole bh) {
        final int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        bh.consume(concurrentHashMap.put(k, k));
    }

    @Benchmark
    @Threads(8)
    public void concGet_Custom(Blackhole bh) {
        bh.consume(customMap.get(ThreadLocalRandom.current().nextInt(KEY_RANGE)));
    }

    @Benchmark
    @Threads(8)
    public void concGet_HashMap(Blackhole bh) {
        bh.consume(syncHashMap.get(ThreadLocalRandom.current().nextInt(KEY_RANGE)));
    }

    @Benchmark
    @Threads(8)
    public void concGet_ConcurrentHashMap(Blackhole bh) {
        bh.consume(concurrentHashMap.get(ThreadLocalRandom.current().nextInt(KEY_RANGE)));
    }

    @Benchmark
    @Threads(8)
    public void concMixed_Custom(Blackhole bh) {
        final int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (ThreadLocalRandom.current().nextInt(5) == 0) {
            bh.consume(customMap.put(k, k));
        } else {
            bh.consume(customMap.get(k));
        }
    }

    @Benchmark
    @Threads(8)
    public void concMixed_HashMap(Blackhole bh) {
        final int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (ThreadLocalRandom.current().nextInt(5) == 0) {
            bh.consume(syncHashMap.put(k, k));
        } else {
            bh.consume(syncHashMap.get(k));
        }
    }

    @Benchmark
    @Threads(8)
    public void concMixed_ConcurrentHashMap(Blackhole bh) {
        final int k = ThreadLocalRandom.current().nextInt(KEY_RANGE);
        if (ThreadLocalRandom.current().nextInt(5) == 0) {
            bh.consume(concurrentHashMap.put(k, k));
        } else {
            bh.consume(concurrentHashMap.get(k));
        }
    }
}
