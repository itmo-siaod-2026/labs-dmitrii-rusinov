package org.example;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 2)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class ScalingBenchmark {

    private static final int KEY_POOL_SIZE = 500;

    @Param({"5000", "7000", "9000", "11000"})
    public int tableSize;

    private CustomHashMap<Integer, Integer> map;
    private int[] keyPool;

    @Setup
    public void setup() {
        map = new CustomHashMap<>(tableSize);
        for (int i = 0; i < tableSize; i++) {
            map.put(i, i);
        }
        keyPool = new int[KEY_POOL_SIZE];
        for (int i = 0; i < KEY_POOL_SIZE; i++) {
            keyPool[i] = i;
        }
    }

    @Benchmark
    public void scalingGet(Blackhole bh) {
        bh.consume(map.get(keyPool[ThreadLocalRandom.current().nextInt(KEY_POOL_SIZE)]));
    }

    @Benchmark
    public void scalingPut(Blackhole bh) {
        final int k = ThreadLocalRandom.current().nextInt(tableSize);
        bh.consume(map.put(k, k));
    }

    @Benchmark
    public void scalingMerge(Blackhole bh) {
        bh.consume(map.merge(keyPool[ThreadLocalRandom.current().nextInt(KEY_POOL_SIZE)], 1, Integer::sum));
    }
}
