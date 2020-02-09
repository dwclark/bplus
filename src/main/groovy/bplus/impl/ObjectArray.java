package bplus.impl;

import bplus.*;
import java.util.function.Consumer;

public class ObjectArray<K extends Comparable<K>,V> implements NodeFactory<K,V> {

    private final Class<K> keyType;
    private final Class<V> valueType;
    private final int order;
    
    private Node<K,V> root;

    public ObjectArray(final Class<K> keyType, final Class<V> valueType, final int order) {
        this.keyType = keyType;
        this.valueType = valueType;
        this.order = order;
        this.root = new _Leaf();
    }

    public Branch<K,V> newBranch() {
        return new _Branch();
    }

    public Leaf<K,V> newLeaf() {
        return new _Leaf();
    }

    public Node<K,V> getRoot() {
        return root;
    }

    public void setRoot(final Node<K,V> val) {
        root = val;
    }

    private class _Branch implements Branch<K,V> {
        final Object[] ary = new Object[1 + 2 * order];
        private int _size = 0;
        
        public int size() { return _size; }
        public Node<K,V> size(final int sz) { _size = sz; return this; }
        public int order() { return order; }

        public Branch<K,V> put(final int index, final Node<K,V> left, final K k, final Node<K,V> right) {
            ary[leftIndex(index)] = left;
            ary[keyIndex(index)] = k;
            ary[rightIndex(index)] = right;
            return this;
        }

        public void done() {}
        public K key(final int index) { return keyType.cast(ary[keyIndex(index)]); }
        public Node<K,V> left(final int index) { return extractNode(leftIndex(index)); }
        public Node<K,V> right(final int index) { return extractNode(rightIndex(index)); }

        public Branch<K,V> copy(final int argSrcPos, final Node<K,V> argSrc, final int argDestPos, final int argLength) {
            final ObjectArray._Branch src = extractSameType(argSrc.asBranch());
            if(src != null) {
                final int srcIndex = leftIndex(argSrcPos);
                final int destIndex = leftIndex(argDestPos);
                final int length = copyLength(argSrcPos, src, argDestPos, argLength);
                System.arraycopy(src.ary, srcIndex, ary, destIndex, length);
            }
            else {
                slowCopy(argSrcPos, argSrc.asBranch(), argDestPos, argLength);
            }

            return this;
        }
        
        private int leftIndex(final int index) { return index << 1; }
        private int keyIndex(final int index) { return leftIndex(index) + 1; }
        private int rightIndex(final int index) { return leftIndex(index) + 2; }

        private ObjectArray._Branch extractSameType(final Branch<K,V> branch) {
            return (branch instanceof ObjectArray._Branch) ? (ObjectArray._Branch) branch : null;
        }

        private int copyLength(final int srcPos, final ObjectArray._Branch src, final int destPos, final int length) {
            final int base = (length << 1);
            if(srcPos + length == src.size()) {
                return base + 1;
            }
            else if(destPos + length == size()) {
                return base + 1;
            }
            else {
                return base;
            }
        }

        @SuppressWarnings("unchecked")
        private Node<K,V> extractNode(final int actualIndex) {
            return (Node<K,V>) ary[actualIndex];
        }
    }
    
    private class _Leaf implements Leaf<K,V> {
        final Object[] ary = new Object[2 * order];
        private int _size = 0;

        public int size() { return _size; }
        public Node<K,V> size(final int sz) { _size = sz; return this; }
        public int order() { return order; }
        public void done() {}

        public Leaf<K,V> put(final int index, final K k, final V v) {
            ary[keyIndex(index)] = k;
            ary[valueIndex(index)] = v;
            return this;
        }

        public Leaf<K,V> copy(final int argSrcPos, final Node<K,V> argSrc, final int argDestPos, final int argLength) {
            final ObjectArray._Leaf src = extractSameType(argSrc.asLeaf());
            if(src != null) {
                final int srcIndex = keyIndex(argSrcPos);
                final int destIndex = keyIndex(argDestPos);
                final int length = argLength << 1;
                System.arraycopy(src.ary, srcIndex, ary, destIndex, length);
            }
            else {
                slowCopy(argSrcPos, argSrc.asLeaf(), argDestPos, argLength);
            }

            return this;
        }
        
        public K key(final int index) { return keyType.cast(ary[keyIndex(index)]); }
        public V value(final int index) { return valueType.cast(ary[valueIndex(index)]); }
        private int keyIndex(final int index) { return index << 1; }
        private int valueIndex(final int index) { return keyIndex(index) + 1; }

        private ObjectArray._Leaf extractSameType(final Leaf<K,V> arg) {
            return (arg instanceof ObjectArray._Leaf) ? (ObjectArray._Leaf) arg : null;
        }
    }
}
