package org.example;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;

public class CustomHashMap<K, V> implements Iterable<Map.Entry<K, V>> {

    private static final int DEFAULT_CAPACITY = 16;

    private volatile AtomicReferenceArray<Node<K, V>> table;
    private final LongAdder size = new LongAdder();
    private final ReentrantReadWriteLock tableLock = new ReentrantReadWriteLock();

    private static final class Node<K, V> {
        final K key;
        final int hash;
        final AtomicReference<V> value;
        final Node<K, V> next;

        Node(K key, int hash, V value, Node<K, V> next) {
            this.key = key;
            this.hash = hash;
            this.value = new AtomicReference<>(value);
            this.next = next;
        }
    }

    public CustomHashMap() {
        this(DEFAULT_CAPACITY);
    }

    public CustomHashMap(int initialCapacity) {
        this.table = new AtomicReferenceArray<>(nextPowerOfTwo(initialCapacity));
    }

    public V get(K key) {
        final int hash = calc_hash(key);
        final AtomicReferenceArray<Node<K, V>> t = table;
        for (Node<K, V> e = t.get(bucketIndex(hash, t.length())); e != null; e = e.next) {
            if (e.hash == hash && Objects.equals(e.key, key))
                return e.value.get();
        }
        return null;
    }

    public V put(K key, V value) {
        final int hash = calc_hash(key);
        final Lock readLock = tableLock.readLock();
        readLock.lock();
        try {
            return putCAS(hash, key, value);
        } finally {
            readLock.unlock();
            maybeGrow();
        }
    }

    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        final int hash = calc_hash(key);
        final Lock readLock = tableLock.readLock();
        readLock.lock();
        try {
            return mergeCAS(hash, key, value, remappingFunction);
        } finally {
            readLock.unlock();
            maybeGrow();
        }
    }

    public int size() {
        return (int) size.sum();
    }

    public void clear() {
        final Lock writeLock = tableLock.writeLock();
        writeLock.lock();
        try {
            table = new AtomicReferenceArray<>(DEFAULT_CAPACITY);
            size.reset();
        } finally {
            writeLock.unlock();
        }
    }

    private V putCAS(int hash, K key, V value) {
        final AtomicReferenceArray<Node<K, V>> t = table;
        final int i = bucketIndex(hash, t.length());
        while (true) {
            final Node<K, V> head = t.get(i);
            for (Node<K, V> e = head; e != null; e = e.next) {
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    V prev;
                    do {
                        prev = e.value.get();
                    } while (!e.value.compareAndSet(prev, value));
                    return prev;
                }
            }
            if (t.compareAndSet(i, head, new Node<>(key, hash, value, head))) {
                size.increment();
                return null;
            }
        }
    }

    private V mergeCAS(int hash, K key, V value,
            BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        final AtomicReferenceArray<Node<K, V>> t = table;
        final int i = bucketIndex(hash, t.length());
        while (true) {
            final Node<K, V> head = t.get(i);
            for (Node<K, V> e = head; e != null; e = e.next) {
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    V prev, merged;
                    do {
                        prev = e.value.get();
                        merged = remappingFunction.apply(prev, value);
                    } while (!e.value.compareAndSet(prev, merged));
                    return merged;
                }
            }
            if (t.compareAndSet(i, head, new Node<>(key, hash, value, head))) {
                size.increment();
                return value;
            }
        }
    }

    private void maybeGrow() {
        if (size.sum() < (long) (table.length() * 0.5))
            return;
        final Lock writeLock = tableLock.writeLock();
        writeLock.lock();
        try {
            if (size.sum() < (long) (table.length() * 0.5))
                return;
            final AtomicReferenceArray<Node<K, V>> old = table;
            final int newCap = old.length() * 2;
            final AtomicReferenceArray<Node<K, V>> grown = new AtomicReferenceArray<>(newCap);
            for (int i = 0; i < old.length(); i++) {
                for (Node<K, V> e = old.get(i); e != null; e = e.next) {
                    final int ni = e.hash & (newCap - 1);
                    grown.set(ni, new Node<>(e.key, e.hash, e.value.get(), grown.get(ni)));
                }
            }
            table = grown;
        } finally {
            writeLock.unlock();
        }
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
            return new AbstractMap.SimpleImmutableEntry<>(e.key, e.value.get());
        }
    }
}
