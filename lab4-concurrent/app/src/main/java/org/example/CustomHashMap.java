package org.example;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;

public class CustomHashMap<K, V> implements Iterable<Map.Entry<K, V>> {

    private static final int DEFAULT_CAPACITY = 16;
    private static final int LOCKS_COUNT = 30;

    private volatile AtomicReferenceArray<Node<K, V>> table;
    private final LongAdder size = new LongAdder();
    private final ReentrantReadWriteLock tableLock = new ReentrantReadWriteLock();
    private final ReentrantLock[] perBucketLocks;

    private static final class Node<K, V> {
        final K key;
        final int hash;
        volatile V value;
        final Node<K, V> next;

        Node(K key, int hash, V value, Node<K, V> next) {
            this.key = key;
            this.hash = hash;
            this.value = value;
            this.next = next;
        }
    }

    private static final class Lease implements AutoCloseable {
        private final Lock first;
        private final Lock second;

        Lease(Lock only) {
            this.first = only;
            this.second = null;
            only.lock();
        }

        Lease(Lock first, Lock second) {
            this.first = first;
            this.second = second;
            first.lock();
            second.lock();
        }

        @Override
        public void close() {
            if (second != null)
                second.unlock();
            first.unlock();
        }
    }

    public CustomHashMap() {
        this(DEFAULT_CAPACITY);
    }

    public CustomHashMap(int initialCapacity) {
        this.table = new AtomicReferenceArray<>(nextPowerOfTwo(initialCapacity));
        this.perBucketLocks = new ReentrantLock[LOCKS_COUNT];
        for (int i = 0; i < LOCKS_COUNT; i++) {
            perBucketLocks[i] = new ReentrantLock();
        }
    }

    public V get(K key) {
        final int hash = calc_hash(key);
        final AtomicReferenceArray<Node<K, V>> t = table;
        for (Node<K, V> e = t.get(bucketIndex(hash, t.length())); e != null; e = e.next) {
            if (e.hash == hash && Objects.equals(e.key, key))
                return e.value;
        }
        return null;
    }

    public V put(K key, V value) {
        final int hash = calc_hash(key);
        final V previous;
        try (Lease ignored = bucketLease(hash)) {
            previous = insertOrUpdate(hash, key, value);
        }
        maybeGrow();
        return previous;
    }

    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        final int hash = calc_hash(key);
        final V result;
        try (Lease ignored = bucketLease(hash)) {
            result = mergeUnderLock(hash, key, value, remappingFunction);
        }
        maybeGrow();
        return result;
    }

    public int size() {
        return (int) size.sum();
    }

    public void clear() {
        try (Lease ignored = new Lease(tableLock.writeLock())) {
            table = new AtomicReferenceArray<>(DEFAULT_CAPACITY);
            size.reset();
        }
    }

    private V insertOrUpdate(int hash, K key, V value) {
        final AtomicReferenceArray<Node<K, V>> t = table;
        final int i = bucketIndex(hash, t.length());
        for (Node<K, V> e = t.get(i); e != null; e = e.next) {
            if (e.hash == hash && Objects.equals(e.key, key)) {
                final V previous = e.value;
                e.value = value;
                return previous;
            }
        }
        t.set(i, new Node<>(key, hash, value, t.get(i)));
        size.increment();
        return null;
    }

    private V mergeUnderLock(int hash, K key, V value,
            BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        final AtomicReferenceArray<Node<K, V>> t = table;
        final int i = bucketIndex(hash, t.length());
        for (Node<K, V> e = t.get(i); e != null; e = e.next) {
            if (e.hash == hash && Objects.equals(e.key, key)) {
                final V merged = remappingFunction.apply(e.value, value);
                e.value = merged;
                return merged;
            }
        }
        t.set(i, new Node<>(key, hash, value, t.get(i)));
        size.increment();
        return value;
    }

    private void maybeGrow() {
        if (size.sum() < (long) (table.length() * 0.5))
            return;
        try (Lease ignored = new Lease(tableLock.writeLock())) {
            if (size.sum() < (long) (table.length() * 0.5))
                return;
            final AtomicReferenceArray<Node<K, V>> old = table;
            final int newCap = old.length() * 2;
            final AtomicReferenceArray<Node<K, V>> grown = new AtomicReferenceArray<>(newCap);
            for (int i = 0; i < old.length(); i++) {
                for (Node<K, V> e = old.get(i); e != null; e = e.next) {
                    final int ni = e.hash & (newCap - 1);
                    grown.set(ni, new Node<>(e.key, e.hash, e.value, grown.get(ni)));
                }
            }
            table = grown;
        }
    }

    private Lease bucketLease(int hash) {
        return new Lease(tableLock.readLock(), perBucketLocks[hash & (LOCKS_COUNT - 1)]);
    }

    private static int calc_hash(Object key) {
        if (key == null)
            return 0;
        final int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    private static int bucketIndex(int hash, int tableLength) {
        return hash & (tableLength - 1);
    }

    private static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n)
            p <<= 1;
        return p;
    }

    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
        return new EntryIterator(table);
    }

    private final class EntryIterator implements Iterator<Map.Entry<K, V>> {
        private final AtomicReferenceArray<Node<K, V>> snapshot;
        private int bucketIndex;
        private Node<K, V> next;

        EntryIterator(AtomicReferenceArray<Node<K, V>> snapshot) {
            this.snapshot = snapshot;
            advance();
        }

        private void advance() {
            while (next == null && bucketIndex < snapshot.length())
                next = snapshot.get(bucketIndex++);
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Map.Entry<K, V> next() {
            if (next == null) throw new NoSuchElementException();
            final Node<K, V> e = next;
            next = e.next;
            if (next == null) advance();
            return new AbstractMap.SimpleImmutableEntry<>(e.key, e.value);
        }
    }
}
