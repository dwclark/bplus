package bplus.impl;

import bplus.*;

public class ObjectArray<K extends Comparable<K>,V> implements NodeFactory<K,V> {

    private final Class<K> keyType;
    private final Class<V> valueType;
    private final Node<K> root;
    private final int order;

    public ObjectArray(final Class<K> keyType, final Class<V> valueType, final int order) {
        this.keyType = keyType;
        this.valueType = valueType;
        this.order = order;
        this.root = new _Leaf();
    }

    public Branch<K> newBranch() {
        return new _Branch();
    }

    public Leaf<K,V> newLeaf() {
        return new _Leaf();
    }

    public Node<K> getRoot() {
        return root;
    }

    private class _Branch implements Branch<K> {
        final Object[] ary = new Object[1 + 2 * order];
        private int _size = 0;
        
        public int size() { return _size; }
        public Node<K> size(final int sz) { _size = sz; return this; }
        public int order() { return order; }

        public Branch<K> put(final int index, final Node<K> left, final K k, final Node<K> right) {
            ary[leftIndex(index)] = left;
            ary[keyIndex(index)] = k;
            ary[rightIndex(index)] = right;
            return this;
        }

        public void done() {}
        public K key(final int index) { return keyType.cast(ary[keyIndex(index)]); }
        public Node<K> left(final int index) { return extractNode(leftIndex(index)); }
        public Node<K> right(final int index) { return extractNode(rightIndex(index)); }

        public Branch<K> copy(final int argSrcPos, final Branch<K> src, final int argDestPos, final int argLength) {
            if(src instanceof ObjectArray._Branch) {
                final int srcIndex = leftIndex(argSrcPos);
                final int destIndex = leftIndex(argDestPos);
                final int length = (2 * argLength) + ((argDestPos + argLength == size()) ? 1 : 0);
                System.arraycopy(src, srcIndex, this, destIndex, length);
            }
            else {
                slowCopy(argSrcPos, src, argDestPos, argLength);
            }

            return this;
        }
        
        private int leftIndex(final int index) { return index << 1; }
        private int keyIndex(final int index) { return leftIndex(index) + 1; }
        private int rightIndex(final int index) { return leftIndex(index) + 2; }

        @SuppressWarnings("unchecked")
        private Node<K> extractNode(final int actualIndex) {
            return (Node<K>) ary[actualIndex];
        }
    }
    
    private class _Leaf implements Leaf<K,V> {
        final Object[] ary = new Object[2 * order];
        private int _size = 0;

        public int size() { return _size; }
        public Node<K> size(final int sz) { _size = sz; return this; }
        public int order() { return order; }
        public void done() {}

        public Leaf<K,V> put(final int index, final K k, final V v) {
            ary[keyIndex(index)] = k;
            ary[valueIndex(index)] = v;
            return this;
        }
        
        public K key(final int index) { return keyType.cast(ary[keyIndex(index)]); }
        public V value(final int index) { return valueType.cast(ary[valueIndex(index)]); }
        private int keyIndex(final int index) { return index << 1; }
        private int valueIndex(final int index) { return keyIndex(index) + 1; }
    }
}
