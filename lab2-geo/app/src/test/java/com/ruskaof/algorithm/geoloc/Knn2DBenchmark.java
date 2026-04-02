package com.ruskaof.algorithm.geoloc;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(3)
public class Knn2DBenchmark {

    private static final double SPAN = 10_000.0;

    @Param({ "500", "1000", "1500", "2000", "2500", "3000" })
    public int pointCount;

    public int nearestK = 5;

    private Knn2D<Long> linearKnn;
    private Random random;
    private double[] queryXs;
    private double[] queryYs;

    @Setup(Level.Trial)
    public void setup() {
        random = new Random();
        List<KdItem<Long>> points = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            double x = random.nextDouble() * SPAN;
            double y = random.nextDouble() * SPAN;
            points.add(new KdItem<>(x, y, (long) i));
        }

        linearKnn = Knn2D.from(points);

        int queries = 10_000;
        queryXs = new double[queries];
        queryYs = new double[queries];
        for (int i = 0; i < queries; i++) {
            queryXs[i] = random.nextDouble() * SPAN;
            queryYs[i] = random.nextDouble() * SPAN;
        }
    }

    @Benchmark
    public List<KdItem<Long>> benchmarkNearestQueriesLinearKnn() {
        int i = random.nextInt(queryXs.length);
        return linearKnn.findNearest(queryXs[i], queryYs[i], nearestK);
    }
}
