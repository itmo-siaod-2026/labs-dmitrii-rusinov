package com.ruskaof.algorithm.geoloc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Knn2DTest {

    @Test
    void fromRejectsNullList() {
        assertThrows(NullPointerException.class, () -> Knn2D.from(null));
    }

    @Test
    void fromRejectsNullElement() {
        List<KdItem<Integer>> list = new ArrayList<>();
        list.add(new KdItem<>(0, 0, 1));
        list.add(null);
        assertThrows(NullPointerException.class, () -> Knn2D.from(list));
    }

    @Test
    void emptyNearestReturnsEmpty() {
        Knn2D<Integer> knn = Knn2D.from(List.of());
        assertTrue(knn.findNearest(0, 0, 5).isEmpty());
        assertEquals(0, knn.size());
    }

    @Test
    void nonPositiveNReturnsEmpty() {
        Knn2D<Integer> knn = Knn2D.from(List.of(new KdItem<>(1, 2, 1)));
        assertTrue(knn.findNearest(0, 0, 0).isEmpty());
        assertTrue(knn.findNearest(0, 0, -1).isEmpty());
    }

    @Test
    void inputListNotMutated() {
        List<KdItem<String>> list = new ArrayList<>();
        list.add(new KdItem<>(1, 1, "a"));
        Knn2D.from(list);
        list.clear();
        Knn2D<String> knn = Knn2D.from(List.of(new KdItem<>(2, 2, "b")));
        assertEquals(1, knn.size());
    }

    @Test
    void matchesBruteForceOnRandomData() {
        Random random = new Random();
        int numPoints = 500;
        List<KdItem<Integer>> points = new ArrayList<>(numPoints);
        for (int i = 0; i < numPoints; i++) {
            points.add(new KdItem<>(random.nextDouble() * 1000, random.nextDouble() * 1000, i));
        }

        Knn2D<Integer> knn = Knn2D.from(points);
        assertEquals(numPoints, knn.size());

        for (int trial = 0; trial < 200; trial++) {
            double qx = random.nextDouble() * 1000;
            double qy = random.nextDouble() * 1000;
            int k = 1 + random.nextInt(40);

            List<KdItem<Integer>> expected = bruteForceNearest(points, qx, qy, k);
            List<KdItem<Integer>> actual = knn.findNearest(qx, qy, k);

            assertEquals(expected.size(), actual.size(), "trial " + trial);
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i).payload(), actual.get(i).payload(), "trial " + trial + " rank " + i);
            }
        }
    }

    private static <T> List<KdItem<T>> bruteForceNearest(List<KdItem<T>> points, double qx, double qy, int k) {
        int n = Math.min(k, points.size());
        return points.stream()
                .sorted(Comparator.comparingDouble(p -> dist2(qx, qy, p.x(), p.y())))
                .limit(n)
                .collect(Collectors.toList());
    }

    private static double dist2(double ax, double ay, double bx, double by) {
        double dx = ax - bx;
        double dy = ay - by;
        return dx * dx + dy * dy;
    }
}
