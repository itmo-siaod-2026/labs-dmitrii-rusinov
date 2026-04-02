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

class KdTree2DTest {

    @Test
    void addRejectsNull() {
        KdTree2D<String> tree = new KdTree2D<>();
        assertThrows(NullPointerException.class, () -> tree.add(null));
    }

    @Test
    void geoObjectRejectsNullPayload() {
        assertThrows(NullPointerException.class, () -> new KdItem<>(0, 0, null));
    }

    @Test
    void emptyTreeNearestReturnsEmpty() {
        KdTree2D<Integer> tree = new KdTree2D<>();
        assertTrue(tree.findNearest(0, 0, 5).isEmpty());
        assertEquals(0, tree.size());
    }

    @Test
    void buildBalancedEmpty() {
        KdTree2D<Integer> tree = KdTree2D.buildBalanced(List.of());
        assertEquals(0, tree.size());
        assertTrue(tree.findNearest(0, 0, 1).isEmpty());
    }

    @Test
    void buildBalancedRejectsNullList() {
        assertThrows(NullPointerException.class, () -> KdTree2D.buildBalanced(null));
    }

    @Test
    void buildBalancedRejectsNullElement() {
        List<KdItem<Integer>> list = new ArrayList<>();
        list.add(new KdItem<>(0, 0, 1));
        list.add(null);
        assertThrows(NullPointerException.class, () -> KdTree2D.buildBalanced(list));
    }

    @Test
    void nonPositiveNReturnsEmpty() {
        KdTree2D<Integer> tree = new KdTree2D<>();
        tree.add(new KdItem<>(1, 2, 1));
        assertTrue(tree.findNearest(0, 0, 0).isEmpty());
        assertTrue(tree.findNearest(0, 0, -1).isEmpty());
    }

    @Test
    void singlePoint() {
        KdTree2D<String> tree = new KdTree2D<>();
        tree.add(new KdItem<>(10, 20, "a"));
        List<KdItem<String>> near = tree.findNearest(0, 0, 3);
        assertEquals(1, near.size());
        assertEquals("a", near.get(0).payload());
        assertEquals(1, tree.size());
    }

    @Test
    void nearestOrderingSmallGrid() {
        KdTree2D<String> tree = new KdTree2D<>();
        tree.add(new KdItem<>(0, 0, "origin"));
        tree.add(new KdItem<>(100, 0, "far"));
        tree.add(new KdItem<>(3, 4, "close"));

        List<KdItem<String>> near = tree.findNearest(0, 0, 2);
        assertEquals(2, near.size());
        assertEquals("origin", near.get(0).payload());
        assertEquals("close", near.get(1).payload());
    }

    @Test
    void nGreaterThanSizeReturnsAllSortedByDistance() {
        KdTree2D<Integer> tree = new KdTree2D<>();
        tree.add(new KdItem<>(0, 0, 1));
        tree.add(new KdItem<>(10, 0, 2));
        tree.add(new KdItem<>(5, 0, 3));

        List<KdItem<Integer>> near = tree.findNearest(0, 0, 100);
        assertEquals(3, near.size());
        assertEquals(1, (int) near.get(0).payload());
        assertEquals(3, (int) near.get(1).payload());
        assertEquals(2, (int) near.get(2).payload());
    }

    @Test
    void buildBalancedMatchesBruteForceOnRandomData() {
        Random random = new Random();
        int numPoints = 500;
        List<KdItem<Integer>> points = new ArrayList<>(numPoints);
        for (int i = 0; i < numPoints; i++) {
            points.add(new KdItem<>(random.nextDouble() * 1000, random.nextDouble() * 1000, i));
        }

        KdTree2D<Integer> tree = KdTree2D.buildBalanced(points);
        assertEquals(numPoints, tree.size());

        for (int trial = 0; trial < 200; trial++) {
            double qx = random.nextDouble() * 1000;
            double qy = random.nextDouble() * 1000;
            int k = 1 + random.nextInt(40);

            List<KdItem<Integer>> expected = bruteForceNearest(points, qx, qy, k);
            List<KdItem<Integer>> actual = tree.findNearest(qx, qy, k);

            assertEquals(expected.size(), actual.size(), "trial " + trial);
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i).payload(), actual.get(i).payload(), "trial " + trial + " rank " + i);
            }
        }
    }

    @Test
    void matchesBruteForceOnRandomData() {
        Random random = new Random();
        int numPoints = 500;
        List<KdItem<Integer>> points = new ArrayList<>(numPoints);
        for (int i = 0; i < numPoints; i++) {
            points.add(new KdItem<>(random.nextDouble() * 1000, random.nextDouble() * 1000, i));
        }

        KdTree2D<Integer> tree = new KdTree2D<>();
        for (KdItem<Integer> p : points) {
            tree.add(p);
        }
        assertEquals(numPoints, tree.size());

        for (int trial = 0; trial < 200; trial++) {
            double qx = random.nextDouble() * 1000;
            double qy = random.nextDouble() * 1000;
            int k = 1 + random.nextInt(40);

            List<KdItem<Integer>> expected = bruteForceNearest(points, qx, qy, k);
            List<KdItem<Integer>> actual = tree.findNearest(qx, qy, k);

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
