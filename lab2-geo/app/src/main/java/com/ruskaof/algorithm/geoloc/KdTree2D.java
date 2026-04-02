package com.ruskaof.algorithm.geoloc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

public final class KdTree2D<T> {

    private static final class Node<T> {
        final double x;
        final double y;
        final T payload;
        Node<T> left;
        Node<T> right;

        Node(KdItem<T> object) {
            this.x = object.x();
            this.y = object.y();
            this.payload = object.payload();
        }
    }

    private static final class Neighbor<T> {
        final double dist2;
        final KdItem<T> object;

        Neighbor(double dist2, KdItem<T> object) {
            this.dist2 = dist2;
            this.object = object;
        }
    }

    private Node<T> root;
    private int size;

    public int size() {
        return size;
    }

    public static <T> KdTree2D<T> buildBalanced(List<KdItem<T>> objects) {
        Objects.requireNonNull(objects, "objects must not be null");
        for (KdItem<T> o : objects) {
            Objects.requireNonNull(o, "object must not be null");
        }
        KdTree2D<T> tree = new KdTree2D<>();
        if (objects.isEmpty()) {
            return tree;
        }
        ArrayList<KdItem<T>> copy = new ArrayList<>(objects);
        tree.root = buildBalanced(copy, 0, copy.size() - 1, 0);
        tree.size = copy.size();
        return tree;
    }

    private static <T> Node<T> buildBalanced(List<KdItem<T>> points, int from, int to, int depth) {
        if (from > to) {
            return null;
        }
        int dim = depth & 1;
        Comparator<KdItem<T>> cmp = dim == 0
                ? Comparator.comparingDouble(KdItem::x)
                : Comparator.comparingDouble(KdItem::y);
        points.subList(from, to + 1).sort(cmp);
        int mid = (from + to) >>> 1;
        Node<T> node = new Node<>(points.get(mid));
        node.left = buildBalanced(points, from, mid - 1, depth + 1);
        node.right = buildBalanced(points, mid + 1, to, depth + 1);
        return node;
    }

    public void add(KdItem<T> object) {
        Objects.requireNonNull(object, "object must not be null");
        root = insert(root, object, 0);
        size++;
    }

    private Node<T> insert(Node<T> node, KdItem<T> object, int depth) {
        if (node == null) {
            return new Node<>(object);
        }
        int dim = depth & 1;
        double v = dim == 0 ? object.x() : object.y();
        double cut = dim == 0 ? node.x : node.y;
        if (v < cut) {
            node.left = insert(node.left, object, depth + 1);
        } else {
            node.right = insert(node.right, object, depth + 1);
        }
        return node;
    }

    public List<KdItem<T>> findNearest(double x, double y, int n) {
        if (n <= 0) {
            return List.of();
        }
        if (root == null) {
            return List.of();
        }
        int k = Math.min(n, size);
        PriorityQueue<Neighbor<T>> queue = new PriorityQueue<>(
                Comparator.comparingDouble((Neighbor<T> a) -> a.dist2).reversed());

        nearest(root, x, y, 0, queue, k);

        List<KdItem<T>> out = new ArrayList<>(queue.size());
        while (!queue.isEmpty()) {
            out.add(queue.poll().object);
        }
        out.sort(Comparator.comparingDouble(o -> dist2(x, y, o.x(), o.y())));
        return out;
    }

    private void nearest(Node<T> node, double qx, double qy, int depth, PriorityQueue<Neighbor<T>> queue, int k) {
        if (node == null) {
            return;
        }

        double d2 = dist2(qx, qy, node.x, node.y);
        offer(queue, k, new Neighbor<>(d2, new KdItem<>(node.x, node.y, node.payload)));

        int dim = depth & 1;
        double planeDist;
        boolean queryLeft;
        if (dim == 0) {
            planeDist = qx - node.x;
            queryLeft = planeDist < 0;
        } else {
            planeDist = qy - node.y;
            queryLeft = planeDist < 0;
        }

        Node<T> nearChild = queryLeft ? node.left : node.right;
        Node<T> farChild = queryLeft ? node.right : node.left;

        nearest(nearChild, qx, qy, depth + 1, queue, k);

        double planeDist2 = planeDist * planeDist;
        double worst = queue.size() < k ? Double.POSITIVE_INFINITY : queue.peek().dist2;
        if (planeDist2 < worst || queue.size() < k) {
            nearest(farChild, qx, qy, depth + 1, queue, k);
        }
    }

    private static <T> void offer(PriorityQueue<Neighbor<T>> queue, int k, Neighbor<T> candidate) {
        if (queue.size() < k) {
            queue.offer(candidate);
            return;
        }
        if (candidate.dist2 < queue.peek().dist2) {
            queue.poll();
            queue.offer(candidate);
        }
    }

    private static double dist2(double ax, double ay, double bx, double by) {
        double dx = ax - bx;
        double dy = ay - by;
        return dx * dx + dy * dy;
    }
}
