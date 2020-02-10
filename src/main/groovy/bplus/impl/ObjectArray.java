package bplus.impl;

import bplus.*;

public class ObjectArray<K extends Comparable<K>,V> implements NodeStore<K,V> {

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

    public Node<K,V> getRoot() {
        return root;
    }

    public void setRoot(final Node<K,V> val) {
        root = val;
    }

    private class Base {
        private int _size = 0;
        protected final Object[] ary;

        protected Base(final int arySize) {
            this.ary = new Object[arySize];
        }
        
        public int size() { return _size; }
        public void size(final int sz) { _size = sz; }

        public Branch<K,V> newBranch() {
            return new _Branch();
        }

        public Leaf<K,V> newLeaf() {
            return new _Leaf();
        }

        public int order() { return order; }
        public void done() {}

        protected int check(final int index) {
            if(index >= size()) {
                throw new IndexOutOfBoundsException();
            }
            
            return index;
        }
    }

    private class _Branch extends Base implements Branch<K,V> {
        protected _Branch() { super(1 + 2 * order); }

        public Branch<K,V> put(final int index, final Node<K,V> left, final K k, final Node<K,V> right) {
            check(index);
            ary[leftIndex(index)] = left;
            ary[keyIndex(index)] = k;
            ary[rightIndex(index)] = right;
            return this;
        }

        public K key(final int index) { return keyType.cast(ary[keyIndex(check(index))]); }
        public Node<K,V> left(final int index) { return extractNode(leftIndex(check(index))); }
        public Node<K,V> right(final int index) { return extractNode(rightIndex(check(index))); }

        public Branch<K,V> copy(final int argSrcPos, final Node<K,V> argSrc, final int argDestPos, final int argLength) {
            final ObjectArray._Branch src = extract(argSrc.asBranch());
            final int srcIndex = leftIndex(argSrcPos);
            final int destIndex = leftIndex(argDestPos);
            final int length = copyLength(argSrcPos, src, argDestPos, argLength);
            System.arraycopy(src.ary, srcIndex, ary, destIndex, length);
            return this;
        }
        
        private int leftIndex(final int index) { return index << 1; }
        private int keyIndex(final int index) { return leftIndex(index) + 1; }
        private int rightIndex(final int index) { return leftIndex(index) + 2; }

        private ObjectArray._Branch extract(final Branch<K,V> branch) {
            if(branch instanceof ObjectArray._Branch) {
                return (ObjectArray._Branch) branch;
            }
            else {
                throw new IllegalArgumentException("source node is not the correct type");
            }
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
    
    private class _Leaf extends Base implements Leaf<K,V> {
        protected _Leaf() { super(2 * order); }

        public Leaf<K,V> put(final int index, final K k, final V v) {
            check(index);
            ary[keyIndex(index)] = k;
            ary[valueIndex(index)] = v;
            return this;
        }

        public Leaf<K,V> copy(final int argSrcPos, final Node<K,V> argSrc, final int argDestPos, final int argLength) {
            final ObjectArray._Leaf src = extract(argSrc.asLeaf());
            final int srcIndex = keyIndex(argSrcPos);
            final int destIndex = keyIndex(argDestPos);
            final int length = argLength << 1;
            System.arraycopy(src.ary, srcIndex, ary, destIndex, length);
            return this;
        }
        
        public K key(final int index) { return keyType.cast(ary[keyIndex(check(index))]); }
        public V value(final int index) { return valueType.cast(ary[valueIndex(check(index))]); }
        private int keyIndex(final int index) { return index << 1; }
        private int valueIndex(final int index) { return keyIndex(index) + 1; }

        private ObjectArray._Leaf extract(final Leaf<K,V> arg) {
            if(arg instanceof ObjectArray._Leaf) {
                return (ObjectArray._Leaf) arg;
            }
            else {
                throw new IllegalArgumentException("source node is not the correct type");
            }
        }
    }
}
