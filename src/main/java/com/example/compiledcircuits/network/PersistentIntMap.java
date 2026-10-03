package com.example.compiledcircuits.network;

import java.util.*;

/** Immutable AVL map. Capturing a repair target set is O(1); edits copy only a logarithmic path. */
public final class PersistentIntMap<V> {
    private record Node<V>(int key, V value, Node<V> left, Node<V> right, int height, int size) {}
    private final Node<V> root;
    public PersistentIntMap() { this(null); }
    private PersistentIntMap(Node<V> root) { this.root = root; }
    private static int height(Node<?> n) { return n == null ? 0 : n.height; }
    private static int size(Node<?> n) { return n == null ? 0 : n.size; }
    private static <V> Node<V> node(int key, V value, Node<V> left, Node<V> right) {
        return new Node<>(key, value, left, right, 1 + Math.max(height(left), height(right)), 1 + size(left) + size(right));
    }
    private static <V> Node<V> right(Node<V> n) {
        var l = n.left; return node(l.key, l.value, l.left, node(n.key, n.value, l.right, n.right));
    }
    private static <V> Node<V> left(Node<V> n) {
        var r = n.right; return node(r.key, r.value, node(n.key, n.value, n.left, r.left), r.right);
    }
    private static <V> Node<V> balance(Node<V> n) {
        if (height(n.left) - height(n.right) > 1) {
            if (height(n.left.left) < height(n.left.right)) n = node(n.key, n.value, left(n.left), n.right);
            return right(n);
        }
        if (height(n.right) - height(n.left) > 1) {
            if (height(n.right.right) < height(n.right.left)) n = node(n.key, n.value, n.left, right(n.right));
            return left(n);
        }
        return n;
    }
    public V get(int key) {
        var n = root;
        while (n != null) { if (key == n.key) return n.value; n = key < n.key ? n.left : n.right; }
        return null;
    }
    public int size() { return size(root); }
    public boolean isEmpty() { return root == null; }
    public PersistentIntMap<V> put(int key, V value) { return new PersistentIntMap<>(put(root, key, Objects.requireNonNull(value))); }
    private static <V> Node<V> put(Node<V> n, int key, V value) {
        if (n == null) return node(key, value, null, null);
        if (key == n.key) return node(key, value, n.left, n.right);
        return balance(key < n.key ? node(n.key, n.value, put(n.left, key, value), n.right)
                : node(n.key, n.value, n.left, put(n.right, key, value)));
    }
    public PersistentIntMap<V> remove(int key) { return new PersistentIntMap<>(remove(root, key)); }
    private static <V> Node<V> remove(Node<V> n, int key) {
        if (n == null) return null;
        if (key < n.key) return balance(node(n.key, n.value, remove(n.left, key), n.right));
        if (key > n.key) return balance(node(n.key, n.value, n.left, remove(n.right, key)));
        if (n.left == null) return n.right;
        if (n.right == null) return n.left;
        var successor = n.right; while (successor.left != null) successor = successor.left;
        return balance(node(successor.key, successor.value, n.left, remove(n.right, successor.key)));
    }
    public Collection<V> values() {
        return Collections.unmodifiableCollection(new AbstractCollection<>() {
            @Override public int size() { return PersistentIntMap.this.size(); }
            @Override public Iterator<V> iterator() {
                return new Iterator<>() {
                    final ArrayDeque<Node<V>> stack = new ArrayDeque<>();
                    { descend(root); }
                    private void descend(Node<V> n) { while (n != null) { stack.push(n); n = n.left; } }
                    @Override public boolean hasNext() { return !stack.isEmpty(); }
                    @Override public V next() {
                        if (stack.isEmpty()) throw new NoSuchElementException();
                        var n = stack.pop(); descend(n.right); return n.value;
                    }
                };
            }
        });
    }
}
