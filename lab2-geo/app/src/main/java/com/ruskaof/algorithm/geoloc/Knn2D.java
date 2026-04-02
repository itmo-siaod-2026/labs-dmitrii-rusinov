package com.ruskaof.algorithm.geoloc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

public final class Knn2D<T> {

    private static final class Neighbor<T> {
        final double dist2;
        final KdItem<T> object;

        Neighbor(double dist2, KdItem<T> object) {
            this.dist2 = dist2;
            this.object = object;
        }
    }

    private final List<KdItem<T>> points;

    private Knn2D(List<KdItem<T>> points) {
        this.points = points;
    }

    public static <T> Knn2D<T> from(List<KdItem<T>> objects) {
        Objects.requireNonNull(objects, "objects must not be null");
        for (KdItem<T> o : objects) {
            Objects.requireNonNull(o, "object must not be null");
        }
        return new Knn2D<>(new ArrayList<>(objects));
    }

    public int size() {
        return points.size();
    }

    public List<KdItem<T>> findNearest(double x, double y, int n) {
        if (n <= 0) {
            return List.of();
        }
        if (points.isEmpty()) {
            return List.of();
        }
        int k = Math.min(n, points.size());
        PriorityQueue<Neighbor<T>> heap = new PriorityQueue<>(
                Comparator.comparingDouble((Neighbor<T> a) -> a.dist2).reversed());
        for (KdItem<T> p : points) {
            double d2 = dist2(x, y, p.x(), p.y());
            offer(heap, k, new Neighbor<>(d2, p));
        }
        List<KdItem<T>> out = new ArrayList<>(heap.size());
        while (!heap.isEmpty()) {
            out.add(heap.poll().object);
        }
        out.sort(Comparator.comparingDouble(o -> dist2(x, y, o.x(), o.y())));
        return out;
    }

    private static <T> void offer(PriorityQueue<Neighbor<T>> heap, int k, Neighbor<T> candidate) {
        if (heap.size() < k) {
            heap.offer(candidate);
            return;
        }

        if (candidate.dist2 < heap.peek().dist2) {
            heap.poll();
            heap.offer(candidate);
        }
    }

    private static double dist2(double ax, double ay, double bx, double by) {
        double dx = ax - bx;
        double dy = ay - by;
        return dx * dx + dy * dy;
    }
}
